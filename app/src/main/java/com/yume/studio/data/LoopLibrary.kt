package com.yume.studio.data

import com.yume.studio.audio.Articulation
import com.yume.studio.audio.InstrumentType
import com.yume.studio.model.Note
import com.yume.studio.model.PPQ

enum class LoopCategory(val title: String) {
    SYNTH("Синтезаторы"),
    DRUMS("Барабаны"),
    BASS("Басы"),
    GUITAR("Гитары"),
    KEYS("Клавиши"),
    STRINGS("Струнные"),
    VOCAL("Вокалы"),
}

/**
 * Луп из встроенной библиотеки. Хранится как MIDI-паттерн и проигрывается движком,
 * поэтому всегда совпадает с темпом проекта и весит ноль байт.
 * Мелодические лупы записаны в до мажоре / ля миноре и транспонируются под тональность проекта.
 */
data class Loop(
    val id: String,
    val name: String,
    val category: LoopCategory,
    val type: InstrumentType,
    val presetIndex: Int,
    val bars: Int,
    val notes: List<Note>,
    val tags: String = "",
    val articulation: Articulation = Articulation.LEGATO,
) {
    val melodic: Boolean get() = type != InstrumentType.DRUMS
    val lengthTicks: Int get() = bars * PPQ * 4
    fun seconds(bpm: Float): Float = bars * 4 * 60f / bpm
    fun transposed(semitones: Int): List<Note> =
        if (!melodic || semitones == 0) notes else notes.map { it.copy(key = (it.key + semitones).coerceIn(0, 127)) }
}

private const val S = PPQ / 4  // шестнадцатая
private const val E = PPQ / 2  // восьмая
private const val Q = PPQ      // четверть
private const val BAR = PPQ * 4

/** Строка шагов: 'x' — акцент, 'o' — тихо, '-' — призрачная нота, '.' — пауза. 16 шагов = такт. */
private fun steps(key: Int, pattern: String, bars: Int = 1, swing: Float = 0f): List<Note> {
    val clean = pattern.filter { it != ' ' && it != '|' }
    val out = ArrayList<Note>()
    for (b in 0 until bars) {
        clean.forEachIndexed { i, c ->
            val vel = when (c) { 'x' -> 112; 'o' -> 78; '-' -> 45; else -> 0 }
            if (vel > 0) {
                var t = b * clean.length * S + i * S
                if (swing > 0f && i % 2 == 1) t += (S * swing).toInt()
                out += Note(t, S, key, vel)
            }
        }
    }
    return out
}

private fun drums(bars: Int = 1, swing: Float = 0f, vararg rows: Pair<Int, String>): List<Note> {
    val all = rows.flatMap { (k, p) ->
        val stepsPerPattern = p.count { it != ' ' && it != '|' }
        val repeats = maxOf(1, bars * 16 / stepsPerPattern)
        steps(k, p, repeats, swing)
    }
    return all.sortedBy { it.tick }
}

private fun chord(at: Int, len: Int, vel: Int, vararg keys: Int) = keys.map { Note(at, len, it, vel) }

// Cmaj7 – Am7 – Dm7 – G7 (каноничный лоуфай/сити-поп)
private val PROG = listOf(
    intArrayOf(60, 64, 67, 71),
    intArrayOf(57, 60, 64, 67),
    intArrayOf(62, 65, 69, 72),
    intArrayOf(55, 59, 62, 65),
)
private val ROOTS = intArrayOf(36, 33, 38, 31)

private fun progression(len: Int = BAR - 30, vel: Int = 80, octave: Int = 0): List<Note> =
    PROG.flatMapIndexed { i, c -> chord(i * BAR, len, vel, *c.map { it + octave * 12 }.toIntArray()) }

private fun arpeggio(rate: Int, vel: Int, octave: Int = 0, pattern: IntArray = intArrayOf(0, 1, 2, 3, 2, 1)): List<Note> {
    val out = ArrayList<Note>()
    PROG.forEachIndexed { bar, c ->
        var t = 0
        var i = 0
        while (t < BAR) {
            out += Note(bar * BAR + t, rate - 10, c[pattern[i % pattern.size]] + 12 * octave, vel - (i % 2) * 12)
            t += rate; i++
        }
    }
    return out
}

private fun melody(vararg n: Pair<Int, Int>, start: Int = 0, vel: Int = 92): List<Note> {
    var t = start
    val out = ArrayList<Note>()
    for ((key, dur) in n) {
        if (key > 0) out += Note(t, dur - 20, key, vel)
        t += dur
    }
    return out
}

private fun strum(at: Int, keys: IntArray, vel: Int, down: Boolean = true, len: Int = E): List<Note> {
    val ordered = if (down) keys.toList() else keys.reversed()
    return ordered.mapIndexed { i, k -> Note(at + i * 18, len, k, vel - i * 3) }
}

object LoopLibrary {
    val all: List<Loop> by lazy { build() }

    fun byCategory(c: LoopCategory?) = if (c == null) all else all.filter { it.category == c }
    fun find(id: String) = all.firstOrNull { it.id == id }

    private fun build(): List<Loop> = listOf(
        // ── Барабаны ──
        Loop("lofi_drum_loop", "lofi_drum_loop", LoopCategory.DRUMS, InstrumentType.DRUMS, 0, 1,
            drums(1, 0.33f,
                36 to "x......o..x.....",
                38 to "....x.......x..-",
                42 to "o-o-o-o-o-o-o-o-"), "лоуфай бит хип-хоп"),
        Loop("boom_bap", "boom_bap", LoopCategory.DRUMS, InstrumentType.DRUMS, 1, 2,
            drums(2, 0.2f,
                36 to "x.....x...x.....x.....x..x......",
                38 to "....x.......x.......x.......x...",
                42 to "x.x.x.x.x.x.x.x.",
                46 to "..............o.............o..."), "хип-хоп"),
        Loop("four_on_floor", "four_on_floor", LoopCategory.DRUMS, InstrumentType.DRUMS, 6, 1,
            drums(1, 0f,
                36 to "x...x...x...x...",
                39 to "....x.......x...",
                42 to "o.o.o.o.o.o.o.o.",
                46 to "..x...x...x...x."), "хаус дэнс"),
        Loop("trap_hats", "trap_hats", LoopCategory.DRUMS, InstrumentType.DRUMS, 6, 2,
            drums(2, 0f,
                36 to "x.......x.x.....x......x..x.....",
                39 to "........x...............x.......",
                42 to "xoxoxoxoxoxoxxxxxoxoxoxoxoxoxxxx"), "трэп гиперпоп"),
        Loop("grunge_rock", "grunge_rock", LoopCategory.DRUMS, InstrumentType.DRUMS, 4, 2,
            drums(2, 0f,
                36 to "x.....x.x.....x.x.....x.x...x...",
                38 to "....x.......x.......x.......x.x.",
                49 to "x...............................",
                46 to "..o...o...o...o...o...o...o...o."), "гранж рок"),
        Loop("shoegaze_beat", "shoegaze_beat", LoopCategory.DRUMS, InstrumentType.DRUMS, 3, 2,
            drums(2, 0f,
                36 to "x.......x.x.....x.......x.......",
                38 to "....x.......x.......x.......x...",
                51 to "x.o.x.o.x.o.x.o.",
                47 to "..............o.............oxo."), "шугейз дрим-поп"),
        Loop("city_pop_groove", "city_pop_groove", LoopCategory.DRUMS, InstrumentType.DRUMS, 0, 1,
            drums(1, 0.1f,
                36 to "x..o..x...x..o..",
                38 to "....x.......x...",
                42 to "xoxoxoxoxoxoxoxo",
                54 to "..o...o...o...o."), "сити-поп 80-е"),
        Loop("brush_ballad", "brush_ballad", LoopCategory.DRUMS, InstrumentType.DRUMS, 9, 1,
            drums(1, 0.33f,
                36 to "x.......o.......",
                38 to "....o.......o...",
                42 to "o.-.o.-.o.-.o.-."), "джаз баллада щётки"),

        // ── Басы ──
        Loop("lofi_bass", "lofi_bass", LoopCategory.BASS, InstrumentType.BASS, 0, 4,
            ROOTS.flatMapIndexed { i, r ->
                listOf(Note(i * BAR, Q + E, r, 100), Note(i * BAR + Q * 2, E, r + 7, 80), Note(i * BAR + Q * 2 + E * 3, E, r + 12, 70))
            }, "лоуфай"),
        Loop("synth_bass_pulse", "synth_bass_pulse", LoopCategory.BASS, InstrumentType.BASS, 6, 4,
            ROOTS.flatMapIndexed { i, r -> (0 until 8).map { s -> Note(i * BAR + s * E, E - 40, r, if (s % 2 == 0) 105 else 80) } },
            "синтвейв ночной драйв"),
        Loop("slap_funk", "slap_funk", LoopCategory.BASS, InstrumentType.BASS, 3, 2,
            melody(36 to S, 0 to S, 48 to S, 36 to S, 0 to E, 43 to S, 46 to S, 36 to E, 48 to S, 0 to S, 41 to E, 43 to E)
                + melody(33 to S, 0 to S, 45 to S, 33 to S, 0 to E, 40 to S, 43 to S, 33 to E, 45 to S, 0 to S, 38 to E, 40 to E, start = BAR),
            "фанк"),
        Loop("acid_line", "acid_line", LoopCategory.BASS, InstrumentType.SYNTH, 7, 1,
            melody(36 to S, 36 to S, 48 to S, 36 to S, 39 to S, 36 to S, 46 to S, 36 to S, 36 to S, 48 to S, 36 to S, 43 to S, 36 to S, 46 to S, 41 to S, 39 to S),
            "эйсид техно"),
        Loop("sub_808", "sub_808", LoopCategory.BASS, InstrumentType.SYNTH, 6, 2,
            melody(36 to Q * 3, 36 to E, 39 to E, 33 to Q * 2, 31 to Q * 2), "трэп 808"),

        // ── Клавиши ──
        Loop("rhodes_chords", "rhodes_chords", LoopCategory.KEYS, InstrumentType.KEYS, 5, 4,
            PROG.flatMapIndexed { i, c ->
                chord(i * BAR, Q + E, 82, *c) + chord(i * BAR + Q * 2 + E, Q, 70, *c)
            }, "лоуфай сити-поп электропиано"),
        Loop("music_box", "music_box_melody", LoopCategory.KEYS, InstrumentType.KEYS, 12, 4,
            melody(76 to Q, 79 to E, 81 to E, 79 to Q, 76 to Q, 74 to Q, 76 to E, 72 to E, 74 to Q * 2,
                72 to Q, 76 to E, 79 to E, 84 to Q, 83 to Q, 81 to Q * 2, 79 to Q * 2), "шкатулка колыбельная"),
        Loop("piano_ballad", "piano_ballad", LoopCategory.KEYS, InstrumentType.KEYS, 0, 4,
            arpeggio(E, 80, 0, intArrayOf(0, 2, 1, 3, 2, 1, 0, 2)) +
                ROOTS.mapIndexed { i, r -> Note(i * BAR, BAR - 40, r + 12, 85) }, "баллада рояль"),
        Loop("city_pop_keys", "city_pop_keys", LoopCategory.KEYS, InstrumentType.KEYS, 6, 4,
            PROG.flatMapIndexed { i, c ->
                listOf(0, E * 3, Q * 2 + E, Q * 3 + E).flatMap { off -> chord(i * BAR + off, E - 30, 88, *c.map { it + 12 }.toIntArray()) }
            }, "сити-поп фм"),

        // ── Синтезаторы ──
        Loop("dream_pad", "dream_pad", LoopCategory.SYNTH, InstrumentType.SYNTH, 0, 4, progression(BAR - 10, 75), "пэд эмбиент мечта"),
        Loop("pluck_arp", "pluck_arp", LoopCategory.SYNTH, InstrumentType.SYNTH, 2, 4, arpeggio(S, 92, 1), "арпеджио"),
        Loop("chiptune", "chiptune_melody", LoopCategory.SYNTH, InstrumentType.SYNTH, 4, 2,
            melody(72 to E, 76 to E, 79 to E, 84 to E, 83 to E, 79 to E, 76 to Q, 74 to E, 77 to E, 81 to E, 86 to E, 84 to Q, 79 to Q), "8-бит чиптюн"),
        Loop("supersaw_stabs", "supersaw_stabs", LoopCategory.SYNTH, InstrumentType.SYNTH, 5, 4,
            PROG.flatMapIndexed { i, c -> listOf(0, E * 3, Q * 2 + E).flatMap { off -> chord(i * BAR + off, E, 95, *c) } }, "транс хаус"),
        Loop("hyperpop_lead", "hyperpop_lead", LoopCategory.SYNTH, InstrumentType.SYNTH, 9, 2,
            melody(79 to E, 81 to E, 84 to Q, 86 to S, 84 to S, 81 to E, 79 to Q, 76 to E, 79 to E, 81 to Q + E, 0 to E, 84 to E, 88 to E), "гиперпоп"),
        Loop("shoegaze_wall", "shoegaze_wall", LoopCategory.SYNTH, InstrumentType.SYNTH, 10, 4, progression(BAR, 70, -1), "шугейз стена звука"),

        // ── Гитары ──
        Loop("noisy_guitar", "noisy_guitar", LoopCategory.GUITAR, InstrumentType.GUITAR, 11, 2,
            listOf(40 to 47, 40 to 47, 43 to 50, 45 to 52).flatMapIndexed { i, (a, b) ->
                (0 until 4).flatMap { s -> listOf(Note(i * Q * 2 + s * E, E - 20, a, 105), Note(i * Q * 2 + s * E, E - 20, b, 100)) }
            }, "гранж дисторшн пауэр-аккорды"),
        Loop("nylon_arp", "nylon_arpeggio", LoopCategory.GUITAR, InstrumentType.GUITAR, 0, 4, arpeggio(E, 85, -1, intArrayOf(0, 2, 3, 1, 2, 3, 1, 2)), "нейлон перебор"),
        Loop("clean_strum", "clean_strum", LoopCategory.GUITAR, InstrumentType.GUITAR, 7, 4,
            PROG.flatMapIndexed { i, c ->
                val k = c.map { it - 12 }.toIntArray()
                listOf(0 to true, Q to true, Q + E to false, Q * 2 + E to false, Q * 3 to true, Q * 3 + E to false)
                    .flatMap { (off, d) -> strum(i * BAR + off, k, 92, d) }
            }, "чистая хорус сити-поп"),
        Loop("funk_chops", "funk_chops", LoopCategory.GUITAR, InstrumentType.GUITAR, 8, 1,
            listOf(S, Q + S, Q * 2 + S * 3, Q * 3 + S).flatMap { strum(it, intArrayOf(55, 59, 62, 65), 100, len = S) }, "фанк"),

        // ── Струнные ──
        Loop("string_pad", "string_pad", LoopCategory.STRINGS, InstrumentType.STRINGS, 5, 4, progression(BAR - 10, 70, -1), "оркестр пэд"),
        Loop("pizzicato_walk", "pizzicato_walk", LoopCategory.STRINGS, InstrumentType.STRINGS, 4, 2,
            melody(60 to E, 64 to E, 67 to E, 72 to E, 69 to E, 65 to E, 62 to E, 59 to E,
                60 to E, 67 to E, 64 to E, 72 to E, 71 to E, 67 to E, 62 to E, 67 to E), "пиццикато",
            Articulation.PIZZICATO),
        Loop("cello_line", "cello_line", LoopCategory.STRINGS, InstrumentType.STRINGS, 2, 4,
            melody(48 to Q * 2, 52 to Q * 2, 45 to Q * 2, 48 to Q * 2, 50 to Q * 2, 53 to Q * 2, 43 to Q * 3, 47 to Q), "виолончель"),

        // ── Вокалы ──
        Loop("vocal_chop", "vocal_chop", LoopCategory.VOCAL, InstrumentType.KEYS, 24, 2,
            melody(72 to E, 0 to S, 72 to S, 76 to E, 79 to E, 0 to E, 77 to S, 76 to S, 74 to Q,
                72 to E, 0 to S, 74 to S, 76 to E, 72 to E, 0 to E, 69 to E, 67 to Q), "вокальный чоп"),
        Loop("choir_aah", "choir_aah", LoopCategory.VOCAL, InstrumentType.KEYS, 23, 4, progression(BAR - 10, 72, 0), "хор"),
    )
}
