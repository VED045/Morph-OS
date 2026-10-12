package ai.morphos.launcher

import ai.morphos.launcher.data.ClipboardSync
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class ClipboardIntegrationTest {
    @Test fun pairedTlsTransferAndWrongCertificateRejection() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val host = args.getString("clipboardHost")
        assumeTrue("Run with the local companion fixture", host != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sync = ClipboardSync(context)
        val pin = requireNotNull(args.getString("clipboardPin"))
        val key = requireNotNull(args.getString("clipboardKey"))
        assertTrue(runCatching { sync.pair(host!!, "0".repeat(64), key) }.isFailure)
        sync.pair(host!!, pin, key)
        assertTrue(sync.paired()); assertFalse(sync.enabled.value)
        sync.poll(false)
        assertTrue(sync.clips.value.any { it.text == "Windows test to Android" })
        val image = sync.clips.value.first { it.mime == "image/png" }
        assertTrue(image.file!!.length() > 0)
        sync.sendUri(sync.uri(image))
        sync.sendText("Android test to Windows")
        sync.disconnect()
        assertFalse(sync.paired()); assertTrue(sync.clips.value.isEmpty())
    }
}
