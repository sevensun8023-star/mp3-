package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import kotlin.math.max

class ScrollLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lines: List<LrcLine> = emptyList()
    private var positionMs: Long = 0L

    init {
        setWillNotDraw(false)
    }

    fun update(lines: List<LrcLine>, positionMs: Long) {
        this.lines = lines
        this.positionMs = positionMs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (lines.isEmpty()) {
            dimPaint.color = settings.pendingColor
            dimPaint.textSize = 20f * resources.displayMetrics.scaledDensity
            canvas.drawText("♪ 暂无歌词", width / 2f - dimPaint.measureText("♪ 暂无歌词") / 2f, height / 2f, dimPaint)
            return
        }

        val currentIndex = findCurrentIndex()
        val fontSize = settings.fontSizeSp * resources.displayMetrics.scaledDensity
        val lineHeight = fontSize * 1.55f

        val centerY = height / 2f
        val startIndex = max(0, currentIndex - 2)
        val endIndex = minOf(lines.lastIndex, currentIndex + 4)

        for (i in startIndex..endIndex) {
            val line = lines[i]
            val relativeIndex = i - currentIndex
            val baseline = centerY + relativeIndex * lineHeight
            if (i == currentIndex) {
                drawKaraokeLine(canvas, line, positionMs, baseline, fontSize)
            } else {
                dimPaint.textSize = if (kotlin.math.abs(relativeIndex) == 1) fontSize * 0.92f else fontSize * 0.78f
                dimPaint.color = if (i < currentIndex) settings.nextLineColor else settings.pendingColor
                val text = line.text
                canvas.drawText(text, (width - dimPaint.measureText(text)) / 2f, baseline, dimPaint)
            }
        }
    }

    private fun findCurrentIndex(): Int {
        var index = 0
        for (i in lines.indices) {
            if (positionMs >= lines[i].startTimeMs) index = i else break
        }
        return index
    }

    private fun drawKaraokeLine(canvas: Canvas, line: LrcLine, positionMs: Long, baselineY: Float, fontSize: Float) {
        val chars = line.chars
        if (chars.isEmpty()) return
        var sungIndex = -1
        for (i in chars.indices) {
            if (positionMs >= chars[i].startTimeMs) sungIndex = i
        }
        val fullText = line.text
        textPaint.textSize = fontSize
        dimPaint.textSize = fontSize
        var x = max(paddingLeft.toFloat(), (width - dimPaint.measureText(fullText)) / 2f)
        for (i in chars.indices) {
            val ch = chars[i].char
            val paint = if (i <= sungIndex) textPaint else dimPaint
            paint.color = if (i <= sungIndex) settings.highlightColor else settings.pendingColor
            paint.textSize = if (i == sungIndex) fontSize * 1.08f else fontSize
            canvas.drawText(ch, x, baselineY, paint)
            x += paint.measureText(ch)
        }
    }
}
