package com.jing.ddys.compose.screen

import com.jing.ddys.repository.VideoEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EpisodeGroupsTest {

    @Test
    fun movieEpisodeGroupUsesNoSelectionTitle() {
        val groups = listOf(
            VideoEpisode(
                id = "movie",
                name = "正片",
                subTitleUrl = "",
                seasonName = "1"
            )
        ).toEpisodeGroups()

        assertEquals(1, groups.size)
        assertEquals("", groups.single().title)
        assertFalse(groups.single().showSeasonInEpisodeName)
    }

    @Test
    fun singleSeasonSeriesEpisodesUseDefaultSelectionTitle() {
        val groups = listOf(
            VideoEpisode(id = "episode-1", name = "第1集", subTitleUrl = "", seasonName = "1"),
            VideoEpisode(id = "episode-2", name = "第2集", subTitleUrl = "", seasonName = "1")
        ).toEpisodeGroups()

        assertEquals(1, groups.size)
        assertEquals("选集", groups.single().title)
        assertFalse(groups.single().showSeasonInEpisodeName)
    }

    @Test
    fun multipleSeasonEpisodesShowSeasonTitles() {
        val groups = listOf(
            VideoEpisode(id = "s1e1", name = "第1集", subTitleUrl = "", seasonName = "1"),
            VideoEpisode(id = "s2e1", name = "第1集", subTitleUrl = "", seasonName = "2")
        ).toEpisodeGroups()

        assertEquals(listOf("第1季", "第2季"), groups.map { it.title })
    }
}
