package com.jing.ddys.watchtogether

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTogetherModelsTest {

    @Test
    fun roomCodeMustBeExactlySixDigits() {
        assertTrue(WatchTogetherRoomCode.isValid("123456"))

        assertFalse(WatchTogetherRoomCode.isValid("12345"))
        assertFalse(WatchTogetherRoomCode.isValid("1234567"))
        assertFalse(WatchTogetherRoomCode.isValid("12345a"))
        assertFalse(WatchTogetherRoomCode.isValid(" 123456"))
    }

    @Test
    fun playingSnapshotAdvancesByElapsedTimeAndPlaybackRate() {
        val state = WatchTogetherRoomState(
            roomCode = "123456",
            detailPageUrl = "https://ddys.example/video",
            title = "Title",
            episodeIndex = 1,
            positionMs = 10_000L,
            durationMs = 60_000L,
            playbackRate = 1.5f,
            paused = false,
            updatedAtMs = 1_000L,
            memberCount = 2
        )

        assertEquals(13_000L, state.estimatedPositionAt(nowMs = 3_000L))
    }

    @Test
    fun pausedSnapshotKeepsOriginalPosition() {
        val state = WatchTogetherRoomState(
            roomCode = "123456",
            detailPageUrl = "https://ddys.example/video",
            title = "Title",
            episodeIndex = 1,
            positionMs = 10_000L,
            durationMs = 60_000L,
            playbackRate = 2f,
            paused = true,
            updatedAtMs = 1_000L,
            memberCount = 2
        )

        assertEquals(10_000L, state.estimatedPositionAt(nowMs = 3_000L))
    }
}
