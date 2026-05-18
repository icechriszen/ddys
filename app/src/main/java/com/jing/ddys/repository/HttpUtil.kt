package com.jing.ddys.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.jing.ddys.BuildConfig
import com.jing.ddys.DdysApplication
import com.jing.ddys.ext.inflate
import com.jing.ddys.ext.unGzip
import com.jing.ddys.setting.NetworkProxySettings
import com.jing.ddys.setting.SettingsViewModel
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object HttpUtil {

    private val TAG = HttpUtil::class.java.simpleName

    const val USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/105.0.0.0 Safari/537.36"

    val VIDEO_BASE_URL: String
        get() = VideoSourceAuth.videoBaseUrl

    val BASE_URL: String
        get() = VideoSourceAuth.siteBaseUrl


    @Volatile
    var cookie_cf_bm: Pair<Long, String>? = null
        private set

    @Volatile
    var cookie_cache_key: String = ""
        private set

    private val sharedPreferences: SharedPreferences by lazy {
        DdysApplication.context.getSharedPreferences("ddys", Context.MODE_PRIVATE)
    }

    private val gson = Gson()


    @Volatile
    lateinit var okHttpClient: OkHttpClient
        private set

    init {
        buildOkhttpClientWithProxySetting(
            NetworkProxySettings.loadFromSharedPreference(
                SettingsViewModel.getSettingSharedPreference()
            )
        )
    }

    fun searchVideo(page: Int, keyword: String): BasePageResult<SearchResult> {
        val url = "$BASE_URL/page/$page/?s=${keyword}&post_type=post"
        val document = Jsoup.parse(getHtml(url))
        val videos = document.getElementsByTag("article").map { article ->
            val link = article.selectFirst("a")!!
            val href = link.attr("href")
            SearchResult(
                url = href,
                title = link.text().trim(),
                desc = article.selectFirst(".entry-content>p")?.text()?.trim() ?: "",
                updateTime = article.selectFirst(".meta_date")?.children()?.firstOrNull()?.text()
                    ?: ""
            )
        }
        val lastPage =
            document.selectFirst(".nav-links")?.children()?.lastOrNull()?.hasClass("current")
                ?: true
        return BasePageResult(
            data = videos, page = page, hasNext = !lastPage
        )
    }

    private fun getHtml(url: String): String {
        val req = Request.Builder().url(url).get().build()
        return okHttpClient.newCall(req).execute().use {
            readHtmlResponse(it)
        }
    }

    fun queryVideoOfCategory(pageUrl: String, page: Int): BasePageResult<VideoCardInfo> {
        var finalUrl = VideoSourceAuth.resolveSiteUrl(pageUrl)
        if (page > 1) {
            finalUrl = "${finalUrl.trimEnd('/')}/page/$page/"
        }
        val html = getHtml(finalUrl)
        val pattern = Pattern.compile("background-image:\\s*url\\((.*?)\\);")
        fun findImageUrl(input: String): String {
            val matcher = pattern.matcher(input)
            matcher.find()
            return matcher.group(1) ?: ""
        }

        val document = Jsoup.parse(html)
        val videoList = document.select("article").map { article ->
            val img = article.selectFirst(".post-box-image")!!
            val imageUrl = findImageUrl(img.attr("style"))
            val title = article.selectFirst(".post-box-title")!!.text()
            val url = article.dataset()["href"]!!
            val subTitle = article.selectFirst(".post-box-text p")?.text()
            VideoCardInfo(
                imageUrl = imageUrl, title = title, url = url, subTitle = subTitle
            )
        }
        val hasNext = document.selectFirst(".nav-links")?.let {
            it.children().isNotEmpty() && it.child(it.childrenSize() - 1).text() == "下一页"
        } ?: false
        return BasePageResult(
            data = videoList, page = page, hasNext = hasNext
        )
    }

    fun queryDetailPage(pageUrl: String): VideoDetailInfo {
        val detailPageUrl = VideoSourceAuth.resolveSiteUrl(pageUrl)
        val html =
            okHttpClient.newCall(Request.Builder().url(detailPageUrl).get().build()).execute().use {
                if (cookie_cache_key.isEmpty()) {
                    Cookie.parseAll(it.request.url, it.headers).find {
                        it.name == "X_CACHE_KEY"
                    }?.let { cookie ->
                        cookie_cache_key = cookie.value
                        sharedPreferences.edit().putString(cacheKeyPreferenceKey(), cookie.value).apply()
                    }
                }
                readHtmlResponse(it)
            }
        val document = Jsoup.parse(html, detailPageUrl)
        val seasonList = document.selectFirst(".page-links")?.children()?.map { season ->
            val url = season.absUrl("href")
            VideoSeason(
                seasonName = season.text().trim(),
                seasonUrl = url.takeIf { it.isNotEmpty() },
                currentSeason = url.isEmpty()
            )
        }?.takeIf { it.size > 1 } ?: emptyList()
        val relatedVideos = document.select(".crp_related li").map { li ->
            val url = li.selectFirst("a")!!.absUrl("href")
            val imgSrc = li.selectFirst("img")?.let {
                it.attr("src") ?: it.dataset()["src"]
            } ?: ""
            val title = li.selectFirst(".crp_title")!!.text().trim()
            VideoCardInfo(
                imageUrl = imgSrc, title = title, subTitle = null, url = url
            )
        }
        val infoArea = document.selectFirst(".doulist-subject")
        val title =
            infoArea?.selectFirst(".title")?.text()?.trim() ?: document.selectFirst(".post-title")!!
                .text().run {
                val idx = indexOfAny(charArrayOf('(', '（'))
                if (idx > 0) {
                    substring(0, idx).trim()
                } else {
                    trim()
                }
            }
        val cover = infoArea?.selectFirst(".post img")?.let {
            it.attr("src") ?: it.dataset()["src"]
        }
        val ratingNumber = infoArea?.selectFirst(".rating_nums")?.text()
        val infoRows =
            infoArea?.selectFirst(".abstract")?.textNodes()?.map { it.text() } ?: emptyList()
        val videoId = URL(detailPageUrl).path
        val currentSeasonName = seasonList.firstOrNull { it.currentSeason }?.seasonName.orEmpty()
        val episodeList = parseLegacyPlaylistEpisodes(document, videoId, currentSeasonName)
            .ifEmpty { WpsePlaylistParser.parse(document, videoId) }
        return VideoDetailInfo(
            id = videoId,
            title = title,
            coverUrl = cover ?: "",
            seasons = seasonList,
            episodes = episodeList.distinctBy { it.id },
            relatedVideo = relatedVideos,
            rating = ratingNumber ?: "",
            description = infoRows.lastOrNull() ?: "",
            infoRows = if (infoRows.isNotEmpty()) infoRows.slice(0 until infoRows.size - 1) else emptyList(),
            detailPageUrl = detailPageUrl
        )

    }

    private fun parseLegacyPlaylistEpisodes(
        document: Document,
        videoId: String,
        seasonName: String
    ): List<VideoEpisode> {
        val tracks = document.selectFirst(".wp-playlist-script")?.html()
            ?.takeIf { it.isNotBlank() }
            ?.let {
                (gson.fromJson(it, Map::class.java)["tracks"] as? List<*>)
            }
            ?: emptyList<Any?>()
        return tracks.mapNotNull { track ->
            val trackInfo = track as? Map<*, *> ?: return@mapNotNull null
            val src0 = trackInfo["src0"]?.toString() ?: ""
            val src1 = trackInfo["src1"]?.toString() ?: ""
            val name = trackInfo["caption"]?.toString()?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            VideoEpisode(
                id = src1.ifEmpty { "$videoId|$name" },
                name = name,
                subTitleUrl = trackInfo["subsrc"]?.toString()?.let {
                    VideoSourceAuth.resolveSiteUrl("/subddr${it}")
                } ?: "",
                seasonName = seasonName,
                src0 = src0,
                src1 = src1,
            )
        }
    }

    fun queryVideoUrl(id: String, detailPageUrl: String): VideoUrl {
        val resolvedDetailPageUrl = VideoSourceAuth.resolveSiteUrl(detailPageUrl)
        try {
            return requestVideoUrl(id, resolvedDetailPageUrl)
        } catch (ex: Exception) {
            if (ex is SourceAuthRequiredException) {
                throw ex
            }
            Log.w(TAG, "直接请求视频链接失败，尝试兼容旧接口认证:${ex.message}", ex)
        }

        val cfBm = prepareLegacyVideoAuthCookie(resolvedDetailPageUrl)
        return requestVideoUrl(
            id = id,
            detailPageUrl = resolvedDetailPageUrl,
            extraCookie = "__cf_bm=${cfBm.second}; X_CACHE_KEY=${cookie_cache_key}"
        )
    }

    private fun prepareLegacyVideoAuthCookie(detailPageUrl: String): Pair<Long, String> {
        var cfBm = cookie_cf_bm
        if (cfBm == null || System.currentTimeMillis() + 10_000 > cfBm.first) {
            var cacheKey = cookie_cache_key
            if (cacheKey.isEmpty()) {
                cacheKey = sharedPreferences.getString(cacheKeyPreferenceKey(), "") ?: ""
                if (cacheKey.isNotEmpty()) {
                    cookie_cache_key = cacheKey
                }
            }
            if (cacheKey.isEmpty()) {
                okHttpClient.newCall(Request.Builder().url(detailPageUrl).get().build())
                    .execute().use {
                        Cookie.parseAll(detailPageUrl.toHttpUrl(), it.headers)
                            .find { it.name == "X_CACHE_KEY" }?.let { cacheKey = it.value }
                        readHtmlResponse(it)
                    }
                if (cacheKey.isEmpty()) {
                    throw RuntimeException("未获取到CACHE_KEY")
                }
                cookie_cache_key = cacheKey
                sharedPreferences.edit().putString(cacheKeyPreferenceKey(), cacheKey).apply()
            }

            val sParam = okHttpClient.newCall(
                Request.Builder().url("$BASE_URL/cdn-cgi/challenge-platform/scripts/jsd/main.js")
                    .get().build()
            )
                .execute()
                .use { readHtmlResponse(it) }
                .let { js ->
                    val end = js.indexOf("'.split(',')")
                    if (end == -1) {
                        throw RuntimeException("读取main.js失败")
                    }
                    var start = -1
                    for (i in (end - 1) downTo 0) {
                        if (js[i] == '\'') {
                            start = i
                            break
                        }
                    }
                    if (start == -1) {
                        throw RuntimeException("读取main.js失败")
                    }
                    js.substring((start + 1) until end).split(',').find {
                        it.count { ch -> ch == ':' } == 2 && !it.startsWith('/')
                    }
                }

            val param = mapOf(
                "s" to sParam, "wp" to encodeParam()
            )
            Request.Builder().url("$BASE_URL/cdn-cgi/challenge-platform/h/g/cv/result/$cacheKey")
                .post(gson.toJson(param).toRequestBody("application/json".toMediaType())).build()
                .let { okHttpClient.newCall(it).execute() }.use { response ->
                    val ck = Cookie.parseAll(response.request.url, response.headers).find {
                        it.name == "__cf_bm"
                    } ?: throw RuntimeException("未获取到__cf_bm")
                    cfBm = Pair(ck.expiresAt, ck.value)
                    cookie_cf_bm = cfBm
                }


        }
        return cfBm!!
    }

    private fun requestVideoUrl(
        id: String,
        detailPageUrl: String,
        extraCookie: String? = null
    ): VideoUrl {
        val reqBuilder = Request.Builder().header("referer", detailPageUrl)
            .url("$BASE_URL/getvddr3/video?id=$id&type=json").get()
        if (!extraCookie.isNullOrBlank()) {
            reqBuilder.header("cookie", extraCookie)
        }
        val req = reqBuilder.build()
        val resp = okHttpClient.newCall(req).execute().use {
            readHtmlResponse(it)
        }
        Log.d(TAG, "queryVideoUrl: $resp")
        val map = gson.fromJson(resp, Map::class.java)
        val err = map["err"]
        if (err != null) {
            throw RuntimeException("获取链接地址错误,请稍后再试:$err")
        }
        val url = map["url"] as String?
        if (url != null) {
            return VideoUrl(
                type = VideoUrlType.URL, url = Uri.parse(url)
            )
        }
        val pin = map["pin"] as String?
        if (pin != null) {
            return VideoUrl(
                type = VideoUrlType.M3U8_TEXT,
                m3u8Text = pin.toByteArray(Charsets.ISO_8859_1).inflate().toString(Charsets.UTF_8)
            )
        }
        throw RuntimeException("无法识别的接口响应:$resp")
    }

    fun downloadSubtitles(url: String): String {
        if (url.isBlank()) {
            throw RuntimeException("字幕地址为空")
        }
        val resp = Request.Builder().url(url).get().build().let {
            okHttpClient.newCall(it).execute()
        }
        val bytes = resp.use {
            if (it.code != 200) {
                throw RuntimeException("请求字幕出错")
            }
            it.body?.bytes() ?: throw RuntimeException("字幕响应体为空")
        }
        if (bytes.firstOrNull()?.toInt()?.toChar() == '<') {
            VideoSourceAuth.ensureNotPasswordProtected(bytes.toString(Charsets.UTF_8), url)
        }
        return decryptSubtitle(bytes).unGzip().toString(Charsets.UTF_8)
    }

    private fun decryptSubtitle(bytes: ByteArray): ByteArray {
        val key = bytes.sliceArray(0 until 0x10)
        val iv = bytes.sliceArray(0 until 0x10)
        val keySpec = SecretKeySpec(key, "AES")
        return Cipher.getInstance("AES/CBC/PKCS5Padding").run {
            init(Cipher.DECRYPT_MODE, keySpec, IvParameterSpec(iv))
            doFinal(bytes.sliceArray(0x10 until bytes.size))
        }
    }

    fun resetOkhttpClientWithProxySettings(proxySettings: NetworkProxySettings) {
        okHttpClient.dispatcher.executorService.shutdown()
        okHttpClient.connectionPool.evictAll()
        buildOkhttpClientWithProxySetting(proxySettings = proxySettings)
    }

    private fun buildOkhttpClientWithProxySetting(proxySettings: NetworkProxySettings) {
        val builder = OkHttpClient.Builder()
            .readTimeout(10, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .apply {
                if (BuildConfig.DEBUG) {
                    addNetworkInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                    })
                }
            }
        if (BuildConfig.DEBUG) {
            builder.addNetworkInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            })
        }

        builder.addInterceptor(Interceptor { chain ->
            val originalReq = chain.request()
            val reqBuilder = originalReq.newBuilder().header(
                "user-agent", USER_AGENT
            )
            appendSourceAuthCookieIfNeeded(originalReq, reqBuilder)
            if (originalReq.header("referer") == null) {
                reqBuilder.header("referer", "$BASE_URL/true-sight/")
            }
            reqBuilder.build().let {
                chain.proceed(it)
            }
        })
        if (proxySettings.proxyEnabled && proxySettings.proxyHost.isNotEmpty()) {
            builder.proxy(
                Proxy(
                    Proxy.Type.HTTP,
                    InetSocketAddress(proxySettings.proxyHost, proxySettings.proxyPort)
                )
            )
        }
        okHttpClient = builder.build()
    }

    fun resolveVideoUrl(pathOrUrl: String): Uri = Uri.parse(VideoSourceAuth.resolveVideoUrl(pathOrUrl))

    private fun readHtmlResponse(resp: Response): String {
        val html = resp.body?.byteString()?.utf8() ?: throw RuntimeException("响应体为空")
        VideoSourceAuth.ensureNotPasswordProtected(html, resp.request.url.toString())
        return html
    }

    private fun appendSourceAuthCookieIfNeeded(
        originalReq: Request,
        reqBuilder: Request.Builder
    ) {
        if (originalReq.url.host != VideoSourceAuth.sourceHost()) {
            return
        }
        val authCookie = VideoSourceAuth.getAuthCookieHeader()
        if (authCookie.isBlank()) {
            return
        }
        val cookieHeader = listOfNotNull(
            originalReq.header("cookie")?.takeIf { it.isNotBlank() },
            authCookie
        ).joinToString("; ")
        reqBuilder.header("cookie", cookieHeader)
    }

    private fun cacheKeyPreferenceKey(): String = "cache_key.${VideoSourceAuth.sourceHost()}"


}
