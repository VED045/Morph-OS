package ai.morphos.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ReferenceImage {
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth in 8..16000 && bounds.outHeight in 8..16000 && bounds.outMimeType in setOf("image/jpeg", "image/png"))
        var sample = 1
        while (bounds.outWidth / sample > 1280 || bounds.outHeight / sample > 1280) sample *= 2
        val bitmap = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: error("Cannot read image")
        try {
            val output = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)
            require(output.size() <= 1000000)
            Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        } finally { bitmap.recycle() }
    }
}
