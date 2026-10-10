package no.mwmai.flashlight

enum class Mode { STEADY, STROBE, SOS }

/** One step of a blink pattern: torch on or off, held for [millis]. */
data class Step(val on: Boolean, val millis: Long)

const val MIN_STROBE_HZ = 1
const val MAX_STROBE_HZ = 15
const val DEFAULT_STROBE_HZ = 5

/** One strobe flash: on for half the period, off for the rest. */
fun strobeSteps(hz: Int): List<Step> {
    val period = 1000L / hz.coerceIn(MIN_STROBE_HZ, MAX_STROBE_HZ)
    val on = period / 2
    return listOf(Step(true, on), Step(false, period - on))
}

/**
 * Morse SOS (... --- ...) with a dot of [unit] ms. Standard Morse timing: a
 * dash is 3 dots, 1 dot between signals, 3 between letters, and a 7-dot word
 * gap at the end so the signal repeats cleanly when looped.
 */
fun sosSteps(unit: Long = 200L): List<Step> {
    val steps = mutableListOf<Step>()
    fun letter(signals: List<Int>) = signals.forEachIndexed { i, length ->
        if (i > 0) steps += Step(false, unit)
        steps += Step(true, length * unit)
    }
    letter(listOf(1, 1, 1))
    steps += Step(false, 3 * unit)
    letter(listOf(3, 3, 3))
    steps += Step(false, 3 * unit)
    letter(listOf(1, 1, 1))
    steps += Step(false, 7 * unit)
    return steps
}
