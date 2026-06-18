package com.car.mp3player.ui

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.car.mp3player.R

class VinylRecordView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val discView: ImageView
    private val coverView: ImageView
    private var rotationAnimator: ObjectAnimator? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.view_vinyl_record, this, true)
        discView = findViewById(R.id.vinylDisc)
        coverView = findViewById(R.id.vinylCover)
        coverView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        coverView.clipToOutline = true
    }

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
            rotationAnimator = ObjectAnimator.ofFloat(discView, ROTATION, discView.rotation, discView.rotation + 360f).apply {
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
