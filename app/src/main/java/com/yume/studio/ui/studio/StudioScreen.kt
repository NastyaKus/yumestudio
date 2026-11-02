package com.yume.studio.ui.studio

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Circle
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.yume.studio.audio.InstrumentType
import com.yume.studio.audio.NoteNames
import com.yume.studio.data.ExportStore
import com.yume.studio.model.Timing
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.RoundIcon
import com.yume.studio.ui.components.Segmented
import com.yume.studio.ui.components.YumeSlider
import com.yume.studio.ui.editor.PianoRollView
import com.yume.studio.ui.fx.FxView
import com.yume.studio.ui.home.InstrumentCard
import com.yume.studio.ui.instruments.InstrumentHost
import com.yume.studio.ui.library.LibraryBrowser
import com.yume.studio.ui.mixer.MixerView
import com.yume.studio.ui.settings.SettingsContent
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeFont
import com.yume.studio.ui.theme.YumeText
import com.yume.studio.ui.tracks.TracksView
import kotlinx.coroutines.launch

@Composable
fun StudioScreen(vm: StudioViewModel, onExit: () -> Unit) {
    val c = Yume.colors
    val project by vm.project.collectAsState()
    val ui by vm.ui.collectAsState()
    val transport by vm.transport.collectAsState()
    val settings by vm.settings.collectAsState()
    val snack = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    var showTempo by remember { mutableStateOf(false) }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_STOP) vm.onBackground() }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let { launch { snack.showSnackbar(it) }; vm.consumeMessage() }
    }
    BackHandler(enabled = ui.mode != StudioMode.TRACKS) { vm.setMode(StudioMode.TRACKS) }
    val exit = { vm.saveNow(); vm.stop(); onExit() }
    BackHandler(enabled = ui.mode == StudioMode.TRACKS) { exit() }

    val p = project
    Box(Modifier.fillMaxSize().background(c.background)) {
        if (p == null) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = c.primary)
                Spacer(Modifier.height(12.dp))
                Text(if (ui.engineFailed) "Аудиодвижок не запустился :(" else "Настраиваю инструменты…", style = YumeText.body, color = c.textDim)
            }
            return@Box
        }
        val track = p.tracks.getOrNull(ui.selected)
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                vm = vm, ui = ui, transport = transport,
                position = Timing.formatPosition(transport.position, p.beatsPerBar),
                bpm = p.bpm, key = NoteNames.pitchClass(p.keyRoot) + if (p.minor) "m" else "",
                metronome = p.metronome, loop = p.loopOn, trackType = track?.type,
                onExit = exit, onSettings = { showSettings = true }, onTempo = { showTempo = true },
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(ui.mode, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "mode") { mode ->
                    when (mode) {
                        StudioMode.TRACKS -> TracksView(vm)
                        StudioMode.INSTRUMENT -> if (track != null) InstrumentHost(vm, ui.selected, track) else EmptyHint { vm.showTrackPicker(true) }
                        StudioMode.MIXER -> MixerView(vm)
                        StudioMode.FX -> if (track != null) FxView(vm, ui.selected, track) else EmptyHint { vm.showTrackPicker(true) }
                        StudioMode.EDITOR -> PianoRollView(vm)
                        StudioMode.LIBRARY -> Box(Modifier.padding(10.dp)) {
                            val app = LocalContext.current.applicationContext as com.yume.studio.YumeApp
                            val scope = androidx.compose.runtime.rememberCoroutineScope()
                            LibraryBrowser(
                                bpm = p.bpm,
                                favorites = settings.favorites,
                                onToggleFavorite = { id -> scope.launch { app.prefs.toggleFavorite(id) } },
                                onPreview = vm::previewLoop,
                                onStopPreview = vm::stopPreview,
                                onAdd = vm::addLoop,
                                addLabel = "В проект",
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snack, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp))
    }

    if (ui.showTrackPicker) TrackPickerDialog(vm, onDismiss = { vm.showTrackPicker(false) })
    if (showSettings) Dialog(onDismissRequest = { showSettings = false }) {
        Box(Modifier.clip(RoundedCornerShape(24.dp)).background(c.background).padding(14.dp).height(420.dp)) {
            SettingsContent()
        }
    }
    if (showTempo && p != null) TempoDialog(vm, p, onDismiss = { showTempo = false })
    ui.exportProgress?.let { prog ->
        Dialog(onDismissRequest = {}) {
            Column(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface).padding(24.dp).width(320.dp)) {
                Text("Свожу трек…", style = YumeText.title, color = c.text)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator({ prog }, Modifier.fillMaxWidth(), color = c.primary, trackColor = c.surfaceLow)
                Spacer(Modifier.height(8.dp))
                Text("${(prog * 100).toInt()}%", style = YumeText.small, color = c.textDim)
            }
        }
    }
    ui.lastExport?.let { f ->
        val ctx = LocalContext.current
        AlertDialog(
            onDismissRequest = vm::clearLastExport,
            title = { Text("Трек готов ♡") },
            text = { Text("${f.name}\nСохранён в «Музыка/YumeStudio» и в разделе «Экспорт».") },
            confirmButton = { TextButton({ ctx.startActivity(ExportStore.shareIntent(ctx, f)); vm.clearLastExport() }) { Text("Поделиться") } },
            dismissButton = { TextButton(vm::clearLastExport) { Text("Ок") } },
            containerColor = c.surface,
        )
    }
}

@Composable
private fun EmptyHint(onAdd: () -> Unit) {
    val c = Yume.colors
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Здесь пока нет дорожек", style = YumeText.title, color = c.text)
            Spacer(Modifier.height(10.dp))
            Pill("Добавить инструмент", selected = true, onClick = onAdd)
        }
    }
}

@Composable
private fun TopBar(
    vm: StudioViewModel,
    ui: StudioUi,
    transport: TransportState,
    position: String,
    bpm: Float,
    key: String,
    metronome: Boolean,
    loop: Boolean,
    trackType: InstrumentType?,
    onExit: () -> Unit,
    onSettings: () -> Unit,
    onTempo: () -> Unit,
) {
    val c = Yume.colors
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIcon(Icons.Rounded.KeyboardArrowDown, onExit, description = "Мои проекты")
        Spacer(Modifier.width(4.dp))
        Row(Modifier.clip(CircleShape).background(c.surface).padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
            ModeIcon(Icons.AutoMirrored.Rounded.ViewList, ui.mode == StudioMode.TRACKS) { vm.setMode(StudioMode.TRACKS) }
            ModeIcon(instrumentIcon(trackType), ui.mode == StudioMode.INSTRUMENT) { vm.setMode(StudioMode.INSTRUMENT) }
            ModeIcon(Icons.Rounded.Tune, ui.mode == StudioMode.MIXER) { vm.setMode(StudioMode.MIXER) }
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (ui.mode == StudioMode.FX) c.primary else Color.Transparent)
                    .clickable { vm.setMode(StudioMode.FX) },
                contentAlignment = Alignment.Center,
            ) { Text("FX", style = YumeText.small.copy(fontSize = 14.sp), color = if (ui.mode == StudioMode.FX) c.onPrimary else c.text) }
            ModeIcon(Icons.Rounded.LibraryMusic, ui.mode == StudioMode.LIBRARY) { vm.setMode(StudioMode.LIBRARY) }
        }
        Spacer(Modifier.weight(1f))
        // транспорт
        Row(Modifier.clip(CircleShape).background(c.surface).padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIcon(Icons.Rounded.SkipPrevious, vm::rewind, size = 38.dp, description = "В начало")
            RoundIcon(if (transport.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, vm::togglePlay, size = 42.dp, active = transport.playing, description = "Играть")
            RoundIcon(Icons.Rounded.Circle, vm::toggleRecord, size = 38.dp, tint = if (transport.recording) Color.White else c.danger,
                active = transport.recording, activeColor = c.danger, description = "Запись")
        }
        Spacer(Modifier.width(8.dp))
        // «LCD»
        Row(
            Modifier.clip(RoundedCornerShape(14.dp)).background(c.surfaceLow).clickable(onClick = onTempo).padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (transport.countIn) "отсчёт" else position, style = YumeText.title.copy(fontSize = 16.sp, fontFamily = YumeFont), color = if (transport.recording) c.danger else c.text)
                Text("такт", style = YumeText.tiny, color = c.textFaint)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${bpm.toInt()}", style = YumeText.title.copy(fontSize = 16.sp), color = c.text)
                Text("bpm", style = YumeText.tiny, color = c.textFaint)
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(key, style = YumeText.title.copy(fontSize = 16.sp), color = c.text)
                Text("тон.", style = YumeText.tiny, color = c.textFaint)
            }
        }
        Spacer(Modifier.weight(1f))
        RoundIcon(Icons.Rounded.Timer, vm::toggleMetronome, active = metronome, size = 38.dp, description = "Метроном")
        RoundIcon(Icons.Rounded.Loop, vm::toggleLoop, active = loop, size = 38.dp, activeColor = c.peach, description = "Цикл")
        RoundIcon(Icons.AutoMirrored.Rounded.Undo, vm::undo, size = 38.dp, tint = if (ui.canUndo) c.text else c.textFaint, description = "Отменить")
        RoundIcon(Icons.AutoMirrored.Rounded.Redo, vm::redo, size = 38.dp, tint = if (ui.canRedo) c.text else c.textFaint, description = "Повторить")
        RoundIcon(Icons.Rounded.FileDownload, vm::export, size = 38.dp, description = "Экспорт WAV")
        RoundIcon(Icons.Rounded.Settings, onSettings, size = 38.dp, description = "Настройки")
    }
}

@Composable
private fun ModeIcon(icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    RoundIcon(icon, onClick, active = selected, size = 38.dp)
}

fun instrumentIcon(t: InstrumentType?): ImageVector = when (t) {
    InstrumentType.KEYS, null -> Icons.Rounded.Piano
    InstrumentType.GUITAR -> Icons.Rounded.MusicNote
    InstrumentType.BASS -> Icons.Rounded.GraphicEq
    InstrumentType.DRUMS -> Icons.Rounded.Album
    InstrumentType.SYNTH -> Icons.Rounded.AutoAwesome
    InstrumentType.STRINGS -> Icons.Rounded.LibraryMusic
    InstrumentType.AUDIO -> Icons.Rounded.Mic
}

@Composable
private fun TrackPickerDialog(vm: StudioViewModel, onDismiss: () -> Unit) {
    val c = Yume.colors
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importAudio(uri)
        onDismiss()
    }
    Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.padding(24.dp).clip(RoundedCornerShape(26.dp)).background(c.background).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Выбери инструмент", style = YumeText.title, color = c.text, modifier = Modifier.weight(1f))
                Pill("Импорт аудио", Icons.Rounded.FileUpload) { pick.launch(arrayOf("audio/*")) }
            }
            Spacer(Modifier.height(10.dp))
            LazyVerticalGrid(GridCells.Adaptive(190.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(InstrumentType.entries) { t -> InstrumentCard(t) { vm.addTrack(t) } }
            }
        }
    }
}

@Composable
private fun TempoDialog(vm: StudioViewModel, p: com.yume.studio.model.Project, onDismiss: () -> Unit) {
    val c = Yume.colors
    var taps by remember { mutableStateOf(listOf<Long>()) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(c.surface).padding(20.dp).width(420.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Темп и тональность", style = YumeText.title, color = c.text)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.bpm.toInt()} BPM", style = YumeText.title, color = c.primary, modifier = Modifier.width(100.dp))
                Pill("−") { vm.setBpm(p.bpm - 1) }
                Spacer(Modifier.width(6.dp))
                YumeSlider((p.bpm - 40f) / 200f, { vm.setBpm(40f + it * 200f) }, Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                Pill("+") { vm.setBpm(p.bpm + 1) }
                Spacer(Modifier.width(6.dp))
                Pill("Тап") {
                    val now = System.currentTimeMillis()
                    taps = (taps.filter { now - it < 3000 } + now).takeLast(6)
                    if (taps.size >= 3) {
                        val avg = (taps.last() - taps.first()) / (taps.size - 1).toFloat()
                        vm.setBpm(60000f / avg)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Размер", style = YumeText.body, color = c.textDim, modifier = Modifier.width(100.dp))
                Segmented(listOf(3, 4, 5, 6), p.beatsPerBar, { "$it/4" }, vm::setBeatsPerBar)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Тоника", style = YumeText.body, color = c.textDim, modifier = Modifier.width(100.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    (0 until 12).forEach { pc ->
                        val sel = pc == p.keyRoot
                        Box(
                            Modifier.size(24.dp).clip(CircleShape).background(if (sel) c.primary else c.surfaceLow).clickable { vm.setKey(pc, p.minor) },
                            contentAlignment = Alignment.Center,
                        ) { Text(NoteNames.pitchClass(pc), style = YumeText.tiny, color = if (sel) c.onPrimary else c.textDim) }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Лад", style = YumeText.body, color = c.textDim, modifier = Modifier.width(100.dp))
                Segmented(listOf(false, true), p.minor, { if (it) "Минор" else "Мажор" }, { vm.setKey(p.keyRoot, it) })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Отсчёт", style = YumeText.body, color = c.textDim, modifier = Modifier.width(100.dp))
                Segmented(listOf(0, 1, 2), p.countInBars, { if (it == 0) "Нет" else "$it такт" }, vm::setCountIn)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Длина песни", style = YumeText.body, color = c.textDim, modifier = Modifier.width(100.dp))
                Pill("−4") { vm.setLengthBars(p.lengthBars - 4) }
                Text("  ${p.lengthBars} тактов  ", style = YumeText.body, color = c.text)
                Pill("+4") { vm.setLengthBars(p.lengthBars + 4) }
            }
            Text("Регионы занимают ${Timing.formatTime(p.endTick.toDouble(), p.bpm)}", style = YumeText.small, color = c.textFaint)
        }
    }
}
