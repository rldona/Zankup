package com.zankup.android.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/** Runs [action] as the finger lands, before the tap is decided: the activity starts loading. */
internal fun Modifier.onPressDown(key: Any?, action: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        action()
    }
}

/** A small capsule in a pale [color], its text towards the foreground so it reads on it. */
@Composable
internal fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = lerp(color, colors.fg, 0.4f),
        maxLines = 1,
        modifier =
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = ZankupTheme.spacing.s, vertical = 2.dp)
    )
}

/** The form's colour: rested blue, neither grey, building green, overreaching red. */
internal val FormTone.color: Color
    @Composable @ReadOnlyComposable
    get() =
        ZankupTheme.colors.let {
            when (this) {
                FormTone.FRESH -> it.fitness
                FormTone.NEUTRAL -> it.muted
                FormTone.OPTIMAL -> it.formPositive
                FormTone.OVERREACHING -> it.formNegative
            }
        }

internal val FormTone.label: Int
    get() =
        when (this) {
            FormTone.FRESH -> R.string.home_form_fresh
            FormTone.NEUTRAL -> R.string.home_form_neutral
            FormTone.OPTIMAL -> R.string.home_form_optimal
            FormTone.OVERREACHING -> R.string.home_form_overreaching
        }

/** One figure of a list card: its icon in a colour, what it is, its value and, maybe, a small ring. */
internal class Figure(
    val key: String,
    val icon: ZIcon,
    val tint: Color,
    val label: String,
    val value: String,
    val extra: (@Composable () -> Unit)? = null
)

/** Figures one under the other in a card, with hairlines between them. */
@Composable
internal fun FigureCard(figures: List<Figure>, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    ZankupCard(modifier) {
        figures.forEachIndexed { i, figure ->
            if (i > 0) HorizontalDivider(color = colors.line)
            Row(
                Modifier.fillMaxWidth().padding(vertical = ZankupTheme.spacing.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.sm)
            ) {
                ZankupIcon(figure.icon, contentDescription = null, tint = figure.tint)
                Text(
                    figure.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(figure.value, style = MaterialTheme.typography.bodyLarge.merge(Numbers.tabular))
                figure.extra?.invoke()
            }
        }
    }
}

/** A tile of the metrics: its icon and name, then its gauge or ring in the middle. */
@Composable
internal fun Tile(icon: ZIcon, label: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ZankupCard(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.s)
        ) {
            ZankupIcon(icon, contentDescription = null, size = 22.dp)
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(ZankupTheme.spacing.sm))
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(ZankupTheme.spacing.xs)
        ) { content() }
    }
}

/** Progress as a ring (steps against the day's goal), with [content] in the middle. */
@Composable
internal fun Ring(
    share: Float,
    size: Dp,
    stroke: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val track = ZankupTheme.colors.line
    val done = ZankupTheme.colors.sport.ride
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val width = stroke.toPx()
            val inset = width / 2
            val arc = Size(this.size.width - width, this.size.height - width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arc, style = Stroke(width))
            if (share > 0f) {
                drawArc(
                    done,
                    -90f,
                    max(1f, share * 360f),
                    false,
                    Offset(inset, inset),
                    arc,
                    style = Stroke(width, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

private const val GAUGE_START = -135f
private const val GAUGE_SWEEP = 270f
private const val GAUGE_GAP = 8f

/**
 * A 270° gauge in coloured bands, a dot at the value in its band's colour, and the figure (and its
 * unit) in the middle. [bandColors] has one colour per band of [scale].
 */
@Composable
internal fun Gauge(
    value: Double,
    scale: GaugeScale,
    bandColors: List<Color>,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    val colors = ZankupTheme.colors
    val dot = bandColors[scale.band(value)]
    val ring = colors.surface
    Box(modifier.size(120.dp).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(120.dp)) {
            val r = 48.dp.toPx()
            val topLeft = Offset(center.x - r, center.y - r)
            val arc = Size(r * 2, r * 2)
            val last = scale.spans.lastIndex
            scale.spans.forEachIndexed { i, span ->
                val from = GAUGE_START + GAUGE_SWEEP * span.start + if (i > 0) GAUGE_GAP / 2 else 0f
                val to = GAUGE_START + GAUGE_SWEEP * span.endInclusive - if (i < last) GAUGE_GAP / 2 else 0f
                if (to > from) {
                    drawArc(
                        bandColors[i],
                        from - 90f,
                        to - from,
                        false,
                        topLeft,
                        arc,
                        style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            val angle = Math.toRadians((GAUGE_START + GAUGE_SWEEP * scale.fraction(value) - 90f).toDouble())
            val at = Offset(center.x + r * cos(angle).toFloat(), center.y + r * sin(angle).toFloat())
            drawCircle(ring, 10.dp.toPx(), at)
            drawCircle(dot, 8.dp.toPx(), at)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.headlineLarge.merge(Numbers.tabular))
            if (unit != null) {
                Text(unit, style = MaterialTheme.typography.bodySmall, color = colors.muted)
            }
        }
    }
}

/** A hairline across a card, between its parts. */
@Composable
internal fun CardDivider() {
    HorizontalDivider(
        color = ZankupTheme.colors.line,
        modifier = Modifier.padding(vertical = ZankupTheme.spacing.sm)
    )
}

@Composable
internal fun restingText(bpm: Double) = stringResource(R.string.home_resting, Math.round(bpm).toInt())

@Composable
internal fun msText(ms: Double) = stringResource(R.string.home_ms, Math.round(ms).toInt())
