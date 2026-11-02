package com.yume.studio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.yume.studio.audio.NoteNames
import kotlin.math.abs

private class Touch(val string: Int, var fret: Int, var key: Int, val startY: Float)

/**
 * Гриф: струны горизонтально (самая толстая — внизу), лады слева направо.
 * Касание — нота, скольжение вдоль струны — смена лада (легато),
 * вертикальный сдвиг пальца — бенд (до 2 полутонов).
 */
@Composable
fun FretboardView(
    tuning: List<Int>,
    frets: Int,
    onNoteOn: (key: Int, velocity: Float) -> Unit,
    onNoteOff: (key: Int) -> Unit,
    onBend: (semitones: Float) -> Unit,
    modifier: Modifier = Modifier,
    firstFret: Int = 0,
    highlight: (Int) -> Boolean = { false },
    showLabels: Boolean = true,
    woodColor: Color = Color(0xFF5B3A44),
    stringColor: Color = Color(0xFFF4E6D8),
    dotColor: Color = Color(0xFFF6C9D6),
    accent: Color = Color(0xFFF29BB5),
) {
    val touches = remember { mutableStateMapOf<PointerId, Touch>() }
    val on by rememberUpdatedState(onNoteOn)
    val off by rememberUpdatedState(onNoteOff)
    val bend by rememberUpdatedState(onBend)
    val tuningState by rememberUpdatedState(tuning)
    val firstFretState by rememberUpdatedState(firstFret)
    val fretsState by rememberUpdatedState(frets)

    Canvas(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val n = tuningState.size
                    val rowH = h / n
                    val fw = w / fretsState
                    for (ch in ev.changes) {
                        if (ch.pressed) {
                            val stringFromBottom = (n - 1 - (ch.position.y / rowH).toInt()).coerceIn(0, n - 1)
                            val fret = firstFretState + (ch.position.x / fw).toInt().coerceIn(0, fretsState - 1)
                            val t = touches[ch.id]
                            if (t == null) {
                                val key = tuningState[stringFromBottom] + fret
                                on(key, 0.85f)
                                touches[ch.id] = Touch(stringFromBottom, fret, key, ch.position.y)
                            } else {
                                if (fret != t.fret) {
                                    // слайд по ладам
                                    val key = tuningState[t.string] + fret
                                    off(t.key)
                                    on(key, 0.75f)
                                    t.fret = fret; t.key = key
                                }
                                val dy = abs(ch.position.y - t.startY)
                                bend((dy / rowH).coerceIn(0f, 2f))
                            }
                            ch.consume()
                        } else {
                            touches.remove(ch.id)?.let {
                                off(it.key)
                                if (touches.isEmpty()) bend(0f)
                            }
                        }
                    }
                }
            }
        },
    ) {
        val n = tuning.size
        val rowH = size.height / n
        val fw = size.width / frets
        drawRoundRect(Brush.verticalGradient(listOf(woodColor, woodColor.copy(red = woodColor.red * 0.85f))), Offset.Zero, size, CornerRadius(14.dp.toPx()))
        // лады
        for (f in 0..frets) {
            val x = f * fw
            val absFret = firstFret + f
            drawLine(if (absFret == 0) Color(0xFFFFF1E6) else Color(0xAAD9C2B0), Offset(x, 0f), Offset(x, size.height), if (absFret == 0) 5.dp.toPx() else 2.dp.toPx())
        }
        // точки-инкрустации (сердечки-кружочки)
        for (f in 0 until frets) {
            val absFret = firstFret + f + 1
            val cx = (f + 0.5f) * fw
            if (absFret % 12 == 0) {
                drawCircle(dotColor.copy(alpha = 0.5f), 5.dp.toPx(), Offset(cx, size.height * 0.3f))
                drawCircle(dotColor.copy(alpha = 0.5f), 5.dp.toPx(), Offset(cx, size.height * 0.7f))
            } else if (absFret in setOf(3, 5, 7, 9, 15, 17)) {
                drawCircle(dotColor.copy(alpha = 0.45f), 5.dp.toPx(), Offset(cx, size.height / 2))
            }
        }
        // подсветка лада гаммы
        for (s in 0 until n) {
            val y = size.height - (s + 0.5f) * rowH
            for (f in 0 until frets) {
                val key = tuning[s] + firstFret + f
                if (highlight(key)) drawCircle(accent.copy(alpha = 0.35f), minOf(rowH * 0.2f, 7.dp.toPx()), Offset((f + 0.5f) * fw, y))
            }
        }
        // струны
        for (s in 0 until n) {
            val y = size.height - (s + 0.5f) * rowH
            val thick = (1.2f + (n - 1 - s) * 0.45f).dp.toPx()
            drawLine(Color(0x55000000), Offset(0f, y + 1.5f), Offset(size.width, y + 1.5f), thick)
            drawLine(stringColor, Offset(0f, y), Offset(size.width, y), thick)
        }
        // нажатия
        for (t in touches.values) {
            val f = t.fret - firstFret
            val y = size.height - (t.string + 0.5f) * rowH
            drawCircle(accent, rowH * 0.32f, Offset((f + 0.5f) * fw, y))
            if (showLabels) drawContext.canvas.nativeCanvas.drawText(
                NoteNames.name(t.key), (f + 0.5f) * fw - 9.dp.toPx(), y + 4.dp.toPx(),
                android.graphics.Paint().apply { color = android.graphics.Color.WHITE; textSize = 10.dp.toPx(); isAntiAlias = true; typeface = android.graphics.Typeface.DEFAULT_BOLD },
            )
        }
    }
}
