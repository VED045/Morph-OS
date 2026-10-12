package ai.morphos.launcher.ui

import ai.morphos.launcher.domain.*
import ai.morphos.launcher.data.*
import android.Manifest
import android.content.*
import android.os.BatteryManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import kotlin.math.*

internal class PolygonShape(private val points: Int, private val star: Boolean) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path(); val n = if (star) points * 2 else points
        repeat(n) { i ->
            val angle = -PI / 2 + 2 * PI * i / n
            val r = if (star && i % 2 == 1) .29f else .5f
            val x = size.width * (.5f + r * cos(angle).toFloat()); val y = size.height * (.5f + r * sin(angle).toFloat())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }; path.close(); return Outline.Generic(path)
    }
}
@Composable internal fun rememberBattery(): Pair<Int, Boolean> {
    val context = LocalContext.current
    var state by remember { mutableStateOf(-1 to false) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                state = (if (level >= 0 && scale > 0) level * 100 / scale else -1) to ((intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) > 0)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state
}
@Composable internal fun WidgetCard(widget: WidgetSpec, style: Style, action: () -> Unit, modifier: Modifier = Modifier, fit: Boolean = false, compact: Boolean = false) {
    val geometric = style.spec.homeStyle == "GEOMETRIC"
    val shape = when (widget.shape) { WidgetShape.CIRCLE -> CircleShape; WidgetShape.HEXAGON -> PolygonShape(6, false); WidgetShape.STAR -> PolygonShape(5, true); WidgetShape.RECTANGLE -> RoundedCornerShape(0.dp); else -> RoundedCornerShape(if (geometric) 22.dp else 32.dp) }
    val bg = if (style.oxygen) Color(0xFFF3F4F9) else if (geometric && widget.kind == WidgetKind.COUNTER) style.accent else style.surface.copy(alpha = if (geometric) 1f else .86f)
    val fg = if (geometric && widget.kind == WidgetKind.COUNTER) Color.White else if (style.oxygen) Color(0xFF182536) else style.text
    val battery = rememberBattery()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(widget.kind, widget.deadline) { while (true) { now = System.currentTimeMillis(); delay(if (widget.kind == WidgetKind.TIMER && widget.deadline > now) 1000 else 15000) } }
    Surface(color = bg, shape = shape, modifier = modifier.then(if (fit) Modifier else Modifier.height(172.dp)).graphicsLayer { rotationZ = widget.rotation; scaleX = widget.scale; scaleY = widget.scale }.testTag("widget_${widget.id}")) {
        when (widget.kind) {
            WidgetKind.CLOCK -> AnalogClock(style, fg)
            WidgetKind.BIRTHDAYS, WidgetKind.CALENDAR -> PersonalDataWidget(widget, if (style.oxygen) style.copy(palette = style.palette.copy(foreground = "#182536", muted = "#596574")) else style)
            else -> Column(Modifier.padding(horizontal = if (widget.shape == WidgetShape.CIRCLE) 32.dp else if (compact) 10.dp else 16.dp, vertical = if (widget.shape == WidgetShape.CIRCLE) 16.dp else if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = if (widget.shape == WidgetShape.CIRCLE) Alignment.CenterHorizontally else Alignment.Start) {
                if (!compact || widget.kind != WidgetKind.TIMER) Text(widget.title.uppercase(), color = fg.copy(alpha = .7f), fontSize = 10.sp, letterSpacing = 1.sp, maxLines = 1)
                when (widget.kind) {
                    WidgetKind.BATTERY -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (battery.first < 0) "—" else "${battery.first}", color = fg, fontSize = if (compact) 24.sp else 44.sp, fontWeight = FontWeight.Light)
                            Text("%", color = fg.copy(alpha = .7f), modifier = Modifier.padding(start = 4.dp))
                        }
                        Canvas(Modifier.fillMaxWidth().height(if (compact) 8.dp else 20.dp).semantics { contentDescription = "Battery ${battery.first} percent" }) {
                            repeat(12) { i -> drawRoundRect(if (battery.first >= (i + 1) * 100 / 12) fg else fg.copy(alpha = .12f), Offset(i * size.width / 12, 0f), Size(size.width / 12 - 3.dp.toPx(), size.height), CornerRadius(2.dp.toPx())) }
                        }
                        if (!compact) Text(if (battery.second) "Charging" else "On battery", color = fg.copy(alpha = .7f), fontSize = 11.sp)
                    }
                    WidgetKind.TIMER -> {
                        val seconds = if (widget.deadline == 0L) widget.durationSeconds.toLong() else ((widget.deadline - now) / 1000).coerceAtLeast(0)
                        Text("%02d:%02d".format(seconds / 60, seconds % 60), color = fg, fontSize = if (compact) 22.sp else 35.sp, fontWeight = FontWeight.Light)
                        if (!compact) LinearProgressIndicator(progress = { (seconds.toFloat() / widget.durationSeconds).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (geometric) fg else style.accent, trackColor = fg.copy(alpha = .1f))
                        TextButton(onClick = action, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("action_${widget.id}")) { Text(if (widget.deadline > now) "Stop session" else if (widget.deadline > 0) "Done · Start again" else "Start session", color = fg) }
                    }
                    WidgetKind.COUNTER -> {
                        Text("${widget.count.toString().padStart(2, '0')}", color = fg, fontSize = 46.sp, fontWeight = FontWeight.Light)
                        TextButton(onClick = action, contentPadding = PaddingValues(0.dp), modifier = Modifier.heightIn(min = 48.dp).testTag("action_${widget.id}")) { Text("+  Add a win", color = fg) }
                    }
                    else -> Unit
                }
            }
        }
    }
}
@Composable private fun AnalogClock(style: Style, fg: Color) {
    var time by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { time = LocalDateTime.now(); delay(if (style.spec.reducedMotion) 15000 else 1000) } }
    Canvas(Modifier.fillMaxSize().padding(20.dp).semantics { contentDescription = "Analog clock ${time.hour}:${time.minute}" }) {
        val radius = size.minDimension / 2
        repeat(60) { i ->
            val a = (i * 6 - 90) * PI / 180
            val start = center + Offset(cos(a).toFloat(), sin(a).toFloat()) * (radius * if (i % 5 == 0) .85f else .94f)
            val end = center + Offset(cos(a).toFloat(), sin(a).toFloat()) * radius
            drawLine(fg.copy(alpha = if (i % 5 == 0) .65f else .30f), start, end, if (i % 5 == 0) 2.dp.toPx() else 1.dp.toPx())
        }
        fun hand(angle: Double, length: Float, width: Float) { val a = (angle - 90) * PI / 180; drawLine(fg, center, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * radius * length, width.dp.toPx(), StrokeCap.Round) }
        hand((time.hour % 12 + time.minute / 60.0) * 30, .52f, 5f); hand(time.minute * 6.0, .8f, 3f)
        if (!style.spec.reducedMotion) { val a = (time.second * 6 - 90) * PI / 180; drawLine(style.accent, center - Offset(cos(a).toFloat(), sin(a).toFloat()) * radius * .18f, center + Offset(cos(a).toFloat(), sin(a).toFloat()) * radius * .85f, 1.5.dp.toPx()) }
        drawCircle(style.accent, 4.dp.toPx(), center)
    }
}
@Composable private fun PersonalDataWidget(widget: WidgetSpec, style: Style) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val permission = if (widget.kind == WidgetKind.BIRTHDAYS) Manifest.permission.READ_CONTACTS else Manifest.permission.READ_CALENDAR
    var refresh by remember { mutableIntStateOf(0) }
    var permitted by remember { mutableStateOf(WidgetBindings.allowed(context, permission)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it; refresh++ }
    DisposableEffect(lifecycle, permission) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { permitted = WidgetBindings.allowed(context, permission); refresh++ } }
        lifecycle.lifecycle.addObserver(observer); onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val items by produceState<List<BoundItem>>(emptyList(), refresh, permitted) {
        value = emptyList()
        if (permitted) value = if (widget.kind == WidgetKind.BIRTHDAYS) WidgetBindings.birthdays(context) else WidgetBindings.calendar(context)
    }
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(widget.title, color = style.text, fontSize = 12.sp)
        if (!permitted) { Text("Private · On your phone", color = style.muted, fontSize = 11.sp); TextButton(onClick = { launcher.launch(permission) }) { Text("Connect", color = style.accent) } }
        else if (items.isEmpty()) Text(if (widget.kind == WidgetKind.BIRTHDAYS) "No saved birthdays" else "No events this week", color = style.muted, fontSize = 12.sp)
        else items.forEach { item -> Column(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, item.uri)) } }) { Text(item.title, color = style.text, fontSize = 12.sp, maxLines = 1); Text(item.detail, color = style.muted, fontSize = 10.sp) } }
    }
}
