package com.jing.ddys.watchtogether

import com.google.gson.Gson
import com.jing.ddys.BuildConfig
import com.jing.ddys.repository.HttpUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.net.URLEncoder

class WatchTogetherClient(
    private val baseUrl: String = BuildConfig.WATCH_TOGETHER_BASE_URL,
    private val gson: Gson = Gson()
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun createRoom(request: WatchTogetherCreateRoomRequest): WatchTogetherCreateRoomResponse {
        return post("/rooms", request)
    }

    suspend fun getRoom(roomCode: String): WatchTogetherRoomState {
        requireValidRoomCode(roomCode)
        return get("/rooms/$roomCode")
    }

    suspend fun updateHostState(
        roomCode: String,
        hostToken: String,
        state: WatchTogetherRoomState
    ): WatchTogetherRoomState {
        requireValidRoomCode(roomCode)
        return post(
            "/rooms/$roomCode/state",
            WatchTogetherHostStateRequest(hostToken = hostToken, state = state)
        )
    }

    fun openRoomSocket(
        roomCode: String,
        role: WatchTogetherRole,
        token: String?,
        listener: WebSocketListener
    ): WebSocket {
        requireValidRoomCode(roomCode)
        val encodedRole = URLEncoder.encode(role.wireValue, "UTF-8")
        val encodedToken = URLEncoder.encode(token.orEmpty(), "UTF-8")
        val request = Request.Builder()
            .url("${requireWebSocketBaseUrl()}/rooms/$roomCode/ws?role=$encodedRole&token=$encodedToken")
            .build()
        return HttpUtil.okHttpClient.newWebSocket(request, listener)
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${requireHttpBaseUrl()}$path")
            .get()
            .build()
        HttpUtil.okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException(body.ifBlank { "一起看服务请求失败:${response.code}" })
            }
            gson.fromJson(body, T::class.java)
        }
    }

    private suspend inline fun <reified T> post(path: String, payload: Any): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${requireHttpBaseUrl()}$path")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            HttpUtil.okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(body.ifBlank { "一起看服务请求失败:${response.code}" })
                }
                gson.fromJson(body, T::class.java)
            }
        }

    private fun requireValidRoomCode(roomCode: String) {
        require(WatchTogetherRoomCode.isValid(roomCode)) { "请输入 6 位数字房间码" }
    }

    private fun requireHttpBaseUrl(): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        check(trimmed.isNotEmpty()) { "未配置一起看服务地址" }
        return trimmed
    }

    private fun requireWebSocketBaseUrl(): String {
        val httpBase = requireHttpBaseUrl()
        return when {
            httpBase.startsWith("https://") -> httpBase.replaceFirst("https://", "wss://")
            httpBase.startsWith("http://") -> httpBase.replaceFirst("http://", "ws://")
            else -> error("一起看服务地址必须以 http:// 或 https:// 开头")
        }
    }
}
