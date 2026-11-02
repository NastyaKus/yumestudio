package com.yume.studio.audio

import android.content.Context
import com.yume.studio.model.EffectSlot
import com.yume.studio.model.Project
import com.yume.studio.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Синхронизация модели проекта с нативным движком. Сравнивает старое и новое
 * состояние и шлёт в движок только изменившееся (чтобы не сбрасывать хвосты эффектов).
 */
class EngineBridge(private val context: Context) {
    enum class State { LOADING, READY, FAILED }

    private val _state = MutableStateFlow(State.LOADING)
    val state: StateFlow<State> = _state.asStateFlow()
    private val initLock = Mutex()
    private var lastArrangement: IntArray? = null

    val presetList: List<String> by lazy { NativeEngine.nativePresetList().toList() }

    suspend fun init() = initLock.withLock {
        if (_state.value == State.READY) return@withLock
        withContext(Dispatchers.IO) {
            val ok = runCatching {
                NativeEngine.nativeCreate() &&
                    NativeEngine.nativeLoadSoundFont(context.assets, "soundfonts/GeneralUser-GS.sf2") &&
                    NativeEngine.nativeStart()
            }.getOrDefault(false)
            _state.value = if (ok) State.READY else State.FAILED
        }
    }

    fun resume() { if (_state.value == State.READY) NativeEngine.nativeStart() }
    fun pause() { if (_state.value == State.READY) NativeEngine.nativeStop() }

    // ───────────────────────── дорожки ─────────────────────────

    fun applyInstrument(index: Int, t: Track) {
        val presets = t.type.presets
        val preset = presets.getOrElse(t.presetIndex) { presets.first() }
        when (t.type) {
            InstrumentType.SYNTH -> {
                NativeEngine.nativeSetTrackInstrument(index, NativeEngine.KIND_SYNTH, 0, 0)
                t.synthParams.forEachIndexed { id, v -> NativeEngine.nativeSetSynthParam(index, id, v) }
            }
            InstrumentType.STRINGS -> {
                val (bank, prog) = Instruments.stringsProgram(preset, t.articulation)
                NativeEngine.nativeSetTrackInstrument(index, NativeEngine.KIND_SF2, bank, prog)
            }
            InstrumentType.DRUMS -> NativeEngine.nativeSetTrackInstrument(index, NativeEngine.KIND_DRUMS, 128, preset.program)
            InstrumentType.AUDIO -> NativeEngine.nativeSetTrackInstrument(index, NativeEngine.KIND_AUDIO, 0, 0)
            else -> NativeEngine.nativeSetTrackInstrument(index, NativeEngine.KIND_SF2, preset.bank, preset.program)
        }
    }

    fun applyMix(index: Int, t: Track) {
        NativeEngine.nativeSetTrackMix(index, t.volume, t.pan, t.reverbSend, t.mute, t.solo)
    }

    fun applyEffects(index: Int, old: List<EffectSlot>?, new: List<EffectSlot>) {
        val sameLayout = old != null && old.size == new.size && old.indices.all { old[it].type == new[it].type }
        if (sameLayout) {
            new.forEachIndexed { slot, fx ->
                val o = old!![slot]
                if (o.enabled != fx.enabled) NativeEngine.nativeSetFxEnabled(index, slot, fx.enabled)
                fx.params.forEachIndexed { pid, v ->
                    if (o.params.getOrNull(pid) != v) NativeEngine.nativeSetFxParam(index, slot, pid, v)
                }
            }
            return
        }
        for (slot in 0 until MAX_FX) {
            val fx = new.getOrNull(slot)
            val o = old?.getOrNull(slot)
            if (fx == null) {
                if (o != null || old == null) NativeEngine.nativeSetFx(index, slot, -1)
                continue
            }
            if (o != null && o.type == fx.type) {
                if (o.enabled != fx.enabled) NativeEngine.nativeSetFxEnabled(index, slot, fx.enabled)
                fx.params.forEachIndexed { pid, v -> if (o.params.getOrNull(pid) != v) NativeEngine.nativeSetFxParam(index, slot, pid, v) }
            } else {
                NativeEngine.nativeSetFx(index, slot, fx.type)
                fx.params.forEachIndexed { pid, v -> NativeEngine.nativeSetFxParam(index, slot, pid, v) }
                NativeEngine.nativeSetFxEnabled(index, slot, fx.enabled)
            }
        }
    }

    fun applyGlobals(old: Project?, p: Project, metronomeVolume: Float) {
        if (old == null || old.bpm != p.bpm) NativeEngine.nativeSetBpm(p.bpm)
        if (old == null || old.beatsPerBar != p.beatsPerBar) NativeEngine.nativeSetTimeSignature(p.beatsPerBar)
        if (old == null || old.loopOn != p.loopOn || old.loopStart != p.loopStart || old.loopEnd != p.loopEnd)
            NativeEngine.nativeSetLoop(p.loopOn, p.loopStart, p.loopEnd)
        if (old == null || old.metronome != p.metronome) NativeEngine.nativeSetMetronome(p.metronome, metronomeVolume)
        if (old == null || old.masterVolume != p.masterVolume) NativeEngine.nativeSetMasterVolume(p.masterVolume)
        if (old == null || old.reverbSize != p.reverbSize) NativeEngine.nativeSetReverb(0.4f + p.reverbSize * 0.6f, 0.45f)
    }

    /** Полная синхронизация с учётом предыдущего состояния (null — всё с нуля). */
    fun sync(old: Project?, new: Project, clipIds: Map<String, Int>, metronomeVolume: Float) {
        applyGlobals(old, new, metronomeVolume)
        new.tracks.forEachIndexed { i, t ->
            if (i >= NativeEngine.MAX_TRACKS) return@forEachIndexed
            val o = old?.tracks?.getOrNull(i)?.takeIf { it.id == t.id }
            if (o == null || o.type != t.type || o.presetIndex != t.presetIndex || o.articulation != t.articulation) {
                applyInstrument(i, t)
            } else if (t.type == InstrumentType.SYNTH && o.synthParams != t.synthParams) {
                t.synthParams.forEachIndexed { id, v -> if (o.synthParams.getOrNull(id) != v) NativeEngine.nativeSetSynthParam(i, id, v) }
            }
            if (o == null || o.volume != t.volume || o.pan != t.pan || o.reverbSend != t.reverbSend || o.mute != t.mute || o.solo != t.solo) {
                applyMix(i, t)
            }
            if (o == null || o.effects != t.effects) applyEffects(i, o?.effects, t.effects)
        }
        val oldCount = old?.tracks?.size ?: NativeEngine.MAX_TRACKS
        for (i in new.tracks.size until minOf(oldCount, NativeEngine.MAX_TRACKS)) NativeEngine.nativeRemoveTrack(i)
        pushArrangement(new, clipIds)
        NativeEngine.nativeCollectGarbage()
    }

    fun pushArrangement(p: Project, clipIds: Map<String, Int>, force: Boolean = false) {
        val data = encodeArrangement(p, clipIds)
        if (!force && lastArrangement?.contentEquals(data) == true) return
        lastArrangement = data
        NativeEngine.nativeSetArrangement(data)
    }

    fun resetCache() { lastArrangement = null }

    // ───────────────────────── превью лупов библиотеки ─────────────────────────

    fun previewLoop(loop: com.yume.studio.data.Loop, bpm: Float, keyRoot: Int) {
        if (_state.value != State.READY) return
        val t = Track(
            id = -1, name = "preview", type = loop.type, presetIndex = loop.presetIndex,
            articulation = loop.articulation,
            synthParams = if (loop.type == InstrumentType.SYNTH) SynthPresets.all[loop.presetIndex].params else SynthPresets.all.first().params,
        )
        applyInstrument(NativeEngine.PREVIEW_TRACK, t)
        NativeEngine.nativeSetTrackMix(NativeEngine.PREVIEW_TRACK, 0.9f, 0f, 0.15f, false, false)
        val notes = loop.transposed(transposeFor(keyRoot))
        val arr = IntArray(notes.size * 4)
        notes.forEachIndexed { i, n ->
            arr[i * 4] = n.tick; arr[i * 4 + 1] = n.length; arr[i * 4 + 2] = n.key; arr[i * 4 + 3] = n.velocity
        }
        NativeEngine.nativePreviewPattern(arr, loop.lengthTicks, bpm)
    }

    fun stopPreview() {
        if (_state.value == State.READY) NativeEngine.nativeStopPreview()
    }

    /** Тоника → сдвиг в полутонах в диапазоне −5..+6, чтобы лупы не уезжали далеко. */
    fun transposeFor(keyRoot: Int): Int = if (keyRoot > 6) keyRoot - 12 else keyRoot

    companion object {
        const val MAX_FX = 8

        /** Формат описан в cpp/jni/yume_jni.cpp (nativeSetArrangement). */
        fun encodeArrangement(p: Project, clipIds: Map<String, Int>): IntArray {
            val out = ArrayList<Int>(1024)
            val tracks = p.tracks.take(NativeEngine.MAX_TRACKS)
            out += tracks.size
            tracks.forEachIndexed { i, t ->
                out += i
                val midi = t.regions.filter { !it.isAudio }
                out += midi.size
                for (r in midi) {
                    out += r.start; out += r.length; out += r.contentLength
                    out += r.notes.size
                    for (n in r.notes) { out += n.tick; out += n.length; out += n.key; out += n.velocity }
                }
                val audio = t.regions.filter { it.isAudio && clipIds.containsKey(it.clipFile) }
                out += audio.size
                for (r in audio) {
                    out += r.start; out += r.length; out += clipIds.getValue(r.clipFile!!)
                    out += (r.clipOffset ushr 32).toInt(); out += (r.clipOffset and 0xFFFFFFFFL).toInt()
                    out += (r.gain * 1000).toInt()
                }
            }
            return out.toIntArray()
        }

        fun defaultEffects(type: InstrumentType): List<EffectSlot> = when (type) {
            InstrumentType.GUITAR -> listOf(
                EffectSlot(FxDef.COMPRESSOR.type, enabled = false),
                EffectSlot(FxDef.OVERDRIVE.type, enabled = false),
                EffectSlot(FxDef.CHORUS.type, enabled = true, params = listOf(0.25f, 0.4f, 0.35f)),
                EffectSlot(FxDef.DELAY.type, enabled = false),
                EffectSlot(FxDef.AMP.type, enabled = true, params = listOf(0.1f, 0.4f, 0.55f, 0.5f, 0.6f, 0.55f)),
            )
            InstrumentType.BASS -> listOf(
                EffectSlot(FxDef.COMPRESSOR.type, enabled = true, params = listOf(0.5f, 0.4f, 0.25f, 0.3f, 0.25f, 1f)),
                EffectSlot(FxDef.OCTAVER.type, enabled = false),
                EffectSlot(FxDef.AMP.type, enabled = false, params = listOf(0.9f, 0.35f, 0.55f, 0.5f, 0.5f, 0.6f)),
            )
            InstrumentType.AUDIO -> listOf(
                EffectSlot(FxDef.PITCH.type, enabled = false),
                EffectSlot(FxDef.COMPRESSOR.type, enabled = true, params = listOf(0.5f, 0.35f, 0.15f, 0.35f, 0.3f, 1f)),
                EffectSlot(FxDef.REVERB.type, enabled = true, params = listOf(0.55f, 0.45f, 0.22f, 1f)),
                EffectSlot(FxDef.DELAY.type, enabled = false, params = listOf(0.5f, 0.3f, 0.2f, 0.6f, 1f)),
            )
            else -> emptyList()
        }
    }
}
