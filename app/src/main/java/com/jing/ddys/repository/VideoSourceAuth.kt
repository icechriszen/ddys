package com.jing.ddys.repository

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import com.jing.ddys.DdysApplication
import okhttp3.HttpUrl.Companion.toHttpUrl

data class VideoSourceConfig(
    val siteBaseUrl: String = "https://ddys.app",
    val videoBaseUrl: String = "https://v2.ddys.app",
    val password: String = "ddys"
)

class SourceAuthRequiredException : RuntimeException("视频源需要登录，请到设置中打开视频源登录")

object VideoSourceAuth {

    private const val PREF_NAME = "video_source"
    private const val AUTH_COOKIE_KEY_PREFIX = "auth_cookie."

    val config = VideoSourceConfig()

    val siteBaseUrl: String
        get() = config.siteBaseUrl.trimEnd('/')

    val videoBaseUrl: String
        get() = config.videoBaseUrl.trimEnd('/')

    val password: String
        get() = config.password

    private val sharedPreferences by lazy {
        DdysApplication.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun loginUrl(redirectUrl: String = "$siteBaseUrl/"): String {
        return "$siteBaseUrl/?password-protected=login&redirect_to=${Uri.encode(redirectUrl)}"
    }

    fun sourceHost(): String = siteBaseUrl.toHttpUrl().host

    fun saveAuthCookieFromWebView(): Boolean {
        val cookie = CookieManager.getInstance().getCookie(siteBaseUrl)
        if (cookie.isNullOrBlank()) {
            return false
        }
        saveAuthCookie(cookie)
        return true
    }

    fun saveAuthCookie(cookie: String) {
        sharedPreferences.edit().putString(authCookieKey(), cookie).apply()
    }

    fun getAuthCookieHeader(): String {
        return sharedPreferences.getString(authCookieKey(), "") ?: ""
    }

    fun hasAuthCookie(): Boolean = getAuthCookieHeader().isNotBlank()

    fun clearAuthCookie() {
        sharedPreferences.edit().remove(authCookieKey()).apply()
    }

    fun isLoginUrl(url: String?): Boolean {
        return url?.contains("password-protected=login") == true
    }

    fun isPasswordProtectedHtml(html: String): Boolean {
        return html.contains("login-password-protected") ||
            html.contains("password_protected_pwd") ||
            html.contains("输入密码后才可访问网站")
    }

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
