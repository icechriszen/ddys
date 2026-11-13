package com.jing.ddys.setting

import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockResponse
import com.jing.ddys.repository.SourceLoginPolicy
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class BrowserLoginBridgeTest {
    private fun fixture(block: (String) -> Unit) {
        val server = MockWebServer()
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val site = server.url("/").toString()
        fun gate() = """<html><head><script src='/widget.js'></script></head><body class='ddys-protect-gate'>
            <h1>Owned fixture</h1><a href='/member/login'>Member</a><form method='post' action='/'>
            <input name='ddys_protect_password'><input type='hidden' name='nonce' value='fixture'>
            <cap-widget data-cap-api-endpoint='/cap/'></cap-widget><button>Enter</button></form></body></html>"""
        server.dispatcher = object : Dispatcher() {
          override fun dispatch(exchange: RecordedRequest): MockResponse {
            val path = exchange.path
            val body = exchange.body.readUtf8()
            val reply = MockResponse()
            val (type, response) = when {
                path == "/widget.js" -> "application/javascript" to "/* original fixture script */"
                path == "/assets/hashwx.wasm" -> "application/wasm" to "wasm-fixture"
                path == "/cap/challenge" -> "application/json" to """{"origin":"${exchange.getHeader("Origin")}"}"""
                exchange.method == "POST" && body.contains("cap-token=owned-proof") && body.contains("nonce=fixture") -> {
                    reply.addHeader("Set-Cookie", "session=accepted; Path=/; HttpOnly")
                    "text/html" to "<article>Local catalog</article>"
                }
                exchange.getHeader("Cookie")?.contains("session=accepted") == true ->
                    "text/html" to "<article>Local catalog</article>"
                else -> "text/html" to gate()
            }
            return reply.setHeader("Content-Type", type).setBody(response)
          }
        }
        try { block(site) } finally { server.shutdown() }
    }
    private val browser = OkHttpClient()
    private fun get(url: String) = browser.newCall(Request.Builder().url(url).header("User-Agent", "OwnedBrowser/1").build()).execute()

    @Test fun phoneLoginPreservesWidgetAndCommitsOnlyVerifiedSession() = fixture { site ->
        val success = CountDownLatch(1)
        val saved = AtomicReference<List<Cookie>>()
        val savedUA = AtomicReference<String>()
        BrowserLoginBridge(site, OkHttpClient(), { cookies, ua -> saved.set(cookies); savedUA.set(ua); success.countDown() }).use { bridge ->
            val url = bridge.start(InetAddress.getByName("127.0.0.1"))
            val local = url.toHttpUrl()
            val origin = "http://${local.host}:${local.port}"
            val (html, pairingCookie) = get(url).use {
                assertEquals(200, it.code)
                assertEquals("same-origin", it.header("Referrer-Policy"))
                it.body!!.string() to it.header("Set-Cookie")!!.substringBefore(';')
            }
            val doc = Jsoup.parse(html)
            assertTrue(doc.select("a").isEmpty())
            assertTrue(html.contains("ddys-verification-error"))
            get(origin + doc.selectFirst("script[src]")!!.attr("src")).use {
                assertEquals("/* original fixture script */", it.body!!.string())
            }
            val endpoint = doc.selectFirst("cap-widget")!!.attr("data-cap-api-endpoint")
            browser.newCall(Request.Builder().url(endpoint + "challenge").header("Origin", origin)
                .post(ByteArray(0).toRequestBody(null)).build()).execute().use {
                assertEquals(200, it.code); assertTrue(it.body!!.string().contains(site.trimEnd('/')))
            }
            get(origin + "/assets/hashwx.wasm").use { assertEquals(404, it.code) }
            browser.newCall(Request.Builder().url(origin + "/assets/hashwx.wasm").header("Cookie", pairingCookie).build()).execute().use {
                assertEquals("wasm-fixture", it.body!!.string())
            }
            val submit = origin + doc.selectFirst("form")!!.attr("action")
            browser.newCall(Request.Builder().url(submit).header("Origin", origin)
                .post(FormBody.Builder().add("ddys_protect_password", "ddys").build()).build()).execute().use {
                assertEquals(200, it.code); assertTrue(it.body!!.string().contains("cap-widget"))
            }
            assertNull(saved.get())
            browser.newCall(Request.Builder().url(submit).header("Origin", origin)
                .post(FormBody.Builder().add("nonce", "fixture").add("cap-token", "owned-proof").build()).build()).execute().use {
                assertEquals(200, it.code); assertTrue(it.body!!.string().contains("验证成功")); assertNull(it.header("Set-Cookie"))
            }
            assertTrue(success.await(2, TimeUnit.SECONDS))
            assertEquals("accepted", saved.get().single().value)
            assertEquals("OwnedBrowser/1", savedUA.get())
        }
    }

    @Test fun rejectsForgedOriginHostAndUnknownPaths() = fixture { site ->
        BrowserLoginBridge(site, OkHttpClient(), { _, _ -> fail("Must not authenticate") }).use { bridge ->
            val url = bridge.start(InetAddress.getByName("127.0.0.1"))
            for (origin in listOf("null", "https://evil.example")) {
                browser.newCall(Request.Builder().url(url).header("Origin", origin).build()).execute().use { assertEquals(403, it.code) }
            }
            browser.newCall(Request.Builder().url(url).header("Host", "evil.example").build()).execute().use { assertEquals(403, it.code) }
            get(url.toHttpUrl().newBuilder().encodedPath("/unknown").build().toString()).use { assertEquals(404, it.code) }
            bridge.close()
            assertTrue(runCatching { get(url).close() }.isFailure)
        }
    }

    @Test fun expiresAndClosesListener() = fixture { site ->
        val expired = CountDownLatch(1)
        BrowserLoginBridge(site, OkHttpClient(), { _, _ -> }, { expired.countDown() }, 100).use { bridge ->
            bridge.start(InetAddress.getByName("127.0.0.1"))
            assertTrue(expired.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun rejectsUnknownHtmlAndLoginPagesEvenWithCatalogMarkup() {
        val site = "https://ddys.app/"
        assertFalse(SourceLoginPolicy.isCatalog("<html>challenge</html>", site, site))
        assertFalse(SourceLoginPolicy.isCatalog("<article>fake</article><cap-widget></cap-widget>", site, site))
        assertFalse(SourceLoginPolicy.isCatalog("<article>catalog</article>", "https://other.example/", site))
        assertTrue(SourceLoginPolicy.isCatalog("<article>catalog</article>", site, site))
        assertFalse(SourceLoginPolicy.isSourceUrl("http://ddys.app/", site))
        assertFalse(SourceLoginPolicy.isSourceUrl("https://ddys.app:444/", site))
    }
}
