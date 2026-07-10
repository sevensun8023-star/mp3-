package com.car.mp3player.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.car.mp3player.R
import kotlin.math.min

class VinylRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rotateGroup: FrameLayout
    private val discView: ImageView
    private val coverView: ImageView
    private var rotationAnimator: ObjectAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_vinyl_record, this, true)
        rotateGroup = findViewById<FrameLayout>(R.id.vinylRotateGroup)
        discView = findViewById<ImageView>(R.id.vinylDisc)
        coverView = findViewById<ImageView>(R.id.vinylCover)
        coverView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        coverView.clipToOutline = true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val available = min(w, h).toFloat()
        val discPx = (available * 0.94f).toInt().coerceIn(dp(240), dp(480))
        val coverPx = (discPx * 0.435f).toInt()
        layoutDiscAndCover(discPx, coverPx)
    }

    private fun layoutDiscAndCover(discPx: Int, coverPx: Int) {
        (discView.layoutParams as FrameLayout.LayoutParams).apply {
            width = discPx
            height = discPx
            gravity = android.view.Gravity.CENTER
            discView.layoutParams = this
        }
        (coverView.layoutParams as FrameLayout.LayoutParams).apply {
            width = coverPx
            height = coverPx
            gravity = android.view.Gravity.CENTER
            coverView.layoutParams = this
        }
        rotateGroup.requestLayout()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    fun setCoverBitmap(bitmap: Bitmap?) {
        if (bitmap == null) {
            coverView.setImageResource(R.drawable.bg_album_placeholder)
        } else {
            coverView.setImageBitmap(bitmap)
        }
    }

    fun setPlaying(playing: Boolean) {
        if (playing) {
            if (rotationAnimator?.isRunning == true) return
            rotationAnimator?.cancel()
            rotationAnimator = ObjectAnimator.ofFloat(
                rotateGroup,
                View.ROTATION,
                rotateGroup.rotation,
                rotateGroup.rotation + 360f
            ).apply {
                duration = 18_000L
                interpolator = LinearInterpolator()
                repeatCount = ObjectAnimator.INFINITE
                start()
            }
        } else {
            rotationAnimator?.cancel()
            rotationAnimator = null
        }
    }

    override fun onDetachedFromWindow() {
        rotationAnimator?.cancel()
        rotationAnimator = null
        super.onDetachedFromWindow()
    }
}
