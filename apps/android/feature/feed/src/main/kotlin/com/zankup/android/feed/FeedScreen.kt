package com.zankup.android.feed

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zankup.android.api.model.ActivitySummary
import com.zankup.android.api.model.SearchTotals
import com.zankup.android.designsystem.components.ChipPicker
import com.zankup.android.designsystem.components.EmptyState
import com.zankup.android.designsystem.components.ErrorState
import com.zankup.android.designsystem.components.Skeleton
import com.zankup.android.designsystem.components.ZIcon
import com.zankup.android.designsystem.components.ZankupIcon
import com.zankup.android.designsystem.components.ZankupTopBar
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
 * Activities (OS-1131): the title with Sync and Calendar, the search bar with its filters, the sport
 * chips, then the feed or the search's results. A card opens its activity, prefetched as it is pressed.
 */
@Composable
fun FeedScreen(onOpen: (Long) -> Unit, onSync: () -> Unit, onCalendar: () -> Unit, modifier: Modifier = Modifier) {
    val model: FeedViewModel = hiltViewModel()
    val ui by model.ui.collectAsStateWithLifecycle()
    val text by model.text.collectAsStateWithLifecycle()
    val sheet by model.sheet.collectAsStateWithLifecycle()
    // Each list keeps its own scroll: going back to the feed finds it where it was.
    val feedScroll = rememberLazyListState()
    val resultsScroll = rememberLazyListState()
    LaunchedEffect(ui.request) { if (ui.searching) resultsScroll.scrollToItem(0) }
    Column(modifier.fillMaxSize()) {
        ZankupTopBar(
            title = stringResource(R.string.feed_title),
            actions = {
                IconButton(onClick = onSync) {
                    ZankupIcon(ZIcon.Sync, contentDescription = stringResource(R.string.feed_sync))
                }
                IconButton(onClick = onCalendar) {
                    ZankupIcon(ZIcon.Calendar, contentDescription = stringResource(R.string.feed_calendar))
                }
            }
        )
        SearchBar(
            text = text,
            filtersOn = ui.filtersOn,
            onType = model::type,
            onClear = model::clearText,
            onFilters = model::openFilters
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

@Composable
private fun SearchBar(
    text: String,
    filtersOn: Boolean,
    onType: (String) -> Unit,
    onClear: () -> Unit,
    onFilters: () -> Unit
) {
    val colors = ZankupTheme.colors
    val keyboard = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = text,
        onValueChange = onType,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ZankupTheme.spacing.gutter),
        placeholder = { Text(stringResource(R.string.feed_search_hint)) },
        leadingIcon = { ZankupIcon(ZIcon.Search, contentDescription = null, tint = colors.muted) },
        trailingIcon = {
            Row {
                if (text.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        ZankupIcon(
                            ZIcon.Close,
                            contentDescription = stringResource(R.string.feed_search_clear),
                            tint = colors.muted
                        )
                    }
                }
                IconButton(onClick = onFilters) {
                    val label = if (filtersOn) R.string.feed_filters_on else R.string.feed_filters
                    ZankupIcon(
                        ZIcon.Options,
                        contentDescription = stringResource(label),
                        tint = if (filtersOn) colors.accent else colors.muted
                    )
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.line,
            cursorColor = colors.accent,
            focusedTextColor = colors.fg,
            unfocusedTextColor = colors.fg,
            focusedPlaceholderColor = colors.muted,
            unfocusedPlaceholderColor = colors.muted
        )
    )
}

@Composable
private fun SportChips(selected: Sport?, onSelect: (Sport?) -> Unit) {
    val all = stringResource(UiR.string.sport_all)
    ChipPicker(
        options = listOf<Sport?>(null) + Sport.entries,
        selected = selected,
        label = { sport -> sport?.let { stringResource(sportName(it)) } ?: all },
        onSelect = onSelect,
        modifier = Modifier.padding(
            start = ZankupTheme.spacing.gutter,
            end = ZankupTheme.spacing.gutter,
            top = ZankupTheme.spacing.sm,
            bottom = ZankupTheme.spacing.s
        )
    )
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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = spacing.gutter,
                end = spacing.gutter,
                top = spacing.xs,
                bottom = spacing.xl
            ),
            verticalArrangement = Arrangement.spacedBy(spacing.sm)
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
