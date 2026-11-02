package com.yume.studio.audio

import android.content.res.AssetManager

/**
 * Тонкая JNI-обёртка над нативным движком (app/src/main/cpp).
 * Все вызовы потокобезопасны: команды попадают в lock-free очередь аудиопотока.
 */
object NativeEngine {
    const val KIND_NONE = 0
    const val KIND_SF2 = 1
    const val KIND_SYNTH = 2
    const val KIND_DRUMS = 3
    const val KIND_AUDIO = 4

    const val MAX_TRACKS = 32
    const val PREVIEW_TRACK = 32
    const val METER_COUNT = (MAX_TRACKS + 2) * 2  // дорожки + превью + мастер
    const val MASTER_METER = (MAX_TRACKS + 1) * 2
    const val PPQ = 960

    init {
        System.loadLibrary("yume")
    }

    external fun nativeCreate(): Boolean
    external fun nativeLoadSoundFont(assets: AssetManager, path: String): Boolean
    external fun nativeStart(): Boolean
    external fun nativeStop()
    external fun nativeSampleRate(): Int
    external fun nativeOutputLatencyMs(): Float
    external fun nativeInputLatencyMs(): Float
    external fun nativePresetList(): Array<String>

    external fun nativeSetTrackInstrument(track: Int, kind: Int, bank: Int, preset: Int)
    external fun nativeRemoveTrack(track: Int)
    external fun nativeSetTrackMix(track: Int, volume: Float, pan: Float, send: Float, mute: Boolean, solo: Boolean)
    external fun nativeSetSynthParam(track: Int, id: Int, value: Float)
    external fun nativeSetFx(track: Int, slot: Int, type: Int)
    external fun nativeSetFxParam(track: Int, slot: Int, id: Int, value: Float)
    external fun nativeSetFxEnabled(track: Int, slot: Int, enabled: Boolean)

    external fun nativeNoteOn(track: Int, key: Int, velocity: Float, delayMs: Float)
    external fun nativeNoteOff(track: Int, key: Int, delayMs: Float)
    external fun nativeAllNotesOff(track: Int)
    external fun nativeSetSustain(track: Int, on: Boolean)
    external fun nativeSetPitchBend(track: Int, semitones: Float)

    external fun nativeSetArrangement(data: IntArray)
    external fun nativePlay()
    external fun nativeStopTransport()
    external fun nativeSeek(tick: Double)
    external fun nativePosition(): Double
    external fun nativeIsPlaying(): Boolean
    external fun nativeSetBpm(bpm: Float)
    external fun nativeSetTimeSignature(beatsPerBar: Int)
    external fun nativeSetLoop(on: Boolean, start: Int, end: Int)
    external fun nativeSetMetronome(on: Boolean, volume: Float)
    external fun nativeSetMasterVolume(volume: Float)
    external fun nativeSetReverb(size: Float, damp: Float)

    external fun nativeOpenInput(): Boolean
    external fun nativeCloseInput()
    external fun nativeSetInputMonitor(track: Int, on: Boolean)
    external fun nativeInputPeak(): Float

    external fun nativeStartRecording(track: Int, countInBars: Int, audio: Boolean, wavPath: String)
    external fun nativeStopRecording(): Double
    external fun nativeIsRecording(): Boolean
    external fun nativePollRecorded(): IntArray

    external fun nativeLoadClip(id: Int, path: String): Long
    external fun nativeUnloadClip(id: Int)
    external fun nativeClipPeaks(id: Int, buckets: Int): FloatArray
    external fun nativeClipSampleRate(id: Int): Int

    external fun nativePreviewPattern(notes: IntArray, lengthTicks: Int, bpm: Float)
    external fun nativePreviewClip(id: Int)
    external fun nativeStopPreview()

    external fun nativeMeters(out: FloatArray)
    external fun nativeExportWav(path: String, endTick: Int, bits: Int): Boolean
    external fun nativeExportProgress(): Float
    external fun nativeCollectGarbage()
}
