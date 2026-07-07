package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricFontFamily
import kotlin.math.max
import kotlin.math.min

object LyricRenderer {

    const val PLACEHOLDER_LINE = "-----------"

    /** 悬浮歌词：待唱用主题色淡染（避免纯白在浅色背景看不见） */
    private fun overlayPendingColor(highlight: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(overlayHighlightColor(highlight), hsv)
        hsv[1] = (hsv[1] * 0.55f).coerceIn(0.18f, 0.65f)
        hsv[2] = (hsv[2] * 0.72f).coerceIn(0.42f, 0.78f)
        return Color.HSVToColor(0xD8, hsv)
    }

    /** 悬浮歌词：下一行略亮，仍带主题色调 */
    private fun overlayNextColor(highlight: Int): Int {
        val hsv = FloatArray(3)
        Color.colorToHSV(overlayHighlightColor(highlight), hsv)
        hsv[1] = (hsv[1] * 0.7f).coerceIn(0.22f, 0.75f)
        hsv[2] = (hsv[2] * 0.88f).coerceIn(0.55f, 0.92f)
        return Color.HSVToColor(0xEE, hsv)
    }

    data class Style(
        val highlightColor: Int,
        val pendingColor: Int,
        val nextLineColor: Int,
        val currentSizePx: Float,
        val nextSizePx: Float,
        val otherSizePx: Float,
        val typeface: Typeface?,
        val maxVisualLines: Int,
        val currentScale: Float,
        val nextScale: Float,
        val bold: Boolean,
        val outline: Boolean
    )

    fun styleFrom(
        context: Context,
        settings: SettingsRepository,
        density: Float,
        forPlayer: Boolean
    ): Style {
        val overlaySize = settings.fontSizeSp
        val baseCurrent = if (forPlayer) settings.playerFontSizeSp else overlaySize
        val baseNext = if (forPlayer) settings.playerNextFontSizeSp else overlaySize * 0.88f
        val baseOther = if (forPlayer) settings.playerFontSizeSp * 0.82f else overlaySize * 0.85f
        val family = settings.lyricFontFamily()
        val bold = !forPlayer && settings.overlayLyricBold
        val highlight = if (forPlayer) settings.highlightColor else overlayHighlightColor(settings.highlightColor)
        val pending = if (forPlayer) settings.pendingColor else overlayPendingColor(settings.highlightColor)
        val next = if (forPlayer) settings.nextLineColor else overlayNextColor(settings.highlightColor)
        return Style(
            highlightColor = highlight,
            pendingColor = pending,
            nextLineColor = next,
            currentSizePx = baseCurrent * density * if (forPlayer) settings.currentLineScale else 1f,
            nextSizePx = baseNext * density * if (forPlayer) settings.nextLineScale else 1f,
            otherSizePx = baseOther * density,
            typeface = LyricFontProvider.getTypeface(context, family, bold),
            maxVisualLines = settings.maxLyricVisualLines,
            currentScale = settings.currentLineScale,
            nextScale = settings.nextLineScale,
            bold = bold,
            outline = !forPlayer
        )
    }

    fun wrapText(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isEmpty() || maxWidth <= 0f) return emptyList()
        val lines = mutableListOf<String>()
        var start = 0
        while (start < text.length && lines.size < maxLines) {
            val count = paint.breakText(text, start, text.length, true, maxWidth, null)
            if (count <= 0) break
            var end = start + count
            if (end < text.length && lines.size == maxLines - 1) {
                val slice = text.substring(start, end).trimEnd()
                lines.add(if (end < text.length) slice.dropLastWhile { it != ' ' }.trimEnd() + "…" else slice)
                break
            }
            lines.add(text.substring(start, end).trimEnd())
            start = end
            while (start < text.length && text[start].isWhitespace()) start++
        }
        return lines.ifEmpty { listOf(text) }
    }

    data class CharRow(val chars: List<LrcChar>, val startX: Float, val baselineY: Float)

    fun layoutKaraokeRows(
        line: LrcLine,
        paint: Paint,
        maxWidth: Float,
        maxRows: Int
    ): List<CharRow> {
        if (line.chars.isEmpty()) return emptyList()
        val rows = mutableListOf<CharRow>()
        var rowChars = mutableListOf<LrcChar>()
        var rowWidth = 0f
        var currentY = 0f
        val lineHeight = paint.textSize * 1.35f

        fun flushRow() {
            if (rowChars.isEmpty()) return
            val totalW = rowChars.sumOf { paint.measureText(it.char).toDouble() }.toFloat()
            val startX = max(0f, (maxWidth - totalW) / 2f)
            rows.add(CharRow(rowChars.toList(), startX, currentY))
            rowChars = mutableListOf()
            rowWidth = 0f
            currentY += lineHeight
        }

        for (ch in line.chars) {
            val w = paint.measureText(ch.char)
            if (rowWidth + w > maxWidth && rowChars.isNotEmpty()) {
                flushRow()
                if (rows.size >= maxRows) break
            }
            rowChars.add(ch)
            rowWidth += w
        }
        if (rows.size < maxRows && rowChars.isNotEmpty()) flushRow()
        return rows
    }

    fun drawKaraokeLine(
        canvas: Canvas,
        line: LrcLine,
        positionMs: Long,
        centerY: Float,
        style: Style,
        maxWidth: Float,
        sungPaint: Paint,
        pendingPaint: Paint
    ): Float {
        sungPaint.typeface = style.typeface
        pendingPaint.typeface = style.typeface
        sungPaint.isFakeBoldText = style.bold
        pendingPaint.isFakeBoldText = style.bold
        sungPaint.textSize = style.currentSizePx
        pendingPaint.textSize = style.currentSizePx

        val rows = layoutKaraokeRows(line, pendingPaint, maxWidth - padding(style), style.maxVisualLines)
        if (rows.isEmpty()) return 0f

        val totalHeight = rows.size * style.currentSizePx * 1.35f
        var topY = centerY - totalHeight / 2f + style.currentSizePx

        var sungIndex = -1
        for (i in line.chars.indices) {
            if (positionMs >= line.chars[i].startTimeMs) sungIndex = i
        }
        var charGlobalIndex = 0

        for (row in rows) {
            var x = row.startX + padding(style) / 2f
            for (ch in row.chars) {
                val paint = if (charGlobalIndex <= sungIndex) sungPaint else pendingPaint
                paint.color = if (charGlobalIndex <= sungIndex) style.highlightColor else style.pendingColor
                val size = if (charGlobalIndex == sungIndex) style.currentSizePx * 1.04f else style.currentSizePx
                paint.textSize = size
                drawText(canvas, ch.char, x, topY, paint, style)
                x += paint.measureText(ch.char)
                charGlobalIndex++
            }
            topY += style.currentSizePx * 1.35f
        }
        return totalHeight
    }

    fun drawWrappedStaticLine(
        canvas: Canvas,
        text: String,
        centerY: Float,
        paint: Paint,
        style: Style,
        maxWidth: Float,
        color: Int,
        sizePx: Float,
        maxLines: Int
    ) {
        paint.typeface = style.typeface
        paint.color = color
        paint.textSize = sizePx
        val lines = wrapText(text, paint, maxWidth - padding(style), maxLines)
        val lineHeight = sizePx * 1.3f
        val totalH = lines.size * lineHeight
        var y = centerY - totalH / 2f + sizePx
        val pad = padding(style) / 2f
        for (line in lines) {
            val w = paint.measureText(line)
            drawText(canvas, line, max(pad, (maxWidth - w) / 2f), y, paint, style)
            y += lineHeight
        }
    }

    fun drawOverlayBlock(
        canvas: Canvas,
        current: LrcLine?,
        next: LrcLine?,
        positionMs: Long,
        width: Int,
        height: Int,
        style: Style,
        sungPaint: Paint,
        nextPaint: Paint,
        pendingPaint: Paint
    ) {
        sungPaint.typeface = style.typeface
        nextPaint.typeface = style.typeface
        pendingPaint.typeface = style.typeface
        sungPaint.isFakeBoldText = style.bold
        nextPaint.isFakeBoldText = style.bold
        pendingPaint.isFakeBoldText = style.bold
        val maxW = width.toFloat()
        val gap = style.currentSizePx * 0.6f
        val blockH = height / 2f

        current?.let { line ->
            drawKaraokeLine(
                canvas, line, positionMs, blockH - gap, style, maxW, sungPaint, pendingPaint
            )
        } ?: drawWrappedStaticLine(
            canvas, PLACEHOLDER_LINE, blockH - gap, pendingPaint, style, maxW,
            style.pendingColor, style.currentSizePx, 1
        )

        next?.let { line ->
            drawWrappedStaticLine(
                canvas, line.text, blockH + blockH / 2f + gap, nextPaint, style, maxW,
                style.nextLineColor, style.nextSizePx, style.maxVisualLines
            )
        }
    }

    private fun padding(style: Style) = 24f

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, paint: Paint, style: Style) {
        if (!style.outline) {
            canvas.drawText(text, x, y, paint)
            return
        }
        val fillColor = paint.color
        val strokeWidth = (paint.textSize * 0.08f).coerceIn(2.5f, 7f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = Color.argb(220, 0, 0, 0)
        canvas.drawText(text, x, y, paint)
        paint.style = Paint.Style.FILL
        paint.color = fillColor
        canvas.drawText(text, x, y, paint)
    }

    /** 悬浮歌词：正在播放行用高饱和主题色（网易云红） */
    private fun overlayHighlightColor(color: Int): Int {
        val base = if (Color.alpha(color) == 0) Color.parseColor("#EC4141") else color
        val hsv = FloatArray(3)
        Color.colorToHSV(base, hsv)
        if (hsv[1] < 0.08f) {
            return Color.parseColor("#EC4141")
        }
        hsv[1] = min(1f, hsv[1] * 1.2f)
        hsv[2] = min(1f, hsv[2] * 1.05f)
        return Color.HSVToColor(255, hsv)
    }
}
