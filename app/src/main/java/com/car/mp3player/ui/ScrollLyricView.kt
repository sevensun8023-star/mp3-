package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import kotlin.math.abs

class ScrollLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val sungPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pendingPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var lines: List<LrcLine> = emptyList()
    private var targetPositionMs = 0L
    private var displayPositionMs = 0f
    private var smoothScrollY = 0f
    private var targetScrollY = 0f
    private var animating = false

    private val frameCallback = Choreographer.FrameCallback {
        if (!animating) return@FrameCallback
        val smooth = settings.smoothLyrics
        val lerp = if (smooth) 0.18f else 1f
        displayPositionMs += (targetPositionMs - displayPositionMs) * lerp
        computeTargetScroll()
        smoothScrollY += (targetScrollY - smoothScrollY) * lerp
        computeTargetScroll()
        invalidate()
        if (smooth && (abs(targetPositionMs - displayPositionMs) > 8f || abs(targetScrollY - smoothScrollY) > 0.5f)) {
            Choreographer.getInstance().postFrameCallback(this)
        } else {
            displayPositionMs = targetPositionMs.toFloat()
            smoothScrollY = targetScrollY
            animating = false
        }
    }

    init {
        setWillNotDraw(false)
    }

    fun update(lines: List<LrcLine>, positionMs: Long) {
        this.lines = lines
        targetPositionMs = positionMs
        if (!settings.smoothLyrics) {
            displayPositionMs = positionMs.toFloat()
            computeTargetScroll()
            smoothScrollY = targetScrollY
            invalidate()
            return
        }
        computeTargetScroll()
        if (!animating) {
            animating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun refreshStyle() {
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    private fun computeTargetScroll() {
        val pos = if (settings.smoothLyrics) targetPositionMs else displayPositionMs.toLong()
        val idx = findIndex(pos)
        val density = resources.displayMetrics.scaledDensity
        val style = LyricRenderer.styleFrom(settings, density, forPlayer = true)
        val blockH = style.currentSizePx * 1.35f * style.maxVisualLines + style.currentSizePx * 0.8f
        targetScrollY = idx * blockH
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val density = resources.displayMetrics.scaledDensity
        val style = LyricRenderer.styleFrom(settings, density, forPlayer = true)

        if (lines.isEmpty()) {
            LyricRenderer.drawWrappedStaticLine(
                canvas, "♪ 暂无歌词", height / 2f, pendingPaint, style, width.toFloat(),
                style.pendingColor, style.otherSizePx, 2
            )
            return
        }

        val idx = findIndex(displayPositionMs.toLong())
        val blockH = style.currentSizePx * 1.35f * style.maxVisualLines + style.currentSizePx * 0.8f
        val centerY = height / 2f - smoothScrollY + idx * blockH

        val start = (idx - 2).coerceAtLeast(0)
        val end = (idx + 3).coerceAtMost(lines.lastIndex)

        var y = centerY - (idx - start) * blockH
        for (i in start..end) {
            if (i == idx) {
                LyricRenderer.drawKaraokeLine(
                    canvas, lines[i], displayPositionMs.toLong(), y + blockH / 2f,
                    style, width.toFloat(), sungPaint, pendingPaint
                )
            } else {
                val size = when {
                    abs(i - idx) == 1 -> style.nextSizePx
                    else -> style.otherSizePx
                }
                val color = if (i < idx) style.nextLineColor else style.pendingColor
                LyricRenderer.drawWrappedStaticLine(
                    canvas, lines[i].text, y + blockH / 2f, nextPaint, style,
                    width.toFloat(), color, size, style.maxVisualLines
                )
            }
            y += blockH
        }
    }

    private fun findIndex(positionMs: Long): Int {
        var index = 0
        for (i in lines.indices) {
            if (positionMs >= lines[i].startTimeMs) index = i else break
        }
        return index
    }
}
