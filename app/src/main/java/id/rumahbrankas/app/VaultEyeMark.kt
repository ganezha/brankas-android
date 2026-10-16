package id.rumahbrankas.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val GoldLight = Color(0xFFF7ECC8)
private val GoldMuted = Color(0xFFB8A26A)
private val GoldDark = Color(0xFF7A683D)
private val BezelDark = Color(0xFF141512)
private val BezelHighlight = Color(0xFF383A31)
private val TumblerSurface = Color(0xFF1D1E1A)
private val RecessDark = Color(0xFF0C0D0A)
private val TickSubtle = Color(0xFF4E5045)

/**
 * Precision mechanical safe dial mark for the PIN unlock screen.
 *
 * Combines clean, high-precision vector geometry with satisfying tactile feedback:
 * - Rotates with a mechanical ratcheting click and combination-lock rhythm on each keystroke.
 * - Pulses a warm-gold ember light through the center keyhole core and alignment needle.
 * - Subtle 3D parallax layer shifts respond to touch gestures across the screen.
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

    // Dynamic combination dial sequence: alternates clockwise & counter-clockwise
    val targetDialAngle = remember(typedCharacters) {
        when (typedCharacters) {
            0 -> 0f
            else -> {
                val combinationSteps = floatArrayOf(
                    0f, 48f, -36f, 75f, -55f, 96f, -70f, 120f, -85f, 140f, -100f, 165f, -125f,
                )
                if (typedCharacters < combinationSteps.size) {
                    combinationSteps[typedCharacters]
                } else {
                    val sign = if (typedCharacters % 2 == 1) 1f else -1f
                    sign * (40f + (typedCharacters * 14f) % 180f)
                }
            }
        }
    }

    // Dial rotation with spring-snappy mechanical inertia
    val dialRotation by animateFloatAsState(
        targetValue = targetDialAngle,
        animationSpec = spring(
            dampingRatio = 0.65f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "dialRotation",
    )

    // Touch parallax target
    val targetParallax = when {
        gazeInWindow != null && placed -> {
            val delta = gazeInWindow - center
            val reach = with(LocalDensity.current) { reachPx.toPx() }
            Offset(
                (delta.x / reach).coerceIn(-1f, 1f),
                (delta.y / reach).coerceIn(-1f, 1f),
            )
        }
        typedCharacters > 0 -> Offset(0f, 0.45f) // Subtle downward tilt toward keypad while typing
        else -> Offset.Zero
    }

    val parallaxX by animateFloatAsState(
        targetValue = targetParallax.x,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "parallaxX",
    )
    val parallaxY by animateFloatAsState(
        targetValue = targetParallax.y,
        animationSpec = tween(durationMillis = 100, easing = LinearEasing),
        label = "parallaxY",
    )

    // Keystroke flash pulse
    val keystrokePulse = remember { Animatable(0f) }
    LaunchedEffect(typedCharacters) {
        if (typedCharacters <= 0) return@LaunchedEffect
        keystrokePulse.snapTo(1f)
        keystrokePulse.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        )
    }

    Box(
        modifier = modifier.onGloballyPositioned {
            placed = true
            center = it.boundsInWindow().center
        },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = (size.minDimension / 2f) - 6.dp.toPx()
            if (radius <= 0f) return@Canvas

            val dialCenter = Offset(cx, cy)
            val pulse = keystrokePulse.value
            val innerShift = Offset(parallaxX * 2.5.dp.toPx(), parallaxY * 2.5.dp.toPx())
            val coreShift = Offset(parallaxX * 4.5.dp.toPx(), parallaxY * 4.5.dp.toPx())

            // 1. Outer ambient drop shadow
            drawCircle(
                color = Color.Black.copy(alpha = 0.5f),
                radius = radius + 3.dp.toPx(),
                center = dialCenter + Offset(0f, 4.dp.toPx()),
            )

            // 2. Fixed Outer Bezel & Housing Ring
            drawOuterBezel(dialCenter, radius)

            // 3. Rotating Main Dial Wheel
            rotate(dialRotation, dialCenter) {
                drawRotatingDial(dialCenter, radius, pulse)
            }

            // 4. Fixed Top Alignment Needle & Pointer
            drawTopPointer(dialCenter, radius, pulse)

            // 5. Counter-Rotating Inner Tumbler Assembly (with subtle parallax)
            val counterRotation = -dialRotation * 0.52f
            translate(innerShift.x, innerShift.y) {
                rotate(counterRotation, dialCenter) {
                    drawInnerTumbler(dialCenter, radius, pulse)
                }
            }

            // 6. Central Keyhole / Vault Core Emblem (highest parallax depth)
            translate(coreShift.x, coreShift.y) {
                drawVaultCore(dialCenter, radius, pulse)
            }
        }
    }
}

/** Draws the heavy brushed-charcoal bezel with beveled chamfers and gold precision rivets. */
private fun DrawScope.drawOuterBezel(center: Offset, radius: Float) {
    // Bezel base body
    drawCircle(
        color = BezelDark,
        radius = radius,
        center = center,
    )

    // Brushed metallic gradient ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Panel, PanelRaised, BezelDark),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )

    // Outer hairline border
    drawCircle(
        color = Line,
        radius = radius,
        center = center,
        style = Stroke(width = 1.6.dp.toPx()),
    )

    // Dual-tone lighting bevel on the outer rim
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                BezelHighlight,
                BezelDark,
                Line,
                BezelDark,
                BezelHighlight,
            ),
            center = center,
        ),
        radius = radius - 1.dp.toPx(),
        center = center,
        style = Stroke(width = 1.dp.toPx()),
    )

    // 4 Corner Rivets on the Bezel (at 45°, 135°, 225°, 315°)
    val rivetDist = radius * 0.93f
    val rivetAngles = floatArrayOf(45f, 135f, 225f, 315f)
    for (angle in rivetAngles) {
        val rad = angle * (PI.toFloat() / 180f)
        val rx = center.x + cos(rad) * rivetDist
        val ry = center.y + sin(rad) * rivetDist
        val rivetCenter = Offset(rx, ry)

        drawCircle(
            color = BezelDark,
            radius = 2.8.dp.toPx(),
            center = rivetCenter,
        )
        drawCircle(
            color = GoldDark,
            radius = 2.dp.toPx(),
            center = rivetCenter,
            style = Stroke(width = 0.8.dp.toPx()),
        )
        drawCircle(
            color = GoldMuted,
            radius = 0.9.dp.toPx(),
            center = rivetCenter,
        )
    }
}

/** Draws the rotating combination dial with 60 precision tick marks and cardinal gold diamonds. */
private fun DrawScope.drawRotatingDial(center: Offset, radius: Float, pulse: Float) {
    val dialRadius = radius * 0.87f

    // Dial background disc
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(PanelRaised, TumblerSurface, BezelDark),
            center = center,
            radius = dialRadius,
        ),
        radius = dialRadius,
        center = center,
    )

    // Outer edge groove of the dial
    drawCircle(
        color = Line.copy(alpha = 0.8f),
        radius = dialRadius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx()),
    )

    // 60 Precision Radial Tick Marks (every 6 degrees)
    val outerTickR = dialRadius - 2.dp.toPx()
    for (i in 0 until 60) {
        val angleDeg = i * 6f
        val rad = angleDeg * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)

        val isMajor = (i % 5 == 0) // Every 30 degrees (12 major ticks)
        val isMedium = (i % 5 == 2 || i % 5 == 3) // Intermediate ticks

        val tickLen = when {
            isMajor -> 7.dp.toPx()
            isMedium -> 4.5.dp.toPx()
            else -> 3.dp.toPx()
        }

        val startR = outerTickR - tickLen
        val start = Offset(center.x + cosA * startR, center.y + sinA * startR)
        val end = Offset(center.x + cosA * outerTickR, center.y + sinA * outerTickR)

        val tickColor = when {
            isMajor -> {
                if (pulse > 0.05f) {
                    Gold.mix(GoldLight, pulse * 0.7f)
                } else {
                    Gold
                }
            }
            isMedium -> GoldMuted.copy(alpha = 0.75f)
            else -> TickSubtle
        }

        val strokeWidth = when {
            isMajor -> 1.7.dp.toPx()
            isMedium -> 1.1.dp.toPx()
            else -> 0.8.dp.toPx()
        }

        drawLine(
            color = tickColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }

    // 4 Cardinal Gold Diamond Studs at 0°, 90°, 180°, 270°
    val diamondDist = dialRadius * 0.72f
    val diamondAngles = floatArrayOf(0f, 90f, 180f, 270f)
    for (angle in diamondAngles) {
        val rad = angle * (PI.toFloat() / 180f)
        val dx = center.x + cos(rad) * diamondDist
        val dy = center.y + sin(rad) * diamondDist
        drawDiamond(Offset(dx, dy), 3.2.dp.toPx(), Gold)
    }

    // Inner dial track border
    drawCircle(
        color = Line.copy(alpha = 0.6f),
        radius = dialRadius * 0.62f,
        center = center,
        style = Stroke(width = 0.9.dp.toPx()),
    )
}

/** Draws the top alignment pointer (the stationary reference needle at 12 o'clock). */
private fun DrawScope.drawTopPointer(center: Offset, radius: Float, pulse: Float) {
    val pointerTipY = center.y - radius * 0.87f
    val pointerBaseY = center.y - radius * 0.98f
    val halfWidth = 5.5.dp.toPx()

    // Warm glow behind pointer on pulse
    if (pulse > 0.02f) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    GoldLight.copy(alpha = 0.5f * pulse),
                    Gold.copy(alpha = 0.2f * pulse),
                    Color.Transparent,
                ),
                center = Offset(center.x, pointerTipY),
                radius = 16.dp.toPx(),
            ),
            radius = 16.dp.toPx(),
            center = Offset(center.x, pointerTipY),
        )
    }

    // Triangular downward pointer
    val pointerPath = Path().apply {
        moveTo(center.x, pointerTipY)
        lineTo(center.x - halfWidth, pointerBaseY)
        lineTo(center.x + halfWidth, pointerBaseY)
        close()
    }

    val pointerColor = Gold.mix(GoldLight, pulse)
    drawPath(pointerPath, color = pointerColor)

    // Center jewel line on pointer
    drawLine(
        color = Paper,
        start = Offset(center.x, pointerTipY + 1.dp.toPx()),
        end = Offset(center.x, pointerBaseY),
        strokeWidth = 1.1.dp.toPx(),
    )
}

/** Draws the counter-rotating inner tumbler ring with lathe grooves and mechanical ports. */
private fun DrawScope.drawInnerTumbler(center: Offset, radius: Float, pulse: Float) {
    val tumblerRadius = radius * 0.54f

    // Recessed dark groove between outer dial and tumbler
    drawCircle(
        color = RecessDark,
        radius = tumblerRadius + 2.dp.toPx(),
        center = center,
    )
    drawCircle(
        color = GoldDark.copy(alpha = 0.4f),
        radius = tumblerRadius + 1.dp.toPx(),
        center = center,
        style = Stroke(width = 0.8.dp.toPx()),
    )

    // Tumbler disc base
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(PanelRaised, TumblerSurface, BezelDark),
            center = center,
            radius = tumblerRadius,
        ),
        radius = tumblerRadius,
        center = center,
    )

    // Concentric precision lathe grooves (machined metallic finish)
    val latheRadii = floatArrayOf(tumblerRadius * 0.90f, tumblerRadius * 0.76f, tumblerRadius * 0.62f)
    for (r in latheRadii) {
        drawCircle(
            color = Line.copy(alpha = 0.45f),
            radius = r,
            center = center,
            style = Stroke(width = 0.7.dp.toPx()),
        )
    }

    // 6 Mechanical Port Openings around the tumbler
    val portCount = 6
    val portDist = tumblerRadius * 0.76f
    val portRadius = 3.6.dp.toPx()
    for (i in 0 until portCount) {
        val angle = i * (360f / portCount) * (PI.toFloat() / 180f)
        val px = center.x + cos(angle) * portDist
        val py = center.y + sin(angle) * portDist
        val portCenter = Offset(px, py)

        // Recessed cavity
        drawCircle(
            color = RecessDark,
            radius = portRadius,
            center = portCenter,
        )
        drawCircle(
            color = Line,
            radius = portRadius,
            center = portCenter,
            style = Stroke(width = 0.8.dp.toPx()),
        )
        // Center brass pin in port
        val pinColor = if (pulse > 0.1f) GoldLight else GoldMuted
        drawCircle(
            color = pinColor,
            radius = 1.2.dp.toPx(),
            center = portCenter,
        )
    }
}

/** Draws the central raised vault hub with the golden geometric keyhole / safe crest. */
private fun DrawScope.drawVaultCore(center: Offset, radius: Float, pulse: Float) {
    val coreRadius = radius * 0.28f

    // Ambient shadow under center hub
    drawCircle(
        color = Color.Black.copy(alpha = 0.6f),
        radius = coreRadius + 2.dp.toPx(),
        center = center + Offset(0f, 2.dp.toPx()),
    )

    // Raised boss body
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(PanelRaised, Panel, BezelDark),
            center = center,
            radius = coreRadius,
        ),
        radius = coreRadius,
        center = center,
    )

    // Outer golden bezel ring of center hub
    drawCircle(
        color = Gold.mix(GoldLight, pulse * 0.5f),
        radius = coreRadius,
        center = center,
        style = Stroke(width = 1.6.dp.toPx()),
    )

    // Inner fine accent ring
    drawCircle(
        color = Line,
        radius = coreRadius - 2.5.dp.toPx(),
        center = center,
        style = Stroke(width = 0.8.dp.toPx()),
    )

    // Core warm ember glow behind keyhole
    val glowAlpha = (0.28f + 0.62f * pulse).coerceIn(0f, 1f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                GoldLight.copy(alpha = glowAlpha),
                Gold.copy(alpha = glowAlpha * 0.45f),
                Color.Transparent,
            ),
            center = center,
            radius = coreRadius * 0.95f,
        ),
        radius = coreRadius * 0.95f,
        center = center,
    )

    // Stylized Keyhole Geometry in the center
    val keyholeTopR = 4.8.dp.toPx()
    val keyholeTopCenter = center - Offset(0f, 2.2.dp.toPx())
    val slotW = 3.6.dp.toPx()
    val slotBottomY = center.y + 7.5.dp.toPx()

    val keyholePath = Path().apply {
        // Upper circle of keyhole
        addOval(
            androidx.compose.ui.geometry.Rect(
                center = keyholeTopCenter,
                radius = keyholeTopR,
            ),
        )
        // Lower slot
        moveTo(center.x - slotW / 2f, keyholeTopCenter.y)
        lineTo(center.x - slotW * 0.8f, slotBottomY)
        lineTo(center.x + slotW * 0.8f, slotBottomY)
        lineTo(center.x + slotW / 2f, keyholeTopCenter.y)
        close()
    }

    // Keyhole interior (dark vault depth)
    drawPath(keyholePath, color = RecessDark)

    // Keyhole rim stroke in warm gold
    drawPath(
        path = keyholePath,
        color = Gold.mix(GoldLight, pulse),
        style = Stroke(width = 1.1.dp.toPx()),
    )
}

/** Draws a symmetrical 4-point diamond star ornament. */
private fun DrawScope.drawDiamond(center: Offset, size: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - size)
        lineTo(center.x + size * 0.7f, center.y)
        lineTo(center.x, center.y + size)
        lineTo(center.x - size * 0.7f, center.y)
        close()
    }
    drawPath(path, color)
}

/** Utility to blend two colors. */
private fun Color.mix(other: Color, ratio: Float): Color {
    val r = ratio.coerceIn(0f, 1f)
    return Color(
        red = red + (other.red - red) * r,
        green = green + (other.green - green) * r,
        blue = blue + (other.blue - blue) * r,
        alpha = alpha + (other.alpha - alpha) * r,
    )
}
