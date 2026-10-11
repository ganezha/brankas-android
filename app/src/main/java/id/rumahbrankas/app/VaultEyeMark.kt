package id.rumahbrankas.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private val ShellDark = Color(0xFF121310)
private val ShellLit = Color(0xFF34352C)
private val LidDark = Color(0xFF0C0D0B)
private val LidLit = Color(0xFF24251E)
private val Gem = Color(0xFF8C2E2C)
private val GemLit = Color(0xFFC44B40)
private val IrisDeep = Color(0xFF5C221E)
private val IrisRing = Color(0xFFB83C32)
private val IrisHot = Color(0xFFE85A42)
private val CoreHot = Color(0xFFFFF4EC)
private val LockMetal = Color(0xFF3C3D36)
private val LockShade = Color(0xFF22231C)

/**
 * The unlock emblem. The shell, lock, and red inlays stay put.
 * The eyelid rings follow a touch or the PIN field a short distance, and the red core follows farther.
 */
@Composable
internal fun VaultEyeMark(
    gazeInWindow: Offset?,
    typedCharacters: Int,
    modifier: Modifier = Modifier,
) {
    var center by remember { mutableStateOf(Offset.Zero) }
    var placed by remember { mutableStateOf(false) }
    val reachPx = 220.dp
    val target = when {
        gazeInWindow != null && placed -> {
            val delta = gazeInWindow - center
            val reach = with(LocalDensity.current) { reachPx.toPx() }
            Offset(
                (delta.x / reach).coerceIn(-1f, 1f),
                (delta.y / reach).coerceIn(-1f, 1f),
            )
        }
        typedCharacters > 0 -> Offset((((typedCharacters % 5) - 2) / 2f) * 0.24f, 0.84f)
        else -> Offset.Zero
    }
    val motion = spring<Float>(dampingRatio = 0.78f, stiffness = 340f)
    val lookX by animateFloatAsState(target.x, motion, label = "eyeLookX")
    val lookY by animateFloatAsState(target.y, motion, label = "eyeLookY")
    val blink = remember { Animatable(0f) }
    LaunchedEffect(typedCharacters) {
        if (typedCharacters <= 0) return@LaunchedEffect
        blink.snapTo(1f)
        blink.animateTo(0f, tween(durationMillis = 240, easing = FastOutSlowInEasing))
    }
    Box(modifier.onGloballyPositioned {
        placed = true
        center = it.boundsInWindow().center
    }) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f - 1.dp.toPx()
            val origin = Offset(size.width / 2f, size.height / 2f)
            val socket = radius * 0.57f
            val coreShift = Offset(lookX, lookY) * socket * 0.30f
            val irisShift = coreShift * 0.62f
            val lidShift = coreShift * 0.34f
            drawShell(origin, radius)
            drawGem(origin, radius * 0.78f, -90f, radius * 0.11f, radius * 0.15f)
            drawInlay(origin, radius, 48f)
            drawInlay(origin, radius, 132f)
            val opening = Path().apply { addOval(Rect(origin, socket)) }
            clipPath(opening) {
                translate(lidShift.x, lidShift.y) { drawEyelids(origin, socket) }
                translate(irisShift.x, irisShift.y) { drawIris(origin, socket * 0.46f) }
                translate(coreShift.x, coreShift.y) { drawCore(origin, socket * 0.16f) }
                if (blink.value > 0.02f) {
                    translate(lidShift.x, lidShift.y) { drawBlink(origin, socket, blink.value) }
                }
            }
            drawCircle(color = ShellLit.copy(alpha = 0.85f), radius = socket, center = origin, style = Stroke(width = 1.4.dp.toPx()))
            drawLock(origin, radius)
            drawGem(origin, radius * 0.90f, 90f, radius * 0.05f, radius * 0.065f)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawShell(center: Offset, radius: Float) {
    facetRing(center, radius * 0.64f, radius * 0.98f, 14, -90f, 0.55f) { _, angle -> shellTone(angle) }
    facetRing(center, radius * 0.575f, radius * 0.66f, 18, -80f, 0.35f) { _, angle ->
        shellTone(angle).mix(LidDark, 0.45f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawEyelids(center: Offset, socket: Float) {
    facetRing(center, socket * 0.78f, socket * 0.98f, 16, -70f, 0.4f) { _, angle -> lidTone(angle) }
    facetRing(center, socket * 0.60f, socket * 0.78f, 14, -50f, 0.35f) { _, angle -> lidTone(angle).mix(LidDark, 0.35f) }
    facetRing(center, socket * 0.46f, socket * 0.60f, 12, -30f, 0.3f) { _, angle -> lidTone(angle).mix(LidDark, 0.62f) }
    drawCircle(LidDark, socket * 0.46f, center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIris(center: Offset, radius: Float) {
    drawCircle(IrisDeep, radius, center)
    drawCircle(IrisRing, radius * 0.78f, center, style = Stroke(1.7.dp.toPx()))
    drawCircle(IrisHot, radius * 0.52f, center, style = Stroke(1.45.dp.toPx()))
    drawCircle(Color(0xFFFFB089), radius * 0.28f, center, style = Stroke(1.15.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCore(center: Offset, radius: Float) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CoreHot, IrisHot, Color(0x00E85A42)),
            center = center,
            radius = radius * 2.4f,
        ),
        radius = radius * 2.4f,
        center = center,
    )
    drawCircle(CoreHot, radius * 0.42f, center)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBlink(center: Offset, socket: Float, amount: Float) {
    val depth = amount * socket * 0.96f
    val left = center.x - socket
    val right = center.x + socket
    val top = Path().apply {
        moveTo(left, center.y - socket)
        lineTo(right, center.y - socket)
        lineTo(right, center.y - socket + depth * 0.35f)
        quadraticTo(center.x, center.y - socket + depth * 1.35f, left, center.y - socket + depth * 0.35f)
        close()
    }
    val bottom = Path().apply {
        moveTo(left, center.y + socket)
        lineTo(right, center.y + socket)
        lineTo(right, center.y + socket - depth * 0.28f)
        quadraticTo(center.x, center.y + socket - depth * 1.15f, left, center.y + socket - depth * 0.28f)
        close()
    }
    drawPath(top, LidDark)
    drawPath(bottom, LidDark.mix(ShellDark, 0.35f))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInlay(center: Offset, radius: Float, angle: Float) {
    val tip = polar(center, radius * 0.95f, angle)
    val left = polar(center, radius * 0.74f, angle - 7.5f)
    val right = polar(center, radius * 0.74f, angle + 7.5f)
    val path = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(path, Gem)
    val highlight = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(polar(center, radius * 0.84f, angle).x, polar(center, radius * 0.84f, angle).y)
        close()
    }
    drawPath(highlight, GemLit)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGem(
    center: Offset,
    radius: Float,
    angle: Float,
    halfWidth: Float,
    halfHeight: Float,
) {
    val at = polar(center, radius, angle)
    val up = polar(Offset.Zero, 1f, angle)
    val side = Offset(-up.y, up.x)
    fun atDelta(along: Float, across: Float) = at + up * along + side * across
    val top = atDelta(halfHeight, 0f)
    val right = atDelta(0f, halfWidth)
    val bottom = atDelta(-halfHeight * 0.72f, 0f)
    val left = atDelta(0f, -halfWidth)
    val path = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(bottom.x, bottom.y)
        lineTo(left.x, left.y)
        close()
    }
    drawPath(path, Gem)
    val cap = Path().apply {
        moveTo(top.x, top.y)
        lineTo(right.x, right.y)
        lineTo(at.x, at.y)
        lineTo(left.x, left.y)
        close()
    }
    drawPath(cap, GemLit)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLock(center: Offset, radius: Float) {
    val at = Offset(center.x, center.y + radius * 0.60f)
    drawCircle(ShellLit, radius * 0.175f, at)
    drawCircle(ShellDark, radius * 0.175f, at, style = Stroke(1.2.dp.toPx()))
    val shackle = radius * 0.055f
    drawArc(
        color = LockMetal,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(at.x - shackle, at.y - shackle * 2.15f),
        size = Size(shackle * 2f, shackle * 2f),
        style = Stroke(width = radius * 0.028f, cap = StrokeCap.Round),
    )
    val bodyW = radius * 0.11f
    val bodyH = radius * 0.09f
    drawRoundRect(
        color = LockMetal,
        topLeft = Offset(at.x - bodyW, at.y - bodyH * 0.15f),
        size = Size(bodyW * 2f, bodyH * 1.7f),
        cornerRadius = CornerRadius(radius * 0.02f),
    )
    drawCircle(LockShade, radius * 0.022f, Offset(at.x, at.y + bodyH * 0.35f))
    val hole = Path().apply {
        moveTo(at.x - radius * 0.012f, at.y + bodyH * 0.48f)
        lineTo(at.x + radius * 0.012f, at.y + bodyH * 0.48f)
        lineTo(at.x, at.y + bodyH * 0.95f)
        close()
    }
    drawPath(hole, LockShade)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.facetRing(
    center: Offset,
    inner: Float,
    outer: Float,
    segments: Int,
    rotation: Float,
    gapDegrees: Float,
    colorFor: (index: Int, midAngle: Float) -> Color,
) {
    val sweep = 360f / segments
    for (index in 0 until segments) {
        val start = rotation + index * sweep + gapDegrees
        val end = rotation + (index + 1) * sweep - gapDegrees
        val mid = (start + end) / 2f
        val outerStart = polar(center, outer, start)
        val outerEnd = polar(center, outer, end)
        val innerEnd = polar(center, inner, end)
        val innerStart = polar(center, inner, start)
        val path = Path().apply {
            moveTo(outerStart.x, outerStart.y)
            lineTo(outerEnd.x, outerEnd.y)
            lineTo(innerEnd.x, innerEnd.y)
            lineTo(innerStart.x, innerStart.y)
            close()
        }
        drawPath(path, colorFor(index, mid))
    }
}

private fun shellTone(angle: Float): Color {
    val tone = lightAmount(angle)
    val wobble = if (((angle + 90f) / (360f / 14f)).toInt() % 2 == 0) 0.06f else -0.04f
    return ShellDark.mix(ShellLit, (tone + wobble).coerceIn(0f, 1f))
}

private fun lidTone(angle: Float): Color = LidDark.mix(LidLit, lightAmount(angle) * 0.85f)

private fun lightAmount(angle: Float): Float {
    val radians = Math.toRadians(angle.toDouble())
    val dot = cos(radians).toFloat() * -0.28f + sin(radians).toFloat() * -0.96f
    return ((dot + 1f) * 0.5f).coerceIn(0f, 1f)
}

private fun polar(center: Offset, radius: Float, degrees: Float): Offset {
    val radians = Math.toRadians(degrees.toDouble())
    return Offset(center.x + cos(radians).toFloat() * radius, center.y + sin(radians).toFloat() * radius)
}

private fun Color.mix(other: Color, amount: Float): Color {
    val ratio = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * ratio,
        green = green + (other.green - green) * ratio,
        blue = blue + (other.blue - blue) * ratio,
        alpha = alpha + (other.alpha - alpha) * ratio,
    )
}
