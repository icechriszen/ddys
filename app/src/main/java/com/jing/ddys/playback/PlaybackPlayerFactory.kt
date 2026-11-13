package com.jing.ddys.playback

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.google.common.net.HttpHeaders
import com.jing.ddys.BuildConfig
import com.jing.ddys.repository.HttpUtil
import com.jing.ddys.repository.VideoSourceAuth
import com.jing.ddys.setting.NetworkProxySettings
import com.jing.ddys.setting.SettingsViewModel
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetSocketAddress
import java.net.Proxy

internal fun createPlaybackHttpClient(): OkHttpClient {
    return OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
            val networkProxySettings =
                NetworkProxySettings.loadFromSharedPreference(SettingsViewModel.getSettingSharedPreference())
            if (networkProxySettings.proxyEnabled && networkProxySettings.proxyHost.isNotEmpty()) {
                proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress.createUnresolved(
                            networkProxySettings.proxyHost,
                            networkProxySettings.proxyPort
                        )
                    )
                )
            }
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val cookie = VideoSourceAuth.getAuthCookieHeader(request.url.toString())
            chain.proceed(if (cookie.isBlank()) request else request.newBuilder().header("Cookie", cookie).build())
        }
        .build()
}

@UnstableApi
internal fun createPlaybackDataSourceFactory(context: Context): DefaultDataSource.Factory {
    val okHttpClient = createPlaybackHttpClient()
    return DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory { request ->
            val newRequest = request.newBuilder()
                .header(HttpHeaders.USER_AGENT, HttpUtil.requestUserAgent)
                .header(HttpHeaders.REFERER, HttpUtil.BASE_URL + '/')
                .build()
            okHttpClient.newCall(newRequest)
        }
    )
}

@UnstableApi
internal fun createPlaybackMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
    return DefaultMediaSourceFactory(createPlaybackDataSourceFactory(context))
}

@UnstableApi
internal fun createPlaybackLoadControl(): DefaultLoadControl {
    return DefaultLoadControl.Builder().setBufferDurationsMs(
        20_000,
        50_000,
        1000,
        1000
    ).build()
}
