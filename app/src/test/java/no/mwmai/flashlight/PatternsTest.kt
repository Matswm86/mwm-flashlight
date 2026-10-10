package no.mwmai.flashlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PatternsTest {
    @Test
    fun strobeSplitsThePeriodInHalf() {
        assertEquals(listOf(Step(true, 100), Step(false, 100)), strobeSteps(5))
        assertEquals(listOf(Step(true, 500), Step(false, 500)), strobeSteps(1))
    }

    @Test
    fun strobeSpeedIsClamped() {
        assertEquals(strobeSteps(MAX_STROBE_HZ), strobeSteps(100))
        assertEquals(strobeSteps(MIN_STROBE_HZ), strobeSteps(0))
    }

    @Test
    fun sosFollowsMorseTiming() {
        val steps = sosSteps(unit = 1)
        val flashes = steps.filter { it.on }.map { it.millis }
        assertEquals(listOf<Long>(1, 1, 1, 3, 3, 3, 1, 1, 1), flashes)
        // 15 units of light, 2+2+2 between signals, 3+3 between letters, 7 at the end.
        assertEquals(34L, steps.sumOf { it.millis })
        assertEquals(Step(false, 7), steps.last())
    }

    @Test
    fun sosAlternatesOnAndOff() {
        val steps = sosSteps()
        assertTrue(steps.zipWithNext().all { (a, b) -> a.on != b.on })
        assertTrue(steps.first().on)
    }
}
