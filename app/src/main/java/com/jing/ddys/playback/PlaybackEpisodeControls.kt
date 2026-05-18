package com.jing.ddys.playback

internal object PlaybackEpisodeControls {
    fun shouldShowEpisodeChooser(episodeCount: Int): Boolean = episodeCount > 1
}
