package com.car.mp3player.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.TextPaint
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.model.LrcLine
import com.car.mp3player.model.LyricState
import kotlin.math.abs

class KaraokeLyricView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val settings = SettingsRepository(context)
    private val sungPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val nextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val pendingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)

    private var lyricState: LyricState? = null
    private var displayPositionMs = 0f
    private var targetPositionMs = 0L
    private var animating = false

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!animating) return
            val lerp = if (settings.smoothLyrics) 0.22f else 1f
            displayPositionMs += (targetPositionMs - displayPositionMs) * lerp
            invalidate()
            if (settings.smoothLyrics && abs(targetPositionMs - displayPositionMs) > 6f) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                displayPositionMs = targetPositionMs.toFloat()
                animating = false
            }
        }
    }

    init {
        setWillNotDraw(false)
    }

    fun update(state: LyricState?) {
        lyricState = state
        targetPositionMs = state?.positionMs ?: 0L
        if (!settings.smoothLyrics) {
            displayPositionMs = targetPositionMs.toFloat()
            invalidate()
            return
        }
        if (!animating) {
            animating = true
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    fun applySettings() {
        invalidate()
    }

    override fun onDetachedFromWindow() {
        animating = false
        Choreographer.getInstance().removeFrameCallback(frameCallback)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val state = lyricState ?: return
        val density = resources.displayMetrics.scaledDensity
        val style = LyricRenderer.styleFrom(context, settings, density, forPlayer = false)
        LyricRenderer.drawOverlayBlock(
            canvas,
            state.currentLine,
            state.nextLine,
            displayPositionMs.toLong(),
            width,
            height,
            style,
            sungPaint,
            nextPaint,
            pendingPaint
        )
    }
}
