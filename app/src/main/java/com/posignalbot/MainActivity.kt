package com.posignalbot

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusBar: TextView
    private lateinit var progressBar: ProgressBar
    private var sessionSent = false

    // Track whether user has actually logged in
    private var userLoggedIn = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── Build layout ──────────────────────────────────────────────────────
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0f1117"))
        }

        statusBar = TextView(this).apply {
            text = "Open Pocket Option and login with your credentials"
            setTextColor(Color.parseColor("#90caf9"))
            setBackgroundColor(Color.parseColor("#1a1d27"))
            textSize = 13f
            setPadding(24, 18, 24, 18)
        }
        root.addView(statusBar, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        root.addView(progressBar, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 6
        ))

        webView = WebView(this)
        root.addView(webView, android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
        setupWebView()
        webView.loadUrl("https://po.trade/login")
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

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        // Register JS bridge
        webView.addJavascriptInterface(
            JsBridge(this) { success, message -> onBridgeResult(success, message) },
            "POBridge"
        )

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val safeUrl = url ?: ""
                handlePageChange(view, safeUrl)
            }
        }
    }

    private fun handlePageChange(view: WebView?, url: String) {
        when {
            // ── LOGIN PAGE — user needs to login ─────────────────────────────
            url.contains("login") || url == "https://po.trade/" ||
            url == "https://po.trade" -> {
                userLoggedIn = false
                setStatus("🔐 Please login with your email and password", "#90caf9")
            }

            // ── CABINET / TRADING PAGE — user is now logged in ────────────────
            url.contains("cabinet") || url.contains("/trade") ||
            (url.contains("po.trade") && !url.contains("login") && !url.contains("register")) -> {
                if (!userLoggedIn) {
                    // First time landing on cabinet = just logged in
                    userLoggedIn = true
                    setStatus("🔍 Logged in! Extracting session...", "#fbbf24")

                    // Wait 1.5 seconds for page JS to fully initialize localStorage
                    webView.postDelayed({
                        if (!sessionSent) extractSession(view)
                    }, 1500)
                }
            }

            else -> {
                setStatus("🌐 Loading...", "#888888")
            }
        }
    }

    private fun extractSession(view: WebView?) {
        val js = """
        (function() {
            try {
                // Collect all localStorage entries
                var store = {};
                for (var i = 0; i < localStorage.length; i++) {
                    var k = localStorage.key(i);
                    var v = localStorage.getItem(k);
                    if (v && v.length > 0) {
                        store[k] = v;
                    }
                }

                // Also grab cookies
                if (document.cookie && document.cookie.length > 0) {
                    store['__cookies__'] = document.cookie;
                }

                // Check for any meaningful session value
                var sessionKeys = ['token','ssid','session','access_token',
                                   'auth','authToken','user_token','userToken'];
                var hasSession = false;
                for (var j = 0; j < sessionKeys.length; j++) {
                    if (store[sessionKeys[j]] && store[sessionKeys[j]].length > 10) {
                        hasSession = true;
                        break;
                    }
                }

                // Also check if any value is a long token-like string
                if (!hasSession) {
                    for (var key in store) {
                        if (store[key].length > 30) {
                            hasSession = true;
                            break;
                        }
                    }
                }

                if (hasSession) {
                    POBridge.onSession(JSON.stringify(store));
                } else if (Object.keys(store).length > 0) {
                    // Has data but no obvious session — send anyway
                    POBridge.onSession(JSON.stringify(store));
                } else {
                    POBridge.onNotFound();
                }

            } catch(e) {
                POBridge.onError(e.message);
            }
        })();
        """.trimIndent()

        view?.evaluateJavascript(js, null)
    }

    private fun onBridgeResult(success: Boolean, message: String) {
        runOnUiThread {
            if (success) {
                setStatus("✅ Bot connected! You can close this app.", "#86efac")
                // Show a toast as well
                Toast.makeText(this, "🎉 Signal bot connected!", Toast.LENGTH_LONG).show()
            } else {
                setStatus("❌ $message", "#fca5a5")
            }
        }
    }

    private fun setStatus(text: String, colorHex: String) {
        statusBar.text = text
        statusBar.setTextColor(Color.parseColor(colorHex))
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }
}


// ── JavaScript → Android bridge ───────────────────────────────────────────────

class JsBridge(
    private val context: Context,
    private val onResult: (Boolean, String) -> Unit
) {
    private var sent = false

    @JavascriptInterface
    fun onSession(json: String) {
        if (sent) return

        android.util.Log.d("POBridge", "Session data received: ${json.take(200)}")

        // Parse and build the auth payload
        val ssid = buildAuthPayload(json)
        if (ssid == null) {
            onResult(false, "Session data found but couldn't parse it. Try again.")
            return
        }

        sent = true
        postToBot(ssid)
    }

    @JavascriptInterface
    fun onNotFound() {
        // Retry after another 2 seconds — localStorage may not be populated yet
        android.util.Log.d("POBridge", "Session not found, will retry")
        onResult(false, "Session not found yet — still on login page? Please login first.")
    }

    @JavascriptInterface
    fun onError(msg: String) {
        android.util.Log.e("POBridge", "JS error: $msg")
        onResult(false, "Error: $msg")
    }

    private fun buildAuthPayload(json: String): String? {
        return try {
            val data = JSONObject(json)

            // Priority: known session keys
            val sessionKeys = listOf("token","ssid","session","access_token",
                                      "auth","authToken","user_token","userToken")
            var sessionVal: String? = null
            var uid = ""

            for (key in sessionKeys) {
                if (data.has(key)) {
                    val v = data.getString(key)
                    if (v.length > 10) {
                        sessionVal = v
                        break
                    }
                }
            }

            // Try to get uid
            for (key in listOf("uid","user_id","userId","id")) {
                if (data.has(key)) {
                    uid = data.getString(key)
                    break
                }
            }

            // If no known key, use the longest value
            if (sessionVal == null) {
                var maxLen = 0
                val keys = data.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    if (k == "__cookies__") continue
                    val v = data.getString(k)
                    if (v.length > maxLen) {
                        maxLen = v.length
                        sessionVal = v
                    }
                }
            }

            if (sessionVal == null) return null

            // If it's already a full Socket.IO message, return as-is
            if (sessionVal.startsWith("42")) return sessionVal

            // Build the auth envelope
            val auth = JSONObject().apply {
                put("session", sessionVal)
                put("isDemo", 0)
                if (uid.isNotEmpty()) put("uid", uid)
            }
            "42${org.json.JSONArray().apply { put("auth"); put(auth) }}"

        } catch (e: Exception) {
            android.util.Log.e("POBridge", "Parse error: ${e.message}")
            // Return raw JSON as fallback — server will parse it
            json
        }
    }

    private fun postToBot(ssid: String, attempt: Int = 1) {
        val client = OkHttpClient.Builder()
            .connectTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(35, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        val body = JSONObject().apply { put("ssid", ssid) }.toString()
        val request = Request.Builder()
            .url("https://pocket-bot-ssh6.onrender.com/ssid")
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Content-Type", "application/json")
            .build()

        onResult(false, "⏳ Connecting to bot server (attempt $attempt/3)...")

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val respBody = response.body?.string() ?: "{}"
                android.util.Log.d("POBridge", "Server response: $respBody")
                val ok = try { JSONObject(respBody).getBoolean("ok") } catch (e: Exception) { false }
                if (ok) {
                    onResult(true, "Connected!")
                } else {
                    sent = false
                    val error = try { JSONObject(respBody).getString("error") } catch (e: Exception) { "Unknown error" }
                    onResult(false, "Server error: $error")
                }
            }
            override fun onFailure(call: Call, e: IOException) {
                android.util.Log.e("POBridge", "Attempt $attempt failed: ${e.message}")
                if (attempt < 3) {
                    // Retry after 5 seconds — Render may still be waking up
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        postToBot(ssid, attempt + 1)
                    }, 5000)
                } else {
                    sent = false
                    onResult(false, "❌ Could not reach bot server after 3 attempts.
Make sure the Render bot is deployed and running.")
                }
            }
        })
    }
            }
            
