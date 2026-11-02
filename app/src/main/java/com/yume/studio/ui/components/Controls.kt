package com.yume.studio.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.yume.studio.ui.theme.Yume
import com.yume.studio.ui.theme.YumeText
import kotlin.math.cos
import kotlin.math.sin

/** Круглая «пилюля»-кнопка с иконкой и/или текстом. */
@Composable
fun Pill(
    text: String? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    color: Color? = null,
    contentColor: Color? = null,
    height: Dp = 36.dp,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = Yume.colors
    val bg by animateColorAsState(color ?: if (selected) c.primary else c.surfaceHigh, label = "pill")
    val fg = contentColor ?: if (selected) c.onPrimary else c.text
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(bg.copy(alpha = if (enabled) bg.alpha else 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (text == null) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(height * 0.55f))
        if (icon != null && text != null) Spacer(Modifier.width(6.dp))
        if (text != null) Text(text, color = fg, style = YumeText.small, maxLines = 1)
    }
}

/** Круглая кнопка-иконка (транспорт, тулбар). */
@Composable
fun RoundIcon(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    tint: Color? = null,
    activeColor: Color? = null,
    size: Dp = 40.dp,
    description: String? = null,
) {
    val c = Yume.colors
    val bg by animateColorAsState(if (active) (activeColor ?: c.primary) else Color.Transparent, label = "ri")
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = tint ?: if (active) c.onPrimary else c.text, modifier = Modifier.size(size * 0.56f))
    }
}

/** Сегментированный переключатель из «пилюль». */
@Composable
fun <T> Segmented(
    items: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 32.dp,
) {
    val c = Yume.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(c.surfaceLow)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item ->
            val sel = item == selected
            val bg by animateColorAsState(if (sel) c.primary else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .height(height)
                    .clip(CircleShape)
                    .background(bg)
                    .clickable { onSelect(item) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label(item), style = YumeText.small, color = if (sel) c.onPrimary else c.textDim, maxLines = 1)
            }
        }
    }
}

/** Переключатель «‹ Пианино ›» поверх арта. */
@Composable
fun PresetSwitcher(name: String, onPrev: () -> Unit, onNext: () -> Unit, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
            .background(Color(0xE63A2631))
            .border(1.dp, Color(0x44FFFFFF), CircleShape)
            .height(38.dp)
            .widthIn(min = 180.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clickable(onClick = onPrev), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Предыдущий", tint = Color.White)
        }
        Text(
            name,
            color = Color.White,
            style = YumeText.title.copy(fontSize = YumeText.title.fontSize * 0.9f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f, fill = false).widthIn(min = 100.dp).clickable(onClick = onClick).padding(horizontal = 6.dp),
        )
        Box(Modifier.size(38.dp).clickable(onClick = onNext), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Следующий", tint = Color.White)
        }
    }
}

/** Крутилка: вертикальный drag меняет значение, двойной тап — сброс. */
@Composable
fun Knob(
    value: Float,
    onChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    default: Float = 0.5f,
    size: Dp = 48.dp,
    color: Color? = null,
    valueText: String? = null,
    trackColor: Color? = null,
    labelColor: Color? = null,
) {
    val c = Yume.colors
    val accent = color ?: c.primary
    val current by rememberUpdatedState(value)
    val onChangeState by rememberUpdatedState(onChange)
    var dragAcc by remember { mutableFloatStateOf(0f) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectTapGestures(onDoubleTap = { onChangeState(default) })
                }
                .pointerInput(Unit) {
                    detectDragGestures(onDragStart = { dragAcc = current }) { change, drag ->
                        change.consume()
                        dragAcc = (dragAcc + (-drag.y + drag.x * 0.5f) / (this.size.height * 3.2f)).coerceIn(0f, 1f)
                        onChangeState(dragAcc)
                    }
                },
        ) {
            val r = this.size.minDimension / 2f
            val stroke = r * 0.16f
            val start = 135f
            val sweep = 270f
            drawArc(trackColor ?: c.surfaceLow, start, sweep, false, Offset(stroke, stroke), Size(r * 2 - stroke * 2, r * 2 - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(accent, start, sweep * value, false, Offset(stroke, stroke), Size(r * 2 - stroke * 2, r * 2 - stroke * 2), style = Stroke(stroke, cap = StrokeCap.Round))
            // корпус
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4F7), Color(0xFFE9C9D5)), center = Offset(r * 0.8f, r * 0.7f), radius = r), r * 0.62f, Offset(r, r))
            val a = Math.toRadians((start + sweep * value).toDouble())
            val p1 = Offset(r + (r * 0.18f * cos(a)).toFloat(), r + (r * 0.18f * sin(a)).toFloat())
            val p2 = Offset(r + (r * 0.5f * cos(a)).toFloat(), r + (r * 0.5f * sin(a)).toFloat())
            drawLine(Color(0xFF8A4A63), p1, p2, strokeWidth = r * 0.11f, cap = StrokeCap.Round)
        }
        Text(
            valueText ?: label, style = YumeText.tiny, color = labelColor ?: c.textDim, maxLines = 1, softWrap = false,
            textAlign = TextAlign.Center, modifier = Modifier.requiredWidth(size + 22.dp),
        )
    }
}

/** Вертикальный фейдер (микшер). */
@Composable
fun Fader(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    color: Color? = null,
    default: Float = 0.8f,
) {
    val c = Yume.colors
    val onChangeState by rememberUpdatedState(onChange)
    val current by rememberUpdatedState(value)
    var acc by remember { mutableFloatStateOf(0f) }
    Canvas(
        modifier
            .width(34.dp)
            .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onChangeState(default) }) }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { acc = current }) { ch, d ->
                    ch.consume()
                    acc = (acc - d.y / (size.height * 0.85f)).coerceIn(0f, 1f)
                    onChangeState(acc)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val pad = 14.dp.toPx()
        val trackW = 6.dp.toPx()
        val usable = h - pad * 2
        drawRoundRect(c.surfaceLow, Offset(w / 2 - trackW / 2, pad), Size(trackW, usable), CornerRadius(trackW))
        val y = pad + usable * (1f - value)
        drawRoundRect(color ?: c.primary, Offset(w / 2 - trackW / 2, y), Size(trackW, h - pad - y), CornerRadius(trackW))
        // засечки
        for (i in 0..10) {
            val yy = pad + usable * i / 10f
            drawLine(c.gridLineStrong, Offset(w * 0.08f, yy), Offset(w * 0.22f, yy), 1.dp.toPx())
        }
        val capH = 26.dp.toPx()
        val capW = w * 0.86f
        drawRoundRect(Color(0x55000000), Offset(w / 2 - capW / 2, y - capH / 2 + 2.dp.toPx()), Size(capW, capH), CornerRadius(8.dp.toPx()))
        drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFFFF4F7), Color(0xFFF2C8D6)), startY = y - capH / 2, endY = y + capH / 2),
            Offset(w / 2 - capW / 2, y - capH / 2), Size(capW, capH), CornerRadius(8.dp.toPx()))
        drawLine(color ?: c.primaryDeep, Offset(w / 2 - capW * 0.3f, y), Offset(w / 2 + capW * 0.3f, y), 2.dp.toPx())
    }
}

/** Индикатор уровня (стерео), значения — пиковые амплитуды 0..1+. */
@Composable
fun LevelMeter(left: Float, right: Float, modifier: Modifier = Modifier) {
    val c = Yume.colors
    val l by animateFloatAsState(toMeter(left), label = "l")
    val r by animateFloatAsState(toMeter(right), label = "r")
    Canvas(modifier.width(12.dp)) {
        val bw = size.width / 2 - 1.dp.toPx()
        listOf(l to 0f, r to bw + 2.dp.toPx()).forEach { (v, x) ->
            drawRoundRect(c.surfaceLow, Offset(x, 0f), Size(bw, size.height), CornerRadius(3f))
            val h = size.height * v
            drawRoundRect(
                Brush.verticalGradient(listOf(Color(0xFFFF7A9C), Color(0xFFFFD27A), Color(0xFF8FE3B0)), 0f, size.height),
                Offset(x, size.height - h), Size(bw, h), CornerRadius(3f),
            )
        }
    }
}

/** Пик → позиция метра (шкала −48…+3 дБ). */
fun toMeter(peak: Float): Float {
    if (peak <= 1e-5f) return 0f
    val db = 20f * kotlin.math.log10(peak)
    return ((db + 48f) / 51f).coerceIn(0f, 1f)
}

/** Пастельный тумблер (как на экране вокала). */
@Composable
fun YumeToggle(checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    val c = Yume.colors
    val bg by animateColorAsState(if (checked) (color ?: c.primary) else c.surfaceLow, label = "tg")
    val x by animateFloatAsState(if (checked) 1f else 0f, label = "tx")
    Box(
        modifier
            .size(46.dp, 26.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!checked) }
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .padding(start = (20.dp * x))
                .size(20.dp)
                .shadow(2.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/** Горизонтальный ползунок в стиле приложения. */
@Composable
fun YumeSlider(value: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier, color: Color? = null) {
    val c = Yume.colors
    val onChangeState by rememberUpdatedState(onChange)
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                detectTapGestures { onChangeState((it.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(Unit) {
                detectDragGestures { ch, _ ->
                    ch.consume()
                    onChangeState((ch.position.x / size.width).coerceIn(0f, 1f))
                }
            },
    ) {
        val h = 6.dp.toPx()
        val y = size.height / 2
        drawRoundRect(c.surfaceLow, Offset(0f, y - h / 2), Size(size.width, h), CornerRadius(h))
        drawRoundRect(color ?: c.primary, Offset(0f, y - h / 2), Size(size.width * value, h), CornerRadius(h))
        drawCircle(Color.White, 10.dp.toPx(), Offset(size.width * value, y))
        drawCircle(color ?: c.primary, 5.dp.toPx(), Offset(size.width * value, y))
    }
}

/** Карточка-панель со скруглением. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    color: Color? = null,
    padding: PaddingValues = PaddingValues(12.dp),
    content: @Composable () -> Unit,
) {
    val c = Yume.colors
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color ?: c.surface)
            .padding(padding),
    ) { content() }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = YumeText.title, color = Yume.colors.text, modifier = modifier)
}

@Composable
fun RowScope.Weight(w: Float) = Spacer(Modifier.weight(w))

@Composable
fun VDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxHeight().width(1.dp).background(Yume.colors.gridLine))
}

@Composable
fun HDivider(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Yume.colors.gridLine))
}
