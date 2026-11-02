package com.yume.studio.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yume.studio.R
import com.yume.studio.YumeApp
import com.yume.studio.audio.InstrumentType
import com.yume.studio.data.ExportStore
import com.yume.studio.data.Settings
import com.yume.studio.model.ProjectMeta
import com.yume.studio.ui.components.CatPawIcon
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.components.SakuraPetals
import com.yume.studio.ui.library.LibraryBrowser
import com.yume.studio.ui.settings.SettingsContent
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class HomeSection(val title: String, val icon: ImageVector) {
    PROJECTS("Проекты", Icons.Rounded.Home),
    SOUNDS("Звуки", Icons.Rounded.MusicNote),
    INSTRUMENTS("Инструменты", Icons.Rounded.Piano),
    EXPORTS("Экспорт", Icons.Rounded.Inventory2),
    SETTINGS("Настройки", Icons.Rounded.Settings),
}

@Composable
fun HomeScreen(onOpenProject: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as YumeApp
    val c = Yume.colors
    val projects by app.repository.projects.collectAsState()
    val settings by app.prefs.settings.collectAsState(initial = Settings())
    val scope = rememberCoroutineScope()
    var section by remember { mutableStateOf(HomeSection.PROJECTS) }

    fun create(type: InstrumentType? = null) = scope.launch {
        val p = app.repository.create(firstTrack = type)
        onOpenProject(p.id)
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        if (settings.sakuraPetals) SakuraPetals(Modifier.fillMaxSize(), color = c.primary.copy(alpha = 0.35f))
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp)) {
            // ── сайдбар ──
            Column(Modifier.width(200.dp).fillMaxHeight().padding(end = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 16.dp)) {
                    Text("Yume Studio", style = YumeText.logo.copy(fontSize = 24.sp), color = c.text)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.MusicNote, null, tint = c.primary, modifier = Modifier.size(22.dp))
                }
                HomeSection.entries.forEach { s ->
                    val sel = s == section
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Brush.horizontalGradient(listOf(c.primary, c.primaryDeep)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                            .clickable {
                                if (section == HomeSection.SOUNDS) app.engine.stopPreview()
                                section = s
                            }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(s.icon, null, tint = if (sel) Color.White else c.textDim, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(s.title, style = YumeText.body.copy(fontSize = 15.sp), color = if (sel) Color.White else c.text)
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    CatPawIcon(c.primary.copy(alpha = 0.6f), 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("mobile music creation", style = YumeText.small, color = c.textFaint)
                }
            }
            // ── контент ──
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(26.dp)).background(c.surface.copy(alpha = 0.92f)).padding(14.dp)) {
                AnimatedContent(section, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "home") { s ->
                    when (s) {
                        HomeSection.PROJECTS -> ProjectsPane(projects, onOpenProject, onNew = { create() })
                        HomeSection.SOUNDS -> SoundsPane(settings, onCreateWithLoop = { loop ->
                            scope.launch {
                                val p = app.repository.create(name = loop.name)
                                // луп добавится в студии через кнопку библиотеки; здесь — сразу дорожка с паттерном
                                val r = com.yume.studio.model.Region(System.nanoTime(), 0, loop.lengthTicks * maxOf(1, 4 / loop.bars), loop.lengthTicks, loop.notes, name = loop.name)
                                val t = com.yume.studio.model.Track(
                                    id = System.nanoTime(), name = loop.name, type = loop.type, presetIndex = loop.presetIndex,
                                    articulation = loop.articulation,
                                    effects = com.yume.studio.audio.EngineBridge.defaultEffects(loop.type),
                                    synthParams = if (loop.type == InstrumentType.SYNTH) com.yume.studio.audio.SynthPresets.all[loop.presetIndex].params else com.yume.studio.audio.SynthPresets.all.first().params,
                                    regions = listOf(r),
                                )
                                app.repository.save(p.copy(tracks = listOf(t)))
                                app.engine.stopPreview()
                                onOpenProject(p.id)
                            }
                        })
                        HomeSection.INSTRUMENTS -> InstrumentsPane { create(it) }
                        HomeSection.EXPORTS -> ExportsPane(onOpenProject)
                        HomeSection.SETTINGS -> SettingsContent(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsPane(projects: List<ProjectMeta>, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val c = Yume.colors
    val app = LocalContext.current.applicationContext as YumeApp
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<ProjectMeta?>(null) }
    var deleting by remember { mutableStateOf<ProjectMeta?>(null) }
    val context = LocalContext.current
    var archiveFor by remember { mutableStateOf<String?>(null) }
    val saveArchive = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val id = archiveFor ?: return@rememberLauncherForActivityResult
        if (uri != null) scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { app.repository.exportArchive(id, it) }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val bannerH = (maxHeight * 0.56f).coerceAtMost(260.dp)
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(bannerH).clip(RoundedCornerShape(22.dp))) {
                Image(painterResource(R.drawable.art_home), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, c.surface.copy(alpha = 0.55f)))))
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 18.dp)
                        .shadow(12.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(listOf(Color(0xFFE88AA5), Color(0xFFCF6F8C))))
                        .clickable(onClick = onNew)
                        .padding(horizontal = 28.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Новый проект", style = YumeText.title, color = Color.White)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Недавние проекты", style = YumeText.title, color = c.text, modifier = Modifier.padding(start = 4.dp))
            Spacer(Modifier.height(8.dp))
            if (projects.isEmpty()) {
                Text("Пока ни одного трека. Нажми «Новый проект» — и поехали ♡", style = YumeText.body, color = c.textDim, modifier = Modifier.padding(4.dp))
            }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(projects, key = { it.id }) { p ->
                    ProjectCard(
                        p, onOpen = { onOpen(p.id) },
                        onRename = { renaming = p },
                        onDuplicate = { scope.launch { app.repository.duplicate(p.id) } },
                        onArchive = { archiveFor = p.id; saveArchive.launch("${p.name}.yume") },
                        onDelete = { deleting = p },
                    )
                }
            }
        }
    }

    renaming?.let { p ->
        var name by remember(p.id) { mutableStateOf(p.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Переименовать") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true) },
            confirmButton = { TextButton({ scope.launch { app.repository.rename(p.id, name) }; renaming = null }) { Text("Сохранить") } },
            dismissButton = { TextButton({ renaming = null }) { Text("Отмена") } },
            containerColor = c.surface,
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Удалить «${p.name}»?") },
            text = { Text("Проект и все записи в нём удалятся без возможности восстановления.") },
            confirmButton = { TextButton({ scope.launch { app.repository.delete(p.id) }; deleting = null }) { Text("Удалить", color = c.danger) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Оставить") } },
            containerColor = c.surface,
        )
    }
}

private fun formatDate(ts: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ts }
    val time = SimpleDateFormat("HH:mm", Locale("ru")).format(Date(ts))
    return when {
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) && now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "Сегодня, $time"
        now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR) == 1 && now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> "Вчера, $time"
        else -> SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(ts))
    }
}

@Composable
private fun ProjectCard(
    p: ProjectMeta,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = Yume.colors
    var menu by remember { mutableStateOf(false) }
    val art = p.trackTypes.firstOrNull()?.art ?: R.drawable.art_library
    Column(Modifier.width(190.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2.1f).clip(RoundedCornerShape(16.dp))) {
            Image(painterResource(art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Text("${p.bpm.toInt()} BPM", style = YumeText.tiny, color = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).clip(CircleShape).background(Color(0x99301B28)).padding(horizontal = 8.dp, vertical = 2.dp))
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.name, style = YumeText.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatDate(p.updatedAt), style = YumeText.tiny, color = c.textFaint)
            }
            Box {
                Icon(Icons.Rounded.MoreHoriz, "Ещё", tint = c.textDim, modifier = Modifier.size(32.dp).clip(CircleShape).clickable { menu = true }.padding(4.dp))
                DropdownMenu(menu, { menu = false }, containerColor = c.surfaceHigh) {
                    DropdownMenuItem({ Text("Переименовать") }, { menu = false; onRename() })
                    DropdownMenuItem({ Text("Дублировать") }, { menu = false; onDuplicate() })
                    DropdownMenuItem({ Text("Сохранить как .yume") }, { menu = false; onArchive() })
                    DropdownMenuItem({ Text("Удалить", color = c.danger) }, { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun InstrumentsPane(onPick: (InstrumentType) -> Unit) {
    val c = Yume.colors
    Column(Modifier.fillMaxSize()) {
        Text("С чего начнём?", style = YumeText.title, color = c.text)
        Text("Выбери инструмент — создадим проект и сразу откроем его", style = YumeText.body, color = c.textDim)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(GridCells.Adaptive(200.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(InstrumentType.entries) { t -> InstrumentCard(t) { onPick(t) } }
        }
    }
}

@Composable
fun InstrumentCard(t: InstrumentType, onClick: () -> Unit) {
    val c = Yume.colors
    Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick)) {
        Image(painterResource(t.art), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC2B1F27)))))
        Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
            Text(t.title, style = YumeText.title, color = Color.White)
            Text("${t.presets.size} звуков", style = YumeText.tiny, color = Color(0xFFF7D9E3))
        }
        Box(Modifier.align(Alignment.TopEnd).padding(8.dp).size(10.dp).clip(CircleShape).background(c.track(t.colorIndex)))
    }
}

@Composable
private fun SoundsPane(settings: Settings, onCreateWithLoop: (com.yume.studio.data.Loop) -> Unit) {
    val app = LocalContext.current.applicationContext as YumeApp
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        Text("Библиотека звуков", style = YumeText.title, color = Yume.colors.text)
        Spacer(Modifier.height(8.dp))
        LibraryBrowser(
            bpm = 100f,
            favorites = settings.favorites,
            onToggleFavorite = { id -> scope.launch { app.prefs.toggleFavorite(id) } },
            onPreview = { app.engine.previewLoop(it, 100f, 0) },
            onStopPreview = { app.engine.stopPreview() },
            onAdd = onCreateWithLoop,
            addLabel = "Трек",
        )
    }
}

@Composable
private fun ExportsPane(onOpenProject: (String) -> Unit) {
    val app = LocalContext.current.applicationContext as YumeApp
    val context = LocalContext.current
    val c = Yume.colors
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val files = remember(refresh) { ExportStore.listExports(app.repository.exportsDir()) }
    val importProject = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val p = context.contentResolver.openInputStream(uri)?.use { app.repository.importArchive(it) }
            if (p != null) onOpenProject(p.id)
        }
    }
    LaunchedEffect(Unit) { refresh++ }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Готовые треки", style = YumeText.title, color = c.text)
                Text("WAV-файлы также лежат в «Музыка/YumeStudio»", style = YumeText.small, color = c.textDim)
            }
            Pill("Открыть .yume", Icons.Rounded.FileUpload) { importProject.launch(arrayOf("*/*")) }
        }
        Spacer(Modifier.height(10.dp))
        if (files.isEmpty()) {
            Text("Здесь появятся треки после экспорта (кнопка ⤓ в студии).", style = YumeText.body, color = c.textDim)
        }
        files.forEach { f -> ExportRow(f, onShare = { context.startActivity(ExportStore.shareIntent(context, f)) }, onDelete = { f.delete(); refresh++ }) }
    }
}

@Composable
private fun ExportRow(f: File, onShare: () -> Unit, onDelete: () -> Unit) {
    val c = Yume.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(c.surfaceHigh).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Download, null, tint = c.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(f.name, style = YumeText.body, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("%.1f МБ · %s".format(f.length() / 1_048_576f, formatDate(f.lastModified())), style = YumeText.tiny, color = c.textFaint)
        }
        Icon(Icons.Rounded.Share, "Поделиться", tint = c.text, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onShare).padding(7.dp))
        Icon(Icons.Rounded.Delete, "Удалить", tint = c.textDim, modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDelete).padding(7.dp))
    }
}
