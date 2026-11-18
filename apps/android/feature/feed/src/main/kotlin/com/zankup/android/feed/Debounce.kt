package com.zankup.android.feed

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** How long typing has to pause before the search runs. */
const val TYPING_DEBOUNCE_MS = 300L

/**
 * What is typed in the search bar as the query to search: trimmed, once typing pauses for [millis].
 * Clearing the bar goes through at once, so the feed comes back without waiting.
 */
@OptIn(FlowPreview::class)
fun Flow<String>.debouncedQuery(millis: Long = TYPING_DEBOUNCE_MS): Flow<String> = map { it.trim() }
    .debounce { if (it.isEmpty()) 0L else millis }
    .distinctUntilChanged()
