package com.jing.ddys.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEpisodeControlsTest {

    @Test
    fun movieWithOneEpisodeDoesNotShowEpisodeChooser() {
        assertFalse(PlaybackEpisodeControls.shouldShowEpisodeChooser(episodeCount = 1))
    }

    @Test
    fun seriesWithMultipleEpisodesShowsEpisodeChooser() {
        assertTrue(PlaybackEpisodeControls.shouldShowEpisodeChooser(episodeCount = 2))
    }

    @Test
    fun emptyEpisodesDoNotShowEpisodeChooser() {
        assertFalse(PlaybackEpisodeControls.shouldShowEpisodeChooser(episodeCount = 0))
    }
}
