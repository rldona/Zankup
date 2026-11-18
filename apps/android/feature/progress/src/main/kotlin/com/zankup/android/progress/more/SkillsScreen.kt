package com.zankup.android.progress.more

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.zankup.android.api.endpoints.AuthApi
import com.zankup.android.api.endpoints.StatsApi
import com.zankup.android.api.model.CurvePoint
import com.zankup.android.api.model.Me
import com.zankup.android.data.Store
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupSheet
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.Numbers
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.POWER_LEVELS
import com.zankup.android.domain.SKILL_DURATIONS
import com.zankup.android.domain.Skill
import com.zankup.android.domain.formatNumber
import com.zankup.android.domain.levelAt
import com.zankup.android.domain.levelWatts
import com.zankup.android.domain.skillLevel
import com.zankup.android.network.call
import com.zankup.android.progress.R
import com.zankup.android.ui.LocalFormats
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

/** The best power at each duration for the last eight weeks and for all time, and the weight (Skills.tsx useSkills). */
@HiltViewModel
class SkillsModel
@Inject
constructor(store: Store) : ViewModel() {
    private val today = LocalDate.now()
    private val from = today.minusDays(55).toString()
    private val to = today.plusDays(1).toString()
    val recent =
        store.query("more/curves/power/$from", ListSerializer(CurvePoint.serializer())) { api ->
            call { api.service<StatsApi>().getCurves(StatsApi.KindGetCurves.POWER, from, to) }
        }
    val all =
        store.query("more/curves/power/all", ListSerializer(CurvePoint.serializer())) { api ->
            call { api.service<StatsApi>().getCurves(StatsApi.KindGetCurves.POWER, "2000-01-01", to) }
        }
    val me =
        store.query("more/me", Me.serializer()) { api ->
            call { api.service<AuthApi>().getMe() }
        }

    init {
        viewModelScope.launch { recent.refresh() }
        viewModelScope.launch { all.refresh() }
        viewModelScope.launch { me.refresh() }
    }
}

private enum class Span { RECENT, ALL }

/** The skills' colours, as Strava's, the same on every theme. */
private fun skillColour(skill: Skill) = when (skill) {
    Skill.SPRINT -> Color(0xFF0160CF)
    Skill.ATTACK -> Color(0xFF59A61C)
    Skill.CLIMB -> Color(0xFFFB7432)
}

/** All time in purple, the last eight weeks in pink over it. */
private val RADAR_ALL = Color(0xFF823BAE)
private val RADAR_RECENT = Color(0xFFD97ED1)

private fun skillName(skill: Skill) = when (skill) {
    Skill.SPRINT -> R.string.more_skill_sprint
    Skill.ATTACK -> R.string.more_skill_attack
    Skill.CLIMB -> R.string.more_skill_climb
}

private fun levelLabel(level: Int) = when (level) {
    1 -> R.string.more_level_1
    2 -> R.string.more_level_2
    3 -> R.string.more_level_3
    4 -> R.string.more_level_4
    5 -> R.string.more_level_5
    6 -> R.string.more_level_6
    7 -> R.string.more_level_7
    8 -> R.string.more_level_8
    else -> R.string.more_level_9
}

@Composable
private fun spanLabel(span: Span) =
    stringResource(if (span == Span.RECENT) R.string.more_skill_span_recent else R.string.more_skill_span_all)

/**
 * Skills, from Progress (skills.tsx): the radar of the best power over twelve durations (the last eight
 * weeks over all time, or all time alone); sprint, attack and climb with their level on a nine-step bar,
 * each opening to its durations; a note without a weight. A duration's watts on the radar open its levels.
 */
@Composable
fun SkillsScreen(onBack: () -> Unit, model: SkillsModel = hiltViewModel()) {
    val colors = ZankupTheme.colors
    val recentState by model.recent.state.collectAsStateWithLifecycle(initialValue = null)
    val allState by model.all.state.collectAsStateWithLifecycle(initialValue = null)
    val meState by model.me.state.collectAsStateWithLifecycle(initialValue = null)
    val recent = recentState?.data.orEmpty().associate { it.durationS to it.value }
    val all = allState?.data.orEmpty().associate { it.durationS to it.value }
    val weightKg = meState?.data?.weightKg
    val weight = weightKg ?: REFERENCE_KG
    val pending = recentState?.loading != false || allState?.loading != false
    var span by rememberSaveable { mutableStateOf(Span.RECENT) }
    var open by rememberSaveable { mutableStateOf<Skill?>(null) }
    var picked by rememberSaveable { mutableStateOf<Int?>(null) }
    val bests = if (span == Span.RECENT) recent else all
    val shapes =
        buildList {
            add(RadarShape(all, RADAR_ALL, dots = false))
            if (span == Span.RECENT) add(RadarShape(recent, RADAR_RECENT, dots = true))
        }

    Scaffold(containerColor = colors.page, topBar = {
        ZankupTopBar(stringResource(R.string.more_skills), onBack = onBack) {
            InfoAction(stringResource(R.string.more_skills_info))
        }
    }) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (pending) {
                Skeleton(Modifier.fillMaxWidth().aspectRatio(1f), height = 320.dp)
            } else {
                SkillsRadar(shapes, weight, onPick = { picked = it })
            }
            if (weightKg == null) {
                Text(
                    stringResource(R.string.more_skills_no_weight, REFERENCE_KG.roundToInt().toString()),
                    color = colors.muted
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                (if (span == Span.RECENT) listOf(Span.RECENT, Span.ALL) else listOf(Span.ALL)).forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LegendDot(if (s == Span.RECENT) RADAR_RECENT else RADAR_ALL, spanLabel(s))
                    }
                }
            }
            Segmented(Span.entries, span, { spanLabel(it) }, { span = it })

            Skill.entries.forEach { skill ->
                val levels = skill.durations.map { levelAt(bests[it] ?: 0.0, it, weight) }
                val level = skillLevel(levels)
                val unfolded = open == skill
                val colour = skillColour(skill)
                Column(
                    Modifier.fillMaxWidth().drawBehind {
                        drawLine(colors.line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    }.padding(top = 14.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (unfolded) null else skill },
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(skillName(skill)), color = colors.fg, fontSize = 16.sp)
                                Text(
                                    if (level > 0) {
                                        stringResource(levelLabel(levelName(level))) + " · " +
                                            stringResource(R.string.more_level_n, levelName(level))
                                    } else {
                                        stringResource(R.string.more_no_level)
                                    },
                                    color = colors.muted,
                                    fontSize = 13.sp
                                )
                            }
                            ZankupIcon(
                                if (unfolded) ZIcon.Collapse else ZIcon.Expand,
                                null,
                                tint = colors.fg,
                                size = 26.dp
                            )
                        }
                        LevelBar(level, colour, 8.dp)
                    }
                    if (unfolded) {
                        skill.durations.forEachIndexed { i, d ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    durationLabel(d),
                                    color = colors.fg,
                                    fontSize = 14.sp,
                                    modifier = Modifier.width(54.dp)
                                )
                                Text(
                                    bests[d]?.takeIf { it > 0 }?.let { "${it.roundToInt()} W" } ?: "–",
                                    color = colors.fg,
                                    fontSize = 14.sp,
                                    style = Numbers.tabular,
                                    modifier = Modifier.width(60.dp)
                                )
                                Box(Modifier.weight(1f)) { LevelBar(levels[i], colour, 6.dp) }
                            }
                        }
                    }
                }
            }
            Text(stringResource(R.string.more_skills_scale, POWER_LEVELS), color = colors.muted, fontSize = 12.sp)
        }
    }
    picked?.let { duration ->
        ZankupSheet(onDismiss = { picked = null }) {
            DurationLevels(duration, span, recent, all, weight)
        }
    }
}

private class RadarShape(val bests: Map<Int, Double>, val colour: Color, val dots: Boolean)

/**
 * The radar, as Strava's: rings for the levels (spaced as the bar's segments), a spoke per duration,
 * the skills round it as a coloured ring with each duration written on it, the shapes, and each
 * duration's watts outside the ring (tapping one opens its levels).
 */
@Composable
private fun SkillsRadar(shapes: List<RadarShape>, weight: Double, onPick: (Int) -> Unit) {
    val colors = ZankupTheme.colors
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
        val sizePx = with(density) { maxWidth.toPx() }
        val centre = sizePx / 2
        val band = sizePx * 0.042f
        val outer = sizePx * 0.33f
        val inner = outer - band / 2 - with(density) { 3.dp.toPx() }
        val n = SKILL_DURATIONS.size
        val step = (2 * PI / n).toFloat()
        fun angle(i: Int) = (-PI / 2).toFloat() + i * step
        fun at(i: Int, r: Float) = Offset(centre + r * cos(angle(i)), centre + r * sin(angle(i)))
        fun point(bests: Map<Int, Double>, i: Int) =
            at(i, inner * radialShare(levelAt(bests[SKILL_DURATIONS[i]] ?: 0.0, SKILL_DURATIONS[i], weight)))
        val ringText = TextStyle(fontSize = with(density) { (band * 0.68f).toSp() }, fontWeight = FontWeight.Bold)
        Canvas(Modifier.fillMaxSize()) {
            val hair = 1.dp.toPx()
            val grid = colors.muted.copy(alpha = 0.45f)
            for (k in 1..POWER_LEVELS) {
                drawCircle(grid, inner * radialShare(k.toDouble()), Offset(centre, centre), style = Stroke(hair))
            }
            for (i in 0 until n) drawLine(grid, Offset(centre, centre), at(i, inner), hair)
            var from = 0
            val gap = 0.014f
            val arcBox = Size(outer * 2, outer * 2)
            val arcTopLeft = Offset(centre - outer, centre - outer)
            Skill.entries.forEach { skill ->
                val to = from + skill.durations.size - 1
                val a0 = angle(from) - step / 2 + gap
                val a1 = angle(to) + step / 2 - gap
                drawArc(
                    skillColour(skill),
                    Math.toDegrees(a0.toDouble()).toFloat(),
                    Math.toDegrees((a1 - a0).toDouble()).toFloat(),
                    false,
                    arcTopLeft,
                    arcBox,
                    style = Stroke(band)
                )
                from = to + 1
            }
            // Each duration's words on the ring, centred on its spoke.
            for (i in 0 until n) {
                val text = durationLabel(SKILL_DURATIONS[i])
                val colour = if (skillOf(i) == Skill.SPRINT) Color.White else Color(0xFF141414)
                val layout = measurer.measure(text, ringText.copy(color = colour))
                val p = at(i, outer)
                drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, p.y - layout.size.height / 2f))
            }
            shapes.forEach { s ->
                val path = Path()
                for (i in 0 until n) {
                    val p = point(s.bests, i)
                    if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
                }
                path.close()
                drawPath(path, s.colour.copy(alpha = 0.9f))
            }
            shapes.lastOrNull()?.takeIf { it.dots }?.let { top ->
                for (i in 0 until n) drawCircle(Color.White, 2.5.dp.toPx(), point(top.bests, i))
            }
        }
        val top = shapes.lastOrNull()
        val labelR = outer + band / 2 + with(density) { 26.dp.toPx() }
        SKILL_DURATIONS.forEachIndexed { i, d ->
            val p = at(i, labelR)
            val w = top?.bests?.get(d)?.takeIf { it > 0 }
            val text = w?.let { "${it.roundToInt()} W" } ?: "–"
            val box = with(density) { 72.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset((p.x - box / 2).roundToInt(), (p.y - with(density) { 13.dp.toPx() }).roundToInt())
                    }
                    .width(72.dp)
                    .clickable(role = Role.Button) { onPick(d) }
                    .semantics { contentDescription = "${durationLabel(d)}: $text" },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text,
                    color = colors.fg,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = Numbers.tabular,
                    modifier =
                    if (w != null) {
                        Modifier.drawBehind {
                            val y = size.height + 1.dp.toPx()
                            drawLine(colors.fg, Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        }
                    } else {
                        Modifier
                    }
                )
            }
        }
    }
}

/**
 * One duration's levels, as Strava's sheet: the nine levels from the best down, the watts each needs
 * at the rider's weight, and the rider's best highlighted with how far each level above is.
 */
@Composable
private fun DurationLevels(
    duration: Int,
    first: Span,
    recent: Map<Int, Double>,
    all: Map<Int, Double>,
    weight: Double
) {
    val colors = ZankupTheme.colors
    val locale = LocalFormats.current.locale
    var span by remember { mutableStateOf(first) }
    val best = (if (span == Span.RECENT) recent else all)[duration] ?: 0.0
    val mine = if (best > 0) levelName(levelAt(best, duration, weight)) else 0
    fun watts(w: Double) = "${formatNumber(w.roundToInt().toDouble(), locale)} W"
    Column(
        Modifier.navigationBarsPadding().padding(bottom = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.more_skill_power, durationLabel(duration)),
                color = colors.fg,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                stringResource(R.string.more_skill_sheet_intro, weight.roundToInt()),
                color = colors.fg,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
        Box(Modifier.padding(horizontal = 20.dp)) { Segmented(Span.entries, span, { spanLabel(it) }, { span = it }) }
        Column {
            val header = TextStyle(color = colors.fg, fontSize = 12.sp, letterSpacing = 1.5.sp)
            Row(Modifier.fillMaxWidth().background(colors.surface2).padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(
                    stringResource(R.string.more_levels).uppercase(locale),
                    style = header,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.more_power).uppercase(locale),
                    style = header,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    stringResource(R.string.more_your_best).uppercase(locale),
                    style = header,
                    modifier = Modifier.width(90.dp)
                )
            }
            (POWER_LEVELS downTo 1).forEach { level ->
                val need = levelWatts(level.toDouble(), duration, weight)
                val here = level == mine
                val weightStyle = if (here) FontWeight.ExtraBold else FontWeight.Normal
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(if (here) colors.surface2 else Color.Transparent)
                        .drawBehind { if (here) drawRect(colors.accent, size = Size(4.dp.toPx(), size.height)) }
                        .padding(horizontal = 20.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(levelLabel(level)),
                        color = colors.fg,
                        fontSize = 16.sp,
                        fontWeight = weightStyle,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (level == 1) "–" else watts(need),
                        color = colors.fg,
                        fontSize = 16.sp,
                        fontWeight = weightStyle,
                        style = Numbers.tabular,
                        modifier = Modifier.width(90.dp)
                    )
                    Text(
                        when {
                            here -> watts(best)
                            best > 0 && level > mine -> "+" + watts(need - best)
                            else -> ""
                        },
                        color = colors.fg,
                        fontSize = 16.sp,
                        fontWeight = weightStyle,
                        style = Numbers.tabular,
                        modifier = Modifier.width(90.dp)
                    )
                }
            }
        }
    }
}
