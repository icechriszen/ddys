package com.jing.ddys.watchtogether

import kotlin.math.roundToLong

object WatchTogetherRoomCode {
    private val pattern = Regex("\\d{6}")

    fun isValid(value: String): Boolean = pattern.matches(value)
}

enum class WatchTogetherRole(val wireValue: String) {
    Host("host"),
    Member("member");

    companion object {
        fun fromWireValue(value: String?): WatchTogetherRole? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class WatchTogetherRoomState(
    val roomCode: String,
    val detailPageUrl: String,
    val title: String,
    val episodeIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val playbackRate: Float,
    val paused: Boolean,
    val updatedAtMs: Long,
    val memberCount: Int
) {
    fun estimatedPositionAt(nowMs: Long): Long {
        if (paused) {
            return positionMs
        }
        val elapsedMs = (nowMs - updatedAtMs).coerceAtLeast(0L)
        val estimated = positionMs + (elapsedMs * playbackRate).roundToLong()
        return if (durationMs > 0) {
            estimated.coerceIn(0L, durationMs)
        } else {
            estimated.coerceAtLeast(0L)
        }
    }
}

data class WatchTogetherCreateRoomRequest(
    val detailPageUrl: String,
    val title: String,
    val episodeIndex: Int,
    val positionMs: Long,
    val durationMs: Long,
    val playbackRate: Float,
    val paused: Boolean,
    val updatedAtMs: Long
)

data class WatchTogetherCreateRoomResponse(
    val roomCode: String,
    val hostToken: String,
    val expiresAt: Long,
    val state: WatchTogetherRoomState
)

data class WatchTogetherHostStateRequest(
    val hostToken: String,
    val state: WatchTogetherRoomState
)

data class WatchTogetherSession(
    val roomCode: String,
    val role: WatchTogetherRole,
    val hostToken: String? = null,
    val memberCount: Int = 1
)

object WatchTogetherRoomStateValidator {
    fun requirePlayableDetailPageUrl(state: WatchTogetherRoomState): String {
        if (state.detailPageUrl.isNullOrBlank()) {
            throw IllegalStateException("房间影片信息异常，请让 Host 重新创建房间")
        }
        return state.detailPageUrl
    }
}
