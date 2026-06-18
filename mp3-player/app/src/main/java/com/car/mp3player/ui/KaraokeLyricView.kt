package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState
import kotlin.math.max

class KaraokeLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lyricState: LyricState? = null
    private var previewMode = false
    private var previewProgress = 0f

    init {
        setWillNotDraw(false)
    }

    fun setPreviewMode(enabled: Boolean) {
        previewMode = enabled
        if (enabled) {
            lyricState = buildPreviewState()
        }
        invalidate()
    }

    fun update(state: LyricState?) {
        previewMode = false
        lyricState = state
        invalidate()
    }

    fun applySettings() {
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = if (previewMode) {
            lyricState ?: buildPreviewState()
        } else {
            lyricState
        } ?: return

        val fontSize = settings.fontSizeSp * resources.displayMetrics.scaledDensity
        currentPaint.textSize = fontSize
        nextPaint.textSize = fontSize * 0.92f
        pendingPaint.textSize = fontSize
        pendingPaint.color = settings.pendingColor
        nextPaint.color = settings.nextLineColor

        val lineGap = fontSize * 0.55f
        val currentY = height / 2f - lineGap / 2f
        val nextY = height / 2f + fontSize + lineGap / 2f

        state.currentLine?.let { line ->
            drawKaraokeLine(canvas, line, state.positionMs, currentY, fontSize)
        } ?: run {
            currentPaint.color = settings.pendingColor
            canvas.drawText("♪ 等待播放", paddingLeft.toFloat(), currentY, currentPaint)
        }

        state.nextLine?.let { line ->
            val text = line.text
            val textWidth = nextPaint.measureText(text)
            val x = max(paddingLeft.toFloat(), (width - textWidth) / 2f)
            canvas.drawText(text, x, nextY, nextPaint)
        }
    }

    private fun drawKaraokeLine(canvas: Canvas, line: LrcLine, positionMs: Long, baselineY: Float, fontSize: Float) {
        val chars = line.chars
        if (chars.isEmpty()) return

        val fullText = line.text
        val totalWidth = pendingPaint.measureText(fullText)
        var x = max(paddingLeft.toFloat(), (width - totalWidth) / 2f)

        var sungIndex = -1
        for (i in chars.indices) {
            if (positionMs >= chars[i].startTimeMs) {
                sungIndex = i
            }
        }

        for (i in chars.indices) {
            val ch = chars[i].char
            val charWidth = pendingPaint.measureText(ch)
            currentPaint.color = when {
                i < sungIndex -> settings.highlightColor
                i == sungIndex -> settings.highlightColor
                else -> settings.pendingColor
            }
            if (i == sungIndex) {
                currentPaint.textSize = fontSize * 1.06f
            } else {
                currentPaint.textSize = fontSize
            }
            canvas.drawText(ch, x, baselineY, currentPaint)
            x += charWidth
        }
    }

    private fun buildPreviewState(): LyricState {
        val line1 = LrcLine(
            chars = "我正在逐字变色".mapIndexed { index, c ->
                com.car.mp3player.model.LrcChar(c.toString(), 1000L + index * 250L)
            },
            startTimeMs = 1000L,
            endTimeMs = 3000L
        )
        val line2 = LrcLine(
            chars = "下一行歌词预览".map { com.car.mp3player.model.LrcChar(it.toString(), 3000L) },
            startTimeMs = 3000L,
            endTimeMs = 5000L
        )
        val progress = ((System.currentTimeMillis() / 250L) % 8).toInt()
        val pos = line1.chars.getOrElse(progress) { line1.chars.last() }.startTimeMs
        return LyricState(line1, line2, pos)
    }
}
