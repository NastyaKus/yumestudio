package com.yume.studio.ui.instruments

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yume.studio.model.Chords
import com.yume.studio.model.Fretboard
import com.yume.studio.model.GuitarChord
import com.yume.studio.model.Scales
import com.yume.studio.model.Track
import com.yume.studio.ui.components.FretboardView
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.studio.StudioMode
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText

private enum class GuitarMode(val title: String) { CHORDS("Аккорды"), NOTES("Ноты") }

@Composable
fun GuitarInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val settings by vm.settings.collectAsState()
    var mode by remember { mutableStateOf(GuitarMode.CHORDS) }
    val chords = remember(project?.keyRoot, project?.minor) { Chords.forKey(project?.keyRoot ?: 0, project?.minor ?: false) }
    var current by remember(chords) { mutableIntStateOf(0) }
    var mutedStrum by remember { mutableStateOf(false) }
    var firstFret by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        ArtHeader(vm, index, track, Modifier.weight(0.4f)) {
            if (mode == GuitarMode.CHORDS) {
                ChordDiagram(chords[current], Modifier.align(Alignment.CenterEnd).padding(10.dp).width(78.dp).height(100.dp))
            }
        }
        ControlStrip {
            Segmented(GuitarMode.entries, mode, { it.title }, { mode = it }, height = 28.dp)
            if (mode == GuitarMode.CHORDS) {
                Pill("Приглушить", selected = mutedStrum, height = 32.dp) { mutedStrum = !mutedStrum }
            } else {
                Pill("◀ лады", height = 32.dp) { firstFret = (firstFret - 5).coerceAtLeast(0) }
                Text("с ${firstFret} лада", style = YumeText.small, color = c.textDim)
                Pill("лады ▶", height = 32.dp) { firstFret = (firstFret + 5).coerceAtMost(12) }
            }
            Spacer(Modifier.weight(1f))
            Pill("Педали", Icons.Rounded.Tune, height = 32.dp, color = c.peach, contentColor = c.onPrimary) { vm.setMode(StudioMode.FX) }
        }
        Box(Modifier.weight(0.6f).fillMaxWidth()) {
            if (mode == GuitarMode.CHORDS) {
                Column(Modifier.fillMaxSize()) {
                    StrumStrings(
                        chord = chords[current],
                        onString = { key, vel -> vm.noteOn(key, vel); if (mutedStrum) vm.noteOff(key) },
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        chords.forEachIndexed { i, ch ->
                            ChordButton(
                                ch.name, selected = i == current, modifier = Modifier.weight(1f).fillMaxHeight(),
                                onDown = { down ->
                                    current = i
                                    vm.strum(ch.keys(), 0.9f, down, muteAfterMs = if (mutedStrum) 90f else 0f)
                                },
                                onUp = { if (mutedStrum) vm.releaseChord(ch.keys()) },
                            )
                        }
                    }
                }
            } else {
                val scale = if (project?.minor == true) Scales.minor else Scales.major
                FretboardView(
                    tuning = Fretboard.GUITAR_TUNING, frets = 12, firstFret = firstFret,
                    onNoteOn = { k, v -> vm.noteOn(k, v) }, onNoteOff = { vm.noteOff(it) }, onBend = vm::setPitchBend,
                    modifier = Modifier.fillMaxSize(),
                    highlight = { k -> project?.let { Scales.inScale(k, it.keyRoot, scale) } ?: false },
                    showLabels = settings.showNoteLabels,
                    accent = c.primary,
                )
            }
        }
    }
}

/** Шесть струн выбранного аккорда: провёл пальцем поперёк — страм, коснулся — одна струна. */
@Composable
private fun StrumStrings(chord: GuitarChord, onString: (Int, Float) -> Unit, modifier: Modifier) {
    val c = Yume.colors
    val chordState by rememberUpdatedState(chord)
    val play by rememberUpdatedState(onString)
    val lastString = remember { mutableStateMapOf<PointerId, Int>() }
    val ringing = remember { mutableStateMapOf<Int, Long>() }
    Canvas(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF5B3A44), Color(0xFF6A4552), Color(0xFF5B3A44))))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        val rowH = size.height / 6f
                        for (ch in ev.changes) {
                            if (ch.pressed) {
                                val s = (5 - (ch.position.y / rowH).toInt()).coerceIn(0, 5)
                                val prev = lastString[ch.id]
                                if (prev == null || prev != s) {
                                    // все пересечённые струны (быстрый свайп)
                                    val range = if (prev == null) listOf(s) else if (s > prev) (prev + 1..s).toList() else (s until prev).reversed().toList()
                                    for (st in range) {
                                        val f = chordState.frets[st]
                                        if (f >= 0) {
                                            val key = Fretboard.GUITAR_TUNING[st] + f
                                            play(key, 0.8f)
                                            ringing[st] = System.nanoTime()
                                        }
                                    }
                                    lastString[ch.id] = s
                                }
                                ch.consume()
                            } else {
                                lastString.remove(ch.id)
                            }
                        }
                        if (ev.changes.all { !it.pressed }) ringing.clear()
                    }
                }
            },
    ) {
        val rowH = size.height / 6f
        for (s in 0 until 6) {
            val y = size.height - (s + 0.5f) * rowH
            val active = chord.frets[s] >= 0
            val thick = (1.3f + (5 - s) * 0.5f).dp.toPx()
            val hot = ringing.containsKey(s)
            drawLine(Color(0x55000000), Offset(0f, y + 2f), Offset(size.width, y + 2f), thick)
            drawLine(if (!active) Color(0x66F4E6D8) else if (hot) c.primary else Color(0xFFF4E6D8), Offset(0f, y), Offset(size.width, y), thick)
        }
        drawContext.canvas.nativeCanvas.drawText(
            "проведи по струнам ↕", 12.dp.toPx(), 16.dp.toPx(),
            android.graphics.Paint().apply { color = android.graphics.Color.argb(120, 255, 230, 240); textSize = 11.dp.toPx(); isAntiAlias = true },
        )
    }
}

@Composable
private fun ChordButton(name: String, selected: Boolean, modifier: Modifier, onDown: (down: Boolean) -> Unit, onUp: () -> Unit) {
    val c = Yume.colors
    val down by rememberUpdatedState(onDown)
    val up by rememberUpdatedState(onUp)
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primary else c.surfaceHigh)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val first = awaitPointerEvent()
                        val d = first.changes.firstOrNull { it.pressed && !it.previousPressed } ?: continue
                        // касание — страм вниз сразу (минимальная задержка), свайп вверх — ещё и страм вверх
                        down(true)
                        val startY = d.position.y
                        var upStrummed = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val ch = e.changes.firstOrNull { it.id == d.id } ?: break
                            if (!upStrummed && ch.position.y - startY < -24f) { down(false); upStrummed = true }
                            if (!ch.pressed) { up(); break }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(name, style = YumeText.title, color = if (selected) c.onPrimary else c.text)
    }
}

/** Диаграмма аккорда (как в правом верхнем углу на макете). */
@Composable
fun ChordDiagram(chord: GuitarChord, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(Color(0xE63A2631)).padding(6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(chord.name, style = YumeText.title, color = Color.White)
            Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = 2.dp)) {
                val played = chord.frets.filter { it > 0 }
                val base = if ((played.maxOrNull() ?: 0) > 4) (played.minOrNull() ?: 1) else 1
                val sw = size.width / 5f
                val fh = size.height / 5f
                for (s in 0 until 6) drawLine(Color(0xCCFFFFFF), Offset(s * sw, 0f), Offset(s * sw, size.height), 1.5f)
                for (f in 0..5) drawLine(Color(0xCCFFFFFF), Offset(0f, f * fh), Offset(size.width, f * fh), if (f == 0 && base == 1) 4f else 1.5f)
                chord.frets.forEachIndexed { s, f ->
                    val x = s * sw
                    when {
                        f < 0 -> {
                            drawLine(Color(0xFFFFB3C6), Offset(x - 4f, -10f), Offset(x + 4f, -2f), 2f)
                            drawLine(Color(0xFFFFB3C6), Offset(x + 4f, -10f), Offset(x - 4f, -2f), 2f)
                        }
                        f == 0 -> drawCircle(Color.White, 4f, Offset(x, -6f), style = Stroke(1.5f))
                        else -> drawCircle(Color(0xFFF6A5C0), sw * 0.32f, Offset(x, (f - base + 0.5f) * fh))
                    }
                }
                drawRoundRect(Color.Transparent, Offset.Zero, Size(1f, 1f), CornerRadius(1f))
            }
        }
    }
}

/** Бас: 4 струны, слайды, бенды, подсветка лада. */
@Composable
fun BassInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val settings by vm.settings.collectAsState()
    var firstFret by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize().padding(10.dp)) {
        ArtHeader(vm, index, track, Modifier.weight(0.4f))
        ControlStrip {
            Pill("◀ лады", height = 32.dp) { firstFret = (firstFret - 5).coerceAtLeast(0) }
            Text("с ${firstFret} лада", style = YumeText.small, color = c.textDim)
            Pill("лады ▶", height = 32.dp) { firstFret = (firstFret + 5).coerceAtMost(12) }
            Spacer(Modifier.weight(1f))
            Pill("Педали", Icons.Rounded.Tune, height = 32.dp, color = c.peach, contentColor = c.onPrimary) { vm.setMode(StudioMode.FX) }
        }
        BoxWithConstraints(Modifier.weight(0.6f).fillMaxWidth()) {
            val scale = if (project?.minor == true) Scales.minor else Scales.major
            FretboardView(
                tuning = Fretboard.BASS_TUNING, frets = 12, firstFret = firstFret,
                onNoteOn = { k, v -> vm.noteOn(k, v) }, onNoteOff = { vm.noteOff(it) }, onBend = vm::setPitchBend,
                modifier = Modifier.fillMaxSize(),
                highlight = { k -> project?.let { Scales.inScale(k, it.keyRoot, scale) } ?: false },
                showLabels = settings.showNoteLabels,
                woodColor = Color(0xFF4A3346),
                accent = c.accent,
            )
        }
    }
}
