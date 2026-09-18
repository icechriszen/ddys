package com.jing.ddys.playback

import android.net.Uri
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import com.jing.ddys.DdysApplication
import com.jing.ddys.repository.VideoDetailInfo
import com.jing.ddys.repository.VideoEpisode
import com.jing.ddys.repository.VideoUrl
import com.jing.ddys.repository.VideoUrlType
import com.jing.ddys.room.dao.EpisodeHistoryDao
import com.jing.ddys.room.dao.VideoHistoryDao
import com.jing.ddys.room.entity.EpisodeHistory
import java.lang.reflect.Proxy
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = DdysApplication::class, manifest = Config.NONE, sdk = [34])
class PlaybackUiStateTest {
    private val store = ViewModelStore()
    private lateinit var model: PlaybackViewModel

    @Before
    fun setUp() {
        val video = VideoDetailInfo(
            id = "video", title = "Test video", coverUrl = "", seasons = emptyList(),
            episodes = (0..1).map { VideoEpisode("ep-$it", "$it", "", src0 = "http://127.0.0.1/video.mp4") },
            relatedVideo = emptyList(), rating = "", infoRows = emptyList(), description = "",
            detailPageUrl = "https://example.invalid/video"
        )
        val historyDao = object : EpisodeHistoryDao {
            override suspend fun save(history: EpisodeHistory) = Unit
            override suspend fun queryHistoryByEpisodeId(id: String): EpisodeHistory? = awaitCancellation()
            override fun queryLatestProgress(videoId: String): EpisodeHistory? = null
        }
        val videoDao = Proxy.newProxyInstance(
            VideoHistoryDao::class.java.classLoader, arrayOf(VideoHistoryDao::class.java)
        ) { _, _, _ -> Unit } as VideoHistoryDao
        model = PlaybackViewModel(video, 0, historyDao, videoDao)
        store.put("playback", model)
    }

    @After
    fun tearDown() {
        store.clear()
        shadowOf(Looper.getMainLooper()).idle()
        stopKoin()
    }

    @Test
    fun switchingControlsRestoresExactPositionPausedStateSpeedAndSubtitles() {
        val old = TestPlayer(position = 64_321, playWhenReady = false, speed = 1.75f)
        model.capturePlaybackUiState(old.player, loadedEpisodeIndex = 0)
        val captured = model.consumePlaybackUiState(0)!!
        val replacement = TestPlayer()
        replacement.player.restorePlaybackUiState(captured, history(0))

        assertEquals(64_321L, replacement.position)
        assertFalse(replacement.playWhenReady)
        assertEquals(1.75f, replacement.parameters.speed)
        assertEquals(old.tracks, replacement.tracks)
        assertNull(model.consumePlaybackUiState(0))
    }

    @Test
    fun replayAtZeroDoesNotJumpBackToStoredHistory() {
        val old = TestPlayer(position = 0, playWhenReady = true)
        model.capturePlaybackUiState(old.player, loadedEpisodeIndex = 0)
        val replacement = TestPlayer()
        replacement.player.restorePlaybackUiState(model.consumePlaybackUiState(0), history(90_000))
        assertEquals(0L, replacement.position)
        assertTrue(replacement.playWhenReady)
    }

    @Test
    fun switchingNearTheEndPreservesPositionInsteadOfRestartingEpisode() {
        val captured = PlaybackUiState(0, 118_000, true)
        assertEquals(118_000L, playbackStartPosition(captured, 115_000, 120_000))
        assertEquals(0L, playbackStartPosition(null, 115_000, 120_000))
    }

    @Test
    fun rapidSecondSwitchBeforeMediaLoadsDoesNotOverwritePendingRestore() {
        model.capturePlaybackUiState(TestPlayer(position = 42_123, playWhenReady = false).player, 0)
        model.capturePlaybackUiState(TestPlayer(mediaItemCount = 0).player, null)
        val captured = model.consumePlaybackUiState(0)!!
        assertEquals(42_123L, captured.positionMs)
        assertFalse(captured.playWhenReady)
    }

    @Test
    fun oldEpisodeRestoreIsNotAppliedAfterChangingEpisode() {
        model.capturePlaybackUiState(TestPlayer(position = 42_123).player, 0)
        model.changePlayVideoIndex(1)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, model.videoIndex.value)
        assertNull(model.consumePlaybackUiState(1))
    }

    @Test
    fun switchingDuringEpisodeLoadingUsesTheNewEpisodesHistory() {
        model.changePlayVideoIndex(1)
        shadowOf(Looper.getMainLooper()).idle()
        model.capturePlaybackUiState(TestPlayer(position = 88_000).player, loadedEpisodeIndex = 0)
        val captured = model.consumePlaybackUiState(1)!!
        assertNull(captured.positionMs)
        assertEquals(25_000L, playbackStartPosition(captured, 25_000, 120_000))
    }

    private fun history(position: Long) = VideoUrlWithHistory(
        episodeIndex = 0,
        url = VideoUrl(VideoUrlType.URL, Uri.EMPTY),
        lastPlayPosition = position,
        videoDuration = 120_000
    )

    private class TestPlayer(
        var position: Long = 0,
        var playWhenReady: Boolean = true,
        speed: Float = 1f,
        mediaItemCount: Int = 1
    ) {
        var parameters = PlaybackParameters(speed)
        var tracks = TrackSelectionParameters.Builder(DdysApplication.context)
            .setPreferredTextLanguage("zh").build()
        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getCurrentPosition" -> position
                "getDuration" -> 120_000L
                "getMediaItemCount" -> mediaItemCount
                "getPlayWhenReady" -> playWhenReady
                "getPlaybackParameters" -> parameters
                "getTrackSelectionParameters" -> tracks
                "setPlayWhenReady" -> { playWhenReady = args[0] as Boolean; null }
                "setPlaybackParameters" -> { parameters = args[0] as PlaybackParameters; null }
                "setTrackSelectionParameters" -> { tracks = args[0] as TrackSelectionParameters; null }
                "seekTo" -> { position = args.last() as Long; null }
                else -> error("Unexpected Player call: ${method.name}")
            }
        } as Player
    }
}
