package com.yume.studio.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.DrumMap
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.NoteNames
import com.yume.studio.model.Grid
import com.yume.studio.model.Note
import com.yume.studio.model.PPQ
import com.yume.studio.model.Timing
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.components.YumeSlider
import com.yume.studio.ui.studio.StudioMode
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.max
import kotlin.math.roundToInt

private val ROW_H = 20.dp
private val KEYS_W = 64.dp
private const val LOW = 21
private const val HIGH = 108

@Composable
fun PianoRollView(vm: StudioViewModel) {
    val c = Yume.colors
    val ui by vm.ui.collectAsState()
    val project by vm.project.collectAsState()
    val transport by vm.transport.collectAsState()
    val found = vm.findRegion(ui.editingRegion)
    val p = project
    if (found == null || p == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Выбери MIDI-регион на дорожках и нажми «Ноты»", style = YumeText.body, color = c.textDim)
        }
        return
    }
    val (ti, region) = found
    val track = p.tracks[ti]
    val drums = track.type == InstrumentType.DRUMS
    val density = LocalDensity.current
    val rowPx = with(density) { ROW_H.toPx() }
    var pxPerTick by remember { mutableFloatStateOf(with(density) { 220.dp.toPx() } / (PPQ * 4)) }
    val grid = ui.grid
    var selected by remember(region.id) { mutableStateOf(setOf<Int>()) }
    // черновик нот во время перетаскивания
    var draft by remember(region.id) { mutableStateOf<List<Note>?>(null) }
    val notes = draft ?: region.notes
    val length = max(region.contentLength, PPQ * 4)
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    LaunchedEffect(region.id) {
        val center = if (region.notes.isNotEmpty()) region.notes.map { it.key }.average().toInt() else if (drums) 42 else 60
        vScroll.scrollTo(((HIGH - center) * rowPx - 120 * density.density).toInt().coerceAtLeast(0))
    }

    val notesState by rememberUpdatedState(notes)
    val gridState by rememberUpdatedState(grid)
    val pxState by rememberUpdatedState(pxPerTick)

    fun commit(n: List<Note>, key: String? = null) {
        draft = null
        vm.setNotes(region.id, n, key)
    }

    fun hit(x: Float, y: Float): Int? {
        val key = HIGH - (y / rowPx).toInt()
        val tick = x / pxState
        return notesState.indexOfLast { it.key == key && tick >= it.tick && tick <= it.tick + it.length }.takeIf { it >= 0 }
    }

    Column(Modifier.fillMaxSize()) {
        // ── панель ──
        Row(Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(region.name.ifBlank { track.name }, style = YumeText.title, color = c.text, modifier = Modifier.width(140.dp), maxLines = 1)
            Segmented(listOf(Grid.QUARTER, Grid.EIGHTH, Grid.SIXTEENTH, Grid.TRIPLET, Grid.THIRTYSECOND), grid, { it.title }, vm::setGrid, height = 26.dp)
            Spacer(Modifier.width(8.dp))
            Pill(null, Icons.Rounded.ZoomOut, height = 30.dp) { pxPerTick = (pxPerTick / 1.4f).coerceAtLeast(with(density) { 60.dp.toPx() } / (PPQ * 4)) }
            Pill(null, Icons.Rounded.ZoomIn, height = 30.dp) { pxPerTick = (pxPerTick * 1.4f).coerceAtMost(with(density) { 1600.dp.toPx() } / (PPQ * 4)) }
            Spacer(Modifier.width(8.dp))
            Pill("Квантайз", Icons.Rounded.GridOn, height = 30.dp) {
                val sel = selected
                commit(notes.mapIndexed { i, n -> if (sel.isEmpty() || i in sel) com.yume.studio.model.Quantize.notes(listOf(n), grid.ticks).first() else n })
            }
            Spacer(Modifier.width(6.dp))
            Pill("−12", height = 30.dp) { commit(transpose(notes, selected, -12)) }
            Pill("−1", height = 30.dp) { commit(transpose(notes, selected, -1)) }
            Pill("+1", height = 30.dp) { commit(transpose(notes, selected, 1)) }
            Pill("+12", height = 30.dp) { commit(transpose(notes, selected, 12)) }
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) {
                val v = notes.filterIndexed { i, _ -> i in selected }.map { it.velocity }.average().toFloat() / 127f
                Text("Сила", style = YumeText.tiny, color = c.textDim)
                YumeSlider(v, { nv ->
                    val vel = (nv * 127).roundToInt().coerceIn(1, 127)
                    vm.setNotes(region.id, notes.mapIndexed { i, n -> if (i in selected) n.copy(velocity = vel) else n }, "vel-${region.id}")
                }, Modifier.width(90.dp))
                Pill(null, Icons.Rounded.Delete, height = 30.dp, color = c.danger.copy(alpha = 0.8f), contentColor = Color.White) {
                    commit(notes.filterIndexed { i, _ -> i !in selected }); selected = emptySet()
                }
            }
            Pill("Готово", Icons.Rounded.Check, selected = true, height = 30.dp) { vm.setMode(StudioMode.TRACKS) }
        }
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ── клавиши слева ──
            Box(Modifier.width(KEYS_W).fillMaxHeight().verticalScroll(vScroll)) {
                Canvas(
                    Modifier.width(KEYS_W).height(ROW_H * (HIGH - LOW + 1)).pointerInput(Unit) {
                        detectTapGestures(onPress = { o ->
                            val key = HIGH - (o.y / rowPx).toInt()
                            vm.noteOn(key, 0.8f, ti)
                            tryAwaitRelease()
                            vm.noteOff(key, ti)
                        })
                    },
                ) {
                    val paint = android.graphics.Paint().apply { textSize = 10.dp.toPx(); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD }
                    for (k in LOW..HIGH) {
                        val y = (HIGH - k) * rowPx
                        val black = NoteNames.isBlack(k)
                        drawRect(if (black) c.keyBlack else c.keyWhite, Offset(0f, y), Size(size.width, rowPx - 1f))
                        val label = if (drums) DrumMap.pads.firstOrNull { it.key == k }?.name else if (k % 12 == 0) NoteNames.name(k) else null
                        if (label != null) {
                            paint.color = if (black) android.graphics.Color.WHITE else android.graphics.Color.rgb(120, 80, 100)
                            drawContext.canvas.nativeCanvas.drawText(label, 4.dp.toPx(), y + rowPx * 0.72f, paint)
                        }
                    }
                }
            }
            // ── сетка ──
            Box(Modifier.weight(1f).fillMaxHeight().background(c.backgroundAlt).verticalScroll(vScroll).horizontalScroll(hScroll)) {
                Canvas(
                    Modifier
                        .width(with(density) { (length * pxPerTick).toDp() + 40.dp })
                        .height(ROW_H * (HIGH - LOW + 1))
                        .pointerInput(region.id) {
                            detectTapGestures(
                                onTap = { o ->
                                    val idx = hit(o.x, o.y)
                                    if (idx != null) {
                                        selected = if (idx in selected) selected - idx else setOf(idx)
                                    } else {
                                        val key = HIGH - (o.y / rowPx).toInt()
                                        val tick = Timing.snapDown((o.x / pxState).toInt(), gridState.ticks)
                                        if (tick < length) {
                                            val n = Note(tick, if (drums) PPQ / 4 else gridState.ticks, key, 100)
                                            vm.noteOn(key, 0.8f, ti); vm.noteOff(key, ti)
                                            commit(notesState + n)
                                            selected = emptySet()
                                        }
                                    }
                                },
                                onDoubleTap = { o ->
                                    hit(o.x, o.y)?.let { idx -> commit(notesState.filterIndexed { i, _ -> i != idx }); selected = emptySet() }
                                },
                            )
                        }
                        .pointerInput(region.id) {
                            // перетаскивание ноты (за край — длина); пустое место не трогаем → работает прокрутка
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val idx = hit(down.position.x, down.position.y) ?: return@awaitEachGesture
                                val n0 = notesState[idx]
                                val endX = (n0.tick + n0.length) * pxState
                                val resize = down.position.x > endX - 16.dp.toPx()
                                var dx = 0f
                                var dy = 0f
                                var moved = false
                                drag(down.id) { ch ->
                                    val d = ch.positionChange()
                                    dx += d.x; dy += d.y
                                    if (!moved && (kotlin.math.abs(dx) > 6f || kotlin.math.abs(dy) > 6f)) moved = true
                                    if (moved) {
                                        ch.consume()
                                        val g = gridState.ticks
                                        val upd = if (resize) {
                                            n0.copy(length = max(g / 2, Timing.snap(n0.length + (dx / pxState).toInt(), g)))
                                        } else {
                                            val nt = Timing.snap(n0.tick + (dx / pxState).toInt(), g).coerceIn(0, length - 1)
                                            val nk = (n0.key - (dy / rowPx).roundToInt()).coerceIn(LOW, HIGH)
                                            n0.copy(tick = nt, key = nk)
                                        }
                                        draft = notesState.toMutableList().also { it[idx] = upd }
                                    }
                                }
                                if (moved) {
                                    draft?.let { d ->
                                        val moving = d[idx]
                                        if (moving.key != n0.key) { vm.noteOn(moving.key, 0.7f, ti); vm.noteOff(moving.key, ti) }
                                        val sorted = d.sortedBy { it.tick }
                                        commit(sorted)
                                        selected = setOf(sorted.indexOf(moving))
                                    }
                                }
                            }
                        },
                ) {
                    // фон рядов
                    for (k in LOW..HIGH) {
                        val y = (HIGH - k) * rowPx
                        if (NoteNames.isBlack(k)) drawRect(Color(0x14000000), Offset(0f, y), Size(size.width, rowPx))
                        if (k % 12 == 0) drawLine(c.gridLineStrong, Offset(0f, y + rowPx), Offset(size.width, y + rowPx), 1f)
                    }
                    // вертикальная сетка
                    val g = grid.ticks
                    var t = 0
                    while (t <= length) {
                        val x = t * pxPerTick
                        val strong = t % (PPQ * p.beatsPerBar) == 0
                        val beat = t % PPQ == 0
                        drawLine(if (strong) c.gridLineStrong else if (beat) c.gridLine else c.gridLine.copy(alpha = c.gridLine.alpha * 0.5f),
                            Offset(x, 0f), Offset(x, size.height), if (strong) 2f else 1f)
                        t += g
                    }
                    drawRect(Color(0x33000000), Offset(length * pxPerTick, 0f), Size(size.width, size.height))
                    // ноты
                    val color = c.track(track.colorIndex)
                    notes.forEachIndexed { i, n ->
                        val y = (HIGH - n.key) * rowPx
                        val sel = i in selected
                        val alpha = 0.45f + 0.55f * n.velocity / 127f
                        drawRoundRect(color.copy(alpha = alpha), Offset(n.tick * pxPerTick, y + 1.5f), Size(max(4f, n.length * pxPerTick - 1f), rowPx - 3f), CornerRadius(6f))
                        if (sel) drawRoundRect(Color.White, Offset(n.tick * pxPerTick, y + 1.5f), Size(max(4f, n.length * pxPerTick - 1f), rowPx - 3f), CornerRadius(6f), style = Stroke(2.5f))
                    }
                    // плейхед внутри региона
                    val rel = transport.position - region.start
                    if (rel >= 0 && rel < region.length) {
                        val x = ((rel % length) * pxPerTick).toFloat()
                        drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2f)
                    }
                }
            }
        }
    }
}

private fun transpose(notes: List<Note>, sel: Set<Int>, semis: Int): List<Note> =
    notes.mapIndexed { i, n -> if (sel.isEmpty() || i in sel) n.copy(key = (n.key + semis).coerceIn(LOW, HIGH)) else n }
