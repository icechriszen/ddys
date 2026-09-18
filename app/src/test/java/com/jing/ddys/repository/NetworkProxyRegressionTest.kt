package com.jing.ddys.repository

import android.net.Uri
import android.os.Looper
import coil.fetch.SourceResult
import coil.request.CachePolicy
import coil.request.Options
import com.jing.ddys.DdysApplication
import com.jing.ddys.playback.createPlaybackHttpClient
import com.jing.ddys.setting.NetworkProxySettings
import com.jing.ddys.setting.SettingsViewModel
import com.jing.ddys.update.UpdateHttpClientFactory
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = DdysApplication::class, manifest = Config.NONE, sdk = [34])
class NetworkProxyRegressionTest {
    private val clients = mutableListOf<OkHttpClient>()

    @Before
    fun setUp() {
        applySettings(NetworkProxySettings(proxyPort = 7890))
    }

    @After
    fun tearDown() {
        clients.forEach {
            it.dispatcher.cancelAll()
            it.dispatcher.executorService.shutdown()
            it.connectionPool.evictAll()
        }
        SettingsViewModel.getSettingSharedPreference().edit().clear().commit()
        stopKoin()
    }

    @Test
    fun existingImageLoaderUsesNewProxyAfterEnablingChangingAndDisablingIt() {
        val imageLoader = DdysApplication.imageLoader
        val options = Options(
            context = DdysApplication.context,
            diskCachePolicy = CachePolicy.DISABLED
        )
        fun fetch(url: String): String = runBlocking(Dispatchers.IO) {
            withTimeout(5_000) {
                val fetcher = checkNotNull(
                    imageLoader.components.newFetcher(Uri.parse(url), options, imageLoader)
                ).first
                val result = fetcher.fetch() as SourceResult
                result.source.use { it.source().readUtf8() }
            }
        }

        LocalHttpEndpoint("direct").use { origin ->
            LocalHttpEndpoint("proxy-a").use { proxyA ->
                LocalHttpEndpoint("proxy-b").use { proxyB ->
                    assertEquals("direct", fetch(origin.url))

                    applySettings(proxyA.settings())
                    assertEquals("proxy-a", fetch(origin.url))

                    applySettings(proxyB.settings())
                    assertEquals("proxy-b", fetch(origin.url))

                    applySettings(NetworkProxySettings(proxyPort = 7890))
                    assertEquals("direct", fetch(origin.url))

                    assertEquals(2, origin.requestCount.get())
                    assertEquals(1, proxyA.requestCount.get())
                    assertEquals(1, proxyB.requestCount.get())
                }
            }
        }
    }

    @Test
    fun callCreatedBeforeProxySwitchCanStillBeEnqueued() {
        LocalHttpEndpoint("direct").use { origin ->
            LocalHttpEndpoint("proxy").use { proxy ->
                val call = HttpUtil.okHttpClient.newCall(Request.Builder().url(origin.url).build())
                applySettings(proxy.settings())

                assertEquals("direct", call.enqueueAndRead())
                assertEquals(1, origin.requestCount.get())
                assertEquals(0, proxy.requestCount.get())
            }
        }
    }

    @Test
    fun buildingClientsOnMainThreadLeavesProxyHostnameUnresolved() {
        assertEquals(Looper.getMainLooper(), Looper.myLooper())
        applySettings(NetworkProxySettings(true, "localhost", 7890))
        val playbackClient = createPlaybackHttpClient().also(clients::add)
        val updateClient = UpdateHttpClientFactory.build().also(clients::add)

        listOf(HttpUtil.okHttpClient, playbackClient, updateClient).forEach { client ->
            val address = client.proxy!!.address() as InetSocketAddress
            assertTrue("Proxy DNS must be deferred until a request runs", address.isUnresolved)
            assertEquals("localhost", address.hostString)
            assertEquals(7890, address.port)
        }
    }

    private fun applySettings(settings: NetworkProxySettings) {
        settings.flushToSharedPreference(SettingsViewModel.getSettingSharedPreference())
        HttpUtil.resetOkhttpClientWithProxySettings(settings)
        clients += HttpUtil.okHttpClient
    }

    private fun Call.enqueueAndRead(): String {
        val result = CompletableFuture<String>()
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                result.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    response.use { result.complete(it.body!!.string()) }
                } catch (e: Exception) {
                    result.completeExceptionally(e)
                }
            }
        })
        return result.get(5, TimeUnit.SECONDS)
    }

    private class LocalHttpEndpoint(body: String) : Closeable {
        private val server = ServerSocket(0, 50, InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1)))
        private val executor = Executors.newSingleThreadExecutor()
        val requestCount = AtomicInteger()
        val url: String
            get() = "http://127.0.0.1:${server.localPort}/poster.png"

        private val worker = executor.submit {
            while (!server.isClosed) {
                val connection = try {
                    server.accept()
                } catch (e: SocketException) {
                    if (server.isClosed) break else throw e
                }
                connection.use { socket ->
                    socket.soTimeout = 5_000
                    val reader = socket.getInputStream().bufferedReader()
                    while (true) {
                        val line = reader.readLine() ?: return@use
                        if (line.isEmpty()) break
                    }
                    requestCount.incrementAndGet()
                    val bytes = body.toByteArray()
                    val headers = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: image/png\r\n" +
                        "Content-Length: ${bytes.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    socket.getOutputStream().apply {
                        write(headers.toByteArray())
                        write(bytes)
                        flush()
                    }
                }
            }
        }

        fun settings() = NetworkProxySettings(true, "localhost", server.localPort)

        override fun close() {
            server.close()
            executor.shutdown()
            worker.get(5, TimeUnit.SECONDS)
        }
    }
}
