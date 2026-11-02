package com.yume.studio.ui.instruments

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.DrumMap
import com.yume.studio.audio.DrumPad
import com.yume.studio.model.Note
import com.yume.studio.model.PPQ
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.PresetSwitcher
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.components.YumeSlider
import com.yume.studio.ui.components.catPaw
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.hypot

private data class KitZone(val x: Float, val y: Float, val r: Float, val key: Int)

// координаты барабанов на арте art_drums (нормированные), арт держим в его пропорциях
private const val DRUM_ART_ASPECT = 399f / 172f
private val KIT = listOf(
    KitZone(0.55f, 0.84f, 0.16f, 36),   // бочка с котиком
    KitZone(0.17f, 0.84f, 0.12f, 38),   // малый
    KitZone(0.33f, 0.58f, 0.10f, 48),   // том высокий
    KitZone(0.83f, 0.58f, 0.10f, 45),   // том низкий
    KitZone(0.88f, 0.88f, 0.10f, 41),   // напольный
    KitZone(0.20f, 0.18f, 0.13f, 49),   // крэш
    KitZone(0.90f, 0.22f, 0.10f, 51),   // райд
    KitZone(0.06f, 0.48f, 0.07f, 42),   // хэт
)

private enum class DrumMode(val title: String) { PADS("Кит и пэды"), STEPS("Степ-секвенсор") }

@Composable
fun DrumsInstrument(vm: StudioViewModel, index: Int, track: Track) {
    val c = Yume.colors
    val settings by vm.settings.collectAsState()
    var mode by remember { mutableStateOf(DrumMode.PADS) }
    val haptic = LocalHapticFeedback.current
    val flashes = remember { mutableStateMapOf<Int, Long>() }
    fun hit(key: Int, vel: Float) {
        vm.noteOn(key, vel)
        flashes[key] = System.nanoTime()
        if (settings.haptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    var picker by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(10.dp)) {
        ControlStrip {
            Segmented(DrumMode.entries, mode, { it.title }, { mode = it; if (it == DrumMode.PADS) vm.stopPreview() }, height = 28.dp)
            Spacer(Modifier.weight(1f))
            PresetSwitcher(
                track.type.presets.getOrNull(track.presetIndex)?.name ?: "Кит",
                onPrev = { vm.setPreset(index, track.presetIndex - 1) },
                onNext = { vm.setPreset(index, track.presetIndex + 1) },
                onClick = { picker = true },
            )
        }
        if (mode == DrumMode.PADS) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Box(Modifier.aspectRatio(DRUM_ART_ASPECT, matchHeightConstraintsFirst = true).clip(RoundedCornerShape(18.dp))) {
                        Image(painterResource(track.type.art), null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        KitTouchLayer(flashes, onHit = ::hit)
                    }
                }
                PadGrid(Modifier.width(280.dp).fillMaxHeight(), flashes, onHit = ::hit)
            }
        } else {
            StepSequencer(vm, index, track, Modifier.weight(1f).fillMaxWidth())
        }
    }
    if (picker) PresetPicker(track, onPick = { vm.setPreset(index, it); picker = false }, onDismiss = { picker = false })
}

@Composable
private fun KitTouchLayer(flashes: Map<Int, Long>, onHit: (Int, Float) -> Unit) {
    val hit by rememberUpdatedState(onHit)
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(flashes.size, flashes.values.maxOrNull()) {
        // короткая анимация вспышек
        repeat(12) { withFrameNanos { tick++ } }
    }
    Canvas(
        Modifier.fillMaxSize().pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    for (ch in ev.changes) {
                        if (ch.pressed && !ch.previousPressed) {
                            val nx = ch.position.x / size.width
                            val ny = ch.position.y / size.height
                            val zone = KIT.minByOrNull { hypot((it.x - nx) * DRUM_ART_ASPECT, it.y - ny) - it.r }
                            if (zone != null) {
                                val d = hypot((zone.x - nx) * DRUM_ART_ASPECT, zone.y - ny)
                                if (d < zone.r * 2.2f) hit(zone.key, (1f - d / (zone.r * 3f)).coerceIn(0.45f, 1f))
                            }
                            ch.consume()
                        }
                    }
                }
            }
        },
    ) {
        if (tick < 0) return@Canvas  // чтение tick перерисовывает вспышки по кадрам
        val now = System.nanoTime()
        for (z in KIT) {
            val t = flashes[z.key] ?: continue
            val age = (now - t) / 1e9f
            if (age > 0.25f) continue
            val a = 1f - age / 0.25f
            drawCircle(Color.White.copy(alpha = 0.45f * a), z.r * size.height * (1f + age * 2f), Offset(z.x * size.width, z.y * size.height))
        }
    }
}

@Composable
private fun PadGrid(modifier: Modifier, flashes: Map<Int, Long>, onHit: (Int, Float) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DrumMap.pads.chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { pad -> Pad(pad, flashes[pad.key], Modifier.weight(1f).fillMaxHeight(), onHit) }
            }
        }
    }
}

@Composable
private fun Pad(pad: DrumPad, flash: Long?, modifier: Modifier, onHit: (Int, Float) -> Unit) {
    val c = Yume.colors
    val color = c.pedal(pad.colorIndex + 2)
    val anim = remember { Animatable(1f) }
    val hit by rememberUpdatedState(onHit)
    LaunchedEffect(flash) {
        if (flash != null) { anim.snapTo(0.9f); anim.animateTo(1f, tween(140)) }
    }
    Box(
        modifier
            .scale(anim.value)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(color, color.copy(red = color.red * 0.88f, green = color.green * 0.85f, blue = color.blue * 0.88f))))
            .pointerInput(pad.key) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent()
                        ev.changes.forEach { ch ->
                            if (ch.pressed && !ch.previousPressed) {
                                // центр пэда громче, края тише
                                val dx = (ch.position.x / size.width - 0.5f) * 2
                                val dy = (ch.position.y / size.height - 0.5f) * 2
                                hit(pad.key, (1f - 0.4f * hypot(dx, dy) / 1.41f).coerceIn(0.4f, 1f))
                                ch.consume()
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            catPaw(Offset(size.width / 2, size.height * 0.45f), size.minDimension * 0.18f, Color.White.copy(alpha = 0.35f))
        }
        Text(pad.name, style = YumeText.tiny, color = Color(0xFF4A2E3B), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp))
    }
}

// ───────────────────────── степ-секвенсор ─────────────────────────

@Composable
private fun StepSequencer(vm: StudioViewModel, index: Int, track: Track, modifier: Modifier) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val rows = DrumMap.stepRows
    val grid = remember { mutableStateListOf(*Array(rows.size * 16) { false }) }
    var swing by remember { mutableFloatStateOf(0f) }
    var playing by remember { mutableStateOf(false) }
    var startNs by remember { mutableStateOf(0L) }
    var step by remember { mutableIntStateOf(-1) }
    val bpm = project?.bpm ?: 100f

    fun notes(): List<Note> {
        val out = ArrayList<Note>()
        for (r in rows.indices) for (s in 0 until 16) if (grid[r * 16 + s]) {
            var t = s * PPQ / 4
            if (s % 2 == 1) t += (PPQ / 4 * swing * 0.6f).toInt()
            out += Note(t, PPQ / 4, rows[r].key, if (s % 4 == 0) 112 else 92)
        }
        return out.sortedBy { it.tick }
    }

    fun preview() {
        vm.previewDrumPattern(track, notes(), PPQ * 4)
        startNs = System.nanoTime()
        playing = true
    }

    LaunchedEffect(playing, bpm) {
        if (!playing) { step = -1; return@LaunchedEffect }
        val stepNs = 60_000_000_000.0 / bpm / 4
        while (playing) withFrameNanos { now -> step = (((now - startNs) / stepNs).toLong() % 16).toInt() }
    }

    // пример по умолчанию: простой бит
    LaunchedEffect(Unit) {
        if (grid.none { it }) {
            listOf(0, 8, 10).forEach { grid[0 * 16 + it] = true }
            listOf(4, 12).forEach { grid[1 * 16 + it] = true }
            (0 until 16 step 2).forEach { grid[3 * 16 + it] = true }
        }
    }

    Column(modifier) {
        ControlStrip {
            Pill(if (playing) "Стоп" else "Слушать", if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, selected = playing, height = 32.dp) {
                if (playing) { playing = false; vm.stopPreview() } else preview()
            }
            Pill("Очистить", Icons.Rounded.DeleteSweep, height = 32.dp) { for (i in grid.indices) grid[i] = false; if (playing) preview() }
            Text("Свинг", style = YumeText.small, color = c.textDim)
            YumeSlider(swing, { swing = it }, Modifier.width(120.dp))
            Spacer(Modifier.weight(1f))
            Pill("В проект (4 такта)", Icons.Rounded.Add, selected = true, height = 32.dp) {
                playing = false
                vm.stopPreview()
                vm.addPatternRegionLooped(index, notes(), PPQ * 4, 4, "Бит")
            }
        }
        Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            rows.forEachIndexed { r, pad ->
                Row(Modifier.weight(1f).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(
                        Modifier.width(78.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(c.surfaceHigh)
                            .clickable { vm.noteOn(pad.key, 0.9f) },
                        contentAlignment = Alignment.Center,
                    ) { Text(pad.name, style = YumeText.tiny, color = c.text) }
                    for (s in 0 until 16) {
                        val on = grid[r * 16 + s]
                        val beat = s / 4 % 2 == 0
                        val col = c.pedal(pad.colorIndex + 2)
                        Box(
                            Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        on && s == step -> Color.White
                                        on -> col
                                        s == step -> c.primary.copy(alpha = 0.35f)
                                        beat -> c.surface
                                        else -> c.surfaceLow
                                    },
                                )
                                .clickable {
                                    grid[r * 16 + s] = !on
                                    if (!on) vm.noteOn(pad.key, 0.8f)
                                    if (playing) preview()
                                },
                        )
                    }
                }
            }
        }
    }
}
