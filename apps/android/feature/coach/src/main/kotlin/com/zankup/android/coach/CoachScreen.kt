package com.zankup.android.coach

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.AiStatus
import com.zankup.android.api.model.WeeklyOut
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupCard
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.LocalFormats
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Two examples, short and different: performance and recovery. */
private val SUGGESTIONS = listOf(R.string.coach_suggestion_1, R.string.coach_suggestion_3)

/**
 * The Coach (OS-1162), as the React Native app's: when AI is off or unavailable, a line (and Settings
 * when the person can turn it on). Otherwise the chat: the weekly report and suggestions before the
 * first question, the answers as they are written with what the coach is reading, and the field with
 * Send or Stop. The (i) says what the coach can do; "New conversation" clears it.
 */
@Composable
fun CoachScreen(
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onOpenActivity: (Long) -> Unit,
    modifier: Modifier = Modifier,
    model: CoachViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { model.refresh() }
    val colors = ZankupTheme.colors
    val status by model.status.collectAsStateWithLifecycle()
    val chat by model.chat.collectAsStateWithLifecycle()
    val info = status.data
    val chatting = info != null && info.available && info.enabled
    Scaffold(
        modifier = modifier,
        containerColor = colors.page,
        topBar = {
            ZankupTopBar(stringResource(R.string.coach_title), onBack = onBack) {
                if (chatting) {
                    AboutButton()
                    if (chat.turns.isNotEmpty()) {
                        IconButton(onClick = model::clear) {
                            ZankupIcon(ZIcon.NewChat, contentDescription = stringResource(R.string.coach_clear))
                        }
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                info == null && status.failure != null ->
                    ErrorState(
                        onRetry = model::refresh,
                        offline = status.failure is ApiResult.Offline,
                        modifier = Modifier.padding(ZankupTheme.spacing.gutter)
                    )
                info == null -> Unit
                !chatting -> Off(info, onSettings)
                else -> ChatPane(info, model, onOpenActivity)
            }
        }
    }
}

@Composable
private fun Off(info: AiStatus, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(ZankupTheme.spacing.gutter),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            stringResource(if (info.available) R.string.coach_off else R.string.coach_unavailable),
            style = MaterialTheme.typography.bodyLarge,
            color = ZankupTheme.colors.fg
        )
        if (info.available) {
            Button(onClick = onSettings) { Text(stringResource(R.string.coach_settings)) }
        }
    }
}

@Composable
private fun AboutButton() {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }) {
        ZankupIcon(ZIcon.Info, contentDescription = stringResource(R.string.coach_about))
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(stringResource(R.string.coach_about)) },
            text = { Text(stringResource(R.string.coach_disclaimer)) },
            confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.coach_close)) } }
        )
    }
}

@Composable
private fun ChatPane(info: AiStatus, model: CoachViewModel, onOpenActivity: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val spacing = ZankupTheme.spacing
    val chat by model.chat.collectAsStateWithLifecycle()
    val budgetSpent = info.budget?.exceeded == true
    val listState = rememberLazyListState()
    val side = Modifier.padding(horizontal = spacing.gutter)
    // Only a conversation follows its newest words: on opening, the report shows from the top.
    val lastLength = chat.turns.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(chat.turns.size, lastLength, chat.working) {
        if (chat.turns.isNotEmpty()) {
            listState.animateScrollToItem(
                listState.layoutInfo.totalItemsCount.coerceAtLeast(1) - 1
            )
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(top = spacing.xs, bottom = spacing.m),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (info.weekly && chat.turns.isEmpty()) item("weekly") { WeeklyCard(model, side) }
            if (budgetSpent) item("budget") { Notice(stringResource(R.string.coach_budget), side) }
            if (chat.turns.isEmpty() && !budgetSpent) {
                item("suggestions-title") {
                    Text(
                        stringResource(R.string.coach_suggestions_title),
                        side.padding(top = 4.dp),
                        color = colors.fg,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                SUGGESTIONS.forEach { id ->
                    item("suggestion-$id") {
                        val question = stringResource(id)
                        Text(
                            question,
                            color = colors.fg,
                            modifier = side
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                                .clickable(role = Role.Button) { model.ask(question) }
                                .padding(12.dp)
                        )
                    }
                }
            }
            itemsIndexed(chat.turns, key = { index, _ -> "turn-$index" }) { _, turn ->
                if (turn.role == Speaker.USER) {
                    Box(side.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            turn.content,
                            color = colors.accentFg,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.accent)
                                .padding(12.dp)
                        )
                    }
                } else {
                    Column(side, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (turn.content.isNotEmpty()) Answer(turn.content, onOpenActivity)
                        turn.error?.let {
                            Notice(
                                stringResource(
                                    if (it == TurnError.BUDGET) R.string.coach_budget else R.string.coach_error
                                )
                            )
                        }
                    }
                }
            }
            chat.working?.let { working ->
                item("working") { Muted(workingLabel(working), side.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        }
        if (!budgetSpent) Input(chat.busy, model::ask, model::stop)
    }
}

@Composable
private fun workingLabel(working: Working): String = stringResource(
    when (working) {
        Working.Thinking -> R.string.coach_thinking
        is Working.Tool -> when (working.name) {
            "search_activities" -> R.string.coach_tool_search_activities
            "activity_details" -> R.string.coach_tool_activity_details
            "fitness_stats" -> R.string.coach_tool_fitness_stats
            "records" -> R.string.coach_tool_records
            "daily_health" -> R.string.coach_tool_daily_health
            "estimates" -> R.string.coach_tool_estimates
            else -> R.string.coach_thinking
        }
    }
)

/** The whole width for the question; Send (or Stop, while answering) sits inside it. */
@Composable
private fun Input(busy: Boolean, onSend: (String) -> Unit, onStop: () -> Unit) {
    val colors = ZankupTheme.colors
    var draft by rememberSaveable { mutableStateOf("") }
    HorizontalDivider(color = colors.line)
    OutlinedTextField(
        value = draft,
        onValueChange = { if (it.length <= MAX_QUESTION) draft = it },
        placeholder = { Text(stringResource(R.string.coach_placeholder)) },
        leadingIcon = { ZankupIcon(ZIcon.Coach, contentDescription = null, tint = colors.accent, size = 22.dp) },
        trailingIcon = {
            if (busy || draft.isNotBlank()) {
                IconButton(
                    onClick = {
                        if (busy) {
                            onStop()
                        } else {
                            onSend(draft.trim())
                            draft = ""
                        }
                    },
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (busy) colors.surface2 else colors.accent)
                ) {
                    ZankupIcon(
                        if (busy) ZIcon.Stop else ZIcon.Send,
                        contentDescription = stringResource(if (busy) R.string.coach_stop else R.string.coach_send),
                        tint = if (busy) colors.fg else colors.accentFg,
                        filled = busy,
                        size = 20.dp
                    )
                }
            }
        },
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = colors.accent.copy(alpha = 0.6f),
            focusedBorderColor = colors.accent,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface
        ),
        maxLines = 5,
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp)
            .heightIn(min = 56.dp)
    )
}

/** Paragraphs and bullet lists, with links to activities that open their detail. */
@Composable
private fun Answer(text: String, onOpenActivity: (Long) -> Unit) {
    val colors = ZankupTheme.colors
    val linkStyle = TextLinkStyles(SpanStyle(color = colors.accent, fontWeight = FontWeight.SemiBold))

    @Composable
    fun Line(content: String) {
        val annotated = buildAnnotatedString {
            pieces(content).forEach { piece ->
                when (piece) {
                    is Piece.Text -> append(piece.text)
                    is Piece.Activity -> withLink(
                        LinkAnnotation.Clickable("activity-${piece.id}", linkStyle) { onOpenActivity(piece.id) }
                    ) { append(stringResource(R.string.coach_activity_link, piece.id)) }
                }
            }
        }
        Text(annotated, color = colors.fg, fontSize = 15.sp, lineHeight = 21.sp)
    }
    blocks(text).forEach { block ->
        when (block) {
            is Block.Paragraph -> Line(block.text)
            is Block.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEach { Line("• $it") }
            }
        }
    }
}

// --- the weekly report -----------------------------------------------------------------------------

@Composable
private fun WeeklyCard(model: CoachViewModel, modifier: Modifier) {
    val colors = ZankupTheme.colors
    val reports by model.weekly.collectAsStateWithLifecycle()
    val writing by model.writing.collectAsStateWithLifecycle()
    val writeFailed by model.writeFailed.collectAsStateWithLifecycle()
    val list = reports.data ?: return
    val latest = list.firstOrNull()
    // The long text waits behind a link: the headline, the highlights and the advice say most.
    var full by rememberSaveable { mutableStateOf(false) }
    ZankupCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.coach_weekly_title),
                style = MaterialTheme.typography.titleMedium,
                color = colors.fg
            )
            if (latest == null) {
                Muted(stringResource(R.string.coach_weekly_none))
            } else {
                Muted(stringResource(R.string.coach_weekly_week, weekDay(latest.weekStart)))
                when (latest.status) {
                    "pending" -> Muted(stringResource(R.string.coach_weekly_pending))
                    "failed" -> Notice(stringResource(R.string.coach_weekly_failed))
                    "done" -> Report(latest, full) { full = !full }
                }
            }
            // Writing it by hand only when there is none yet; Monday's comes on its own.
            if (latest == null) {
                Text(
                    stringResource(if (writing) R.string.coach_thinking else R.string.coach_weekly_generate),
                    color = colors.muted,
                    fontSize = 13.sp,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(enabled = !writing, role = Role.Button, onClick = model::writeWeekly)
                        .padding(top = 6.dp, bottom = 2.dp)
                )
            }
            if (writeFailed) Notice(stringResource(R.string.coach_weekly_failed))
        }
    }
}

@Composable
private fun Report(report: WeeklyOut, full: Boolean, onToggle: () -> Unit) {
    val colors = ZankupTheme.colors
    report.headline?.let {
        Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = colors.fg)
    }
    val highlights = report.highlights.orEmpty()
    if (highlights.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            highlights.forEach {
                Text(
                    it,
                    color = colors.fg,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(colors.surface2)
                        .padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
    report.recommendation?.let {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.accentSoft).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(stringResource(R.string.coach_weekly_recommendation), color = colors.fg, fontWeight = FontWeight.Bold)
            Text(it, color = colors.fg)
        }
    }
    if (full) report.summary?.let { Text(it, color = colors.fg) }
    val more = stringResource(R.string.coach_weekly_more)
    val less = stringResource(R.string.coach_weekly_less)
    Row(
        Modifier
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                if (full) {
                    collapse {
                        onToggle()
                        true
                    }
                } else {
                    expand {
                        onToggle()
                        true
                    }
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (full) less else more, color = colors.accent, fontWeight = FontWeight.SemiBold)
        ZankupIcon(
            if (full) ZIcon.Collapse else ZIcon.Expand,
            contentDescription = null,
            tint = colors.accent,
            size = 20.dp
        )
    }
}

// --- bits ------------------------------------------------------------------------------------------

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.bodyMedium, color = ZankupTheme.colors.muted)
}

/** Something to know (a failure, the budget): a muted line TalkBack reads out. */
@Composable
private fun Notice(text: String, modifier: Modifier = Modifier) {
    Muted(text, modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

/** "17 November" ("17 de noviembre"): the Monday of a report, in the phone's language. */
@Composable
private fun weekDay(iso: String): String {
    val locale = LocalFormats.current.locale
    val date = runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() ?: return iso
    val pattern = remember(locale) { DateFormat.getBestDateTimePattern(locale, "dMMMM") }
    return DateTimeFormatter.ofPattern(pattern, locale).format(date)
}

private const val MAX_QUESTION = 4000
