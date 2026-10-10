package no.mwmai.flashlight.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.mwmai.flashlight.MAX_STROBE_HZ
import no.mwmai.flashlight.MIN_STROBE_HZ
import no.mwmai.flashlight.Mode
import kotlin.math.roundToInt

@Composable
fun FlashlightScreen(
    lit: Boolean,
    mode: Mode,
    hasFlash: Boolean,
    busy: Boolean,
    level: Int,
    maxLevel: Int,
    hz: Int,
    onPower: () -> Unit,
    onMode: (Mode) -> Unit,
    onLevel: (Int) -> Unit,
    onHz: (Int) -> Unit,
    onScreenLight: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Deck.Ink)
            .systemBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "MWM FLASHLIGHT",
                fontFamily = Mono,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp,
                color = Deck.TextDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (hasFlash) "FLASH" else "NO FLASH",
                fontFamily = Mono,
                fontSize = 11.sp,
                letterSpacing = 2.sp,
                color = if (hasFlash) Deck.PhosphorDim else Deck.Amber,
            )
        }
        Spacer(Modifier.height(12.dp))

        Display(lit = lit, mode = mode, status = statusText(lit, mode, hasFlash, busy, level, maxLevel, hz))

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            PowerButton(lit = lit, onClick = onPower, modifier = Modifier.size(side * 0.78f))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (m in Mode.entries) {
                DeckButton(
                    label = m.name,
                    active = mode == m,
                    onClick = { onMode(m) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        // Fixed height, so the layout does not jump when the mode changes.
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            when {
                !hasFlash -> Hint("THE SCREEN IS YOUR LIGHT ON THIS PHONE")
                mode == Mode.STROBE -> LabeledSlider(
                    label = "SPEED  $hz Hz",
                    value = hz,
                    range = MIN_STROBE_HZ..MAX_STROBE_HZ,
                    onChange = onHz,
                )
                maxLevel > 1 -> LabeledSlider(
                    label = "BRIGHTNESS  $level/$maxLevel",
                    value = level,
                    range = 1..maxLevel,
                    onChange = onLevel,
                )
                mode == Mode.SOS -> Hint("···  –––  ···   REPEATS UNTIL YOU STOP IT")
                else -> Hint("THIS FLASH HAS ONE BRIGHTNESS")
            }
        }
        Spacer(Modifier.height(10.dp))

        DeckButton(
            label = "SCREEN LIGHT",
            active = false,
            onClick = onScreenLight,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Display(lit: Boolean, mode: Mode, status: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Deck.Lcd)
            .border(BorderStroke(1.dp, Deck.Edge), RoundedCornerShape(16.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                if (lit) "ON" else "OFF",
                fontFamily = Mono,
                fontSize = 64.sp,
                fontWeight = FontWeight.Medium,
                color = if (lit) Deck.Phosphor else Deck.PhosphorDim,
            )
            Spacer(Modifier.weight(1f))
            Text(
                mode.name,
                fontFamily = Mono,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Deck.Amber,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        Text(
            status,
            fontFamily = Mono,
            fontSize = 13.sp,
            letterSpacing = 1.sp,
            color = Deck.PhosphorDim,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun statusText(
    lit: Boolean,
    mode: Mode,
    hasFlash: Boolean,
    busy: Boolean,
    level: Int,
    maxLevel: Int,
    hz: Int,
): String = when {
    !hasFlash -> "NO FLASH: POWER LIGHTS THE SCREEN"
    busy -> "FLASH IN USE BY ANOTHER APP"
    !lit -> "TAP THE BUTTON FOR LIGHT"
    mode == Mode.STROBE -> "STROBE AT $hz FLASHES A SECOND"
    mode == Mode.SOS -> "SIGNALLING SOS IN MORSE"
    maxLevel > 1 -> "STEADY AT LEVEL $level OF $maxLevel"
    else -> "STEADY, FULL BRIGHTNESS"
}

/** Big round switch. Amber with a soft halo when the light is on. */
@Composable
private fun PowerButton(lit: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glow by animateFloatAsState(if (lit) 1f else 0f, label = "glow")
    val haptics = LocalHapticFeedback.current
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            if (glow > 0f) {
                drawCircle(
                    Brush.radialGradient(
                        0.55f to Deck.Amber.copy(alpha = 0.35f * glow),
                        1f to Color.Transparent,
                    ),
                )
            }
        }
        Box(
            Modifier
                .fillMaxSize(0.72f)
                .clip(CircleShape)
                .background(lerp(Deck.Raised, Deck.Amber, glow))
                .border(BorderStroke(2.dp, lerp(Deck.Edge, Deck.Amber, glow)), CircleShape)
                .clickable(role = Role.Button) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
                .semantics { contentDescription = "Power" },
            contentAlignment = Alignment.Center,
        ) {
            val stroke = lerp(Deck.Text, Deck.Ink, glow)
            Canvas(Modifier.fillMaxSize(0.38f)) {
                val width = size.minDimension * 0.11f
                val inset = width / 2
                drawArc(
                    color = stroke,
                    startAngle = -60f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - width, size.height - width),
                    style = Stroke(width = width, cap = StrokeCap.Round),
                )
                drawLine(
                    color = stroke,
                    start = Offset(center.x, 0f),
                    end = Offset(center.x, center.y),
                    strokeWidth = width,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            label,
            fontFamily = Mono,
            fontSize = 12.sp,
            letterSpacing = 2.sp,
            color = Deck.TextDim,
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = Deck.Amber,
                activeTrackColor = Deck.Amber,
                inactiveTrackColor = Deck.Raised,
                activeTickColor = Deck.Ink,
                inactiveTickColor = Deck.Edge,
            ),
            modifier = Modifier.semantics { contentDescription = label },
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        fontFamily = Mono,
        fontSize = 12.sp,
        letterSpacing = 1.sp,
        color = Deck.TextDim,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun DeckButton(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(56.dp)
            .clip(shape)
            .background(if (active) Deck.Phosphor else Deck.Raised)
            .border(BorderStroke(1.dp, if (active) Deck.Phosphor else Deck.Edge), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = Mono,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            color = if (active) Deck.Ink else Deck.Text,
        )
    }
}
