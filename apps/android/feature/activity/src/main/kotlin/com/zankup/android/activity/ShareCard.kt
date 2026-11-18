package com.zankup.android.activity

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import kotlin.math.cos

// The share image (OS-919) as the PWA and the React Native app draw it (packages/core shareCard.ts):
// a 1080 × 1350 card with the title, the date, the route as the API gives it for sharing (privacy
// zones already out) and up to six figures, in the theme's colours.

internal const val CARD_WIDTH = 1080
internal const val CARD_HEIGHT = 1350

/** Where the route goes on the card. */
private const val AREA_X = 80f
private const val AREA_Y = 360f
private const val AREA_W = CARD_WIDTH - 160f
private const val AREA_H = 560f

internal data class CardFigure(val label: String, val value: String)

internal data class CardContent(
    val title: String,
    val subtitle: String,
    val figures: List<CardFigure>,
    /** Stretches of (lat, lon); empty for no map. */
    val route: List<List<Pair<Double, Double>>>
)

internal data class CardColours(val bg: Int, val fg: Int, val muted: Int, val accent: Int)

/** A function that puts a (lat, lon) in the route's area, keeping its shape, centred. */
internal fun projectRoute(stretches: List<List<Pair<Double, Double>>>): (Pair<Double, Double>) -> Pair<Float, Float> {
    val points = stretches.flatten()
    val lat0 = points.sumOf { it.first } / points.size
    val k = cos(Math.toRadians(lat0))
    val xs = points.map { it.second * k }
    val ys = points.map { it.first }
    val minX = xs.min()
    val maxX = xs.max()
    val minY = ys.min()
    val maxY = ys.max()
    val scale = minOf(AREA_W / (maxX - minX).coerceAtLeast(1e-9), AREA_H / (maxY - minY).coerceAtLeast(1e-9))
    val offX = AREA_X + (AREA_W - (maxX - minX) * scale) / 2
    val offY = AREA_Y + (AREA_H - (maxY - minY) * scale) / 2
    return { (lat, lon) -> (offX + (lon * k - minX) * scale).toFloat() to (offY + (maxY - lat) * scale).toFloat() }
}

/** Up to [max] lines of [text] within [width], the last one with an ellipsis when it does not fit. */
internal fun lines(text: String, width: Float, max: Int, measure: (String) -> Float): List<String> {
    val out = mutableListOf<String>()
    var current = ""
    for (word in text.split(Regex("\\s+")).filter { it.isNotEmpty() }) {
        val next = if (current.isEmpty()) word else "$current $word"
        if (measure(next) > width && current.isNotEmpty()) {
            out += current
            current = word
            if (out.size == max) {
                out[max - 1] = out[max - 1] + "…"
                return out
            }
        } else {
            current = next
        }
    }
    if (current.isNotEmpty()) out += current
    return out
}

private fun paint(colour: Int, size: Float = 0f, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = colour
    textSize = size
    typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
}

/** Draws the card. */
internal fun drawCard(content: CardContent, c: CardColours): Bitmap {
    val bitmap = Bitmap.createBitmap(CARD_WIDTH, CARD_HEIGHT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(c.bg)

    val title = paint(c.fg, 64f, bold = true)
    lines(content.title, CARD_WIDTH - 160f, 2, title::measureText).forEachIndexed { i, line ->
        canvas.drawText(line, 80f, 150f + i * 72f, title)
    }
    canvas.drawText(content.subtitle, 80f, 300f, paint(c.muted, 36f))

    if (content.route.any { it.size > 1 }) {
        val at = projectRoute(content.route)
        val stroke = paint(c.accent).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        content.route.filter { it.size > 1 }.forEach { stretch ->
            val path = Path()
            stretch.forEachIndexed { i, p ->
                val (x, y) = at(p)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, stroke)
        }
    }

    val figures = content.figures.take(6)
    val columns = if (figures.size > 4) 3 else 2
    val cell = (CARD_WIDTH - 160f) / columns
    val value = paint(c.fg, 60f, bold = true)
    val label = paint(c.muted, 30f)
    figures.forEachIndexed { i, f ->
        val x = 80f + (i % columns) * cell
        val y = 1010f + (i / columns) * 150f
        canvas.drawText(f.value, x, y, value)
        canvas.drawText(f.label.uppercase(), x, y + 46f, label)
    }
    canvas.drawText("ZANKUP", 80f, CARD_HEIGHT - 60f, paint(c.muted, 30f, bold = true))
    return bitmap
}
