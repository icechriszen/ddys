package com.jing.ddys.watchtogether

import com.google.gson.Gson
import com.jing.ddys.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class WatchTogetherClient(
    private val baseUrl: String = BuildConfig.WATCH_TOGETHER_BASE_URL,
    private val gson: Gson = Gson(),
    private val okHttpClient: OkHttpClient = buildDefaultClient()
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
        return okHttpClient.newWebSocket(request, listener)
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("${requireHttpBaseUrl()}$path")
            .get()
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw IllegalStateException(responseErrorMessage(response.code, body))
                }
                gson.fromJson(body, T::class.java)
            }
        } catch (e: IOException) {
            throw IllegalStateException(networkErrorMessage(e), e)
        }
    }

    private suspend inline fun <reified T> post(path: String, payload: Any): T =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${requireHttpBaseUrl()}$path")
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .build()
            try {
                okHttpClient.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IllegalStateException(responseErrorMessage(response.code, body))
                    }
                    gson.fromJson(body, T::class.java)
                }
            } catch (e: IOException) {
                throw IllegalStateException(networkErrorMessage(e), e)
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

    private fun responseErrorMessage(code: Int, body: String): String {
        return when {
            code == 404 && body.contains("room_not_found") -> "房间不存在或已过期"
            code == 400 && body.contains("invalid_room_code") -> "请输入 6 位数字房间码"
            code == 400 && body.contains("invalid_room_state") -> "房间影片信息异常，请让 Host 重新创建房间"
            code == 403 && body.contains("invalid_host_token") -> "房间 Host 身份已失效，请重新创建房间"
            body.isBlank() -> "一起看服务请求失败($code)"
            else -> "一起看服务请求失败($code): $body"
        }
    }

    private fun networkErrorMessage(error: IOException): String {
        val host = runCatching { URI(requireHttpBaseUrl()).host }.getOrNull().orEmpty()
        val hostText = host.ifBlank { "一起看服务" }
        return when (error) {
            is UnknownHostException -> "无法解析一起看服务域名：$hostText"
            is SocketTimeoutException -> "连接一起看服务超时，请稍后重试"
            is ConnectException -> "无法连接一起看服务：$hostText"
            is SSLException -> "一起看服务 HTTPS 连接失败，请检查设备时间和网络"
            else -> "一起看服务网络请求失败：${error.localizedMessage ?: error.javaClass.simpleName}"
        }
    }

    companion object {
        private const val NETWORK_TIMEOUT_SECONDS = 20L

        private fun buildDefaultClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pingInterval(20, TimeUnit.SECONDS)
                .build()
        }
    }
}
