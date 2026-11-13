package com.jing.ddys.repository

import android.content.Context
import com.jing.ddys.DdysApplication
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = DdysApplication::class, manifest = Config.NONE, sdk = [34])
class SourceSessionPersistenceTest {
    @Before fun reset() { VideoSourceAuth.clearAuthCookie() }
    @After fun cleanup() { VideoSourceAuth.clearAuthCookie(); stopKoin() }

    @Test fun browserSessionPreservesUserAgentAndCookieExpiryPathAndOrigin() {
        val site = VideoSourceAuth.siteBaseUrl.toHttpUrl()
        val cookies = listOf(
            Cookie.parse(site, "gate=valid; Path=/; Secure; Max-Age=600")!!,
            Cookie.parse(site, "section=detail; Path=/film; Secure; Max-Age=600")!!,
            Cookie.parse(site, "expired=gone; Path=/; Max-Age=0")!!
        )
        VideoSourceAuth.saveBrowserSession(cookies, "PhoneBrowser/123")
        assertEquals("PhoneBrowser/123", VideoSourceAuth.browserUserAgent())
        assertEquals("gate=valid", VideoSourceAuth.getAuthCookieHeader("https://ddys.app/"))
        assertEquals("section=detail; gate=valid", VideoSourceAuth.getAuthCookieHeader("https://ddys.app/film/1"))
        assertEquals("gate=valid", VideoSourceAuth.getAuthCookieHeader("https://ddys.app/films"))
        for (url in listOf("http://ddys.app/", "https://ddys.app:444/", "https://cap.ddys.app/", "https://v2.ddys.app/")) {
            assertEquals("", VideoSourceAuth.getAuthCookieHeader(url))
        }
        val stored = DdysApplication.context.getSharedPreferences("video_source", Context.MODE_PRIVATE)
        assertTrue(stored.contains("session_cookies.ddys.app"))
        VideoSourceAuth.clearAuthCookie()
        assertEquals("", VideoSourceAuth.getAuthCookieHeader()); assertNull(VideoSourceAuth.browserUserAgent())
    }

    @Test fun existingCookieStorageStillWorksAndWebViewCanReplacePhoneSession() {
        VideoSourceAuth.saveAuthCookie("legacy=ok")
        assertEquals("legacy=ok", VideoSourceAuth.getAuthCookieHeader())
        VideoSourceAuth.saveBrowserSession(listOf(Cookie.parse(VideoSourceAuth.siteBaseUrl.toHttpUrl(), "phone=ok; Path=/")!!), "Phone/1")
        VideoSourceAuth.saveAuthCookie("webview=new", "WebView/2")
        assertEquals("webview=new", VideoSourceAuth.getAuthCookieHeader())
        assertEquals("WebView/2", VideoSourceAuth.browserUserAgent())
    }
}
