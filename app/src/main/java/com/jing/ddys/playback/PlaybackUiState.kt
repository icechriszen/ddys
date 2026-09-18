package com.jing.ddys.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters

data class PlaybackUiState(
    val episodeIndex: Int,
    val positionMs: Long?,
    val playWhenReady: Boolean,
    val speed: Float = 1f,
    val pitch: Float = 1f,
    val trackSelectionParameters: TrackSelectionParameters? = null
)

internal fun playbackStartPosition(state: PlaybackUiState?, historyPosition: Long, duration: Long): Long {
    // A captured zero is deliberate (e.g. a replay), not an absent resume position.
    state?.positionMs?.let { return it.coerceAtLeast(0) }
    return if (historyPosition > 0 && !(duration > 0 && duration - historyPosition < 10_000)) {
        historyPosition
    } else {
        0L
    }
}

internal fun Player.restorePlaybackUiState(state: PlaybackUiState?, history: VideoUrlWithHistory) {
    seekTo(playbackStartPosition(state, history.lastPlayPosition, history.videoDuration))
    if (state != null) {
        playbackParameters = PlaybackParameters(state.speed, state.pitch)
        state.trackSelectionParameters?.let { trackSelectionParameters = it }
    }
    playWhenReady = state?.playWhenReady ?: true
}
