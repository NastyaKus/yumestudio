package com.yume.studio.ui.tracks

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridOn
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.yume.studio.model.PPQ
import com.yume.studio.model.Project
import com.yume.studio.model.Region
import com.yume.studio.model.Timing
import com.yume.studio.model.Track
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.studio.ClipInfo
import com.yume.studio.ui.studio.StudioViewModel
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

private val HEADER_W = 176.dp
private val LANE_H = 66.dp
private val RULER_H = 30.dp

@Composable
fun TracksView(vm: StudioViewModel) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val ui by vm.ui.collectAsState()
    val transport by vm.transport.collectAsState()
    val clips by vm.clips.collectAsState()
    val recRegion by vm.recordingRegion.collectAsState()
    val p = project ?: return
    val density = LocalDensity.current
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    // пикселей на тик (зум)
    var pxPerTick by remember { mutableFloatStateOf(with(density) { 130.dp.toPx() } / (PPQ * 4)) }
    val bar = p.ticksPerBar
    val totalBars = max(p.lengthBars, p.endTick / bar + 1) + 8
    val totalW = totalBars * bar * pxPerTick
    val snapTicks = if (pxPerTick * bar > with(density) { 240.dp.toPx() }) PPQ else bar
    var renaming by remember { mutableStateOf<Int?>(null) }

    // авто-прокрутка за плейхедом
    LaunchedEffect(transport.position, transport.playing) {
        if (!transport.playing) return@LaunchedEffect
        val x = (transport.position * pxPerTick).toInt()
        val view = hScroll.viewportSize
        if (view > 0 && (x > hScroll.value + view * 0.85f || x < hScroll.value)) {
            hScroll.scrollTo((x - view * 0.15f).toInt().coerceAtLeast(0))
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ── заголовки дорожек ──
            Column(Modifier.width(HEADER_W).fillMaxHeight()) {
                Box(Modifier.height(RULER_H).fillMaxWidth().padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.ZoomOut, "Отдалить", tint = c.textDim, modifier = Modifier.size(28.dp).clip(CircleShape)
                            .clickable { pxPerTick = (pxPerTick / 1.4f).coerceAtLeast(with(density) { 24.dp.toPx() } / bar) }.padding(4.dp))
                        Icon(Icons.Rounded.ZoomIn, "Приблизить", tint = c.textDim, modifier = Modifier.size(28.dp).clip(CircleShape)
                            .clickable { pxPerTick = (pxPerTick * 1.4f).coerceAtMost(with(density) { 900.dp.toPx() } / bar) }.padding(4.dp))
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(vScroll)) {
                    p.tracks.forEachIndexed { i, t ->
                        TrackHeader(
                            vm, i, t, selected = i == ui.selected, recording = transport.recording && i == ui.selected,
                            onRename = { renaming = i },
                        )
                    }
                    Box(
                        Modifier.padding(8.dp).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(14.dp))
                            .border(1.5.dp, c.outline, RoundedCornerShape(14.dp)).clickable { vm.showTrackPicker(true) },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.Add, "Добавить дорожку", tint = c.textDim) }
                }
            }
            // ── таймлайн ──
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().background(c.backgroundAlt)) {
                val viewportW = constraints.maxWidth
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            // щипок двумя пальцами — зум, одиночные касания пропускаем дальше
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                var prevDist = -1f
                                while (true) {
                                    val ev = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressed = ev.changes.filter { it.pressed }
                                    if (pressed.isEmpty()) break
                                    if (pressed.size >= 2) {
                                        val a = pressed[0].position; val b = pressed[1].position
                                        val d = hypot(a.x - b.x, a.y - b.y)
                                        if (prevDist > 0f) {
                                            val f = d / prevDist
                                            val focusTick = (hScroll.value + (a.x + b.x) / 2) / pxPerTick
                                            pxPerTick = (pxPerTick * f).coerceIn(24.dp.toPx() / bar, 900.dp.toPx() / bar)
                                            val target = (focusTick * pxPerTick - (a.x + b.x) / 2).toInt()
                                            scope.launch { hScroll.scrollTo(target.coerceAtLeast(0)) }
                                        }
                                        prevDist = d
                                        ev.changes.forEach { it.consume() }
                                    }
                                }
                            }
                        },
                ) {
                    Column(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
                        Ruler(vm, p, pxPerTick, totalW, transport.position)
                        Box(Modifier.weight(1f).verticalScroll(vScroll)) {
                            Column {
                                p.tracks.forEachIndexed { i, t ->
                                    Lane(
                                        vm = vm, project = p, track = t, index = i, pxPerTick = pxPerTick, totalW = totalW,
                                        selectedRegion = ui.selectedRegion, selectedTrack = ui.selected == i, clips = clips,
                                        snapTicks = snapTicks,
                                        recording = recRegion?.takeIf { transport.recording && i == ui.selected },
                                    )
                                }
                                Spacer(Modifier.height(60.dp).width(with(density) { totalW.toDp() }))
                            }
                        }
                    }
                    // плейхед
                    val phX = transport.position * pxPerTick - hScroll.value
                    if (phX >= -2 && phX <= viewportW + 2) {
                        Canvas(Modifier.fillMaxSize()) {
                            val x = phX.toFloat()
                            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                            val tri = Path().apply {
                                moveTo(x - 7.dp.toPx(), 0f); lineTo(x + 7.dp.toPx(), 0f); lineTo(x, 10.dp.toPx()); close()
                            }
                            drawPath(tri, Color.White)
                        }
                    }
                }
            }
        }
        // панель действий для выделенного региона
        val sel = vm.findRegion(ui.selectedRegion)
        if (sel != null) RegionActions(vm, sel.second)
    }

    renaming?.let { i ->
        var name by remember(i) { mutableStateOf(p.tracks.getOrNull(i)?.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Имя дорожки") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameTrack(i, name); renaming = null }) { Text("Ок") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Отмена") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun TrackHeader(vm: StudioViewModel, i: Int, t: Track, selected: Boolean, recording: Boolean, onRename: () -> Unit) {
    val c = Yume.colors
    var menu by remember { mutableStateOf(false) }
    val color = c.track(t.colorIndex)
    Row(
        Modifier
            .height(LANE_H)
            .fillMaxWidth()
            .padding(start = 6.dp, end = 4.dp, top = 3.dp, bottom = 3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) c.surfaceHigh else c.surface)
            .pointerInput(i) {
                detectTapGestures(
                    onTap = { vm.select(i) },
                    onDoubleTap = { vm.openInstrument(i) },
                    onLongPress = { vm.select(i); menu = true },
                )
            }
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Image(painterResource(t.type.avatar), null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).border(2.dp, if (recording) c.danger else color, RoundedCornerShape(12.dp)))
            DropdownMenu(menu, { menu = false }, containerColor = c.surfaceHigh) {
                DropdownMenuItem({ Text("Открыть инструмент") }, { menu = false; vm.openInstrument(i) })
                DropdownMenuItem({ Text("Переименовать") }, { menu = false; onRename() })
                DropdownMenuItem({ Text("Дублировать") }, { menu = false; vm.duplicateTrack(i) })
                DropdownMenuItem({ Text("Выше") }, { menu = false; vm.moveTrack(i, i - 1) })
                DropdownMenuItem({ Text("Ниже") }, { menu = false; vm.moveTrack(i, i + 1) })
                DropdownMenuItem({ Text("Удалить", color = c.danger) }, { menu = false; vm.deleteTrack(i) })
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = YumeText.small, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (t.mute) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, "Заглушить",
                    tint = if (t.mute) c.danger else c.textDim,
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable { vm.toggleMute(i) }.padding(3.dp))
                Icon(Icons.Rounded.Headphones, "Соло", tint = if (t.solo) c.peach else c.textDim,
                    modifier = Modifier.size(24.dp).clip(CircleShape).clickable { vm.toggleSolo(i) }.padding(3.dp))
            }
            MiniSlider(t.volume, { vm.setVolume(i, it) }, color)
        }
    }
}

@Composable
private fun MiniSlider(value: Float, onChange: (Float) -> Unit, color: Color) {
    val c = Yume.colors
    val cb by rememberUpdatedState(onChange)
    Canvas(
        Modifier.fillMaxWidth().height(12.dp).pointerInput(Unit) {
            detectDragGestures { ch, _ -> ch.consume(); cb((ch.position.x / size.width).coerceIn(0f, 1f)) }
        },
    ) {
        val h = 4.dp.toPx()
        drawRoundRect(c.surfaceLow, Offset(0f, size.height / 2 - h / 2), Size(size.width, h), CornerRadius(h))
        drawRoundRect(color, Offset(0f, size.height / 2 - h / 2), Size(size.width * value, h), CornerRadius(h))
        drawCircle(Color.White, 5.dp.toPx(), Offset(size.width * value, size.height / 2))
    }
}

@Composable
private fun Ruler(vm: StudioViewModel, p: Project, pxPerTick: Float, totalW: Float, position: Double) {
    val c = Yume.colors
    val density = LocalDensity.current
    val bar = p.ticksPerBar
    val pxState by rememberUpdatedState(pxPerTick)
    val pState by rememberUpdatedState(p)
    var loopDrag by remember { mutableStateOf(0) } // 1 — начало, 2 — конец, 3 — весь цикл
    Canvas(
        Modifier
            .width(with(density) { totalW.toDp() })
            .height(RULER_H)
            .background(c.surface)
            .pointerInput(Unit) {
                detectTapGestures { o -> vm.seek(Timing.snap((o.x / pxState).toInt(), PPQ).toDouble()) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val pr = pState
                        val t = o.x / pxState
                        val inLoopBand = o.y < size.height * 0.45f && pr.loopOn
                        loopDrag = when {
                            inLoopBand && abs(t - pr.loopStart) * pxState < 24.dp.toPx() -> 1
                            inLoopBand && abs(t - pr.loopEnd) * pxState < 24.dp.toPx() -> 2
                            inLoopBand && t > pr.loopStart && t < pr.loopEnd -> 3
                            else -> 0
                        }
                    },
                    onDragEnd = { loopDrag = 0 },
                ) { ch, d ->
                    ch.consume()
                    val pr = pState
                    val t = (ch.position.x / pxState).toInt()
                    when (loopDrag) {
                        1 -> vm.setLoopRange(Timing.snap(t, bar), pr.loopEnd)
                        2 -> vm.setLoopRange(pr.loopStart, Timing.snap(t, bar))
                        3 -> {
                            val dt = (d.x / pxState).toInt()
                            if (abs(dt) > 0) vm.setLoopRange(pr.loopStart + dt, pr.loopEnd + dt)
                        }
                        else -> vm.seek(Timing.snap(t, PPQ / 4).toDouble())
                    }
                }
            },
    ) {
        val barPx = bar * pxPerTick
        val bars = (size.width / barPx).toInt() + 1
        if (p.loopOn) {
            drawRoundRect(c.peach.copy(alpha = 0.85f), Offset(p.loopStart * pxPerTick, 2.dp.toPx()),
                Size((p.loopEnd - p.loopStart) * pxPerTick, size.height * 0.36f), CornerRadius(6.dp.toPx()))
        }
        val every = when { barPx < 30.dp.toPx() -> 4; barPx < 60.dp.toPx() -> 2; else -> 1 }
        for (b in 0..bars) {
            val x = b * barPx
            drawLine(c.gridLineStrong, Offset(x, size.height * 0.45f), Offset(x, size.height), 1.dp.toPx())
            if (b % every == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${b + 1}", x + 4.dp.toPx(), size.height - 6.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = c.textDim.toArgbCompat(); textSize = 11.dp.toPx(); isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    },
                )
            }
            if (barPx > 80.dp.toPx()) for (beat in 1 until p.beatsPerBar) {
                val bx = x + beat * PPQ * pxPerTick
                drawLine(c.gridLine, Offset(bx, size.height * 0.7f), Offset(bx, size.height), 1.dp.toPx())
            }
        }
        if (position < 0) {
            drawRect(c.danger.copy(alpha = 0.25f), Offset(0f, 0f), Size(8.dp.toPx(), size.height))
        }
    }
}

private fun Color.toArgbCompat(): Int = android.graphics.Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

@Composable
private fun Lane(
    vm: StudioViewModel,
    project: Project,
    track: Track,
    index: Int,
    pxPerTick: Float,
    totalW: Float,
    selectedRegion: Long?,
    selectedTrack: Boolean,
    clips: Map<String, ClipInfo>,
    snapTicks: Int,
    recording: Region?,
) {
    val c = Yume.colors
    val density = LocalDensity.current
    val pxState by rememberUpdatedState(pxPerTick)
    val bar = project.ticksPerBar
    Box(
        Modifier
            .width(with(density) { totalW.toDp() })
            .height(LANE_H)
            .background(if (selectedTrack) c.surface.copy(alpha = 0.35f) else Color.Transparent)
            .pointerInput(index) {
                detectTapGestures(
                    onTap = { vm.select(index); vm.selectRegion(null) },
                    onDoubleTap = { o -> vm.createRegion(index, (o.x / pxState).toInt()) },
                )
            },
    ) {
        // сетка тактов
        Canvas(Modifier.fillMaxSize()) {
            val barPx = bar * pxPerTick
            var x = 0f
            var b = 0
            while (x < size.width) {
                drawLine(if (b % 4 == 0) c.gridLineStrong else c.gridLine, Offset(x, 0f), Offset(x, size.height), 1f)
                x += barPx; b++
            }
            drawLine(c.gridLine, Offset(0f, size.height - 1), Offset(size.width, size.height - 1), 1f)
        }
        track.regions.forEach { r ->
            RegionBox(vm, project, track, index, r, pxPerTick, selected = r.id == selectedRegion, clips = clips, snapTicks = snapTicks)
        }
        recording?.let { r ->
            RegionBox(vm, project, track, index, r, pxPerTick, selected = false, clips = clips, snapTicks = snapTicks, live = true)
        }
    }
}

@Composable
private fun RegionBox(
    vm: StudioViewModel,
    project: Project,
    track: Track,
    trackIndex: Int,
    r: Region,
    pxPerTick: Float,
    selected: Boolean,
    clips: Map<String, ClipInfo>,
    snapTicks: Int,
    live: Boolean = false,
) {
    val c = Yume.colors
    val density = LocalDensity.current
    val color = if (live) c.danger else c.track(track.colorIndex)
    val pxState by rememberUpdatedState(pxPerTick)
    val regionState by rememberUpdatedState(r)
    val laneH = with(density) { LANE_H.toPx() }
    // локальное смещение во время перетаскивания (без записи в проект на каждый пиксель)
    var dragDx by remember(r.id) { mutableFloatStateOf(0f) }
    var dragDy by remember(r.id) { mutableFloatStateOf(0f) }
    var resizeDx by remember(r.id) { mutableFloatStateOf(0f) }
    var mode by remember(r.id) { mutableStateOf(0) } // 1 move, 2 resize/loop, 3 trim start
    val x = r.start * pxPerTick + if (mode == 1) dragDx else if (mode == 3) resizeDx else 0f
    val w = (r.length * pxPerTick + when (mode) { 2 -> resizeDx; 3 -> -resizeDx; else -> 0f }).coerceAtLeast(8f)
    Box(
        Modifier
            .offset { IntOffset(x.roundToInt(), (3.dp.toPx() + if (mode == 1) dragDy else 0f).roundToInt()) }
            .width(with(density) { w.toDp() })
            .height(LANE_H - 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = if (selected) 0.95f else 0.78f))
            .then(if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
            .pointerInput(r.id, live) {
                if (live) return@pointerInput
                detectTapGestures(
                    onTap = { vm.select(trackIndex); vm.selectRegion(regionState.id) },
                    onDoubleTap = { if (!regionState.isAudio) vm.editRegion(regionState.id) },
                )
            }
            .then(
                if (selected && !live) Modifier.pointerInput(r.id) {
                    val edge = 22.dp.toPx()
                    detectDragGestures(
                        onDragStart = { o ->
                            mode = when {
                                o.x > size.width - edge -> 2
                                o.x < edge * 0.8f -> 3
                                else -> 1
                            }
                            dragDx = 0f; dragDy = 0f; resizeDx = 0f
                        },
                        onDragEnd = {
                            val reg = regionState
                            val px = pxState
                            when (mode) {
                                1 -> {
                                    val newStart = Timing.snap(reg.start + (dragDx / px).toInt(), snapTicks)
                                    val laneShift = (dragDy / laneH).roundToInt()
                                    vm.moveRegion(reg.id, newStart, trackIndex + laneShift)
                                }
                                2 -> {
                                    val newLen = Timing.snap(reg.length + (resizeDx / px).toInt(), snapTicks).coerceAtLeast(snapTicks)
                                    // тянем за верхнюю половину правого края — луп, за нижнюю — длина
                                    vm.resizeRegion(reg.id, newLen, loop = !reg.isAudio && (reg.isLooped || newLen > reg.contentLength))
                                }
                                3 -> vm.trimRegionStart(reg.id, Timing.snap(reg.start + (resizeDx / px).toInt(), snapTicks))
                            }
                            mode = 0; dragDx = 0f; dragDy = 0f; resizeDx = 0f
                        },
                        onDragCancel = { mode = 0; dragDx = 0f; dragDy = 0f; resizeDx = 0f },
                    ) { ch, d ->
                        ch.consume()
                        when (mode) {
                            1 -> { dragDx += d.x; dragDy += d.y }
                            else -> resizeDx += d.x
                        }
                    }
                } else Modifier,
            ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val px = pxPerTick
            val hdr = 14.dp.toPx()
            drawRect(Color(0x33000000), Offset(0f, 0f), Size(size.width, hdr))
            if (r.isAudio) {
                val info = clips[r.clipFile]
                if (info != null) {
                    val spt = Timing.samplesPerTick(project.bpm)
                    val rate = info.sampleRate / 48000.0
                    val perPeak = info.sampleRate / 100.0
                    val mid = hdr + (size.height - hdr) / 2
                    val amp = (size.height - hdr) / 2 * 0.95f
                    var xx = 0f
                    val step = 2f
                    while (xx < size.width) {
                        val tick = xx / px
                        val frame = r.clipOffset + tick * spt * rate
                        val idx = (frame / perPeak).toInt()
                        val v = info.peaks.getOrElse(idx) { 0f } * r.gain
                        drawLine(Color(0xCCFFFFFF), Offset(xx, mid - v * amp), Offset(xx, mid + v * amp), 1.5f)
                        xx += step
                    }
                }
            } else if (r.notes.isNotEmpty()) {
                val lo = r.notes.minOf { it.key } - 1
                val hi = r.notes.maxOf { it.key } + 1
                val range = max(hi - lo, 8)
                val top = hdr + 3.dp.toPx()
                val h = size.height - top - 3.dp.toPx()
                val cl = if (r.contentLength > 0) r.contentLength else r.length
                var base = 0
                while (base < r.length) {
                    val alpha = if (base == 0) 0.95f else 0.6f
                    r.notes.forEach { n ->
                        if (n.tick >= cl) return@forEach
                        val t0 = base + n.tick
                        if (t0 >= r.length) return@forEach
                        val y = top + h * (1f - (n.key - lo).toFloat() / range)
                        val nw = (minOf(n.length, r.length - t0) * px).coerceAtLeast(2f)
                        drawRoundRect(Color.White.copy(alpha = alpha), Offset(t0 * px, y - 2f), Size(nw, 3.5f), CornerRadius(2f))
                    }
                    if (base > 0) drawLine(Color(0x55FFFFFF), Offset(base * px, hdr), Offset(base * px, size.height), 2f)
                    base += cl
                    if (cl <= 0) break
                }
            }
            if (selected) {
                // ручки краёв
                drawRoundRect(Color.White, Offset(size.width - 6.dp.toPx(), size.height * 0.3f), Size(3.dp.toPx(), size.height * 0.4f), CornerRadius(2f))
                drawRoundRect(Color.White, Offset(3.dp.toPx(), size.height * 0.3f), Size(3.dp.toPx(), size.height * 0.4f), CornerRadius(2f))
            }
        }
        Row(Modifier.padding(start = 6.dp, top = 0.dp).height(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (r.isLooped) Icon(Icons.Rounded.Loop, null, tint = Color.White, modifier = Modifier.size(11.dp))
            Text(r.name.ifBlank { track.name }, style = YumeText.tiny, color = Color.White, maxLines = 1)
        }
    }
}

@Composable
private fun RegionActions(vm: StudioViewModel, r: Region) {
    val c = Yume.colors
    var quant by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().background(c.surface).padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(r.name.ifBlank { "Регион" }, style = YumeText.small, color = c.textDim, maxLines = 1, modifier = Modifier.width(110.dp))
        if (!r.isAudio) Pill("Ноты", Icons.Rounded.Edit, height = 32.dp) { vm.editRegion(r.id) }
        Pill("Дубль", Icons.Rounded.ContentCopy, height = 32.dp) { vm.duplicateRegion(r.id) }
        Pill("Разрезать", Icons.Rounded.CallSplit, height = 32.dp) { vm.splitRegionAtPlayhead(r.id) }
        if (!r.isAudio) Pill(if (r.isLooped) "Без цикла" else "Зациклить", Icons.Rounded.Loop, height = 32.dp, selected = r.isLooped) { vm.toggleRegionLoop(r.id) }
        if (!r.isAudio) Box {
            Pill("Квантайз", Icons.Rounded.GridOn, height = 32.dp) { quant = true }
            DropdownMenu(quant, { quant = false }, containerColor = c.surfaceHigh) {
                listOf(com.yume.studio.model.Grid.QUARTER, com.yume.studio.model.Grid.EIGHTH, com.yume.studio.model.Grid.SIXTEENTH,
                    com.yume.studio.model.Grid.TRIPLET, com.yume.studio.model.Grid.THIRTYSECOND).forEach { g ->
                    DropdownMenuItem({ Text(g.title) }, { quant = false; vm.quantizeRegion(r.id, g) })
                }
                DropdownMenuItem({ Text("1/16 + свинг") }, { quant = false; vm.quantizeRegion(r.id, com.yume.studio.model.Grid.SIXTEENTH, 1f, 0.5f) })
            }
        }
        if (r.isAudio) {
            Text("Громкость", style = YumeText.tiny, color = c.textDim)
            com.yume.studio.ui.components.YumeSlider(r.gain / 2f, { vm.setRegionGain(r.id, it * 2f) }, Modifier.width(120.dp))
        }
        Spacer(Modifier.weight(1f))
        Pill(null, Icons.Rounded.Delete, height = 32.dp, color = c.danger.copy(alpha = 0.8f), contentColor = Color.White) { vm.deleteRegion(r.id) }
    }
}
