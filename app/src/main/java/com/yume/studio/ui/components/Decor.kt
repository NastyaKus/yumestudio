package com.yume.studio.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Кошачья лапка: подушечка + 4 пальчика. */
fun DrawScope.catPaw(center: Offset, r: Float, color: Color) {
    drawOval(color, topLeft = Offset(center.x - r * 0.62f, center.y - r * 0.1f), size = Size(r * 1.24f, r * 1.0f))
    val toes = listOf(-0.62f to -0.42f, -0.22f to -0.72f, 0.22f to -0.72f, 0.62f to -0.42f)
    for ((dx, dy) in toes) drawCircle(color, r * 0.26f, Offset(center.x + dx * r, center.y + dy * r))
}

/** Мордочка котика (для пэдов, педалей, пустых состояний). */
fun DrawScope.catFace(center: Offset, r: Float, color: Color, line: Color, sleepy: Boolean = true) {
    val ear = Path().apply {
        moveTo(center.x - r * 0.85f, center.y - r * 0.25f)
        lineTo(center.x - r * 0.7f, center.y - r * 1.05f)
        lineTo(center.x - r * 0.2f, center.y - r * 0.7f)
        close()
        moveTo(center.x + r * 0.85f, center.y - r * 0.25f)
        lineTo(center.x + r * 0.7f, center.y - r * 1.05f)
        lineTo(center.x + r * 0.2f, center.y - r * 0.7f)
        close()
    }
    drawPath(ear, color)
    drawOval(color, Offset(center.x - r, center.y - r * 0.75f), Size(r * 2f, r * 1.6f))
    val sw = r * 0.09f
    if (sleepy) {
        drawArc(line, 20f, 140f, false, Offset(center.x - r * 0.55f, center.y - r * 0.25f), Size(r * 0.35f, r * 0.25f), style = Stroke(sw))
        drawArc(line, 20f, 140f, false, Offset(center.x + r * 0.2f, center.y - r * 0.25f), Size(r * 0.35f, r * 0.25f), style = Stroke(sw))
    } else {
        drawCircle(line, r * 0.1f, Offset(center.x - r * 0.38f, center.y - r * 0.08f))
        drawCircle(line, r * 0.1f, Offset(center.x + r * 0.38f, center.y - r * 0.08f))
    }
    // ротик «ω»
    drawArc(line, 0f, 180f, false, Offset(center.x - r * 0.2f, center.y + r * 0.12f), Size(r * 0.2f, r * 0.18f), style = Stroke(sw))
    drawArc(line, 0f, 180f, false, Offset(center.x, center.y + r * 0.12f), Size(r * 0.2f, r * 0.18f), style = Stroke(sw))
    drawCircle(Color(0x66FF8FB1), r * 0.14f, Offset(center.x - r * 0.62f, center.y + r * 0.18f))
    drawCircle(Color(0x66FF8FB1), r * 0.14f, Offset(center.x + r * 0.62f, center.y + r * 0.18f))
}

fun DrawScope.star(center: Offset, r: Float, color: Color, inner: Float = 0.45f) {
    val p = Path()
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * inner
        val a = -PI / 2 + i * PI / 5
        val x = center.x + (rr * cos(a)).toFloat()
        val y = center.y + (rr * sin(a)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, color)
}

fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        quadraticTo(center.x, center.y, center.x + r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y + r)
        quadraticTo(center.x, center.y, center.x - r, center.y)
        quadraticTo(center.x, center.y, center.x, center.y - r)
        close()
    }
    drawPath(p, color)
}

fun DrawScope.petal(center: Offset, r: Float, angle: Float, color: Color) {
    rotate(angle, center) {
        val p = Path().apply {
            moveTo(center.x, center.y - r)
            cubicTo(center.x + r * 0.9f, center.y - r * 0.6f, center.x + r * 0.6f, center.y + r * 0.8f, center.x, center.y + r)
            cubicTo(center.x - r * 0.6f, center.y + r * 0.8f, center.x - r * 0.9f, center.y - r * 0.6f, center.x, center.y - r)
            close()
        }
        drawPath(p, color)
        // выемка на кончике лепестка сакуры
        drawCircle(Color.Transparent, r * 0.18f, Offset(center.x, center.y - r))
    }
}

@Composable
fun CatPawIcon(color: Color, size: Dp = 18.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { catPaw(Offset(this.size.width / 2, this.size.height * 0.58f), this.size.minDimension * 0.42f, color) }
}

@Composable
fun CatFaceIcon(color: Color, line: Color, size: Dp = 28.dp, sleepy: Boolean = true, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) { catFace(Offset(this.size.width / 2, this.size.height * 0.56f), this.size.minDimension * 0.42f, color, line, sleepy) }
}

/** Падающие лепестки сакуры поверх контента (очень лёгкая анимация). */
@Composable
fun SakuraPetals(modifier: Modifier = Modifier, count: Int = 14, color: Color = Color(0xCCFFC1D3)) {
    val seeds = remember(count) {
        val rnd = Random(7)
        List(count) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.5f + rnd.nextFloat(), rnd.nextFloat() * 360f, 0.6f + rnd.nextFloat() * 0.8f) }
    }
    val t by rememberInfiniteTransition(label = "petals").animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(modifier) {
        for (s in seeds) {
            val prog = (t * s[2] + s[1]) % 1f
            val x = (s[0] * size.width + sin((prog * 6 + s[3]).toDouble()).toFloat() * 30f + prog * 60f) % size.width
            val y = prog * (size.height + 40f) - 20f
            translate(0f, 0f) { petal(Offset(x, y), 7f * s[4], s[3] + prog * 360f, color) }
        }
    }
}
