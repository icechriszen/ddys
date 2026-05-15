package com.jing.ddys.repository


data class VideoDetailInfo(
    val id: String,
    val title: String,
    val coverUrl: String,
    val seasons: List<VideoSeason>,
    val episodes: List<VideoEpisode>,
    val relatedVideo: List<VideoCardInfo>,
    val rating: String,
    val infoRows: List<String>,
    val description: String,
    val detailPageUrl: String
) : java.io.Serializable

data class VideoSeason(
    val seasonName: String, val seasonUrl: String?, val currentSeason: Boolean
) : java.io.Serializable

data class VideoEpisode(
    val id: String,
    val name: String,
    val subTitleUrl: String,
    val seasonName: String = "",
    val src0: String = "",
    val src1: String = ""
) : java.io.Serializable {
    val displayName: String
        get() = if (seasonName.isBlank()) {
            name
        } else {
            "${formatSeasonName(seasonName)} $name"
        }

    companion object {
        fun formatSeasonName(seasonName: String): String {
            val normalized = seasonName.trim()
            return when {
                normalized.isBlank() -> ""
                normalized.startsWith("第") && normalized.endsWith("季") -> normalized
                normalized.endsWith("季") -> normalized
                else -> "第${normalized}季"
            }
        }
    }
}
