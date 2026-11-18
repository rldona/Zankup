package com.zankup.android.ui.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BodyMapTest {
    @Test
    fun exercisesAddUpTheirSetsPerMuscle() {
        val worked = musclesFromExercises(mapOf("bench_press" to 3, "squat" to 4))
        assertEquals(3, worked[Muscle.CHEST])
        assertTrue(worked.getValue(Muscle.QUADS) >= 4)
    }

    @Test
    fun theWorkoutKindWhenNothingWasRecorded() {
        assertEquals(WORKOUT_MUSCLES.getValue("grit"), musclesFor(emptyMap(), "grit"))
        val recorded = mapOf(Muscle.ABS to 2)
        assertEquals(recorded, musclesFor(recorded, "grit"))
        assertTrue(musclesFor(emptyMap(), "unknown").isEmpty())
    }

    @Test
    fun everyShapeParses() {
        // Every muscle drawn on some side, and every side has shapes.
        val drawn = BODY_SHAPES.values.flatMap { it.keys }.toSet()
        assertEquals(Muscle.entries.toSet(), drawn)
        assertTrue(BODY_SILHOUETTE.startsWith("M"))
    }
}
