package com.car.mp3player

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import com.car.mp3player.model.LrcLine
import com.car.mp3player.ui.ScrollLyricView

class ClusterLyricPresentation(
    context: Context,
    display: Display
) : Presentation(context, display) {

    private var lyricView: ScrollLyricView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = ScrollLyricView(getContext())
        lyricView = view
        setContentView(view)
    }

    fun update(lines: List<LrcLine>, positionMs: Long) {
        lyricView?.update(lines, positionMs)
    }

    fun refreshStyle() {
        lyricView?.refreshStyle()
    }
}
