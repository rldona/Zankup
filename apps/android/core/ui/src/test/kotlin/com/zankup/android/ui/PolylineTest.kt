package com.zankup.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PolylineTest {
    // The example from Google's documentation of the format.
    private val google = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"

    @Test
    fun decodes() {
        assertEquals(listOf(38.5 to -120.2, 40.7 to -120.95, 43.252 to -126.453), decodePolyline(google))
    }

    @Test
    fun stopsAtMalformedInput() {
        assertEquals(listOf(38.5 to -120.2), decodePolyline(google.take(12)))
        assertEquals(emptyList<Pair<Double, Double>>(), decodePolyline("\u0001\u0002"))
    }
}
