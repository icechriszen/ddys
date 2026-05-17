package com.jing.ddys.watchtogether

import androidx.lifecycle.ViewModel
import com.jing.ddys.repository.VideoDetailInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class WatchTogetherViewModel(
    private val client: WatchTogetherClient
) : ViewModel() {
    private val _session = MutableStateFlow<WatchTogetherSession?>(null)
    val session: StateFlow<WatchTogetherSession?> = _session.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    suspend fun createRoom(
        videoDetail: VideoDetailInfo,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float,
        paused: Boolean
    ): WatchTogetherSession {
        val now = System.currentTimeMillis()
        val response = client.createRoom(
            WatchTogetherCreateRoomRequest(
                detailPageUrl = videoDetail.detailPageUrl,
                title = videoDetail.title,
                episodeIndex = episodeIndex,
                positionMs = positionMs,
                durationMs = durationMs,
                playbackRate = playbackRate,
                paused = paused,
                updatedAtMs = now
            )
        )
        val session = WatchTogetherSession(
            roomCode = response.roomCode,
            role = WatchTogetherRole.Host,
            hostToken = response.hostToken,
            memberCount = response.state.memberCount
        )
        _session.value = session
        _errorMessage.value = null
        return session
    }

    suspend fun joinRoom(roomCode: String): WatchTogetherRoomState {
        val state = client.getRoom(roomCode)
        _session.value = WatchTogetherSession(
            roomCode = roomCode,
            role = WatchTogetherRole.Member,
            memberCount = state.memberCount
        )
        _errorMessage.value = null
        return state
    }

    fun attachSession(roomCode: String, role: WatchTogetherRole, hostToken: String? = null) {
        if (!WatchTogetherRoomCode.isValid(roomCode)) {
            return
        }
        _session.value = WatchTogetherSession(
            roomCode = roomCode,
            role = role,
            hostToken = hostToken
        )
    }

    fun leaveRoom() {
        _session.value = null
        _errorMessage.value = null
    }

    suspend fun publishHostState(
        videoDetail: VideoDetailInfo,
        episodeIndex: Int,
        positionMs: Long,
        durationMs: Long,
        playbackRate: Float,
        paused: Boolean
    ) {
        val session = _session.value ?: return
        if (session.role != WatchTogetherRole.Host || session.hostToken.isNullOrBlank()) {
            return
        }
        val state = WatchTogetherRoomState(
            roomCode = session.roomCode,
            detailPageUrl = videoDetail.detailPageUrl,
            title = videoDetail.title,
            episodeIndex = episodeIndex,
            positionMs = positionMs,
            durationMs = durationMs,
            playbackRate = playbackRate,
            paused = paused,
            updatedAtMs = System.currentTimeMillis(),
            memberCount = session.memberCount
        )
        runCatching {
            client.updateHostState(session.roomCode, session.hostToken, state)
        }.onSuccess {
            _session.value = session.copy(memberCount = it.memberCount)
            _errorMessage.value = null
        }.onFailure {
            _errorMessage.value = it.message ?: "同步房间状态失败"
        }
    }

    suspend fun refreshRoomState(roomCode: String): WatchTogetherRoomState? {
        return runCatching {
            client.getRoom(roomCode)
        }.onSuccess {
            _session.value = _session.value?.copy(memberCount = it.memberCount)
            _errorMessage.value = null
        }.onFailure {
            _errorMessage.value = it.message ?: "获取房间状态失败"
        }.getOrNull()
    }
}
