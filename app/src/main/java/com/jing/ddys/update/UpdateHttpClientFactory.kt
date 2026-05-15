package com.jing.ddys.update

import com.jing.ddys.setting.NetworkProxySettings
import com.jing.ddys.setting.SettingsViewModel
import okhttp3.OkHttpClient
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

object UpdateHttpClientFactory {
    fun build(): OkHttpClient {
        val proxySettings =
            NetworkProxySettings.loadFromSharedPreference(SettingsViewModel.getSettingSharedPreference())
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .apply {
                if (proxySettings.proxyEnabled) {
                    proxy(
                        Proxy(
                            Proxy.Type.HTTP,
                            InetSocketAddress(proxySettings.proxyHost, proxySettings.proxyPort)
                        )
                    )
                }
            }
            .build()
    }
}
