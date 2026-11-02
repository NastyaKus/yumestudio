package com.yume.studio

import com.yume.studio.audio.EngineBridge
import com.yume.studio.audio.FxDef
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.stepIndex
import com.yume.studio.audio.stepValue
import com.yume.studio.data.LoopLibrary
import com.yume.studio.model.Chords
import com.yume.studio.model.Note
import com.yume.studio.model.PPQ
import com.yume.studio.model.Project
import com.yume.studio.model.Quantize
import com.yume.studio.model.Region
import com.yume.studio.model.Timing
import com.yume.studio.model.Track
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTest {
    @Test
    fun positionFormatting() {
        assertEquals("1.1.1", Timing.formatPosition(0.0, 4))
        assertEquals("2.1.1", Timing.formatPosition((PPQ * 4).toDouble(), 4))
        assertEquals("1.3.3", Timing.formatPosition((PPQ * 2 + PPQ / 2).toDouble(), 4))
        assertEquals("2.1.1", Timing.formatPosition((PPQ * 3).toDouble(), 3))
    }

    @Test
    fun snapping() {
        assertEquals(PPQ, Timing.snap(PPQ + 100, PPQ))
        assertEquals(0, Timing.snapDown(PPQ - 1, PPQ))
        assertEquals(PPQ, Timing.snapUp(1, PPQ))
        assertEquals(-PPQ, Timing.snapDown(-1, PPQ))
    }

    @Test
    fun quantizeMovesNotesToGrid() {
        val q = Quantize.notes(listOf(Note(250, 100, 60), Note(470, 50, 62)), PPQ / 4)
        assertEquals(240, q[0].tick)
        assertEquals(480, q[1].tick)
        assertTrue(q.all { it.length >= PPQ / 8 })
        val half = Quantize.notes(listOf(Note(100, 100, 60)), PPQ, strength = 0.5f)
        assertEquals(50, half[0].tick)
    }

    @Test
    fun samplesPerTick() {
        // 120 BPM, 48 кГц: четверть = 0.5 c = 24000 сэмплов
        assertEquals(24000.0, Timing.samplesPerTick(120f) * PPQ, 1e-6)
    }

    @Test
    fun chordsForKeysAreValid() {
        for (root in 0 until 12) for (minor in listOf(false, true)) {
            val set = Chords.forKey(root, minor)
            assertEquals(8, set.size)
            set.forEach { ch -> assertEquals(6, ch.frets.size); assertTrue(ch.keys().isNotEmpty()) }
        }
        assertEquals(listOf(40, 47, 52, 55, 59, 64), Chords.chord("Em").keys())
    }

    @Test
    fun discreteFxParamsRoundTrip() {
        FxDef.entries.forEach { def ->
            def.params.filter { it.steps != null }.forEach { p ->
                p.steps!!.indices.forEach { i -> assertEquals(i, p.stepIndex(p.stepValue(i))) }
            }
        }
    }

    @Test
    fun arrangementEncoding() {
        val p = Project(
            id = "t", name = "t",
            tracks = listOf(
                Track(1, "keys", InstrumentType.KEYS, regions = listOf(Region(1, 0, PPQ * 4, PPQ * 4, listOf(Note(0, 100, 60, 90))))),
                Track(2, "vox", InstrumentType.AUDIO, regions = listOf(Region(2, PPQ, PPQ * 8, clipFile = "a.wav", clipOffset = 5_000_000_000L, gain = 0.5f))),
            ),
        )
        val a = EngineBridge.encodeArrangement(p, mapOf("a.wav" to 7))
        // [2 tracks] [0, 1 midi, 0,3840,3840,1, 0,100,60,90, 0 audio] [1, 0 midi, 1 audio, 960,7680,7, hi, lo, 500]
        assertEquals(2, a[0])
        assertEquals(listOf(0, 1, 0, 3840, 3840, 1, 0, 100, 60, 90, 0), a.slice(1..11))
        val audioStart = 12
        assertEquals(listOf(1, 0, 1, 960, 7680, 7), a.slice(audioStart until audioStart + 6))
        val off = (a[audioStart + 6].toLong() shl 32) or (a[audioStart + 7].toLong() and 0xFFFFFFFFL)
        assertEquals(5_000_000_000L, off)
        assertEquals(500, a[audioStart + 8])
    }

    @Test
    fun projectSerializationRoundTrip() {
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
        val p = Project(id = "x", name = "Тест ♡", bpm = 92f, tracks = listOf(
            Track(1, "Гитара", InstrumentType.GUITAR, effects = EngineBridge.defaultEffects(InstrumentType.GUITAR)),
        ))
        val back = json.decodeFromString<Project>(json.encodeToString(Project.serializer(), p))
        assertEquals(p, back)
    }

    @Test
    fun loopLibraryIsSane() {
        val ids = LoopLibrary.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        LoopLibrary.all.forEach { loop ->
            assertTrue(loop.name, loop.notes.isNotEmpty())
            assertTrue(loop.name, loop.notes.all { it.tick in 0 until loop.lengthTicks && it.key in 0..127 && it.velocity in 1..127 })
            assertTrue(loop.name, loop.presetIndex in loop.type.presets.indices)
        }
    }
}
