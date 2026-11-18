package com.zankup.android.ui.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zankup.android.api.model.SetOut
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.ui.R

// The strength workout's body map (OS-225), as the PWA and the React Native app draw it: the figure
// front and back, the muscles worked glowing in the strength colour, stronger the more sets.

/** Every muscle worked by the sets' exercises, with how many sets worked it. */
fun musclesWorked(sets: List<SetOut>): Map<Muscle, Int> = musclesFromExercises(
    sets.filter {
        it.kind == "active" && it.category != null
    }.groupingBy { it.category!! }.eachCount()
)

/** The same from the sets per exercise a feed card carries (`exercises`). */
fun musclesFromExercises(exercises: Map<String, Int>?): Map<Muscle, Int> {
    val worked = linkedMapOf<Muscle, Int>()
    exercises.orEmpty().forEach { (category, sets) ->
        EXERCISE_MUSCLES[category].orEmpty().forEach { worked[it] = (worked[it] ?: 0) + sets }
    }
    return worked
}

/** The muscles to draw: the recorded ones, else the chosen workout kind's, else none. */
fun musclesFor(recorded: Map<Muscle, Int>, workoutType: String?): Map<Muscle, Int> =
    if (recorded.isNotEmpty()) recorded else WORKOUT_MUSCLES[workoutType].orEmpty()

/** A path from SVG path data, parsed once, with the glow's centre and reach (its bounds). */
private class Shape(val path: Path, val bounds: Rect)

private val shapes = HashMap<String, Shape>()

private fun shapeOf(d: String): Shape = synchronized(shapes) {
    shapes.getOrPut(d) {
        val path = PathParser().parsePathString(d).toPath()
        Shape(path, path.getBounds())
    }
}

/** One side of the figure on its 100 × 200 box, scaled to the canvas's width. */
private fun DrawScope.side(
    side: String,
    worked: Map<Muscle, Int>,
    bg: Color,
    outline: Color,
    rest: Color,
    strength: Color,
    core: Color
) {
    val most = (worked.values.maxOrNull() ?: 1).coerceAtLeast(1)
    scale(size.width / 100f, pivot = Offset.Zero) {
        val silhouette = shapeOf(BODY_SILHOUETTE).path
        drawPath(silhouette, bg)
        drawPath(silhouette, outline, style = Stroke(width = 1.1f, join = StrokeJoin.Round))
        BODY_DETAIL[side].orEmpty().forEach { drawPath(shapeOf(it).path, rest) }
        BODY_SHAPES[side].orEmpty().forEach { (muscle, paths) ->
            val sets = worked[muscle] ?: 0
            paths.forEach { d ->
                val shape = shapeOf(d)
                if (sets == 0) {
                    drawPath(shape.path, rest)
                } else {
                    val b = shape.bounds
                    val reach = maxOf(b.width, b.height) * 0.6f
                    drawPath(
                        shape.path,
                        Brush.radialGradient(
                            listOf(core, strength),
                            center = b.center,
                            radius = reach.coerceAtLeast(0.1f)
                        ),
                        alpha = 0.55f + 0.45f * sets / most
                    )
                }
            }
        }
    }
}

@Composable
private fun Figure(side: String, worked: Map<Muscle, Int>, width: Dp) {
    val colors = ZankupTheme.colors
    val strength = colors.sport.strength
    val rest = lerp(colors.muted, colors.bg, 0.55f)
    val outline = lerp(colors.muted, colors.fg, 0.3f)
    val core = lerp(strength, Color(0xFFFFD27A), 0.45f)
    Canvas(Modifier.size(width, width * 2)) { side(side, worked, colors.bg, outline, rest, strength, core) }
}

@Composable
private fun spoken(side: String, worked: Map<Muscle, Int>): String {
    val names = (if (side == "front") FRONT else BACK).filter { it in worked }.map { muscleName(it) }
    val label = stringResource(if (side == "front") R.string.strength_front else R.string.strength_back)
    return "$label: " + names.joinToString(", ").ifEmpty { "—" }
}

/** A muscle's name in the person's language. */
@Composable
fun muscleName(muscle: Muscle): String = stringResource(
    when (muscle) {
        Muscle.CHEST -> R.string.muscle_chest
        Muscle.SHOULDERS -> R.string.muscle_shoulders
        Muscle.BICEPS -> R.string.muscle_biceps
        Muscle.FOREARMS -> R.string.muscle_forearms
        Muscle.ABS -> R.string.muscle_abs
        Muscle.OBLIQUES -> R.string.muscle_obliques
        Muscle.QUADS -> R.string.muscle_quads
        Muscle.CALVES -> R.string.muscle_calves
        Muscle.TRAPS -> R.string.muscle_traps
        Muscle.LATS -> R.string.muscle_lats
        Muscle.TRICEPS -> R.string.muscle_triceps
        Muscle.LOWER_BACK -> R.string.muscle_lower_back
        Muscle.GLUTES -> R.string.muscle_glutes
        Muscle.HAMSTRINGS -> R.string.muscle_hamstrings
    }
)

/** Front and back side by side, each [width] wide, with "Front" and "Back" under them. */
@Composable
fun BodyMap(worked: Map<Muscle, Int>, modifier: Modifier = Modifier, width: Dp = 120.dp) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        listOf("front", "back").forEach { side ->
            val description = spoken(side, worked)
            Column(
                Modifier.semantics { contentDescription = description },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Figure(side, worked, width)
                Text(
                    stringResource(if (side == "front") R.string.strength_front else R.string.strength_back),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ZankupTheme.colors.muted
                )
            }
        }
    }
}

/**
 * A strength workout's cover, where a route has its map: the body map large on the card colour, under
 * the see-through header ([top] room left for it), with the muscles worked named under it.
 */
@Composable
fun MuscleCover(worked: Map<Muscle, Int>, top: Dp, height: Dp, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    // The figures fill what the header and the legend leave, two to one.
    val tall = maxOf(120.dp, height - top - 64.dp)
    val names = worked.keys.map { muscleName(it) }.joinToString(" · ")
    Column(
        modifier.fillMaxWidth().height(height).background(colors.surface).padding(top = top + 4.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        BodyMap(worked, width = tall / 2)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(24.dp, 6.dp).clip(RoundedCornerShape(3.dp)).background(colors.sport.strength))
            Text(names, color = colors.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Both sides small, on a feed card's tile in place of a route (88 × 64, as the route's). */
@Composable
fun BodyThumb(worked: Map<Muscle, Int>, modifier: Modifier = Modifier) {
    Row(
        modifier.size(88.dp, 64.dp).clip(RoundedCornerShape(8.dp)).background(ZankupTheme.colors.surface2),
        horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Figure("front", worked, 28.dp)
        Figure("back", worked, 28.dp)
    }
}
