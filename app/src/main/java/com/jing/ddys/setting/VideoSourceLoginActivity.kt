package com.jing.ddys.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import com.jing.ddys.BuildConfig
import com.jing.ddys.ext.showLongToast
import com.jing.ddys.ext.showShortToast
import com.jing.ddys.repository.VideoSourceAuth

class VideoSourceLoginActivity : Activity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView = try {
            WebView(this)
        } catch (ex: Exception) {
            showLongToast("当前设备不支持网站登录，请启用 Android System WebView 后重试")
            finish()
            return
        }

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                cookieManager.setAcceptThirdPartyCookies(this, true)
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (VideoSourceAuth.isLoginUrl(url)) {
                        fillPassword(view)
                    } else if (VideoSourceAuth.saveAuthCookieFromWebView()) {
                        CookieManager.getInstance().flush()
                        showShortToast("网站登录成功")
                        setResult(RESULT_OK)
                        finish()
                    }
                }
            }
        }

        setContentView(createContentView())
        webView.loadUrl(VideoSourceAuth.loginUrl())
        webView.requestFocus()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun createContentView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(TextView(this@VideoSourceLoginActivity).apply {
                text = "密码已自动填写；如出现验证，请用遥控器完成后点击登录。"
                setTextColor(Color.WHITE)
                textSize = 18f
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24, 12, 24, 12)
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
            addView(webView, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
        }
    }

    private fun fillPassword(view: WebView) {
        val script = """
            (function() {
                var input = document.querySelector('input[name="password_protected_pwd"]');
                if (input) {
                    input.focus();
                    input.value = '${VideoSourceAuth.password}';
                    input.dispatchEvent(new Event('input', { bubbles: true }));
                    input.dispatchEvent(new Event('change', { bubbles: true }));
                }
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }
}
