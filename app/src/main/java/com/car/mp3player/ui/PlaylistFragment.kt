package com.car.mp3player.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.car.mp3player.R
import com.car.mp3player.SongAdapter
import com.car.mp3player.databinding.FragmentPlaylistBinding
import com.car.mp3player.model.LyricFilter
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder

class PlaylistFragment : Fragment(), PlaybackStateHolder.Listener {
    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: SongAdapter
    private var allSongs = listOf<Song>()
    private var query = ""
    private var filter = LyricFilter.ALL

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    private var lastClickMs = 0L

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = SongAdapter { song, _ ->
            val now = System.currentTimeMillis()
            if (now - lastClickMs < 280) return@SongAdapter
            lastClickMs = now
            (activity as? MainHost)?.switchToTab(1)
            val index = (activity as? MainHost)?.allSongs()?.indexOfFirst { it.path == song.path } ?: -1
            if (index >= 0) {
                (activity as? MainHost)?.playSongAt(index)
            }
        }
        binding.songList.layoutManager = LinearLayoutManager(requireContext())
        binding.songList.adapter = adapter
        binding.songList.setHasFixedSize(true)

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipAll.setOnClickListener {
            filter = LyricFilter.ALL
            binding.chipAll.isChecked = true
            binding.chipWithLrc.isChecked = false
            applyFilter()
        }
        binding.chipWithLrc.setOnClickListener {
            filter = LyricFilter.WITH_LRC
            binding.chipWithLrc.isChecked = true
            binding.chipAll.isChecked = false
            applyFilter()
        }

        refreshFromHost()
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
        adapter.playingPath = song?.path
    }

    override fun onPlaylistChanged(songs: List<Song>) {
        allSongs = songs
        applyFilter()
    }

    fun refreshFromHost() {
        allSongs = (activity as? MainHost)?.allSongs().orEmpty()
        applyFilter()
    }

    private fun applyFilter() {
        var list = allSongs
        if (filter == LyricFilter.WITH_LRC) {
            list = list.filter { it.lrcPath != null }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        adapter.playingPath = PlaybackStateHolder.currentSong?.path
        adapter.submitSongs(list)
        binding.songCountText.text = getString(R.string.song_count, list.size)
        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.songList.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
