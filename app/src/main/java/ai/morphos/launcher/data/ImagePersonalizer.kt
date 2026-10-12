package ai.morphos.launcher.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.Base64
import ai.morphos.launcher.domain.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*

/** Pixel analysis, not a vision model. No image or extracted information leaves the phone. */
object ImagePersonalizer {
    suspend fun palette(image: String, revision: Int): DesignProposal = withContext(Dispatchers.Default) {
        val bytes = Base64.decode(image, Base64.DEFAULT)
        require(bytes.size in 8..1000000)
        require((bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()) || bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        val bitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        try {
            val sample = Bitmap.createScaledBitmap(bitmap, 48, 48, true)
            val bins = mutableMapOf<Int, Int>()
            for (y in 0 until 48) for (x in 0 until 48) { val c = sample.getPixel(x, y); val key = ((Color.red(c) / 32) shl 6) or ((Color.green(c) / 32) shl 3) or (Color.blue(c) / 32); bins[key] = (bins[key] ?: 0) + 1 }
            if (sample !== bitmap) sample.recycle()
            fun rgb(key: Int) = Color.rgb(((key shr 6) and 7) * 32 + 16, ((key shr 3) and 7) * 32 + 16, (key and 7) * 32 + 16)
            val dominant = rgb(bins.maxBy { it.value }.key)
            val accent = rgb(bins.maxBy { (key, count) -> val hsv = FloatArray(3); Color.colorToHSV(rgb(key), hsv); count * (0.1f + hsv[1]) * (0.2f + hsv[2]) }.key)
            fun mix(c: Int, to: Int, amount: Float): String {
                fun channel(a: Int, b: Int) = (a * (1 - amount) + b * amount).toInt().coerceIn(0, 255)
                return "#%02X%02X%02X".format(channel(Color.red(c), Color.red(to)), channel(Color.green(c), Color.green(to)), channel(Color.blue(c), Color.blue(to)))
            }
            fun palette(dark: Boolean) = buildJsonObject {
                put("background", mix(dominant, if (dark) Color.BLACK else Color.WHITE, if (dark) .86f else .93f))
                put("surface", mix(dominant, if (dark) Color.BLACK else Color.WHITE, if (dark) .70f else .99f))
                put("foreground", if (dark) "#F6F6F6" else "#17191C")
                put("muted", if (dark) "#C2C5CA" else "#545A63")
                put("accent", mix(accent, if (dark) Color.WHITE else Color.BLACK, if (dark) .45f else .35f))
            }
            DesignProposal("Colors sampled from your image on this phone, with readable light and dark surfaces. This is pixel analysis, not semantic image understanding.", buildJsonObject { put("light", palette(false)); put("dark", palette(true)); put("wallpaperStyle", "AURORA"); put("monochromeIcons", true) }, revision, "image", "Local image palette")
        } finally { bitmap.recycle() }
    }
}
