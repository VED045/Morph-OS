package ai.morphos.launcher.data

import android.app.*
import android.content.*
import android.service.notification.*
import android.security.keystore.*
import android.util.AtomicFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.security.KeyStore
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec

@Serializable data class NotificationCard(val key: String, val packageName: String, val title: String, val text: String, val at: Long, val ongoing: Boolean, val category: String)
data class ActiveNotification(val card: NotificationCard, val source: StatusBarNotification)
object NotificationRepository {
    val active = MutableStateFlow<List<ActiveNotification>>(emptyList())
    val history = MutableStateFlow<List<NotificationCard>>(emptyList())
    val connected = MutableStateFlow(false)
    private var listener: MorphNotificationListener? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    fun component(context: Context) = ComponentName(context, MorphNotificationListener::class.java)
    fun allowed(context: Context) = context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(component(context))
    fun historyEnabled(context: Context) = context.getSharedPreferences("notification_consent", Context.MODE_PRIVATE).getBoolean("history", false)
    fun setHistory(context: Context, enabled: Boolean) {
        context.getSharedPreferences("notification_consent", Context.MODE_PRIVATE).edit().putBoolean("history", enabled).apply()
        if (!enabled) clearHistory(context)
    }
    @Synchronized fun connect(service: MorphNotificationListener) {
        listener = service; connected.value = true
        active.value = emptyList()
        if (historyEnabled(service)) scope.launch { val stored = runCatching { load(service) }.getOrDefault(emptyList()); synchronized(this@NotificationRepository) { history.value = (history.value + stored).distinctBy { it.key }.sortedByDescending { it.at }.take(100) } }
        service.activeNotifications?.take(100)?.forEach { posted(service, it) }
    }
    @Synchronized fun disconnect() { listener = null; connected.value = false; active.value = emptyList(); history.value = emptyList() }
    @Synchronized fun posted(context: Context, notification: StatusBarNotification) {
        val n = notification.notification
        val card = NotificationCard(notification.key, notification.packageName,
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.take(200) ?: notification.packageName,
            n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.take(1000) ?: "", notification.postTime,
            notification.isOngoing, n.category ?: "other")
        active.value = ((active.value.filterNot { it.card.key == card.key }) + ActiveNotification(card, notification)).sortedByDescending { it.card.at }.take(100)
        if (historyEnabled(context)) {
            history.value = ((history.value.filterNot { it.key == card.key }) + card).sortedByDescending { it.at }.take(100)
            scope.launch { runCatching { save(context, history.value) } }
        }
    }
    fun removed(key: String) { active.value = active.value.filterNot { it.card.key == key } }
    fun dismiss(key: String) { runCatching { listener?.cancelNotification(key) } }
    fun clearHistory(context: Context) { history.value = emptyList(); scope.launch { synchronized(this@NotificationRepository) { file(context).delete() } } }
    private fun file(context: Context) = AtomicFile(File(context.filesDir, "notification-history.enc"))
    private fun key(): javax.crypto.SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getKey("notification_history", null) as? javax.crypto.SecretKey) ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("notification_history", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized private fun save(context: Context, cards: List<NotificationCard>) {
        if (!historyEnabled(context) || !allowed(context)) return
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val bytes = cipher.doFinal(Json.encodeToString(cards.take(100)).toByteArray(Charsets.UTF_8))
        val target = file(context); val stream = target.startWrite()
        try { stream.write(cipher.iv); stream.write(bytes); target.finishWrite(stream) } catch (e: Exception) { target.failWrite(stream); throw e }
    }
    @Synchronized private fun load(context: Context): List<NotificationCard> {
        if (!allowed(context)) return emptyList()
        val target = file(context); require(target.baseFile.length() in 28..600000)
        val input = target.readFully(); require(input.size in 28..600000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, input.copyOfRange(0, 12))) }
        return Json.decodeFromString<List<NotificationCard>>(cipher.doFinal(input.copyOfRange(12, input.size)).toString(Charsets.UTF_8)).take(100)
    }
}
class MorphNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() { NotificationRepository.connect(this) }
    override fun onListenerDisconnected() { NotificationRepository.disconnect() }
    override fun onNotificationPosted(sbn: StatusBarNotification) { NotificationRepository.posted(this, sbn) }
    override fun onNotificationRemoved(sbn: StatusBarNotification) { NotificationRepository.removed(sbn.key) }
    override fun onDestroy() { NotificationRepository.disconnect(); super.onDestroy() }
}
