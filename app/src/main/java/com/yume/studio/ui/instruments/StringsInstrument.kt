package com.yume.studio.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.Articulation
import com.yume.studio.audio.Instruments
import com.yume.studio.model.Scales
import com.yume.studio.model.Track
import com.yume.studio.ui.components.FretboardView
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

private enum class StringsSurface(val title: String) { STRINGS("Струны"), KEYS("Клавиши") }

/** Строй секций (снизу вверх). */
private fun tuningFor(program: Int): List<Int> = when (program) {
    40 -> listOf(55, 62, 69, 76)  // скрипка G D A E
    41 -> listOf(48, 55, 62, 69)  // альт C G D A
    42 -> listOf(36, 43, 50, 57)  // виолончель C G D A
    43 -> listOf(28, 33, 38, 43)  // контрабас E A D G
    else -> listOf(43, 50, 57, 64)
}

@Composable
fun StringsInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val settings by vm.settings.collectAsState()
    var surface by remember { mutableStateOf(StringsSurface.STRINGS) }
    val preset = track.type.presets.getOrElse(track.presetIndex) { track.type.presets.first() }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        Row(Modifier.fillMaxWidth().weight(0.46f)) {
            ArtHeader(vm, index, track, Modifier.weight(0.5f).fillMaxHeight())
            Spacer(Modifier.width(8.dp))
            Column(
                Modifier.weight(0.5f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(c.surface).padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Струнные", style = YumeText.title, color = c.text)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Instruments.strings.take(4).forEachIndexed { i, sec ->
                        val sel = track.presetIndex == i
                        Column(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(if (sel) c.primary.copy(alpha = 0.25f) else Color.Transparent)
                                .clickable { vm.setPreset(index, i) }.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            ViolinIcon(Modifier.size(width = 26.dp, height = 40.dp + (i * 6).dp), if (sel) c.primary else c.peach)
                            Text(sec.name, style = YumeText.tiny, color = if (sel) c.text else c.textDim)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Articulation.entries.forEach { a ->
                        val sel = track.articulation == a
                        Box(
                            Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (sel) c.primaryDeep else c.surfaceHigh).clickable { vm.setArticulation(index, a) },
                            contentAlignment = Alignment.Center,
                        ) { Text(a.title, style = YumeText.small, color = if (sel) Color.White else c.text) }
                    }
                }
            }
        }
        ControlStrip {
            Segmented(StringsSurface.entries, surface, { it.title }, { surface = it }, height = 28.dp)
            Text(
                when (track.articulation) {
                    Articulation.STACCATO -> "Стаккато: короткие ноты на каждое касание"
                    Articulation.PIZZICATO -> "Пиццикато: щипок пальцем"
                    Articulation.TREMOLO -> "Тремоло смычком"
                    else -> "Легато: веди пальцем вдоль струны"
                },
                style = YumeText.small, color = c.textDim,
            )
        }
        Box(Modifier.weight(0.54f).fillMaxWidth()) {
            if (surface == StringsSurface.STRINGS) {
                val scale = if (project?.minor == true) Scales.minor else Scales.major
                FretboardView(
                    tuning = tuningFor(preset.program), frets = 12,
                    onNoteOn = { k, v -> vm.noteOn(k, v) }, onNoteOff = { vm.noteOff(it) }, onBend = { vm.setPitchBend(it * 0.25f) },
                    modifier = Modifier.fillMaxSize(),
                    highlight = { k -> project?.let { Scales.inScale(k, it.keyRoot, scale) } ?: false },
                    showLabels = settings.showNoteLabels,
                    woodColor = Color(0xFF6B3F35), stringColor = Color(0xFFFFF3DD), accent = c.peach,
                )
            } else {
                KeyboardSurface(vm, index, Modifier.fillMaxSize(), defaultStart = Instruments.stringsBaseNote(preset).let { it - it % 12 })
            }
        }
    }
}

@Composable
private fun ViolinIcon(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val body = Path().apply {
            moveTo(w * 0.5f, h * 0.3f)
            cubicTo(w * 0.95f, h * 0.3f, w * 0.95f, h * 0.55f, w * 0.75f, h * 0.6f)
            cubicTo(w * 1.0f, h * 0.68f, w * 1.0f, h * 0.98f, w * 0.5f, h * 0.98f)
            cubicTo(w * 0.0f, h * 0.98f, w * 0.0f, h * 0.68f, w * 0.25f, h * 0.6f)
            cubicTo(w * 0.05f, h * 0.55f, w * 0.05f, h * 0.3f, w * 0.5f, h * 0.3f)
            close()
        }
        drawPath(body, color)
        drawRect(Color(0xFF5B3A44), Offset(w * 0.44f, h * 0.02f), Size(w * 0.12f, h * 0.45f))
        drawCircle(Color(0xFF5B3A44), w * 0.12f, Offset(w * 0.5f, h * 0.04f))
        drawLine(Color(0xFF5B3A44), Offset(w * 0.5f, h * 0.45f), Offset(w * 0.5f, h * 0.9f), 1.5f)
        drawPath(Path().apply { moveTo(w * 0.3f, h * 0.68f); quadraticTo(w * 0.35f, h * 0.74f, w * 0.3f, h * 0.8f) }, Color(0xFF5B3A44), style = Stroke(1.2f))
        drawPath(Path().apply { moveTo(w * 0.7f, h * 0.68f); quadraticTo(w * 0.65f, h * 0.74f, w * 0.7f, h * 0.8f) }, Color(0xFF5B3A44), style = Stroke(1.2f))
    }
}
