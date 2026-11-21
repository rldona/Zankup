package com.zankup.android.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.SearchTotals
import com.zankup.android.designsystem.R as DesignR
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.theme.ZankupTheme
import com.zankup.android.domain.Sport
import com.zankup.android.network.ApiResult
import com.zankup.android.ui.ActivityCard
import com.zankup.android.ui.LocalFormats
import com.zankup.android.ui.R as UiR
import com.zankup.android.ui.sportName

/** The cards left below the last one shown when the next page starts loading. */
private const val LOAD_AHEAD = 6

/**
 * Activities (OS-1131): the search integrated in the bar, with back and its filters, sync and calendar,
 * the sport chips under it, then the feed or the search's results. A card opens its activity, prefetched
 * as it is pressed.
 */
@Composable
fun FeedScreen(
    onOpen: (Long) -> Unit,
    onBack: () -> Unit,
    onSync: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    feedScroll: LazyListState = rememberLazyListState(),
    resultsScroll: LazyListState = rememberLazyListState()
) {
    val model: FeedViewModel = hiltViewModel()
    val ui by model.ui.collectAsStateWithLifecycle()
    val text by model.text.collectAsStateWithLifecycle()
    val sheet by model.sheet.collectAsStateWithLifecycle()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val ime = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var filtersPending by remember { mutableStateOf(false) }
    // With the keyboard open the sheet would start shorter and grow as the keyboard hides: open it only
    // once the keyboard is gone, so it comes in at its final height.
    LaunchedEffect(ime, filtersPending) {
        if (filtersPending && !ime) {
            filtersPending = false
            model.openFilters()
        }
    }
    // Each list keeps its own scroll: going back to the feed finds it where it was.
    LaunchedEffect(ui.request) { if (ui.searching) resultsScroll.scrollToItem(0) }
    Column(modifier.fillMaxSize()) {
        FeedHeader(
            text = text,
            filtersOn = ui.filtersOn,
            onBack = onBack,
            onType = model::type,
            onClear = model::clearText,
            onFilters = {
                focus.clearFocus()
                if (ime) {
                    filtersPending = true
                    keyboard?.hide()
                } else {
                    model.openFilters()
                }
            },
            onSync = onSync,
            onCalendar = onCalendar
        )
        SportChips(ui.sport, model::choose)
        ActivityList(ui, model, onOpen, if (ui.searching) resultsScroll else feedScroll)
    }
    sheet?.let { open ->
        FiltersSheet(
            sheet = open,
            units = ui.units,
            onChange = model::editFilters,
            onApply = model::applyFilters,
            onClear = model::clearFilters,
            onDismiss = model::closeFilters
        )
    }
}

/** The header's height under the status bar. */
private val HEADER = 56.dp

/**
 * The bar: back, the search field filling what is left, and its filters, sync and calendar on the right.
 * The title is gone, as the React Native app's search screen.
 */
@Composable
private fun FeedHeader(
    text: String,
    filtersOn: Boolean,
    onBack: () -> Unit,
    onType: (String) -> Unit,
    onClear: () -> Unit,
    onFilters: () -> Unit,
    onSync: () -> Unit,
    onCalendar: () -> Unit
) {
    val colors = ZankupTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(HEADER)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                ZankupIcon(ZIcon.Back, contentDescription = stringResource(DesignR.string.back), size = 22.dp)
            }
            SearchField(
                text = text,
                onType = onType,
                onClear = onClear,
                modifier = Modifier.weight(1f)
            )
            HeaderAction(
                icon = ZIcon.Options,
                label = stringResource(if (filtersOn) R.string.feed_filters_on else R.string.feed_filters),
                tint = if (filtersOn) colors.accent else colors.fg,
                onClick = onFilters
            )
            HeaderAction(
                icon = ZIcon.Sync,
                label = stringResource(R.string.feed_sync),
                onClick = onSync
            )
            HeaderAction(
                icon = ZIcon.Calendar,
                label = stringResource(R.string.feed_calendar),
                onClick = onCalendar
            )
        }
    }
}

/** The search integrated in the bar: smaller than a form's field, a pill on the surface colour. */
@Composable
private fun SearchField(text: String, onType: (String) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ZankupTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    BasicTextField(
        value = text,
        onValueChange = onType,
        modifier = modifier,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = colors.fg),
        cursorBrush = SolidColor(colors.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        decorationBox = { field ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.surface2)
                    .padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZankupIcon(ZIcon.Search, contentDescription = null, tint = colors.muted, size = 18.dp)
                Box(Modifier.weight(1f)) {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.feed_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    field()
                }
                if (text.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable(role = Role.Button, onClick = onClear),
                        contentAlignment = Alignment.Center
                    ) {
                        ZankupIcon(
                            ZIcon.Close,
                            contentDescription = stringResource(R.string.feed_search_clear),
                            tint = colors.muted,
                            size = 18.dp
                        )
                    }
                }
            }
        }
    )
}

/** One of the bar's icons, kept small so the search still has room. */
@Composable
private fun HeaderAction(icon: ZIcon, label: String, onClick: () -> Unit, tint: Color = ZankupTheme.colors.fg) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        ZankupIcon(icon, contentDescription = label, tint = tint, size = 22.dp)
    }
}

@Composable
private fun SportChips(selected: Sport?, onSelect: (Sport?) -> Unit) {
    val all = stringResource(UiR.string.sport_all)
    // The row runs edge to edge; the inset is padding inside the scroll, not a margin around it.
    Box(Modifier.fillMaxWidth().background(ZankupTheme.colors.surface)) {
        ChipPicker(
            options = listOf<Sport?>(null) + Sport.entries,
            selected = selected,
            label = { sport -> sport?.let { stringResource(sportName(it)) } ?: all },
            onSelect = onSelect,
            accent = true,
            compact = true,
            contentPadding = PaddingValues(horizontal = ZankupTheme.spacing.gutter),
            modifier = Modifier.padding(vertical = ZankupTheme.spacing.s)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ActivityList(ui: FeedUi, model: FeedViewModel, onOpen: (Long) -> Unit, state: LazyListState) {
    val list = ui.list
    val nearEnd by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
        }
    }
    LaunchedEffect(nearEnd, list.items.size) {
        if (nearEnd && list.items.isNotEmpty()) model.loadMore()
    }
    val spacing = ZankupTheme.spacing
    PullToRefreshBox(
        isRefreshing = ui.refreshing,
        onRefresh = model::refresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = state,
            // The tag the benchmarks scroll by (OS-1180).
            modifier = Modifier.fillMaxSize().testTag("feed"),
            contentPadding = PaddingValues(
                start = spacing.gutter,
                end = spacing.gutter,
                top = spacing.s,
                bottom = spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.s)
        ) {
            val totals = list.totals?.takeIf { ui.searching && it.count > 0 }
            if (totals != null) item(key = "summary") { Summary(totals) }
            when {
                list.skeleton -> items(SKELETON_CARDS) { CardSkeleton() }
                list.failure != null -> item(key = "failure") {
                    ErrorState(onRetry = model::retry, offline = list.failure is ApiResult.Offline)
                }
                list.empty -> item(key = "empty") {
                    if (ui.searching) {
                        EmptyState(title = stringResource(R.string.feed_none), icon = ZIcon.Search)
                    } else {
                        EmptyState(
                            title = stringResource(R.string.feed_empty),
                            body = stringResource(R.string.feed_empty_body),
                            icon = ZIcon.Activities
                        )
                    }
                }
            }
            items(list.items, key = { it.id }) { activity -> Card(activity, model, onOpen) }
            if (list.loadingMore && list.items.isNotEmpty()) item(key = "more") { CardSkeleton() }
        }
    }
}

private const val SKELETON_CARDS = 4

@Composable
private fun Card(activity: ActivitySummary, model: FeedViewModel, onOpen: (Long) -> Unit) {
    val id = activity.id.toLong()
    ActivityCard(
        activity = activity,
        onOpen = { onOpen(id) },
        modifier = Modifier.pointerInput(id) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                model.pressing(id)
            }
        }
    )
}

/** Every match, not only the pages loaded: how many, and their distance, moving time and ascent. */
@Composable
private fun Summary(totals: SearchTotals) {
    val formats = LocalFormats.current
    Text(
        pluralStringResource(
            R.plurals.feed_summary,
            totals.count,
            totals.count,
            formats.distance(totals.distanceM),
            formats.hoursMinutes(totals.movingS),
            formats.elevation(totals.ascentM)
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = ZankupTheme.colors.muted,
        modifier = Modifier.padding(bottom = ZankupTheme.spacing.xs)
    )
}

/** A card's shape while the list loads. */
@Composable
private fun CardSkeleton() {
    Skeleton(Modifier.fillMaxWidth(), height = 112.dp)
}
