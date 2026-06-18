package com.car.mp3player.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.car.mp3player.ArtistAdapter
import com.car.mp3player.R
import com.car.mp3player.SongAdapter
import com.car.mp3player.databinding.FragmentPlaylistBinding
import com.car.mp3player.model.ArtistGroup
import com.car.mp3player.model.PlaylistSortOrder
import com.car.mp3player.model.PlaylistViewMode
import com.car.mp3player.model.Song
import com.car.mp3player.playback.PlaybackStateHolder

class PlaylistFragment : Fragment(), PlaybackStateHolder.Listener {
    private var _binding: FragmentPlaylistBinding? = null
    private val binding get() = _binding!!
    private lateinit var songAdapter: SongAdapter
    private lateinit var artistAdapter: ArtistAdapter
    private var allSongs = listOf<Song>()
    private var query = ""
    private var viewMode = PlaylistViewMode.ALL_SONGS
    private var sortOrder = PlaylistSortOrder.TITLE
    private var selectedArtist: String? = null
    private var lastClickMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPlaylistBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        songAdapter = SongAdapter { song, indexInList ->
            val now = System.currentTimeMillis()
            if (now - lastClickMs < 280) return@SongAdapter
            lastClickMs = now
            (activity as? MainHost)?.switchToTab(1)
            val visibleSongs = currentVisibleSongs()
            val playIndex = visibleSongs.indexOfFirst { it.path == song.path }.takeIf { it >= 0 } ?: indexInList
            (activity as? MainHost)?.playSongSubset(visibleSongs, playIndex)
        }
        artistAdapter = ArtistAdapter { group ->
            selectedArtist = group.name
            updateToolbar()
            applyFilter()
        }

        binding.songList.layoutManager = LinearLayoutManager(requireContext())
        binding.songList.adapter = songAdapter
        binding.songList.setHasFixedSize(true)

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                query = s?.toString().orEmpty()
                applyFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.chipAllSongs.setOnClickListener {
            viewMode = PlaylistViewMode.ALL_SONGS
            selectedArtist = null
            binding.chipAllSongs.isChecked = true
            binding.chipArtists.isChecked = false
            updateToolbar()
            applyFilter()
        }
        binding.chipArtists.setOnClickListener {
            viewMode = PlaylistViewMode.BY_ARTIST
            selectedArtist = null
            binding.chipArtists.isChecked = true
            binding.chipAllSongs.isChecked = false
            updateToolbar()
            applyFilter()
        }

        binding.sortGroup.setOnCheckedChangeListener { _, checkedId ->
            sortOrder = when (checkedId) {
                R.id.sortDurationAsc -> PlaylistSortOrder.DURATION_ASC
                R.id.sortDurationDesc -> PlaylistSortOrder.DURATION_DESC
                else -> PlaylistSortOrder.TITLE
            }
            applyFilter()
        }

        binding.toolbar.setNavigationOnClickListener { exitArtistDetail() }

        refreshFromHost()
    }

    private fun exitArtistDetail() {
        if (selectedArtist == null) return
        selectedArtist = null
        updateToolbar()
        applyFilter()
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
        songAdapter.playingPath = song?.path
    }

    override fun onPlaylistChanged(songs: List<Song>) {
        allSongs = songs
        applyFilter()
    }

    fun refreshFromHost() {
        allSongs = (activity as? MainHost)?.allSongs().orEmpty()
        applyFilter()
    }

    private fun currentVisibleSongs(): List<Song> {
        var list = allSongs
        if (selectedArtist != null) {
            list = list.filter { it.artist == selectedArtist }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        return sortSongs(list)
    }

    private fun applyFilter() {
        if (viewMode == PlaylistViewMode.BY_ARTIST && selectedArtist == null) {
            showArtistList()
            return
        }

        binding.songList.adapter = songAdapter
        val list = currentVisibleSongs()
        songAdapter.playingPath = PlaybackStateHolder.currentSong?.path
        songAdapter.submitSongs(list)
        binding.songCountText.text = getString(R.string.song_count, list.size)
        binding.emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        binding.songList.visibility = if (list.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun showArtistList() {
        binding.songList.adapter = artistAdapter
        var artists = allSongs.groupBy { it.artist }
            .map { (name, songs) -> ArtistGroup(name, songs.size) }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            artists = artists.filter { it.name.lowercase().contains(q) }
        }
        artists = artists.sortedBy { it.name.lowercase() }
        artistAdapter.submitList(artists)
        binding.songCountText.text = getString(R.string.artist_count, artists.size)
        binding.emptyText.visibility = if (artists.isEmpty()) View.VISIBLE else View.GONE
        binding.songList.visibility = if (artists.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun sortSongs(list: List<Song>): List<Song> {
        return when (sortOrder) {
            PlaylistSortOrder.TITLE -> list.sortedBy { it.title.lowercase() }
            PlaylistSortOrder.DURATION_ASC -> list.sortedWith(
                compareBy<Song> { if (it.durationMs <= 0L) Long.MAX_VALUE else it.durationMs }
                    .thenBy { it.title.lowercase() }
            )
            PlaylistSortOrder.DURATION_DESC -> list.sortedWith(
                compareByDescending<Song> { if (it.durationMs <= 0L) Long.MIN_VALUE else it.durationMs }
                    .thenBy { it.title.lowercase() }
            )
        }
    }

    private fun updateToolbar() {
        val inArtistDetail = viewMode == PlaylistViewMode.BY_ARTIST && selectedArtist != null
        binding.toolbar.navigationIcon = if (inArtistDetail) {
            requireContext().getDrawable(android.R.drawable.ic_menu_revert)
        } else {
            null
        }
        binding.toolbar.subtitle = when {
            selectedArtist != null -> selectedArtist
            viewMode == PlaylistViewMode.BY_ARTIST -> getString(R.string.filter_artists)
            else -> null
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
