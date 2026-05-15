package com.jing.ddys.compose.screen

import com.jing.ddys.repository.VideoEpisode

internal data class EpisodeGroup(
    val title: String,
    val episodes: List<IndexedValue<VideoEpisode>>,
    val showSeasonInEpisodeName: Boolean
)

internal fun List<VideoEpisode>.toEpisodeGroups(): List<EpisodeGroup> {
    if (none { it.seasonName.isNotBlank() }) {
        return listOf(
            EpisodeGroup(
                title = "选集",
                episodes = withIndex().toList(),
                showSeasonInEpisodeName = false
            )
        )
    }

    return withIndex().groupBy { it.value.seasonName }.map { (seasonName, episodes) ->
        EpisodeGroup(
            title = VideoEpisode.formatSeasonName(seasonName).ifBlank { "选集" },
            episodes = episodes,
            showSeasonInEpisodeName = false
        )
    }
}
