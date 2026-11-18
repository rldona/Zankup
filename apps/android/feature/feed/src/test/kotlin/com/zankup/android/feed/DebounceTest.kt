package com.zankup.android.feed

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebounceTest {
    @Test
    fun searchesOncePausedAndClearsAtOnce() = runTest {
        val typed = MutableStateFlow("")
        val seen = mutableListOf<String>()
        backgroundScope.launch { typed.debouncedQuery().collect { seen += it } }
        runCurrent()
        assertEquals(listOf(""), seen)

        typed.value = "r"
        advanceTimeBy(100)
        typed.value = "ru"
        advanceTimeBy(100)
        typed.value = "run "
        advanceTimeBy(299)
        runCurrent()
        assertEquals(listOf(""), seen)
        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("", "run"), seen)

        // Only a trailing space more: the same search, not run again.
        typed.value = "run  "
        advanceTimeBy(400)
        runCurrent()
        assertEquals(listOf("", "run"), seen)

        typed.value = ""
        runCurrent()
        assertEquals(listOf("", "run", ""), seen)
    }
}
