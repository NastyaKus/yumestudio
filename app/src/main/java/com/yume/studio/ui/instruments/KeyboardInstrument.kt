package com.yume.studio.ui.instruments

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.NoteNames
import com.yume.studio.model.Scales
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.PianoKeys
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

/** Общая клавиатура с октавами/сустейном/подсветкой лада. */
@Composable
fun KeyboardSurface(
    vm: StudioViewModel,
    index: Int,
    modifier: Modifier = Modifier,
    defaultStart: Int = 48,
    extraControls: @Composable () -> Unit = {},
) {
    val c = Yume.colors
    val settings by vm.settings.collectAsState()
    val project by vm.project.collectAsState()
    var start by remember(index) { mutableIntStateOf(defaultStart) }
    var sustain by remember(index) { mutableStateOf(false) }
    var scaleHint by remember { mutableStateOf(false) }
    val p = project
    Column(modifier) {
        ControlStrip {
            Pill(null, Icons.AutoMirrored.Rounded.KeyboardArrowLeft, height = 32.dp) { vm.allNotesOff(); start = (start - 12).coerceAtLeast(12) }
            Text(NoteNames.name(start), style = YumeText.small, color = c.textDim)
            Pill(null, Icons.AutoMirrored.Rounded.KeyboardArrowRight, height = 32.dp) { vm.allNotesOff(); start = (start + 12).coerceAtMost(96) }
            Pill("Сустейн", selected = sustain, height = 32.dp) { sustain = !sustain; vm.setSustain(sustain) }
            Pill("Лад ${p?.let { NoteNames.pitchClass(it.keyRoot) + if (it.minor) "m" else "" } ?: ""}", Icons.Rounded.Piano, selected = scaleHint, height = 32.dp) { scaleHint = !scaleHint }
            extraControls()
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val whites = (maxWidth / 50.dp).toInt().coerceIn(8, 36)
            val scale = if (p?.minor == true) Scales.minor else Scales.major
            PianoKeys(
                startKey = start,
                whiteCount = whites,
                onNoteOn = { k, v -> vm.noteOn(k, v) },
                onNoteOff = { vm.noteOff(it) },
                modifier = Modifier.fillMaxSize(),
                showLabels = settings.showNoteLabels,
                velocityByPosition = settings.velocityByPosition,
                highlight = { k -> scaleHint && p != null && Scales.inScale(k, p.keyRoot, scale) },
            )
        }
    }
}

@Composable
fun KeyboardInstrument(vm: StudioViewModel, index: Int, track: Track) {
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        ArtHeader(vm, index, track, Modifier.weight(0.42f))
        Spacer(Modifier.padding(2.dp))
        KeyboardSurface(vm, index, Modifier.weight(0.58f))
    }
}
