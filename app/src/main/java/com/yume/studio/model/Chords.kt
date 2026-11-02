package com.yume.studio.model

/** Гитарный аккорд: лады для 6 струн (E A D G B e), -1 — струна не играет. */
data class GuitarChord(val name: String, val frets: List<Int>) {
    fun keys(tuning: List<Int> = Fretboard.GUITAR_TUNING): List<Int> =
        frets.mapIndexedNotNull { i, f -> if (f < 0) null else tuning[i] + f }
}

object Fretboard {
    val GUITAR_TUNING = listOf(40, 45, 50, 55, 59, 64)
    val BASS_TUNING = listOf(28, 33, 38, 43)
    fun key(tuning: List<Int>, string: Int, fret: Int) = tuning[string] + fret
}

object Chords {
    private val shapes = mapOf(
        "C" to listOf(-1, 3, 2, 0, 1, 0),
        "Cm" to listOf(-1, 3, 5, 5, 4, 3),
        "C7" to listOf(-1, 3, 2, 3, 1, 0),
        "Cmaj7" to listOf(-1, 3, 2, 0, 0, 0),
        "D" to listOf(-1, -1, 0, 2, 3, 2),
        "Dm" to listOf(-1, -1, 0, 2, 3, 1),
        "D7" to listOf(-1, -1, 0, 2, 1, 2),
        "Dm7" to listOf(-1, -1, 0, 2, 1, 1),
        "E" to listOf(0, 2, 2, 1, 0, 0),
        "Em" to listOf(0, 2, 2, 0, 0, 0),
        "E7" to listOf(0, 2, 0, 1, 0, 0),
        "Em7" to listOf(0, 2, 0, 0, 0, 0),
        "F" to listOf(1, 3, 3, 2, 1, 1),
        "Fm" to listOf(1, 3, 3, 1, 1, 1),
        "Fmaj7" to listOf(-1, -1, 3, 2, 1, 0),
        "G" to listOf(3, 2, 0, 0, 0, 3),
        "Gm" to listOf(3, 5, 5, 3, 3, 3),
        "G7" to listOf(3, 2, 0, 0, 0, 1),
        "A" to listOf(-1, 0, 2, 2, 2, 0),
        "Am" to listOf(-1, 0, 2, 2, 1, 0),
        "A7" to listOf(-1, 0, 2, 0, 2, 0),
        "Am7" to listOf(-1, 0, 2, 0, 1, 0),
        "B" to listOf(-1, 2, 4, 4, 4, 2),
        "Bm" to listOf(-1, 2, 4, 4, 3, 2),
        "B7" to listOf(-1, 2, 1, 2, 0, 2),
        "Bb" to listOf(-1, 1, 3, 3, 3, 1),
        "F#m" to listOf(2, 4, 4, 2, 2, 2),
        "C#m" to listOf(-1, 4, 6, 6, 5, 4),
        "G#m" to listOf(4, 6, 6, 4, 4, 4),
        "Eb" to listOf(-1, 6, 5, 3, 4, 3),
        "Ab" to listOf(4, 6, 6, 5, 4, 4),
        "Asus2" to listOf(-1, 0, 2, 2, 0, 0),
        "Dsus4" to listOf(-1, -1, 0, 2, 3, 3),
        "C#" to listOf(-1, 4, 6, 6, 6, 4),
        "F#" to listOf(2, 4, 4, 3, 2, 2),
        "Ebm" to listOf(-1, 6, 8, 8, 7, 6),
        "Abm" to listOf(4, 6, 6, 4, 4, 4),
        "Bbm" to listOf(-1, 1, 3, 3, 2, 1),
    )

    fun chord(name: String): GuitarChord = GuitarChord(name, shapes[name] ?: shapes.getValue("C"))
    val allNames: List<String> get() = shapes.keys.toList()

    /** Набор из 8 аккордов, подходящих к тональности (как полосы аккордов GarageBand). */
    fun forKey(root: Int, minor: Boolean): List<GuitarChord> {
        val names = arrayOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        fun n(pc: Int) = names[pc.mod(12)]
        val degrees = if (!minor) {
            listOf(n(root + 4) + "m", n(root + 9) + "m", n(root + 2), n(root + 7), n(root), n(root + 11) + "m", n(root + 5), n(root + 2) + "m")
        } else {
            listOf(n(root) + "m", n(root + 5) + "m", n(root + 7) + "m", n(root + 8), n(root + 3), n(root + 10), n(root + 7), n(root + 2) + "m")
        }
        return degrees.map { chord(if (it in shapes) it else it.removeSuffix("m")) }
    }

    /** Ноты аккорда для клавиш/струнных: трезвучие от тоники в нужной октаве. */
    fun triad(rootKey: Int, minor: Boolean): List<Int> = listOf(rootKey, rootKey + if (minor) 3 else 4, rootKey + 7)
}

/** Гаммы для режима «Лады» (клавиши/синт). */
object Scales {
    val major = intArrayOf(0, 2, 4, 5, 7, 9, 11)
    val minor = intArrayOf(0, 2, 3, 5, 7, 8, 10)
    val pentatonic = intArrayOf(0, 2, 4, 7, 9)
    fun inScale(key: Int, root: Int, scale: IntArray) = ((key - root).mod(12)) in scale
}
