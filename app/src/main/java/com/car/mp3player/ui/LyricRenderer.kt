package com.car.mp3player.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcChar
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricFontFamily
import kotlin.math.max
import kotlin.math.min

object LyricRenderer {

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
        val nextScale: Float
    )

    fun styleFrom(settings: SettingsRepository, density: Float, forPlayer: Boolean): Style {
        val preset = settings.lyricTheme()
        val baseCurrent = if (forPlayer) settings.playerFontSizeSp else preset.playerCurrentSizeSp
        val baseNext = if (forPlayer) settings.playerNextFontSizeSp else preset.playerNextSizeSp
        val baseOther = if (forPlayer) settings.playerFontSizeSp * 0.82f else settings.fontSizeSp * 0.85f
        return Style(
            highlightColor = settings.highlightColor,
            pendingColor = settings.pendingColor,
            nextLineColor = settings.nextLineColor,
            currentSizePx = baseCurrent * density * settings.currentLineScale,
            nextSizePx = baseNext * density * settings.nextLineScale,
            otherSizePx = baseOther * density,
            typeface = typefaceFor(settings.lyricFontFamily()),
            maxVisualLines = settings.maxLyricVisualLines,
            currentScale = settings.currentLineScale,
            nextScale = settings.nextLineScale
        )
    }

    fun typefaceFor(family: LyricFontFamily): Typeface? = when (family) {
        LyricFontFamily.DEFAULT -> null
        LyricFontFamily.SANS -> Typeface.SANS_SERIF
        LyricFontFamily.SERIF -> Typeface.SERIF
        LyricFontFamily.ROUND -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
                canvas.drawText(ch.char, x, topY, paint)
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
            canvas.drawText(line, max(pad, (maxWidth - w) / 2f), y, paint)
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
        sungPaint: TextPaint,
        nextPaint: TextPaint,
        pendingPaint: TextPaint
    ) {
        val maxW = width.toFloat()
        val gap = style.currentSizePx * 0.6f
        val blockH = height / 2f

        current?.let { line ->
            drawKaraokeLine(
                canvas, line, positionMs, blockH - gap, style, maxW, sungPaint, pendingPaint
            )
        } ?: drawWrappedStaticLine(
            canvas, "♪ 等待播放", blockH - gap, pendingPaint, style, maxW,
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
}
