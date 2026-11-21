package com.zankup.android.tools

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which notifications have been seen, opened and deleted, kept in the phone. The bell counts what
 * arrived after the last visit; opening the screen moves that mark to now. Each row keeps its own
 * read mark, and activities older than [trackedSince] are history, not notifications.
 */
@Singleton
class NotificationsSeen
@Inject
constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("notifications", Context.MODE_PRIVATE)
    private val lastSeen = MutableStateFlow(initialLastSeen())
    private val deleted = MutableStateFlow(prefs.getStringSet(DELETED, emptySet()).orEmpty())
    private val read = MutableStateFlow(prefs.getStringSet(READ, emptySet()).orEmpty())
    private val since = MutableStateFlow(initialSince())

    /** The moment of the last visit, in epoch milliseconds. */
    val seenAt: StateFlow<Long> = lastSeen.asStateFlow()

    /** The notification keys hidden by the person. */
    val hidden: StateFlow<Set<String>> = deleted.asStateFlow()

    /** The notification keys the person has already opened. */
    val opened: StateFlow<Set<String>> = read.asStateFlow()

    /** The moment this inbox started following activities, in epoch milliseconds. */
    val trackedSince: StateFlow<Long> = since.asStateFlow()

    fun markSeen() {
        val now = System.currentTimeMillis()
        if (now > lastSeen.value) {
            lastSeen.value = now
            prefs.edit().putLong(LAST_SEEN, now).apply()
        }
    }

    fun markRead(key: String) {
        if (key in read.value) return
        val next = linkedSetOf<String>().apply {
            addAll(read.value)
            add(key)
        }
        while (next.size > MAX_READ) next.remove(next.first())
        read.value = next
        prefs.edit().putStringSet(READ, next).apply()
    }

    fun delete(key: String) = delete(listOf(key))

    fun delete(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val next = deleted.value + keys
        deleted.value = next
        prefs.edit().putStringSet(DELETED, next).apply()
    }

    /** The first visit has no unread pile: it starts counting from then. */
    private fun initialLastSeen(): Long {
        val kept = prefs.getLong(LAST_SEEN, -1)
        if (kept >= 0) return kept
        val now = System.currentTimeMillis()
        prefs.edit().putLong(LAST_SEEN, now).apply()
        return now
    }

    /** Only the activities done from the day this phone started following them, not the history. */
    private fun initialSince(): Long {
        val kept = prefs.getLong(TRACKED_SINCE, -1)
        if (kept >= 0) return kept
        val today = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        prefs.edit().putLong(TRACKED_SINCE, today).apply()
        return today
    }

    private companion object {
        const val LAST_SEEN = "last_seen"
        const val DELETED = "deleted"
        const val READ = "read"
        const val TRACKED_SINCE = "tracked_since"
        const val MAX_READ = 500
    }
}
