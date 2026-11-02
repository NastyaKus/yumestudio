package com.yume.studio.audio

/** Индексы совпадают с enum SynthParam в cpp/instruments/Synth.h */
object SynthParam {
    const val WAVE1 = 0
    const val WAVE2 = 1
    const val MIX2 = 2
    const val DETUNE2 = 3
    const val CUTOFF = 4
    const val RES = 5
    const val FENV = 6
    const val A = 7
    const val D = 8
    const val S = 9
    const val R = 10
    const val FA = 11
    const val FD = 12
    const val FS = 13
    const val FR = 14
    const val LFO_RATE = 15
    const val LFO_PITCH = 16
    const val LFO_CUTOFF = 17
    const val GLIDE = 18
    const val UNISON = 19
    const val VOLUME = 20
    const val MONO = 21
    const val COUNT = 22

    val waveNames = listOf("Синус", "Треугольник", "Пила", "Квадрат", "Шум")
    fun waveValue(index: Int) = index / 4f
    fun waveIndex(value: Float) = (value * 4f + 0.5f).toInt().coerceIn(0, 4)
}

data class SynthPreset(val name: String, val params: List<Float>)

object SynthPresets {
    private fun p(
        name: String,
        w1: Int, w2: Int, mix2: Float, detune2: Float, cutoff: Float, res: Float, fenv: Float,
        a: Float, d: Float, s: Float, r: Float,
        fa: Float = 0.2f, fd: Float = 0.5f, fs: Float = 0.4f, fr: Float = 0.5f,
        lfoRate: Float = 0.3f, lfoPitch: Float = 0.05f, lfoCut: Float = 0.1f,
        glide: Float = 0f, unison: Float = 0f, volume: Float = 0.7f, mono: Boolean = false,
    ) = SynthPreset(
        name,
        listOf(
            SynthParam.waveValue(w1), SynthParam.waveValue(w2), mix2, detune2, cutoff, res, fenv,
            a, d, s, r, fa, fd, fs, fr, lfoRate, lfoPitch, lfoCut, glide, unison, volume, if (mono) 1f else 0f,
        ),
    )

    val all = listOf(
        p("Kawaii Pad", 2, 3, 0.35f, 0.52f, 0.55f, 0.25f, 0.35f, 0.15f, 0.45f, 0.7f, 0.45f, unison = 0.3f),
        p("Lo-fi Keys", 1, 0, 0.4f, 0.5f, 0.45f, 0.1f, 0.4f, 0.0f, 0.55f, 0.25f, 0.35f, lfoRate = 0.15f, lfoPitch = 0.12f),
        p("Pluck", 2, 3, 0.3f, 0.5f, 0.3f, 0.4f, 0.8f, 0.0f, 0.4f, 0.0f, 0.3f, fa = 0f, fd = 0.35f, fs = 0f, fr = 0.3f),
        p("Neon Lead", 2, 2, 0.5f, 0.505f, 0.6f, 0.45f, 0.3f, 0.05f, 0.5f, 0.8f, 0.3f, lfoRate = 0.45f, lfoPitch = 0.15f, glide = 0.25f, mono = true),
        p("Chiptune", 3, 3, 0.25f, 0.75f, 0.9f, 0.0f, 0.0f, 0.0f, 0.3f, 0.6f, 0.15f, lfoRate = 0.6f, lfoPitch = 0.1f),
        p("Supersaw", 2, 2, 0.5f, 0.505f, 0.62f, 0.15f, 0.25f, 0.05f, 0.5f, 0.85f, 0.4f, unison = 0.9f),
        p("Sub Bass", 0, 1, 0.3f, 0.25f, 0.35f, 0.1f, 0.2f, 0.0f, 0.4f, 0.9f, 0.15f, mono = true, glide = 0.1f),
        p("Acid", 2, 3, 0.0f, 0.5f, 0.25f, 0.85f, 0.7f, 0.0f, 0.35f, 0.5f, 0.2f, fa = 0f, fd = 0.3f, fs = 0.1f, glide = 0.2f, mono = true),
        p("Dream Bells", 0, 1, 0.45f, 0.79f, 0.75f, 0.1f, 0.2f, 0.0f, 0.6f, 0.0f, 0.6f, lfoRate = 0.2f, lfoPitch = 0.05f),
        p("Hyperpop Lead", 3, 2, 0.5f, 0.71f, 0.7f, 0.3f, 0.35f, 0.0f, 0.4f, 0.75f, 0.25f, lfoRate = 0.7f, lfoPitch = 0.25f, unison = 0.5f, glide = 0.15f, mono = true),
        p("Shoegaze Wall", 2, 3, 0.5f, 0.5f, 0.5f, 0.2f, 0.15f, 0.45f, 0.6f, 0.9f, 0.7f, unison = 1f, lfoRate = 0.1f, lfoCut = 0.3f),
        p("Night Drive", 2, 0, 0.3f, 0.25f, 0.4f, 0.55f, 0.5f, 0.1f, 0.5f, 0.6f, 0.4f, lfoRate = 0.25f, lfoCut = 0.35f),
    )
}
