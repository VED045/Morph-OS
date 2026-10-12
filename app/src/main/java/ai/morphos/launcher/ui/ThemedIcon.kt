package ai.morphos.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.unit.dp

/** Original geometric glyphs for common system apps. Unrecognised apps retain their own identity. */
internal fun systemGlyph(packageName: String): String? = when {
    packageName.endsWith(".dialer") || packageName.endsWith(".contacts") -> "phone"
    packageName.endsWith(".messaging") || packageName.endsWith(".mms") -> "message"
    packageName.endsWith(".camera") || packageName.endsWith(".camera2") -> "camera"
    packageName.endsWith(".deskclock") || packageName.endsWith(".clock") -> "clock"
    packageName.endsWith(".calendar") -> "calendar"
    packageName.endsWith(".documentsui") || packageName.endsWith(".files") -> "folder"
    packageName.endsWith(".calculator") || packageName.endsWith(".calculator2") -> "calculator"
    packageName.endsWith(".settings") -> "settings"
    packageName.endsWith(".gallery3d") || packageName.endsWith(".gallery") -> "photo"
    packageName.endsWith(".email") -> "mail"
    packageName.endsWith(".music") -> "music"
    packageName.endsWith(".browser") -> "browser"
    else -> null
}

@Composable internal fun ThemedGlyph(glyph: String, style: Style, mono: Boolean, modifier: Modifier = Modifier) {
    val base = when(glyph) { "phone", "message" -> Color(0xFF28AC63); "camera", "settings", "clock" -> Color(0xFF455064); "folder", "calculator" -> Color(0xFFE6A837); "photo" -> Color(0xFFCF5977); else -> Color(0xFF438BE6) }
    val ink = if (mono) style.text else Color.White
    Canvas(modifier.background(if (mono) style.surface else base, if (mono || style.nothing) CircleShape else RoundedCornerShape(32))) {
        val s = size.minDimension
        val stroke = Stroke(s * .038f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(ink, Offset(x*s,y*s), Offset(xx*s,yy*s), s*.038f, StrokeCap.Round)
        fun rect(x: Float,y: Float,w: Float,h: Float) = drawRoundRect(ink, Offset(x*s,y*s), Size(w*s,h*s), CornerRadius(s*.055f), style=stroke)
        when(glyph) {
            "phone" -> { val p=Path().apply { moveTo(.28f*s,.27f*s); cubicTo(.20f*s,.45f*s,.57f*s,.80f*s,.73f*s,.69f*s); lineTo(.75f*s,.59f*s); lineTo(.61f*s,.53f*s); lineTo(.55f*s,.60f*s); cubicTo(.48f*s,.58f*s,.38f*s,.46f*s,.39f*s,.41f*s); lineTo(.46f*s,.36f*s); lineTo(.39f*s,.23f*s); close() }; drawPath(p, ink, style=stroke) }
            "message" -> { rect(.23f,.27f,.54f,.40f); line(.32f,.67f,.30f,.77f); line(.30f,.77f,.46f,.67f); line(.34f,.41f,.66f,.41f); line(.34f,.52f,.55f,.52f) }
            "camera" -> { rect(.23f,.32f,.54f,.39f); rect(.35f,.25f,.21f,.07f); drawCircle(ink,s*.115f,Offset(s*.5f,s*.515f),style=stroke); drawCircle(ink,s*.025f,Offset(s*.68f,s*.40f)) }
            "clock" -> { drawCircle(ink,s*.27f,center,style=stroke); line(.50f,.33f,.50f,.50f); line(.50f,.50f,.63f,.56f) }
            "calendar" -> { rect(.25f,.29f,.50f,.46f); line(.25f,.42f,.75f,.42f); line(.38f,.23f,.38f,.34f); line(.62f,.23f,.62f,.34f); for(x in listOf(.38f,.5f,.62f)) for(y in listOf(.53f,.64f)) drawCircle(ink,s*.022f,Offset(x*s,y*s)) }
            "folder" -> { val p=Path().apply { moveTo(.23f*s,.31f*s); lineTo(.44f*s,.31f*s); lineTo(.50f*s,.38f*s); lineTo(.77f*s,.38f*s); lineTo(.73f*s,.71f*s); lineTo(.23f*s,.71f*s); close() }; drawPath(p,ink,style=stroke) }
            "calculator" -> { rect(.29f,.22f,.42f,.56f); line(.37f,.37f,.63f,.37f); for(x in listOf(.4f,.60f)) for(y in listOf(.51f,.65f)) drawCircle(ink,s*.026f,Offset(x*s,y*s)) }
            "settings" -> { drawCircle(ink,s*.12f,center,style=stroke); drawCircle(ink,s*.23f,center,style=stroke); repeat(8) { rotate(it*45f) { line(.5f,.21f,.5f,.27f) } } }
            "photo" -> { rect(.25f,.25f,.50f,.50f); val p=Path().apply { moveTo(.26f*s,.66f*s); lineTo(.45f*s,.48f*s); lineTo(.59f*s,.62f*s); lineTo(.68f*s,.53f*s); lineTo(.75f*s,.62f*s) }; drawPath(p,ink,style=stroke); drawCircle(ink,s*.045f,Offset(s*.61f,s*.39f)) }
            "mail" -> { rect(.23f,.30f,.54f,.40f); line(.24f,.33f,.50f,.53f); line(.50f,.53f,.76f,.33f) }
            "music" -> { line(.45f,.30f,.70f,.25f); line(.45f,.30f,.45f,.66f); line(.70f,.25f,.70f,.59f); drawOval(ink,Offset(s*.27f,s*.59f),Size(s*.18f,s*.14f)); drawOval(ink,Offset(s*.52f,s*.52f),Size(s*.18f,s*.14f)) }
            else -> { drawCircle(ink,s*.27f,center,style=stroke); drawOval(ink,Offset(s*.38f,s*.23f),Size(s*.24f,s*.54f),style=stroke); line(.24f,.5f,.76f,.5f) }
        }
    }
}
