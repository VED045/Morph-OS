package ai.morphos.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.*

@Composable internal fun WallpaperArt(style: Style, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(style.bg)
        when (style.spec.wallpaperStyle) {
            "ORBS" -> {
                drawCircle(Brush.radialGradient(listOf(style.accent.copy(alpha = .45f), Color.Transparent), Offset(size.width * .9f, size.height * .15f), size.width * .95f), size.width, Offset(size.width * .9f, size.height * .15f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFF81D0CF).copy(alpha = .6f), Color.Transparent), Offset(-size.width * .1f, size.height * .56f), size.width), size.width, Offset(-size.width * .1f, size.height * .56f))
                drawCircle(Brush.radialGradient(listOf(Color(0xFFDDC0A0).copy(alpha = .28f), Color.Transparent), Offset(size.width * .9f, size.height * .8f), size.width), size.width, Offset(size.width * .9f, size.height * .8f))
            }
            "AURORA" -> drawRect(Brush.linearGradient(listOf(style.accent.copy(alpha = .2f), Color.Transparent, style.accent.copy(alpha = .08f))))
            "GRAIN" -> {
                val step = 7.dp.toPx()
                for (x in 0..(size.width / step).toInt()) for (y in 0..(size.height / step).toInt()) {
                    drawCircle(style.text.copy(alpha = if ((x * 7 + y * 11) % 5 == 0) .07f else .02f), .5.dp.toPx(), Offset(x * step, y * step))
                }
            }
        }
    }
}
private val digits = mapOf(
    '0' to listOf("01110","10001","10011","10101","11001","10001","01110"),
    '1' to listOf("00100","01100","00100","00100","00100","00100","01110"),
    '2' to listOf("01110","10001","00001","00010","00100","01000","11111"),
    '3' to listOf("11110","00001","00001","01110","00001","00001","11110"),
    '4' to listOf("00010","00110","01010","10010","11111","00010","00010"),
    '5' to listOf("11111","10000","10000","11110","00001","00001","11110"),
    '6' to listOf("01110","10000","10000","11110","10001","10001","01110"),
    '7' to listOf("11111","00001","00010","00100","01000","01000","01000"),
    '8' to listOf("01110","10001","10001","01110","10001","10001","01110"),
    '9' to listOf("01110","10001","10001","01111","00001","00001","01110"),
    ':' to listOf("0","1","1","0","1","1","0")
)
@Composable internal fun DotTime(value: String, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(94.dp).clearAndSetSemantics { contentDescription = "Time $value" }) {
        val units = value.fold(0) { sum, ch -> sum + if (ch == ':') 3 else 6 } - 1
        val step = min(size.width / units, size.height / 7)
        var left = 0f
        value.forEach { ch ->
            val pattern = digits[ch] ?: return@forEach
            pattern.forEachIndexed { y, line -> line.forEachIndexed { x, bit ->
                drawCircle(if (bit == '1') color else color.copy(alpha = .055f), step * .34f, Offset(left + x * step + step / 2, y * step + step / 2))
            } }
            left += (if (ch == ':') 3 else 6) * step
        }
    }
}
@Composable internal fun HomeClock(now: LocalDateTime, style: Style) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern(if (style.spec.clockStyle == "DOT") "EEEE / dd MMM" else "EEEE, d MMMM")), color = style.text.copy(alpha = .7f), fontSize = 13.sp, letterSpacing = if (style.spec.clockStyle == "DOT") 2.sp else 0.sp, fontFamily = style.font)
        val time = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        if (style.spec.clockStyle == "DOT") DotTime(time, style.text, Modifier.padding(top = 12.dp))
        else Text(time, color = style.text, fontSize = if (style.spec.clockStyle == "STACKED") 82.sp else 76.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-4).sp, modifier = Modifier.clearAndSetSemantics { contentDescription = "Time $time" })
    }
}
