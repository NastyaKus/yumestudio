package com.yume.studio.ui.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.yume.studio.R
import com.yume.studio.data.Loop
import com.yume.studio.data.LoopCategory
import com.yume.studio.data.LoopLibrary
import com.yume.studio.ui.components.Pill
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.abs
import kotlin.math.sin

private fun icon(c: LoopCategory?): ImageVector = when (c) {
    null -> Icons.Rounded.LibraryMusic
    LoopCategory.SYNTH -> Icons.Rounded.AutoAwesome
    LoopCategory.DRUMS -> Icons.Rounded.GraphicEq
    LoopCategory.BASS -> Icons.Rounded.Waves
    LoopCategory.GUITAR -> Icons.Rounded.MusicNote
    LoopCategory.KEYS -> Icons.Rounded.Piano
    LoopCategory.STRINGS -> Icons.Rounded.MusicNote
    LoopCategory.VOCAL -> Icons.Rounded.RecordVoiceOver
}

/**
 * «Библиотека звуков» как на макете: слева категории, справа список лупов с превью,
 * мини-волной, длительностью и избранным.
 */
@Composable
fun LibraryBrowser(
    bpm: Float,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onPreview: (Loop) -> Unit,
    onStopPreview: () -> Unit,
    onAdd: (Loop) -> Unit,
    addLabel: String,
    modifier: Modifier = Modifier,
    showArt: Boolean = true,
) {
    val c = Yume.colors
    var category by remember { mutableStateOf<LoopCategory?>(null) }
    var favOnly by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { onStopPreview() } }

    val loops = remember(category, favOnly, query, favorites) {
        LoopLibrary.byCategory(category)
            .filter { !favOnly || it.id in favorites }
            .filter { query.isBlank() || it.name.contains(query, true) || it.tags.contains(query, true) }
    }

    Row(modifier.fillMaxSize()) {
        if (showArt) {
            Box(Modifier.width(120.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp))) {
                Image(painterResource(R.drawable.art_library), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x88301B28)))))
            }
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.width(150.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CategoryItem("Все звуки", icon(null), category == null && !favOnly) { category = null; favOnly = false }
            LoopCategory.entries.forEach { cat ->
                CategoryItem(cat.title, icon(cat), category == cat && !favOnly) { category = cat; favOnly = false }
            }
            CategoryItem("Избранное", Icons.Rounded.Favorite, favOnly) { favOnly = true; category = null }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(
                Modifier.fillMaxWidth().height(40.dp).clip(CircleShape).background(c.surfaceLow).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = c.textDim, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text("Поиск звуков…", style = YumeText.body, color = c.textFaint)
                    BasicTextField(query, { query = it }, singleLine = true, textStyle = YumeText.body.copy(color = c.text),
                        cursorBrush = SolidColor(c.primary), modifier = Modifier.fillMaxWidth())
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                items(loops, key = { it.id }) { loop ->
                    LoopRow(
                        loop = loop,
                        bpm = bpm,
                        playing = playing == loop.id,
                        favorite = loop.id in favorites,
                        addLabel = addLabel,
                        onPlay = {
                            if (playing == loop.id) { playing = null; onStopPreview() }
                            else { playing = loop.id; onPreview(loop) }
                        },
                        onFav = { onToggleFavorite(loop.id) },
                        onAdd = { playing = null; onAdd(loop) },
                    )
                }
                if (loops.isEmpty()) item {
                    Text("Тут пока пусто — отметь лупы сердечком ♡", style = YumeText.body, color = c.textDim, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(title: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val c = Yume.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) c.primary.copy(alpha = 0.25f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) c.primary else c.textDim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = YumeText.small, color = if (selected) c.text else c.textDim)
    }
}

@Composable
private fun LoopRow(
    loop: Loop,
    bpm: Float,
    playing: Boolean,
    favorite: Boolean,
    addLabel: String,
    onPlay: () -> Unit,
    onFav: () -> Unit,
    onAdd: () -> Unit,
) {
    val c = Yume.colors
    val color = c.track(loop.category.ordinal)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (playing) c.surfaceHigh else c.surface)
            .clickable(onClick = onPlay)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (playing) c.primary else c.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(if (playing) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, null, tint = if (playing) c.onPrimary else c.text, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(150.dp)) {
            Text(loop.name, style = YumeText.body, color = c.text, maxLines = 1)
            Text("${loop.category.title} · ${loop.bars} такт.", style = YumeText.tiny, color = c.textFaint, maxLines = 1)
        }
        // мини-«волна» по нотам лупа
        Canvas(Modifier.weight(1f).height(26.dp).padding(horizontal = 8.dp)) {
            val n = 48
            val len = loop.lengthTicks.toFloat()
            val density = FloatArray(n)
            loop.notes.forEach { note ->
                val i = ((note.tick / len) * n).toInt().coerceIn(0, n - 1)
                density[i] += note.velocity / 127f
            }
            val w = size.width / n
            for (i in 0 until n) {
                val d = (density[i] * 0.6f + 0.15f + 0.1f * abs(sin(i * 1.7f))).coerceAtMost(1f)
                val h = size.height * d
                drawLine(color, Offset(i * w + w / 2, size.height / 2 - h / 2), Offset(i * w + w / 2, size.height / 2 + h / 2), strokeWidth = w * 0.55f)
            }
        }
        Text("0:%02d".format(loop.seconds(bpm).toInt().coerceAtMost(59)), style = YumeText.small, color = c.textDim)
        Spacer(Modifier.width(8.dp))
        Icon(
            if (favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Избранное",
            tint = if (favorite) c.primary else c.textDim,
            modifier = Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onFav).padding(6.dp),
        )
        Pill(addLabel, Icons.Rounded.Add, height = 30.dp, onClick = onAdd)
    }
}
