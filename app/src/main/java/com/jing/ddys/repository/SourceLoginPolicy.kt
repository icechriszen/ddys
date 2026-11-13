package com.jing.ddys.repository

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** Shared by the embedded browser and the temporary phone login bridge. */
object SourceLoginPolicy {
    fun sameOrigin(a: HttpUrl, b: HttpUrl): Boolean =
        a.scheme == b.scheme && a.host == b.host && a.port == b.port

    fun isSourceUrl(value: String?, source: String): Boolean {
        val url = value?.toHttpUrlOrNull() ?: return false
        val site = source.toHttpUrlOrNull() ?: return false
        return sameOrigin(url, site) && url.username.isEmpty() && url.password.isEmpty()
    }

    fun requiresLogin(html: String): Boolean = listOf(
        "login-password-protected", "password_protected_pwd", "ddys-protect-gate",
        "ddys_protect_password", "ddys_protect_action", "输入密码后才可访问网站", "<cap-widget"
    ).any { html.contains(it, ignoreCase = true) }

    fun isCatalog(html: String, url: String, source: String): Boolean =
        isSourceUrl(url, source) && !requiresLogin(html) &&
            Jsoup.parse(html).select("article, .post-box-title").isNotEmpty()
}
