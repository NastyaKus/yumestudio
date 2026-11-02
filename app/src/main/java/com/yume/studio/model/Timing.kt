package com.yume.studio.model

import kotlin.math.roundToInt

/** Утилиты времени/сетки, независимые от Android (покрыты юнит-тестами). */
object Timing {
    fun samplesPerTick(bpm: Float, sampleRate: Int = 48000): Double = sampleRate * 60.0 / (bpm * PPQ)
    fun ticksToSeconds(ticks: Double, bpm: Float): Double = ticks / PPQ * 60.0 / bpm
    fun secondsToTicks(sec: Double, bpm: Float): Double = sec * bpm / 60.0 * PPQ

    /** «такт.доля.шестнадцатая», такты с 1 */
    fun formatPosition(tick: Double, beatsPerBar: Int): String {
        if (tick < 0) return "-" + formatPosition(-tick, beatsPerBar).removePrefix("-")
        val t = tick.toLong()
        val bar = t / (PPQ * beatsPerBar) + 1
        val beat = (t % (PPQ * beatsPerBar)) / PPQ + 1
        val six = (t % PPQ) / (PPQ / 4) + 1
        return "$bar.$beat.$six"
    }

    fun formatTime(tick: Double, bpm: Float): String {
        val s = ticksToSeconds(tick.coerceAtLeast(0.0), bpm)
        val m = (s / 60).toInt()
        val sec = s - m * 60
        return "%d:%04.1f".format(m, sec).replace(',', '.')
    }

    fun snap(tick: Int, grid: Int): Int = if (grid <= 1) tick else ((tick.toDouble() / grid).roundToInt() * grid)
    fun snapDown(tick: Int, grid: Int): Int = if (grid <= 1) tick else Math.floorDiv(tick, grid) * grid
    fun snapUp(tick: Int, grid: Int): Int = if (grid <= 1) tick else -Math.floorDiv(-tick, grid) * grid
}

enum class Grid(val title: String, val ticks: Int) {
    BAR("1", PPQ * 4),
    HALF("1/2", PPQ * 2),
    QUARTER("1/4", PPQ),
    EIGHTH("1/8", PPQ / 2),
    SIXTEENTH("1/16", PPQ / 4),
    TRIPLET("1/8т", PPQ / 3),
    THIRTYSECOND("1/32", PPQ / 8),
    OFF("Свобод.", 1),
}

object Quantize {
    /** Квантайз начала нот с силой strength (0..1), длины не меньше половины сетки. */
    fun notes(notes: List<Note>, grid: Int, strength: Float = 1f, swing: Float = 0f): List<Note> = notes.map { n ->
        var target = Timing.snap(n.tick, grid)
        // свинг смещает каждую вторую долю сетки
        if (swing > 0f && (target / grid) % 2 == 1) target += (grid * swing * 0.5f).toInt()
        val newTick = (n.tick + (target - n.tick) * strength).roundToInt().coerceAtLeast(0)
        n.copy(tick = newTick, length = maxOf(n.length, grid / 2))
    }
}
