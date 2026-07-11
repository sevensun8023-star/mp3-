package com.car.mp3player.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.car.mp3player.R
import com.car.mp3player.SongAdapter
import com.car.mp3player.data.OnlineCurated
import com.car.mp3player.data.OnlineLibraryStore
import com.car.mp3player.data.OnlineMusicApi
import com.car.mp3player.data.SettingsRepository
import com.car.mp3player.databinding.FragmentOnlineMusicBinding
import com.car.mp3player.databinding.ItemChartChipBinding
import com.car.mp3player.databinding.ItemPlaylistCardBinding
import com.car.mp3player.model.CuratedChart
import com.car.mp3player.model.CuratedPlaylist
import com.car.mp3player.model.LibraryKind
import com.car.mp3player.model.OnlinePlaylistSummary
import com.car.mp3player.model.Song
import com.car.mp3player.model.UserPlaylist
import com.car.mp3player.playback.PlaybackStateHolder
import com.car.mp3player.util.MediaPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OnlineMusicFragment : Fragment(), PlaybackStateHolder.Listener {
    private var _binding: FragmentOnlineMusicBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: SettingsRepository
    private lateinit var api: OnlineMusicApi
    private lateinit var library: OnlineLibraryStore
    private lateinit var songAdapter: SongAdapter
    private var currentSongs: List<Song> = emptyList()
    private var showingDetail = false
    private var mineMode = false
    private var lastClickMs = 0L

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOnlineMusicBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        settings = SettingsRepository(requireContext())
        api = OnlineMusicApi(settings)
        library = OnlineLibraryStore(requireContext())
        AppThemeManager.applyFragmentRoot(binding.root, AppThemeManager.palette(requireContext(), settings))

        songAdapter = SongAdapter(
            onClick = { song, index ->
                when {
                    song.path.startsWith("meta://netease-playlist/") -> {
                        val id = song.path.removePrefix("meta://netease-playlist/")
                        loadPlaylist(id, song.title.removePrefix("🎵 ").trim())
                    }
                    song.path.startsWith("meta://playlist/") -> {
                        val id = song.path.removePrefix("meta://playlist/")
                        val tracks = library.songsForPlaylist(id)
                        currentSongs = tracks
                        renderSongList(tracks, getString(R.string.online_empty))
                        binding.toolbar.subtitle = song.title.removePrefix("📁 ").trim()
                        binding.toolbar.navigationIcon =
                            requireContext().getDrawable(android.R.drawable.ic_menu_revert)
                    }
                    else -> {
                        val playable = currentSongs.filter { !it.path.startsWith("meta://") }
                        val playIndex = playable.indexOfFirst { it.path == song.path }.coerceAtLeast(0)
                        val now = System.currentTimeMillis()
                        if (now - lastClickMs < 280) return@SongAdapter
                        lastClickMs = now
                        (activity as? MainHost)?.switchToTab(1)
                        (activity as? MainHost)?.playSongSubset(playable, playIndex, LibraryKind.ONLINE)
                    }
                }
            },
            onLongClick = { song, _ ->
                if (song.path.startsWith("meta://")) return@SongAdapter
                library.addFavoriteTrackFromSong(song, api)
                Toast.makeText(requireContext(), R.string.online_favorited, Toast.LENGTH_SHORT).show()
            }
        )

        binding.chartList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.chartList.adapter = ChartAdapter(OnlineCurated.charts) { loadPlaylist(it.playlistId, it.title) }

        binding.featuredList.layoutManager = LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false)
        binding.featuredList.adapter = FeaturedAdapter(OnlineCurated.featuredPlaylists) { loadPlaylist(it.playlistId, it.title) }

        binding.songList.layoutManager = LinearLayoutManager(requireContext())
        binding.songList.adapter = songAdapter

        binding.chipDiscover.setOnClickListener { switchMine(false) }
        binding.chipMine.setOnClickListener { switchMine(true) }
        binding.toolbar.setNavigationOnClickListener { showDiscoverHome() }

        binding.searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                performSearch(binding.searchInput.text?.toString().orEmpty())
                true
            } else false
        }
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.isNullOrBlank() && showingDetail) showDiscoverHome()
            }
        })

        showDiscoverHome()
    }

    private fun switchMine(mine: Boolean) {
        mineMode = mine
        binding.chipDiscover.isChecked = !mine
        binding.chipMine.isChecked = mine
        if (mine) showMineLibrary() else showDiscoverHome()
    }

    private fun showDiscoverHome() {
        showingDetail = false
        mineMode = false
        binding.chipDiscover.isChecked = true
        binding.toolbar.navigationIcon = null
        binding.toolbar.subtitle = null
        binding.discoverScroll.visibility = View.VISIBLE
        binding.songList.visibility = View.GONE
        binding.emptyText.visibility = View.GONE
    }

    private fun showMineLibrary() {
        showingDetail = true
        binding.discoverScroll.visibility = View.GONE
        binding.songList.visibility = View.VISIBLE
        binding.toolbar.navigationIcon = requireContext().getDrawable(android.R.drawable.ic_menu_revert)
        binding.toolbar.subtitle = getString(R.string.online_mine)
        val playlistHeaders = library.userPlaylists().map { playlist ->
            Song(
                id = playlist.id.hashCode().toLong(),
                title = "📁 ${playlist.name}",
                artist = getString(R.string.online_playlist_tap, playlist.trackLocalIds.size),
                path = "meta://playlist/${playlist.id}",
                lrcPath = null
            )
        }
        val favorites = library.favoriteSongs()
        currentSongs = playlistHeaders + favorites
        renderSongList(currentSongs, getString(R.string.online_favorites_empty))
    }

    private fun loadPlaylist(playlistId: String, title: String) {
        setLoading(true)
        lifecycleScope.launch {
            val (summary, songs) = withContext(Dispatchers.IO) { api.loadPlaylist(playlistId) }
            setLoading(false)
            if (songs.isEmpty()) {
                Toast.makeText(requireContext(), R.string.online_load_failed, Toast.LENGTH_SHORT).show()
                return@launch
            }
            showingDetail = true
            binding.discoverScroll.visibility = View.GONE
            binding.songList.visibility = View.VISIBLE
            binding.toolbar.navigationIcon = requireContext().getDrawable(android.R.drawable.ic_menu_revert)
            binding.toolbar.subtitle = summary?.name ?: title
            currentSongs = songs
            renderSongList(songs, getString(R.string.online_empty))
            binding.toolbar.setOnLongClickListener {
                summary?.let {
                    library.importPlaylist(it.name, playlistId, songs, api, it.coverUrl)
                    Toast.makeText(requireContext(), R.string.online_favorited, Toast.LENGTH_SHORT).show()
                }
                true
            }
        }
    }

    private fun performSearch(keyword: String) {
        if (keyword.isBlank()) return
        setLoading(true)
        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) { api.searchSongs(keyword) }
            val playlists = withContext(Dispatchers.IO) { api.searchPlaylists(keyword) }
            setLoading(false)
            showingDetail = true
            binding.discoverScroll.visibility = View.GONE
            binding.songList.visibility = View.VISIBLE
            binding.toolbar.navigationIcon = requireContext().getDrawable(android.R.drawable.ic_menu_revert)
            binding.toolbar.subtitle = getString(R.string.online_search_result, keyword)
            val playlistRows = playlists.take(5).map { summary ->
                Song(
                    id = summary.id.hashCode().toLong(),
                    title = "🎵 ${summary.name}",
                    artist = getString(R.string.online_playlist_meta, summary.trackCount, formatCount(summary.subscribedCount)),
                    path = "meta://netease-playlist/${summary.id}",
                    lrcPath = summary.coverUrl
                )
            }
            currentSongs = playlistRows + songs
            renderSongList(currentSongs, getString(R.string.online_search_empty))
        }
    }

    private fun renderSongList(songs: List<Song>, emptyMessage: String) {
        songAdapter.playingPath = PlaybackStateHolder.currentSong?.path
        songAdapter.submitSongs(songs)
        binding.emptyText.text = emptyMessage
        binding.emptyText.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.songList.visibility = if (songs.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun setLoading(loading: Boolean) {
        binding.loading.visibility = if (loading) View.VISIBLE else View.GONE
    }

    fun refreshLibrary() {
        if (mineMode) showMineLibrary()
    }

    private fun formatCount(value: Long): String = when {
        value >= 100_000_000 -> "${value / 100_000_000}亿"
        value >= 10_000 -> "${value / 10_000}万"
        else -> value.toString()
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

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private inner class ChartAdapter(
        private val items: List<CuratedChart>,
        private val onClick: (CuratedChart) -> Unit
    ) : RecyclerView.Adapter<ChartAdapter.Holder>() {
        inner class Holder(val binding: ItemChartChipBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemChartChipBinding.inflate(layoutInflater, parent, false)
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.title.text = item.title
            holder.binding.root.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }

    private inner class FeaturedAdapter(
        private val items: List<CuratedPlaylist>,
        private val onClick: (CuratedPlaylist) -> Unit
    ) : RecyclerView.Adapter<FeaturedAdapter.Holder>() {
        inner class Holder(val binding: ItemPlaylistCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            val binding = ItemPlaylistCardBinding.inflate(layoutInflater, parent, false)
            return Holder(binding)
        }

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.title.text = item.title
            holder.binding.subtitle.text = item.subtitle
            holder.binding.cover.setImageResource(R.drawable.ic_play)
            holder.binding.root.setOnClickListener { onClick(item) }
        }

        override fun getItemCount(): Int = items.size
    }
}
