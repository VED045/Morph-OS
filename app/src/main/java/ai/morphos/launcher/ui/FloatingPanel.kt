package ai.morphos.launcher.ui

import ai.morphos.launcher.MainActivity
import ai.morphos.launcher.data.NotificationRepository
import android.content.*
import android.graphics.PixelFormat
import android.os.BatteryManager
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.*
import kotlinx.coroutines.*
import androidx.compose.ui.graphics.toArgb

/** User-created, activity-owned overlay. Removed on Close or owner destruction. */
internal object FloatingPanel {
    private var remove: (() -> Unit)? = null
    fun show(activity: ComponentActivity, style: Style) {
        if (!Settings.canDrawOverlays(activity)) return
        remove?.invoke()
        val manager = activity.getSystemService(WindowManager::class.java)
        val density = activity.resources.displayMetrics.density
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(16, 16, 16, 16); setBackgroundColor(style.surface.toArgb()) }
        val title = TextView(activity).apply { text = "MorphOS / Floating"; textSize = 16f; setTextColor(style.text.toArgb()); setPadding(8, 8, 8, 12) }
        val battery = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val content = TextView(activity).apply { textSize = 13f; setTextColor(style.text.toArgb()); text = "Battery $level%" }
        val open = Button(activity).apply { text = "Open MorphOS"; setOnClickListener { activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) } }
        val close = Button(activity).apply { text = "Close panel"; setOnClickListener { remove?.invoke() } }
        root.addView(title); root.addView(content); root.addView(open); root.addView(close)
        val params = WindowManager.LayoutParams((260 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.START; x = 24; y = 160 }
        var startX = 0f; var startY = 0f; var baseX = 0; var baseY = 0
        title.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> { startX = event.rawX; startY = event.rawY; baseX = params.x; baseY = params.y; true }
                android.view.MotionEvent.ACTION_MOVE -> { params.x = (baseX + event.rawX - startX).toInt().coerceIn(0, (activity.resources.displayMetrics.widthPixels - params.width).coerceAtLeast(0)); params.y = (baseY + event.rawY - startY).toInt().coerceIn(0, (activity.resources.displayMetrics.heightPixels - root.height).coerceAtLeast(0)); runCatching { manager.updateViewLayout(root, params) }; true }
                else -> false
            }
        }
        if (runCatching { manager.addView(root, params) }.isFailure) return
        val job = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        job.launch { NotificationRepository.active.collect { notifications -> content.text = "Battery $level%\n" + notifications.take(2).joinToString("\n") { it.card.title.take(50) } } }
        lateinit var observer: LifecycleEventObserver
        remove = { job.cancel(); runCatching { manager.removeView(root) }; activity.lifecycle.removeObserver(observer); remove = null }
        observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY) remove?.invoke() }
        activity.lifecycle.addObserver(observer)
    }
}
