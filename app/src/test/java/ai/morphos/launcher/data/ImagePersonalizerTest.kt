package ai.morphos.launcher.data

import ai.morphos.launcher.domain.*
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImagePersonalizerTest {
    @Test fun imagePaletteProducesBoundedThemeWithRevisionAndNoModelClaim() = runTest {
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val proposal = ImagePersonalizer.palette(Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP), 8)
        val result = DesignEngine.apply(FallbackTheme.value, proposal.patch)
        assertEquals(8, proposal.revision); assertEquals("image", proposal.source)
        assertTrue(result.monochromeIcons); assertEquals("AURORA", result.wallpaperStyle)
        assertEquals("#F6F6F6", result.dark.foreground)
    }
    @Test fun invalidImageIsRejected() = runTest {
        assertTrue(runCatching { ImagePersonalizer.palette("bm90IGFuIGltYWdl", 0) }.isFailure)
    }
    @Test fun boundedStreamRejectsPayloadInsteadOfTruncating() {
        assertEquals("abc", "abc".byteInputStream().boundedBytes(3).toString(Charsets.UTF_8))
        assertTrue(runCatching { "abcd".byteInputStream().boundedBytes(3) }.isFailure)
    }
}
