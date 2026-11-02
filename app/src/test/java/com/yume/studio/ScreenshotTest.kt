package com.yume.studio

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.yume.studio.audio.Articulation
import com.yume.studio.audio.EngineBridge
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.SynthPresets
import com.yume.studio.data.LoopLibrary
import com.yume.studio.data.ThemeMode
import com.yume.studio.model.PPQ
import com.yume.studio.model.Project
import com.yume.studio.model.Region
import com.yume.studio.model.Track
import com.yume.studio.ui.home.HomeScreen
import com.yume.studio.ui.studio.StudioMode
import com.yume.studio.ui.studio.StudioScreen
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.YumeTheme
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Рендер ключевых экранов на JVM (Robolectric + Roborazzi), чтобы проверять вёрстку без устройства.
 * Картинки пишутся в app/build/screenshots/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w891dp-h411dp-land-xhdpi")
class ScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private fun loopRegion(id: String, start: Int = 0, reps: Int = 2): Region {
        val l = LoopLibrary.find(id)!!
        return Region(System.nanoTime(), start, l.lengthTicks * reps, l.lengthTicks, l.notes, name = l.name)
    }

    private fun sample(): Project {
        fun t(id: Long, type: InstrumentType, preset: Int, vararg regions: Region, name: String = type.title, art: Articulation = Articulation.LEGATO) = Track(
            id, name, type, preset, effects = EngineBridge.defaultEffects(type), regions = regions.toList(), articulation = art,
            synthParams = if (type == InstrumentType.SYNTH) SynthPresets.all[preset].params else SynthPresets.all.first().params,
        )
        return Project(
            id = "demo", name = "golden fields", bpm = 92f, keyRoot = 0, loopOn = true, loopStart = 0, loopEnd = PPQ * 16,
            tracks = listOf(
                t(1, InstrumentType.KEYS, 5, loopRegion("rhodes_chords", 0, 1), loopRegion("rhodes_chords", PPQ * 16, 1), name = "Пианино"),
                t(2, InstrumentType.GUITAR, 7, loopRegion("clean_strum", PPQ * 4, 1), name = "Гитара"),
                t(3, InstrumentType.BASS, 0, loopRegion("lofi_bass", 0, 2), name = "Бас"),
                t(4, InstrumentType.DRUMS, 0, loopRegion("lofi_drum_loop", 0, 12), loopRegion("boom_bap", PPQ * 16 * 3, 4), name = "Ударные"),
                t(5, InstrumentType.SYNTH, 0, loopRegion("dream_pad", PPQ * 16, 1), name = "Синт"),
                t(6, InstrumentType.STRINGS, 4, loopRegion("pizzicato_walk", PPQ * 8, 2), name = "Струнные", art = Articulation.PIZZICATO),
                t(7, InstrumentType.AUDIO, 0, name = "Вокал"),
            ),
        )
    }

    @Test
    fun home() {
        val app = ApplicationProvider.getApplicationContext<YumeApp>()
        runBlocking { app.repository.save(sample()); app.repository.save(sample().copy(id = "b", name = "rewrite_demo")) }
        compose.setContent { YumeTheme(ThemeMode.DUSK) { HomeScreen {} } }
        shot("01_home")
    }

    @Test
    fun studio() {
        val app = ApplicationProvider.getApplicationContext<YumeApp>()
        val p = sample()
        runBlocking { app.repository.save(p) }
        val vm = StudioViewModel(app, p.id)
        compose.setContent { YumeTheme(ThemeMode.DUSK) { StudioScreen(vm) {} } }
        compose.waitUntil(30_000) { vm.project.value != null }
        shot("02_tracks")
        val modes = listOf(
            Triple(0, StudioMode.INSTRUMENT, "03_keys"),
            Triple(1, StudioMode.INSTRUMENT, "04_guitar"),
            Triple(2, StudioMode.INSTRUMENT, "05_bass"),
            Triple(3, StudioMode.INSTRUMENT, "06_drums"),
            Triple(4, StudioMode.INSTRUMENT, "07_synth"),
            Triple(5, StudioMode.INSTRUMENT, "08_strings"),
            Triple(6, StudioMode.INSTRUMENT, "09_vocal"),
            Triple(1, StudioMode.FX, "10_pedals"),
            Triple(0, StudioMode.MIXER, "11_mixer"),
            Triple(0, StudioMode.LIBRARY, "12_library"),
        )
        for ((track, mode, name) in modes) {
            compose.runOnIdle { vm.select(track); vm.setMode(mode) }
            shot(name)
        }
        compose.runOnIdle { vm.editRegion(p.tracks[0].regions[0].id) }
        shot("13_piano_roll")
        compose.runOnIdle { vm.setMode(StudioMode.TRACKS); vm.selectRegion(p.tracks[3].regions[0].id) }
        shot("14_tracks_selected")
        vm.viewModelScope.cancel()
    }

    @Test
    fun sakuraTheme() {
        val app = ApplicationProvider.getApplicationContext<YumeApp>()
        val p = sample().copy(id = "s")
        runBlocking { app.repository.save(p) }
        val vm = StudioViewModel(app, p.id)
        compose.setContent { YumeTheme(ThemeMode.SAKURA) { StudioScreen(vm) {} } }
        compose.waitUntil(30_000) { vm.project.value != null }
        compose.runOnIdle { vm.select(4); vm.setMode(StudioMode.INSTRUMENT) }
        shot("15_sakura_synth")
        vm.viewModelScope.cancel()
    }
}
