package com.car.mp3player.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.car.mp3player.MusicPlaybackService
import com.car.mp3player.R
import com.car.mp3player.databinding.FragmentPlayerBinding
import com.car.mp3player.model.PlaybackMode
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder

class PlayerFragment : Fragment(), PlaybackStateHolder.Listener {
    private var _binding: FragmentPlayerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlayerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnPlayPause.setOnClickListener { sendAction(MusicPlaybackService.ACTION_TOGGLE) }
        binding.btnNext.setOnClickListener { sendAction(MusicPlaybackService.ACTION_NEXT) }
        binding.btnPrev.setOnClickListener { sendAction(MusicPlaybackService.ACTION_PREV) }
        binding.btnMode.setOnClickListener { toggleMode() }
        renderState()
    }

    override fun onStart() {
        super.onStart()
        PlaybackStateHolder.addListener(this)
    }

    override fun onStop() {
        PlaybackStateHolder.removeListener(this)
        super.onStop()
    }

    override fun onPlaybackChanged(
        song: Song?,
        playing: Boolean,
        positionMs: Long,
        lines: List<com.car.mp3player.model.LrcLine>
    ) {
        renderState()
        binding.scrollLyricView.update(lines, positionMs)
    }

    override fun onPlayModeChanged(mode: PlaybackMode) {
        updateModeUi(mode)
    }

    private fun renderState() {
        val song = PlaybackStateHolder.currentSong
        binding.songTitle.text = song?.title ?: getString(R.string.app_name)
        binding.songArtist.text = song?.artist ?: getString(R.string.no_songs)
        binding.btnPlayPause.setImageResource(
            if (PlaybackStateHolder.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.scrollLyricView.update(PlaybackStateHolder.lrcLines, PlaybackStateHolder.positionMs)
        updateModeUi(PlaybackStateHolder.playMode)
    }

    private fun updateModeUi(mode: PlaybackMode) {
        if (mode == PlaybackMode.SHUFFLE) {
            binding.btnMode.setImageResource(R.drawable.ic_mode_shuffle)
            binding.modeLabel.text = getString(R.string.mode_shuffle)
        } else {
            binding.btnMode.setImageResource(R.drawable.ic_mode_order)
            binding.modeLabel.text = getString(R.string.mode_order)
        }
    }

    private fun toggleMode() {
        val next = if (PlaybackStateHolder.playMode == PlaybackMode.ORDER) PlaybackMode.SHUFFLE else PlaybackMode.ORDER
        val intent = Intent(requireContext(), MusicPlaybackService::class.java).apply {
            action = MusicPlaybackService.ACTION_SET_MODE
            putExtra(MusicPlaybackService.EXTRA_MODE, next.ordinal)
        }
        ContextCompat.startForegroundService(requireContext(), intent)
    }

    private fun sendAction(action: String) {
        requireContext().startService(Intent(requireContext(), MusicPlaybackService::class.java).apply { this.action = action })
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
