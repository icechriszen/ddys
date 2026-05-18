package com.jing.ddys.compose.screen

import com.jing.ddys.repository.VideoEpisode

internal data class EpisodeGroup(
    val title: String,
    val episodes: List<IndexedValue<VideoEpisode>>,
    val showSeasonInEpisodeName: Boolean
)

internal fun List<VideoEpisode>.toEpisodeGroups(): List<EpisodeGroup> {
    val seasonNames = map { it.seasonName }.filter { it.isNotBlank() }.distinct()
    if (seasonNames.size <= 1) {
        return listOf(
            EpisodeGroup(
                title = if (size > 1) "选集" else "",
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
