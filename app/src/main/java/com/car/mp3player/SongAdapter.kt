package com.car.mp3player

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.car.mp3player.databinding.ItemSongBinding
import com.car.mp3player.model.Song

class SongAdapter(
    private val onClick: (Song, Int) -> Unit
) : RecyclerView.Adapter<SongAdapter.SongViewHolder>() {

    private val items = mutableListOf<Song>()

    fun submit(list: List<Song>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val binding = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SongViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class SongViewHolder(private val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(song: Song, position: Int) {
            binding.songTitle.text = song.title
            val lrcHint = if (song.lrcPath != null) " · 有歌词" else " · 无 lrc"
            binding.songPath.text = song.artist + lrcHint
            binding.root.setOnClickListener { onClick(song, position) }
        }
    }
}
