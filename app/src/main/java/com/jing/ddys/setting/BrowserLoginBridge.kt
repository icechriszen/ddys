package com.jing.ddys.setting

import com.jing.ddys.repository.SourceLoginPolicy
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Runs the unchanged source widget in the user's phone browser; never solves it. */
class BrowserLoginBridge(
    source: String,
    baseClient: OkHttpClient,
    private val onAuthenticated: (List<Cookie>, String) -> Unit,
    private val onExpired: () -> Unit = {},
    private val lifetimeMillis: Long = 10 * 60 * 1000L
) : Closeable {
    private val site = source.toHttpUrl()
    private val cookies = SourceCookies(site)
    private val client = baseClient.newBuilder().apply {
        // Keep the configured proxy, but do not inherit the app's old cookies/UA.
        interceptors().clear(); networkInterceptors().clear()
    }.cookieJar(cookies).followRedirects(false).followSslRedirects(false)
        .dispatcher(Dispatcher()).connectionPool(ConnectionPool())
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS).build()
    private val token = UUID.randomUUID().toString()
    private val prefix = "/$token/"
    private val assets = ConcurrentHashMap<String, HttpUrl>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val calls = ConcurrentHashMap.newKeySet<Call>()
    private val stopped = AtomicBoolean(false)
    private val authenticated = AtomicBoolean(false)
    private val submitting = AtomicBoolean(false)
    private val workers = ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, ArrayBlockingQueue(12))
    private val timer = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var listener: ServerSocket? = null
    @Volatile private var origin = ""
    @Volatile private var authority = ""
    @Volatile private var cap: HttpUrl? = null
    @Volatile private var action: HttpUrl? = null
    @Volatile private var userAgent = ""
    @Volatile private var deadline = 0L

    fun start(address: InetAddress = localAddress() ?: error("请将设备和手机连接到同一个 Wi-Fi")): String {
        check(listener == null && !stopped.get())
        val server = ServerSocket(0, 12, address)
        listener = server
        authority = "${address.hostAddress}:${server.localPort}"
        origin = "http://$authority"
        deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lifetimeMillis)
        timer.schedule({ if (!stopped.get()) { onExpired(); close() } }, lifetimeMillis, TimeUnit.MILLISECONDS)
        Thread({
            while (!stopped.get()) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets.add(socket)
                try { workers.execute { serve(socket) } }
                catch (_: Exception) { sockets.remove(socket); socket.close() }
            }
        }, "ddys-phone-login").apply { isDaemon = true }.start()
        return origin + prefix
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        runCatching { listener?.close() }; listener = null
        calls.forEach { it.cancel() }
        sockets.forEach { runCatching { it.close() } }; sockets.clear()
        workers.shutdownNow(); timer.shutdownNow()
        client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }

    private fun serve(socket: Socket) {
        val timeout = try {
            timer.schedule({ runCatching { socket.close() } }, 45, TimeUnit.SECONDS)
        } catch (_: java.util.concurrent.RejectedExecutionException) {
            sockets.remove(socket); runCatching { socket.close() }; return
        }
        try {
            socket.soTimeout = 15_000
            val request = readRequest(socket)
            val reply = try { respond(request) } catch (_: Exception) {
                Reply.message("暂时无法连接网站，请稍后刷新此页面重试。", 502)
            }
            val headers = linkedMapOf(
                "Content-Type" to reply.type, "Content-Length" to reply.body.size.toString(),
                "Connection" to "close", "Cache-Control" to "no-store",
                // Safari form POSTs use Origin: null under no-referrer.
                "Referrer-Policy" to "same-origin", "X-Content-Type-Options" to "nosniff", "X-Frame-Options" to "DENY"
            ).apply { putAll(reply.headers) }
            socket.getOutputStream().apply {
                write(("HTTP/1.1 ${reply.status} Response\r\n" + headers.entries.joinToString("") {
                    "${it.key}: ${it.value}\r\n"
                } + "\r\n").toByteArray(Charsets.US_ASCII))
                write(reply.body); flush()
            }
            if (reply.success && !stopped.get() && authenticated.compareAndSet(false, true)) {
                onAuthenticated(cookies.snapshot(), userAgent)
                close()
            }
        } catch (_: Exception) {
            // A phone can cancel requests by reloading or closing its tab.
        } finally { timeout.cancel(false); sockets.remove(socket); runCatching { socket.close() } }
    }

    private data class Incoming(val method: String, val path: String, val headers: Map<String, String>, val body: ByteArray)
    private fun readRequest(socket: Socket): Incoming {
        val input = socket.getInputStream().buffered()
        val header = ByteArrayOutputStream()
        while (true) {
            val byte = input.read(); require(byte >= 0 && header.size() < 16384)
            header.write(byte)
            if (byte == 10 && header.size() >= 4) {
                val data = header.toByteArray()
                if (data.takeLast(4) == listOf<Byte>(13, 10, 13, 10)) break
            }
        }
        val lines = header.toString("US-ASCII").split("\r\n")
        val first = lines.first().split(' '); require(first.size == 3)
        val fields = mutableMapOf<String, String>()
        lines.drop(1).filter { it.isNotEmpty() }.forEach {
            val at = it.indexOf(':'); require(at > 0)
            val key = it.substring(0, at).lowercase()
            require(key !in fields); fields[key] = it.substring(at + 1).trim()
        }
        require("transfer-encoding" !in fields)
        val length = (fields["content-length"] ?: "0").toInt(); require(length in 0..262144)
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) { val n = input.read(body, offset, length - offset); require(n > 0); offset += n }
        return Incoming(first[0], first[1], fields, body)
    }

    private data class Reply(
        val body: ByteArray, val status: Int = 200, val type: String = "text/html; charset=utf-8",
        val headers: Map<String, String> = emptyMap(), val success: Boolean = false
    ) {
        companion object {
            fun message(text: String, status: Int = 200, success: Boolean = false) = Reply(
                ("<!doctype html><html lang='zh-CN'><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>" +
                    "<title>电视登录</title><body style='font:18px system-ui;padding:32px;background:#202020;color:white'>" +
                    "<h1>电视登录</h1><p>$text</p></body></html>").toByteArray(), status = status, success = success)
        }
    }
    private data class Upstream(val body: ByteArray, val code: Int, val type: String, val url: HttpUrl)
    private fun fetch(url: HttpUrl, method: String = "GET", body: ByteArray? = null, json: Boolean = false): Upstream {
        var target = url; var verb = method; var content = body
        repeat(9) {
            check(!stopped.get())
            val builder = Request.Builder().url(target).header("User-Agent", userAgent.ifBlank { "DDYS" })
                .header("Referer", site.toString()).header("Accept-Encoding", "identity")
            if (json) builder.header("Origin", site.newBuilder().encodedPath("/").query(null).build().toString().trimEnd('/'))
            builder.method(verb, if (verb == "POST") (content ?: ByteArray(0)).toRequestBody(
                (if (json) "application/json" else "application/x-www-form-urlencoded; charset=UTF-8").toMediaType()) else null)
            val call = client.newCall(builder.build()); calls.add(call)
            try {
                call.execute().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        val next = target.resolve(response.header("Location") ?: error("Missing redirect")) ?: error("Invalid redirect")
                        // Neither login credentials nor challenge payloads may change origins.
                        require(SourceLoginPolicy.sameOrigin(next, target) && next.username.isEmpty() && next.password.isEmpty())
                        if (response.code == 303 || (verb == "POST" && response.code in listOf(301, 302))) { verb = "GET"; content = null }
                        target = next
                    } else {
                        val bytes = ByteArrayOutputStream()
                        response.body?.byteStream()?.use { stream ->
                            val buffer = ByteArray(16384)
                            while (true) {
                                val count = stream.read(buffer); if (count < 0) break
                                require(bytes.size() + count <= 8 * 1024 * 1024)
                                check(!stopped.get()); bytes.write(buffer, 0, count)
                            }
                        }
                        return Upstream(bytes.toByteArray(), response.code, response.header("Content-Type") ?: "application/octet-stream", target)
                    }
                }
            } finally { calls.remove(call) }
        }
        error("Too many redirects")
    }

    private fun respond(request: Incoming): Reply {
        if (request.headers["host"] != authority) return Reply.message("地址无效。", 403)
        if (System.nanoTime() >= deadline || stopped.get()) return Reply.message("二维码已过期，请在电视上重新生成。", 410)
        if (request.headers["sec-fetch-site"] == "cross-site" || request.headers["origin"]?.let { it != origin } == true)
            return Reply.message("请重新扫描电视上的二维码。", 403)
        val hasCookie = request.headers["cookie"].orEmpty().split(';').any { it.trim() == "ddys_pair=$token" }
        val wasm = request.path in listOf("/assets/hashwx.wasm", "/assets/cap_wasm_bg.wasm")
        if (!request.path.startsWith(prefix) && !(hasCookie && wasm)) return Reply.message("请扫描电视上的二维码。", 404)
        if (request.method !in listOf("GET", "POST")) return Reply.message("请求方式无效。", 405)
        if (request.path == prefix && request.method == "GET") {
            request.headers["user-agent"]?.takeIf { it.length in 1..1024 && '\r' !in it && '\n' !in it }?.let { userAgent = it }
            return page(fetch(site))
        }
        if (request.path == prefix + "submit" && request.method == "POST") {
            val target = action ?: return Reply.message("请刷新页面后重试。", 403)
            if (request.headers["origin"] != origin || request.headers["content-type"]?.startsWith("application/x-www-form-urlencoded") != true)
                return Reply.message("请刷新页面后重新验证。", 403)
            if (!submitting.compareAndSet(false, true)) return Reply.message("正在验证，请稍候。", 409)
            return try { page(fetch(target, "POST", request.body)) } finally { submitting.set(false) }
        }
        val endpoint = cap
        if (request.method == "POST" && endpoint != null && request.path in listOf(prefix + "cap/challenge", prefix + "cap/redeem")) {
            if (request.headers["origin"] != origin || !(request.path.endsWith("/challenge") && request.body.isEmpty() ||
                    request.headers["content-type"]?.startsWith("application/json") == true)) return Reply.message("验证请求无效。", 403)
            val result = fetch(endpoint.newBuilder().addPathSegment(if (request.path.endsWith("challenge")) "challenge" else "redeem").build(), "POST", request.body, true)
            return Reply(result.body, result.code, "application/json")
        }
        if (request.method == "GET") {
            val remote = if (hasCookie && wasm && endpoint != null) endpoint.resolve(request.path) else assets[request.path]
            if (remote != null) { val result = fetch(remote); return Reply(result.body, result.code, result.type) }
        }
        return Reply.message("页面不存在，请重新扫码。", 404)
    }

    private fun resource(url: HttpUrl): String {
        assets.entries.firstOrNull { it.value == url }?.let { return it.key }
        require(assets.size < 512)
        return (prefix + "resource/" + UUID.randomUUID()).also { assets[it] = url }
    }

    private fun page(response: Upstream): Reply {
        val html = response.body.toString(Charsets.UTF_8)
        if (!SourceLoginPolicy.requiresLogin(html)) {
            if (response.code !in 200..299 || !SourceLoginPolicy.isCatalog(html, response.url.toString(), site.toString()))
                return Reply.message("网站尚未确认登录，请刷新页面重试。", 502)
            val confirmation = fetch(site)
            if (confirmation.code !in 200..299 || !SourceLoginPolicy.isCatalog(confirmation.body.toString(Charsets.UTF_8), confirmation.url.toString(), site.toString()))
                return Reply.message("网站尚未确认登录，请刷新页面重试。", 502)
            return Reply.message("验证成功，电视将自动继续。可以关闭此页面。", success = true)
        }
        val document = Jsoup.parse(html, response.url.toString())
        val password = document.selectFirst("input[name=ddys_protect_password], input[name=password_protected_pwd]")
            ?: return Reply.message("网站验证方式已变化，请稍后重试。", 409)
        val form = password.closest("form") ?: return Reply.message("登录表单无效。", 502)
        val target = response.url.resolve(form.attr("action")) ?: response.url
        require(SourceLoginPolicy.sameOrigin(target, site) && target.username.isEmpty() && target.password.isEmpty())
        action = target
        val widget = form.selectFirst("cap-widget") ?: return Reply.message("请返回应用，选择本机网页登录完成此类验证。", 409)
        val endpoint = response.url.resolve(widget.attr("data-cap-api-endpoint")) ?: error("Missing endpoint")
        require(endpoint.username.isEmpty() && endpoint.password.isEmpty() && (SourceLoginPolicy.sameOrigin(endpoint, site) ||
            endpoint.scheme == "https" && endpoint.host == "cap.ddys.app" && endpoint.port == 443))
        cap = endpoint
        widget.attr("data-cap-api-endpoint", origin + prefix + "cap/")
        val help = widget.attr("data-cap-troubleshooting-url")
        response.url.resolve(help)?.takeIf { help.isNotEmpty() && SourceLoginPolicy.sameOrigin(it, site) }?.let {
            widget.attr("data-cap-troubleshooting-url", resource(it))
        }
        form.attr("action", prefix + "submit")
        document.select("script[src], link[href], img[src]").forEach {
            val attribute = if (it.tagName() == "link") "href" else "src"
            val value = it.attr(attribute)
            if (!value.startsWith("data:")) {
                val url = response.url.resolve(value)
                if (url == null || !SourceLoginPolicy.sameOrigin(url, site)) it.remove()
                else { it.attr(attribute, resource(url)); it.removeAttr("integrity"); it.removeAttr("crossorigin") }
            }
        }
        document.select("a, base, meta[http-equiv=refresh]").remove()
        password.attr("value", "ddys")
        document.title("电视 · 网站验证")
        document.selectFirst("h1")?.text("为电视完成验证")
        document.body().prepend("<p style='text-align:center;font-size:14px'>请点击验证框，通过后点“进入”。电视会自动继续。</p>")
        document.body().append(CAP_ERROR_DISPLAY)
        return Reply(document.outerHtml().toByteArray(), headers = mapOf("Set-Cookie" to "ddys_pair=$token; Path=/; HttpOnly; SameSite=Strict; Max-Age=600"))
    }

    private class SourceCookies(private val site: HttpUrl) : CookieJar {
        private val values = mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            if (!SourceLoginPolicy.sameOrigin(url, site)) return
            for (cookie in cookies) {
                values.removeAll { it.name == cookie.name && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
            }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> =
            if (!SourceLoginPolicy.sameOrigin(url, site)) emptyList()
            else values.filter { it.expiresAt > System.currentTimeMillis() && it.matches(url) }
        @Synchronized fun snapshot(): List<Cookie> = values.toList()
    }
    companion object {
        fun localAddress(): InetAddress? = NetworkInterface.getNetworkInterfaces()?.let { interfaces ->
            Collections.list(interfaces).filter { it.isUp && !it.isLoopback && !it.isVirtual }
                .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("eth") || it.name.startsWith("en")) 0 else 1 }
                .flatMap { Collections.list(it.inetAddresses) }
                .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        }
        const val CAP_ERROR_DISPLAY = """<p id="ddys-verification-error" role="status" style="max-width:36rem;margin:1rem auto;padding:0 1rem;overflow-wrap:anywhere"></p>
<script>document.addEventListener('error',function(event){var d=event.detail;if(!d||!d.isCap)return;document.getElementById('ddys-verification-error').textContent='验证未完成：'+String(d.message||'请重试').slice(0,240)+'（'+String(d.code||'unknown').slice(0,64)+'）';});document.addEventListener('solve',function(){document.getElementById('ddys-verification-error').textContent='';});</script>"""
    }
}
