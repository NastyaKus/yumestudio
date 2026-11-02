package com.yume.studio.model

import com.yume.studio.audio.Articulation
import com.yume.studio.audio.FxDef
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.NativeEngine
import com.yume.studio.audio.SynthPresets
import kotlinx.serialization.Serializable

const val PPQ = NativeEngine.PPQ

@Serializable
data class Note(
    val tick: Int,
    val length: Int,
    val key: Int,
    val velocity: Int = 100,
)

@Serializable
data class Region(
    val id: Long,
    val start: Int,
    val length: Int,
    /** длина содержимого; если меньше length — содержимое зацикливается */
    val contentLength: Int = length,
    val notes: List<Note> = emptyList(),
    /** имя WAV-файла в папке проекта audio/ для аудиорегионов */
    val clipFile: String? = null,
    /** смещение начала клипа в сэмплах (48 кГц) */
    val clipOffset: Long = 0,
    val gain: Float = 1f,
    val name: String = "",
) {
    val end: Int get() = start + length
    val isAudio: Boolean get() = clipFile != null
    val isLooped: Boolean get() = contentLength in 1 until length
}

@Serializable
data class EffectSlot(
    val type: Int,
    val enabled: Boolean = true,
    val params: List<Float> = FxDef.byType(type)?.defaults ?: emptyList(),
)

@Serializable
data class Track(
    val id: Long,
    val name: String,
    val type: InstrumentType,
    val presetIndex: Int = 0,
    val volume: Float = 0.8f,
    val pan: Float = 0f,
    val reverbSend: Float = 0.12f,
    val mute: Boolean = false,
    val solo: Boolean = false,
    val articulation: Articulation = Articulation.LEGATO,
    val synthParams: List<Float> = SynthPresets.all.first().params,
    val effects: List<EffectSlot> = emptyList(),
    val regions: List<Region> = emptyList(),
    val colorIndex: Int = type.colorIndex,
)

@Serializable
data class Project(
    val id: String,
    val name: String,
    val bpm: Float = 100f,
    val beatsPerBar: Int = 4,
    /** тоника 0..11 и лад — нужны тюнингу, аккордам и библиотеке */
    val keyRoot: Int = 0,
    val minor: Boolean = false,
    val tracks: List<Track> = emptyList(),
    val loopOn: Boolean = false,
    val loopStart: Int = 0,
    val loopEnd: Int = PPQ * 4 * 4,
    val lengthBars: Int = 16,
    val metronome: Boolean = true,
    val countInBars: Int = 1,
    val masterVolume: Float = 0.9f,
    val reverbSize: Float = 0.6f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val ticksPerBar: Int get() = PPQ * beatsPerBar
    val endTick: Int get() = maxOf(
        tracks.maxOfOrNull { t -> t.regions.maxOfOrNull { it.end } ?: 0 } ?: 0,
        PPQ,
    )
}

/** Краткая информация для списка проектов. */
@Serializable
data class ProjectMeta(
    val id: String,
    val name: String,
    val updatedAt: Long,
    val bpm: Float,
    val trackTypes: List<InstrumentType>,
)

fun Project.meta() = ProjectMeta(id, name, updatedAt, bpm, tracks.map { it.type })
