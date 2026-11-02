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
import com.yume.studio.ui.theme.Yume

/**
 * Мультитач-клавиатура: несколько пальцев одновременно, глиссандо (палец скользит
 * по клавишам), сила нажатия — по высоте касания (выше — тише, ниже — громче).
 */
@Composable
fun PianoKeys(
    startKey: Int,
    whiteCount: Int,
    onNoteOn: (key: Int, velocity: Float) -> Unit,
    onNoteOff: (key: Int) -> Unit,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true,
    velocityByPosition: Boolean = true,
    highlight: (Int) -> Boolean = { false },
    externallyPressed: Set<Int> = emptySet(),
) {
    val c = Yume.colors
    val pressed = remember { mutableStateMapOf<PointerId, Int>() }
    val on by rememberUpdatedState(onNoteOn)
    val off by rememberUpdatedState(onNoteOff)
    val startState by rememberUpdatedState(startKey)
    val countState by rememberUpdatedState(whiteCount)
    val velPos by rememberUpdatedState(velocityByPosition)

    // раскладка: список белых клавиш от startKey (startKey должен быть белой)
    fun whiteKeys(start: Int, count: Int): List<Int> {
        val out = ArrayList<Int>()
        var k = start
        while (out.size < count) { if (!NoteNames.isBlack(k)) out += k; k++ }
        return out
    }

    fun keyAt(x: Float, y: Float, w: Float, h: Float): Int? {
        val whites = whiteKeys(startState, countState)
        val kw = w / whites.size
        val idx = (x / kw).toInt().coerceIn(0, whites.size - 1)
        val white = whites[idx]
        if (y < h * 0.6f) {
            // проверяем чёрные слева/справа
            val local = x - idx * kw
            if (local > kw * 0.68f && NoteNames.isBlack(white + 1)) return white + 1
            if (local < kw * 0.32f && NoteNames.isBlack(white - 1) && idx > 0) return white - 1
        }
        return white
    }

    Canvas(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val ev = awaitPointerEvent()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    for (ch in ev.changes) {
                        val id = ch.id
                        if (ch.pressed) {
                            val k = keyAt(ch.position.x, ch.position.y, w, h) ?: continue
                            val prev = pressed[id]
                            if (prev != k) {
                                if (prev != null) off(prev)
                                val v = if (velPos) (0.35f + 0.65f * (ch.position.y / h)).coerceIn(0.2f, 1f) else 0.8f
                                on(k, v)
                                pressed[id] = k
                            }
                            ch.consume()
                        } else {
                            pressed.remove(id)?.let { off(it) }
                        }
                    }
                }
            }
        },
    ) {
        val whites = whiteKeys(startKey, whiteCount)
        val kw = size.width / whites.size
        val down = pressed.values.toSet() + externallyPressed
        val r = CornerRadius(8.dp.toPx())
        whites.forEachIndexed { i, k ->
            val x = i * kw
            val isDown = k in down
            val base = if (isDown) c.keyWhitePressed else c.keyWhite
            drawRoundRect(Color(0x33000000), Offset(x + 1.5f, 3f), Size(kw - 3f, size.height - 3f), r)
            drawRoundRect(
                Brush.verticalGradient(listOf(base, if (isDown) base else base.copy(red = base.red * 0.97f, green = base.green * 0.93f, blue = base.blue * 0.94f))),
                Offset(x + 1.5f, if (isDown) 2f else 0f), Size(kw - 3f, size.height - 4f), r,
            )
            if (highlight(k)) drawCircle(c.primary.copy(alpha = 0.55f), 4.dp.toPx(), Offset(x + kw / 2, size.height - 30.dp.toPx()))
            if (showLabels && k % 12 == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    NoteNames.name(k), x + 6.dp.toPx(), size.height - 10.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.argb(160, 120, 80, 100); textSize = 11.dp.toPx(); isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    },
                )
            }
        }
        whites.forEachIndexed { i, k ->
            if (NoteNames.isBlack(k + 1) && i < whites.size - 1) {
                val bk = k + 1
                val bw = kw * 0.6f
                val x = (i + 1) * kw - bw / 2
                val isDown = bk in down
                drawRoundRect(Color(0x55000000), Offset(x + 2f, 0f), Size(bw, size.height * 0.6f + 3f), CornerRadius(6.dp.toPx()))
                drawRoundRect(
                    Brush.verticalGradient(listOf(if (isDown) c.keyBlackPressed else c.keyBlack.copy(alpha = 1f), if (isDown) c.keyBlackPressed else Color(0xFF5A3A4A))),
                    Offset(x, 0f), Size(bw, size.height * 0.6f), CornerRadius(6.dp.toPx()),
                )
                if (highlight(bk)) drawCircle(c.primary, 3.dp.toPx(), Offset(x + bw / 2, size.height * 0.6f - 10.dp.toPx()))
            }
        }
    }
}
