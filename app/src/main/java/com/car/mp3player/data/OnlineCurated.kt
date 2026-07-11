package com.car.mp3player.data

import com.car.mp3player.model.CuratedChart
import com.car.mp3player.model.CuratedPlaylist

object OnlineCurated {
    val charts: List<CuratedChart> = listOf(
        CuratedChart("3778678", "热歌榜"),
        CuratedChart("3779629", "新歌榜"),
        CuratedChart("19723756", "飙升榜"),
        CuratedChart("2884035", "原创榜")
    )

    /** 96 前后华语流行：周杰伦、林俊杰、王力宏、陶喆及同年代精选 */
    val featuredPlaylists: List<CuratedPlaylist> = listOf(
        CuratedPlaylist("4922719423", "90后华语回忆录", "8090经典"),
        CuratedPlaylist("26467411", "8090后青春记忆", "怀旧精选"),
        CuratedPlaylist("1116878700", "周杰伦热门歌曲", "Jay Chou"),
        CuratedPlaylist("764984375", "林俊杰精选", "JJ Lin"),
        CuratedPlaylist("492698114", "王力宏精选", "Leehom"),
        CuratedPlaylist("2060260275", "陶喆 R&B 精选", "David Tao"),
        CuratedPlaylist("4923939873", "华语 R&B 慢歌", "节奏蓝调"),
        CuratedPlaylist("5202684592", "华语速爆新歌", "新鲜速递"),
        CuratedPlaylist("113716395", "经典老歌 300 首", "永恒旋律"),
        CuratedPlaylist("7452421334", "开车必听华语", "公路歌单")
    )
}
