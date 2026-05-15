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

@UnstableApi
internal fun createPlaybackDataSourceFactory(context: Context): DefaultDataSource.Factory {
    val okHttpClient = OkHttpClient.Builder()
        .apply {
            if (BuildConfig.DEBUG) {
                addNetworkInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.HEADERS
                })
            }
            val networkProxySettings =
                NetworkProxySettings.loadFromSharedPreference(SettingsViewModel.getSettingSharedPreference())
            if (networkProxySettings.proxyEnabled && networkProxySettings.proxyHost.isNotEmpty()) {
                proxy(
                    Proxy(
                        Proxy.Type.HTTP,
                        InetSocketAddress(
                            networkProxySettings.proxyHost,
                            networkProxySettings.proxyPort
                        )
                    )
                )
            }
        }
        .build()

    return DefaultDataSource.Factory(
        context,
        OkHttpDataSource.Factory { request ->
            val newRequest = request.newBuilder()
                .header(HttpHeaders.USER_AGENT, HttpUtil.USER_AGENT)
                .header(HttpHeaders.REFERER, HttpUtil.BASE_URL + '/')
                .apply {
                    if (request.url.host == VideoSourceAuth.sourceHost()) {
                        val cookie = VideoSourceAuth.getAuthCookieHeader()
                        if (cookie.isNotBlank()) {
                            header("Cookie", cookie)
                        }
                    }
                }
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
