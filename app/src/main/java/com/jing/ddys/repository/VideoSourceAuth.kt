package com.jing.ddys.repository

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import android.webkit.CookieManager
import com.jing.ddys.DdysApplication
import okhttp3.HttpUrl.Companion.toHttpUrl

data class VideoSourceConfig(
    val siteBaseUrl: String = "https://ddys.app",
    val videoBaseUrl: String = "https://v2.ddys.app",
    val password: String = "ddys"
)

class SourceAuthRequiredException : RuntimeException("网站需要登录，请到设置中打开网站登录")

object VideoSourceAuth {

    private const val PREF_NAME = "video_source"
    private const val AUTH_COOKIE_KEY_PREFIX = "auth_cookie."
    private const val SESSION_COOKIE_KEY_PREFIX = "session_cookies."
    private const val USER_AGENT_KEY_PREFIX = "session_user_agent."

    val config = VideoSourceConfig()

    val siteBaseUrl: String
        get() = config.siteBaseUrl.trimEnd('/')

    val videoBaseUrl: String
        get() = config.videoBaseUrl.trimEnd('/')

    val password: String
        get() = config.password

    private val sharedPreferences
        get() = DdysApplication.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun loginUrl(redirectUrl: String = "$siteBaseUrl/"): String {
        return if (redirectUrl == "$siteBaseUrl/") "$siteBaseUrl/"
        else "$siteBaseUrl/?redirect_to=${Uri.encode(redirectUrl)}"
    }

    fun sourceHost(): String = siteBaseUrl.toHttpUrl().host

    fun saveAuthCookieFromWebView(userAgent: String): Boolean {
        val cookie = CookieManager.getInstance().getCookie(siteBaseUrl)
        if (cookie.isNullOrBlank()) {
            return false
        }
        saveAuthCookie(cookie, userAgent)
        return true
    }

    fun saveAuthCookie(cookie: String, userAgent: String? = null) {
        sharedPreferences.edit().putString(authCookieKey(), cookie)
            .remove(SESSION_COOKIE_KEY_PREFIX + sourceHost()).apply {
                if (!userAgent.isNullOrBlank()) putString(USER_AGENT_KEY_PREFIX + sourceHost(), userAgent)
            }.apply()
    }

    fun saveBrowserSession(cookies: List<Cookie>, userAgent: String) {
        sharedPreferences.edit().remove(authCookieKey())
            .putString(SESSION_COOKIE_KEY_PREFIX + sourceHost(), Gson().toJson(cookies.map { it.toString() }))
            .putString(USER_AGENT_KEY_PREFIX + sourceHost(), userAgent).apply()
        // A TV without WebView can still use the native session. This optional copy
        // only makes a later switch to the embedded browser convenient.
        runCatching {
            val manager = CookieManager.getInstance()
            cookies.forEach { manager.setCookie(siteBaseUrl, it.toString()) }
            manager.flush()
        }
    }

    fun getAuthCookieHeader(url: String = siteBaseUrl): String {
        val target = url.toHttpUrlOrNull() ?: return ""
        if (!SourceLoginPolicy.sameOrigin(target, siteBaseUrl.toHttpUrl())) return ""
        val stored = sharedPreferences.getString(SESSION_COOKIE_KEY_PREFIX + sourceHost(), null)
        if (stored != null) {
            val values = runCatching { Gson().fromJson(stored, Array<String>::class.java).toList() }.getOrDefault(emptyList())
            return values.mapNotNull { Cookie.parse(siteBaseUrl.toHttpUrl(), it) }
                .filter { it.expiresAt > System.currentTimeMillis() && it.matches(target) }
                .sortedByDescending { it.path.length }.joinToString("; ") { "${it.name}=${it.value}" }
        }
        return sharedPreferences.getString(authCookieKey(), "") ?: ""
    }

    fun browserUserAgent(): String? = sharedPreferences.getString(USER_AGENT_KEY_PREFIX + sourceHost(), null)

    fun hasAuthCookie(): Boolean = getAuthCookieHeader().isNotBlank()

    fun clearAuthCookie() {
        sharedPreferences.edit().remove(authCookieKey())
            .remove(SESSION_COOKIE_KEY_PREFIX + sourceHost()).remove(USER_AGENT_KEY_PREFIX + sourceHost()).apply()
    }

    fun isLoginUrl(url: String?): Boolean {
        return url?.contains("password-protected=login") == true
    }

    fun isPasswordProtectedHtml(html: String): Boolean = SourceLoginPolicy.requiresLogin(html)

    fun ensureNotPasswordProtected(html: String, url: String? = null) {
        if (isLoginUrl(url) || isPasswordProtectedHtml(html)) {
            throw SourceAuthRequiredException()
        }
    }

    fun resolveSiteUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl
        }
        return if (pathOrUrl.startsWith("/")) {
            siteBaseUrl + pathOrUrl
        } else {
            "$siteBaseUrl/$pathOrUrl"
        }
    }

    fun resolveVideoUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl
        }
        val path = if (pathOrUrl.startsWith("/")) {
            pathOrUrl
        } else {
            "/$pathOrUrl"
        }
        val versionedVideoBaseUrl = Regex("^/v(\\d+)(/|$)").find(path)
            ?.groupValues
            ?.getOrNull(1)
            ?.let { "https://v$it.ddys.app" }
        return (versionedVideoBaseUrl ?: videoBaseUrl) + path
    }

    private fun authCookieKey(): String = AUTH_COOKIE_KEY_PREFIX + sourceHost()
}
