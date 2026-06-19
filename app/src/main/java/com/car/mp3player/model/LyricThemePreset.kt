package com.car.mp3player.model

enum class LyricThemePreset(
    val id: String,
    val displayName: String,
    val highlightColor: Int,
    val pendingColor: Int,
    val nextLineColor: Int,
    val playerCurrentSizeSp: Float,
    val playerNextSizeSp: Float,
    val overlaySizeSp: Float
) {
    CLASSIC(
        "classic", "经典白",
        0xE6FFFFFF.toInt(), 0x99DDDDDD.toInt(), 0xBBCCCCCC.toInt(),
        22f, 18f, 26f
    ),
    NETEASE(
        "netease", "网易云红",
        0xE6EC4141.toInt(), 0xCCFF9999.toInt(), 0xBBFFB3B3.toInt(),
        22f, 18f, 26f
    ),
    PINK(
        "pink", "淡粉",
        0xE6E891A8.toInt(), 0x99FFF0F3.toInt(), 0xBBFADCE4.toInt(),
        21f, 17f, 25f
    ),
    GREEN(
        "green", "淡绿",
        0xE67CB89A.toInt(), 0x99F0FAF4.toInt(), 0xBBDCF5E8.toInt(),
        21f, 17f, 25f
    ),
    BLUE(
        "blue", "淡蓝",
        0xE67BA3D0.toInt(), 0x99F0F6FC.toInt(), 0xBBDBEAFE.toInt(),
        21f, 17f, 25f
    ),
    MINT(
        "mint", "薄荷",
        0xE66BBFAD.toInt(), 0x99F2FBF8.toInt(), 0xBBCCF5EC.toInt(),
        21f, 17f, 25f
    ),
    LAVENDER(
        "lavender", "薰衣草",
        0xE6A99BD4.toInt(), 0x99F7F3FC.toInt(), 0xBBE9DFF8.toInt(),
        21f, 17f, 25f
    );

    companion object {
        fun fromId(id: String): LyricThemePreset =
            entries.firstOrNull { it.id == id }
                ?: if (id in setOf("zelda", "mario")) NETEASE else NETEASE
    }
}

enum class LyricFontFamily(val id: String, val displayName: String) {
    DEFAULT("default", "默认"),
    SANS("sans", "简约"),
    SERIF("serif", "文艺"),
    ROUND("round", "圆润");

    companion object {
        fun fromId(id: String): LyricFontFamily =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
