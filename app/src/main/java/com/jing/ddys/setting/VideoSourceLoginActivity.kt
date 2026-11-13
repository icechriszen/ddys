package com.jing.ddys.setting

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.jing.ddys.R
import com.jing.ddys.BuildConfig
import com.jing.ddys.DdysApplication
import com.jing.ddys.compose.AppFormFactor
import com.jing.ddys.ext.showLongToast
import com.jing.ddys.ext.showShortToast
import com.jing.ddys.repository.HttpUtil
import com.jing.ddys.repository.SourceLoginPolicy
import com.jing.ddys.repository.VideoSourceAuth
import org.json.JSONObject
import org.json.JSONTokener

class VideoSourceLoginActivity : Activity() {
    private var webView: WebView? = null
    private var bridge: BrowserLoginBridge? = null
    private var generation = 0
    private var pageGeneration = 0
    private var phoneLogin = false
    private var stopped = false
    private lateinit var status: TextView
    private lateinit var qr: ImageView
    private lateinit var address: TextView
    private lateinit var refresh: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        phoneLogin = DdysApplication.context.operationModeSettings.resolve(resources.configuration.uiMode) == AppFormFactor.Tv
        if (phoneLogin) showPhoneLogin() else showWebLogin()
    }

    private fun root(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(24), dp(20), dp(24), dp(16))
        setBackgroundColor(Color.rgb(26, 26, 26))
        addView(label(title, 26f), LinearLayout.LayoutParams(-1, -2))
    }
    private fun label(value: String, size: Float = 17f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.WHITE)
        setPadding(0, dp(6), 0, dp(6))
    }
    private fun button(title: String, action: () -> Unit) = Button(this).apply {
        text = title; isAllCaps = false; isFocusable = true
        setOnClickListener { action() }
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showPhoneLogin() {
        phoneLogin = true
        webView?.apply { stopLoading(); destroy() }; webView = null
        val root = root(getString(R.string.login_qr_title))
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val instructions = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), dp(24), 0)
            addView(label(getString(R.string.login_qr_instructions)))
            addView(label(getString(R.string.login_qr_expiry), 14f))
            status = label(getString(R.string.login_qr_preparing), 15f); addView(status)
            refresh = button(getString(R.string.login_qr_refresh)) { startBridge() }; addView(refresh)
            addView(button(getString(R.string.login_on_device)) { showWebLogin() })
            addView(button(getString(R.string.login_back)) { finish() })
        }
        val code = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
            val heightDp = (resources.displayMetrics.heightPixels / resources.displayMetrics.density).toInt()
            val widthDp = (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
            val side = minOf(280, heightDp - 130, widthDp / 2 - 52).coerceAtLeast(160)
            qr = ImageView(this@VideoSourceLoginActivity).apply {
                setBackgroundColor(Color.WHITE); contentDescription = getString(R.string.login_qr_image)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
            addView(qr, LinearLayout.LayoutParams(dp(side), dp(side)))
            address = label("", 12f).apply { gravity = Gravity.CENTER; setTextIsSelectable(false) }
            addView(address, LinearLayout.LayoutParams(-1, -2))
        }
        body.addView(instructions, LinearLayout.LayoutParams(0, -2, 1f))
        body.addView(code, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refresh.requestFocus()
        startBridge()
    }

    private fun stopBridge() { generation++; bridge?.close(); bridge = null }
    private fun startBridge() {
        stopBridge()
        val current = generation
        refresh.isEnabled = false; status.text = getString(R.string.login_qr_preparing)
        qr.setImageDrawable(null); address.text = ""
        Thread {
            val server = BrowserLoginBridge(
                VideoSourceAuth.siteBaseUrl, HttpUtil.okHttpClient,
                onAuthenticated = { cookies, userAgent ->
                    runOnUiThread {
                        if (current == generation && !stopped && !isFinishing) {
                            VideoSourceAuth.saveBrowserSession(cookies, userAgent)
                            loginComplete()
                        }
                    }
                },
                onExpired = {
                    runOnUiThread {
                        if (current == generation && !isFinishing) {
                            qr.setImageDrawable(null); address.text = ""
                            status.text = getString(R.string.login_qr_expired)
                        }
                    }
                }
            )
            try {
                val url = server.start()
                val bits = QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 480, 480)
                val pixels = IntArray(480 * 480) { if (bits[it % 480, it / 480]) Color.BLACK else Color.WHITE }
                val bitmap = Bitmap.createBitmap(pixels, 480, 480, Bitmap.Config.ARGB_8888)
                runOnUiThread {
                    if (current != generation || stopped || isFinishing) { server.close(); return@runOnUiThread }
                    bridge = server; qr.setImageBitmap(bitmap); address.text = url
                    status.text = getString(R.string.login_qr_waiting); refresh.isEnabled = true
                }
            } catch (_: Exception) {
                server.close()
                runOnUiThread {
                    if (current == generation && !isFinishing) {
                        status.text = getString(R.string.login_qr_failed); refresh.isEnabled = true
                    }
                }
            }
        }.apply { name = "ddys-prepare-login"; isDaemon = true }.start()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun showWebLogin() {
        stopBridge(); phoneLogin = false
        val view = try { WebView(this) } catch (_: Exception) {
            showLongToast(getString(R.string.login_webview_unavailable))
            showPhoneLogin(); return
        }
        webView?.destroy(); webView = view
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
        CookieManager.getInstance().setAcceptCookie(true)
        view.apply {
            isFocusable = true; isFocusableInTouchMode = true
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true; settings.useWideViewPort = true
            settings.allowFileAccess = false; settings.allowContentAccess = false
            // Keep the actual WebView UA and execute the site's original Cap widget.
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageGeneration++; super.onPageStarted(view, url, favicon)
                }
                @Deprecated("Compatibility with API 21")
                override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
                    !SourceLoginPolicy.isSourceUrl(url, VideoSourceAuth.siteBaseUrl)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !SourceLoginPolicy.isSourceUrl(request.url.toString(), VideoSourceAuth.siteBaseUrl)
                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    if (!SourceLoginPolicy.isSourceUrl(url, VideoSourceAuth.siteBaseUrl)) return
                    val currentPage = pageGeneration
                    view.evaluateJavascript(VideoSourceLoginScripts.buildReadPageStateScript()) { raw ->
                        if (webView !== view || isFinishing || currentPage != pageGeneration || view.url != url) return@evaluateJavascript
                        val state = runCatching { JSONObject(JSONTokener(raw).nextValue() as String) }.getOrNull() ?: return@evaluateJavascript
                        if (state.optBoolean("login")) {
                            view.evaluateJavascript(VideoSourceLoginScripts.buildEnhanceLoginFormScript(VideoSourceAuth.password), null)
                        } else if (state.optBoolean("catalog") && VideoSourceAuth.saveAuthCookieFromWebView(view.settings.userAgentString)) {
                            CookieManager.getInstance().flush(); loginComplete()
                        }
                    }
                }
            }
        }
        val root = root(getString(R.string.login_title))
        root.addView(label(getString(R.string.login_web_instructions), 15f))
        root.addView(button(getString(R.string.login_use_phone)) { showPhoneLogin() })
        root.addView(view, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        view.loadUrl(VideoSourceAuth.loginUrl()); view.requestFocus()
    }
    private fun loginComplete() {
        stopBridge(); showShortToast(getString(R.string.login_success)); setResult(RESULT_OK); finish()
    }
    override fun onStart() {
        super.onStart()
        if (stopped && phoneLogin) startBridge()
        stopped = false
    }
    override fun onStop() {
        stopped = true
        stopBridge()
        super.onStop()
    }
    override fun onDestroy() {
        stopBridge(); webView?.destroy(); webView = null
        super.onDestroy()
    }
}
