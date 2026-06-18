package com.car.mp3player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.car.mp3player.databinding.ItemSongBinding
import com.car.mp3player.model.Song

class SongAdapter(
    private val onClick: (Song, Int) -> Unit
) : ListAdapter<Song, SongAdapter.SongViewHolder>(DIFF) {

    var playingPath: String? = null

    fun submitSongs(list: List<Song>) {
        submitList(list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song, position: Int) {
            binding.songTitle.text = song.title
            binding.songArtist.text = song.artist
            binding.lrcBadge.visibility = if (song.lrcPath != null) android.view.View.VISIBLE else android.view.View.GONE
            val isPlaying = song.path == playingPath
            binding.playingBadge.visibility = if (isPlaying) android.view.View.VISIBLE else android.view.View.GONE
            binding.root.setBackgroundColor(
                if (isPlaying) ContextCompat.getColor(binding.root.context, R.color.playing_highlight)
                else android.graphics.Color.TRANSPARENT
            )
            binding.root.setOnClickListener { onClick(song, position) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.path == newItem.path
            override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
        }
    }
}
