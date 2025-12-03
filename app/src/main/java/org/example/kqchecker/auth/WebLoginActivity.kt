package org.example.kqchecker.auth

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Color
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.os.Build
import android.webkit.JavascriptInterface
import androidx.activity.ComponentActivity
import android.util.Log
import android.webkit.CookieManager

class WebLoginActivity : ComponentActivity() {
    companion object {
        const val EXTRA_LOGIN_URL = "extra_login_url"
        const val EXTRA_REDIRECT_PREFIX = "extra_redirect_prefix"
        const val EXTRA_FORCE_CLEAR = "extra_force_clear"
        const val RESULT_TOKEN = "result_token"
        const val RESULT_TOKEN_SOURCE = "result_token_source"
    }

    private lateinit var tokenManager: TokenManager
    private var redirectPrefix: String = ""
    private var webViewInstance: WebView? = null
    private var tokenClearedReceiver: BroadcastReceiver? = null

    inner class JsBridge {
        @JavascriptInterface
        fun postUrl(href: String?) {
            if (href == null) return
            try {
                Log.d("WebLoginActivity", "JsBridge.postUrl href=$href")
                val uri = Uri.parse(href)

                try {
                    if (uri.toString().startsWith(redirectPrefix)) {
                        var token: String? = null
                        token = uri.getQueryParameter("token")
                        if (token == null) {
                            val frag = uri.fragment
                            if (frag != null) {
                                val fragUri = Uri.parse("?$frag")
                                token = fragUri.getQueryParameter("token")
                            }
                        }

                            if (token != null) {
                            val tokenVal = token!!
                            val bearer = if (tokenVal.startsWith("bearer ", true)) tokenVal else "bearer $tokenVal"
                            try {
                                try { Log.d("WebLoginActivity", "JsBridge detected token prefix=${tokenVal.take(8)}... (len=${tokenVal.length})") } catch (_: Throwable) {}
                                tokenManager.saveAccessToken(bearer)
                                    try { Log.d("WebLoginActivity", "saved access token (len=${bearer.length}) from JsBridge (source=url)") } catch (_: Throwable) {}
                            } catch (e: Throwable) {
                                Log.e("WebLoginActivity", "JsBridge failed to save token", e)
                            }
                            var refresh: String? = uri.getQueryParameter("refresh_token")
                            if (refresh == null) {
                                val frag = uri.fragment
                                if (frag != null) {
                                    val fragUri = Uri.parse("?$frag")
                                    refresh = fragUri.getQueryParameter("refresh_token")
                                }
                            }
                            tokenManager.saveRefreshToken(refresh)

                            val data = Intent()
                            data.putExtra(RESULT_TOKEN, bearer)
                            data.putExtra(RESULT_TOKEN_SOURCE, "url")
                            setResult(Activity.RESULT_OK, data)
                            runOnUiThread {
                                finish()
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("WebLoginActivity", "JsBridge.handleUrl error for uri=$uri", t)
                }
            } catch (t: Throwable) {
                Log.e("WebLoginActivity", "JsBridge.postUrl error", t)
            }
        }

        @JavascriptInterface
        fun postToken(token: String?) {
            if (token == null) return
            try {
                try { Log.d("WebLoginActivity", "JsBridge.postToken token prefix=${token.take(8)}... (len=${token.length})") } catch (_: Throwable) {}
                val tokenVal = token.trim()
                val bearer = if (tokenVal.startsWith("bearer ", true)) tokenVal else "bearer $tokenVal"
                try {
                    tokenManager.saveAccessToken(bearer)
                    try { Log.d("WebLoginActivity", "saved access token (len=${bearer.length}) from postToken (source=local)") } catch (_: Throwable) {}
                } catch (e: Throwable) {
                    Log.e("WebLoginActivity", "postToken failed to save token", e)
                }
                // also set result and finish
                val data = Intent()
                data.putExtra(RESULT_TOKEN, bearer)
                data.putExtra(RESULT_TOKEN_SOURCE, "local")
                setResult(Activity.RESULT_OK, data)
                runOnUiThread { finish() }
            } catch (t: Throwable) {
                Log.e("WebLoginActivity", "JsBridge.postToken error", t)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("WebLoginActivity", "onCreate intent=${intent?.action} extras=${intent?.extras}")
        tokenManager = TokenManager(this)

        // Debug: if started with EXTRA_FORCE_CLEAR=true, perform a token clear (simulate expiry) and finish.
        try {
            val doClear = intent?.getBooleanExtra(EXTRA_FORCE_CLEAR, false) ?: false
            if (doClear) {
                try {
                    Log.d("WebLoginActivity", "EXTRA_FORCE_CLEAR received — calling TokenManager.clear()")
                } catch (_: Throwable) {}
                try { tokenManager.clear() } catch (_: Throwable) {}
                finish()
                return
            }
        } catch (_: Throwable) {}

        try {
            val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL)
                ?: "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/login"
            this.redirectPrefix = intent.getStringExtra(EXTRA_REDIRECT_PREFIX)
                ?: "http://bkkq.xjtu.edu.cn/attendance-student-pc/#/home"

            Log.i("WebLoginActivity", "loginUrl=$loginUrl redirectPrefix=$redirectPrefix")

            val webView = WebView(this)
            webViewInstance = webView
            webView.setBackgroundColor(Color.WHITE)
            val settings = webView.settings
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowContentAccess = true
            settings.allowFileAccess = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    Log.d("WebLoginActivity", "Console: ${consoleMessage?.message()} -- ${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()}")
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            // 注册 class 级别的 JsBridge 实例
            webView.addJavascriptInterface(JsBridge(), "AndroidBridge")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("WebLoginActivity", "onPageStarted url=$url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d("WebLoginActivity", "onPageFinished url=$url")

                    //页面加载完成后，通过 JS 获取完整的 location.href（包含 fragment），
                    //因为有些站点通过 hash(#) 传递 token，原生的 shouldOverrideUrlLoading
                    //不一定会在 hash 变化时被触发。
                    try {
                        // 1) 立即获取一次 href 以覆盖页面初始状态
                        view?.evaluateJavascript("(function(){return window.location.href;})()") { value ->
                            if (value == null) return@evaluateJavascript
                            var href = value
                            if (href.length >= 2 && href.first() == '"' && href.last() == '"') {
                                href = href.substring(1, href.length - 1)
                            }
                            href = href.replace("\\/", "/")
                            href = href.replace("\\u0026", "&")
                            href = href.replace("\\u003d", "=")
                            href = href.replace("\\u0023", "#")
                            try {
                                val uri = Uri.parse(href)
                                Log.d("WebLoginActivity", "evaluated href=$href")
                                // 直接解析并处理一次（以防 JS 监听未生效）
                                try {
                                    if (uri.toString().startsWith(redirectPrefix)) {
                                        var token: String? = null
                                        token = uri.getQueryParameter("token")
                                        if (token == null) {
                                            val frag = uri.fragment
                                            if (frag != null) {
                                                val fragUri = Uri.parse("?$frag")
                                                token = fragUri.getQueryParameter("token")
                                            }
                                        }
                                        if (token != null) {
                                            val tokenVal = token!!
                                            val bearer = if (tokenVal.startsWith("bearer ", true)) tokenVal else "bearer $tokenVal"
                                            try {
                                                try { Log.d("WebLoginActivity", "evaluateJavascript detected token prefix=${tokenVal.take(8)}... (len=${tokenVal.length})") } catch (_: Throwable) {}
                                                tokenManager.saveAccessToken(bearer)
                                                try { Log.d("WebLoginActivity", "saved access token (len=${bearer.length}) from evaluateJavascript (source=url)") } catch (_: Throwable) {}
                                            } catch (e: Throwable) {
                                                Log.e("WebLoginActivity", "evaluateJavascript failed to save token", e)
                                            }
                                            var refresh: String? = uri.getQueryParameter("refresh_token")
                                            if (refresh == null) {
                                                val frag = uri.fragment
                                                if (frag != null) {
                                                    val fragUri = Uri.parse("?$frag")
                                                    refresh = fragUri.getQueryParameter("refresh_token")
                                                }
                                            }
                                            tokenManager.saveRefreshToken(refresh)
                                            val data = Intent()
                                            data.putExtra(RESULT_TOKEN, bearer)
                                            data.putExtra(RESULT_TOKEN_SOURCE, "url")
                                            setResult(Activity.RESULT_OK, data)
                                            finish()
                                            return@evaluateJavascript
                                        }
                                    }
                                } catch (t: Throwable) {
                                    Log.e("WebLoginActivity", "evaluateHref handle error", t)
                                }
                            } catch (t: Throwable) {
                                Log.e("WebLoginActivity", "failed to parse href from evaluateJavascript: $href", t)
                            }
                        }

                        // 2) 注入 JS 监听器：监听 hashchange/popstate/history API 调用，回传 href 到 AndroidBridge
                        //    并包装 localStorage.setItem，主动回传 localStorage 中常见 token key，以及把 native token 写入 localStorage（如果存在）
                        val inject = "(function(){try{function notify(){try{AndroidBridge.postUrl(window.location.href);}catch(e){console.log('AndroidBridge.postUrl err', e);} }notify();window.addEventListener('hashchange', notify, false);window.addEventListener('popstate', notify, false);var _push=history.pushState;history.pushState=function(){_push.apply(this, arguments);notify();};var _replace=history.replaceState;history.replaceState=function(){_replace.apply(this, arguments);notify();};try{var _ls_set=localStorage.setItem;localStorage.setItem=function(k,v){try{_ls_set.apply(this,arguments);}catch(e){};try{if(k==='access_token'||k==='token'||k==='auth_token'){AndroidBridge.postToken(v);} }catch(e){console.log('postToken err',e);} };var keys=['access_token','token','auth_token'];for(var i=0;i<keys.length;i++){try{var v=localStorage.getItem(keys[i]);if(v)AndroidBridge.postToken(v);}catch(e){}}}catch(e){console.log('localStorage wrap failed', e);} }catch(e){console.log('bridge inject failed', e);} })();"
                        view?.evaluateJavascript(inject, null)

                        // 3) 如果 native 端已有 token，注入到页面 localStorage（写入 raw token without 'bearer ' 前缀）
                        try {
                            val nativeToken = tokenManager.getAccessToken()
                            val savedAt = tokenManager.getTokenSavedAt()
                            val clearedAt = tokenManager.getTokenClearedAt()
                            // Only inject native token into page if it was saved after the last cleared timestamp
                            if (nativeToken != null && savedAt > clearedAt) {
                                // remove optional bearer prefix
                                var tokenPlain = nativeToken
                                if (tokenPlain.startsWith("bearer ", true)) tokenPlain = tokenPlain.substring(7)
                                // escape for JS single-quoted string
                                tokenPlain = tokenPlain.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                                val setScript = "(function(){try{localStorage.setItem('access_token', '$tokenPlain');}catch(e){console.log('set localStorage failed', e);} })();"
                                view?.evaluateJavascript(setScript, null)
                                Log.d("WebLoginActivity", "injected native token into localStorage (len=${tokenPlain.length})")
                            } else {
                                Log.d("WebLoginActivity", "skip injecting native token: savedAt=$savedAt clearedAt=$clearedAt")
                            }
                        } catch (t: Throwable) {
                            Log.e("WebLoginActivity", "failed to inject native token into localStorage", t)
                        }

                        // 4) 立即扫描 localStorage / sessionStorage 中可能的 token（例如 JWT 格式），并回传到 native
                        try {
                            val scanScript = "(function(){try{var jwtRegex=/^[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+$/;var found=0;for(var i=0;i<localStorage.length;i++){var k=localStorage.key(i);var v=localStorage.getItem(k);if(v&& (jwtRegex.test(v)||v.indexOf('eyJ')===0)){try{AndroidBridge.postToken(v);}catch(e){};found++;}}for(var j=0;j<sessionStorage.length;j++){var k=sessionStorage.key(j);var v=sessionStorage.getItem(k);if(v&& (jwtRegex.test(v)||v.indexOf('eyJ')===0)){try{AndroidBridge.postToken(v);}catch(e){};found++;}}return 'scanned:'+found;}catch(e){return 'scanerr:'+e.message;}})();"
                            view?.evaluateJavascript(scanScript) { res ->
                                try { Log.d("WebLoginActivity", "storage scan result=$res") } catch (_: Throwable) {}
                            }
                        } catch (t: Throwable) {
                            Log.e("WebLoginActivity", "failed to inject storage scanner", t)
                        }
                    } catch (t: Throwable) {
                        Log.e("WebLoginActivity", "evaluateJavascript failed", t)
                    }
                }

                override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                    try {
                        val reqUrl = request?.url?.toString()
                        val method = request?.method
                        val headers = request?.requestHeaders
                        val forMain = request?.isForMainFrame
                        Log.d("WebLoginActivity", "shouldInterceptRequest url=$reqUrl method=$method isForMainFrame=$forMain headers=$headers")
                    } catch (t: Throwable) {
                        Log.e("WebLoginActivity", "shouldInterceptRequest log failed", t)
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun shouldInterceptRequest(view: WebView?, url: String?): android.webkit.WebResourceResponse? {
                    try {
                        Log.d("WebLoginActivity", "shouldInterceptRequest(url) -> $url")
                    } catch (t: Throwable) {
                        Log.e("WebLoginActivity", "shouldInterceptRequest(url) log failed", t)
                    }
                    return super.shouldInterceptRequest(view, url)
                }
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    val errorDesc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.description?.toString() ?: "Unknown error"
                    } else {
                        "Unknown error"
                    }
                    val errorCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        error?.errorCode?.toString() ?: "unknown"
                    } else {
                        "unknown"
                    }
                    Log.e("WebLoginActivity", "onReceivedError url=${request?.url} code=$errorCode desc=$errorDesc")
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: android.webkit.WebResourceResponse?) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    Log.e("WebLoginActivity", "onReceivedHttpError url=${request?.url} status=${errorResponse?.statusCode}")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url ?: return false
                    Log.d("WebLoginActivity", "shouldOverrideUrlLoading request=${url}")
                    return handleUrl(url)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url ?: return false
                    Log.d("WebLoginActivity", "shouldOverrideUrlLoading url=$url")
                    return handleUrl(Uri.parse(url))
                }

                private fun handleUrl(uri: Uri): Boolean {
                    try {
                        if (uri.toString().startsWith(redirectPrefix)) {
                            // token may be in query or fragment
                            var token: String? = null
                            // try query first
                            token = uri.getQueryParameter("token")
                            if (token == null) {
                                // try fragment parsing
                                val frag = uri.fragment
                                if (frag != null) {
                                    val fragUri = Uri.parse("?$frag")
                                    token = fragUri.getQueryParameter("token")
                                }
                            }

                                                if (token != null) {
                                                val tokenVal = token!!
                                                val bearer = if (tokenVal.startsWith("bearer ", true)) tokenVal else "bearer $tokenVal"
                                                try {
                                                    try { Log.d("WebLoginActivity", "handleUrl detected token prefix=${tokenVal.take(8)}... (len=${tokenVal.length})") } catch (_: Throwable) {}
                                                    tokenManager.saveAccessToken(bearer)
                                                    try { Log.d("WebLoginActivity", "saved access token (len=${bearer.length}) from handleUrl (source=url)") } catch (_: Throwable) {}
                                                } catch (e: Throwable) {
                                                    Log.e("WebLoginActivity", "handleUrl failed to save token", e)
                                                }
                                // try to extract refresh token too if present
                                var refresh: String? = uri.getQueryParameter("refresh_token")
                                if (refresh == null) {
                                    val frag = uri.fragment
                                    if (frag != null) {
                                        val fragUri = Uri.parse("?$frag")
                                        refresh = fragUri.getQueryParameter("refresh_token")
                                    }
                                }
                                tokenManager.saveRefreshToken(refresh)

                                val data = Intent()
                                data.putExtra(RESULT_TOKEN, bearer)
                                data.putExtra(RESULT_TOKEN_SOURCE, "url")
                                setResult(Activity.RESULT_OK, data)
                                finish()
                                return true
                            }
                        }
                    } catch (t: Throwable) {
                        Log.e("WebLoginActivity", "handleUrl error for uri=$uri", t)
                    }
                    return false
                }
            }

            setContentView(webView)
            Log.d("WebLoginActivity", "loading url: $loginUrl")
            webView.loadUrl(loginUrl)
            // register receiver to clear WebView storage when token cleared
            try {
                tokenClearedReceiver = object : BroadcastReceiver() {
                    override fun onReceive(ctx: Context?, intent: Intent?) {
                        try {
                            Log.d("WebLoginActivity", "received token cleared broadcast, clearing WebView storage")
                            webView.evaluateJavascript("(function(){try{localStorage.removeItem('access_token');localStorage.removeItem('token');localStorage.removeItem('auth_token');}catch(e){} })();", null)
                            try {
                                CookieManager.getInstance().removeAllCookies(null)
                                CookieManager.getInstance().flush()
                            } catch (_: Throwable) {}
                        } catch (t: Throwable) {
                            Log.e("WebLoginActivity", "failed to clear WebView storage on broadcast", t)
                        }
                    }
                }
                registerReceiver(tokenClearedReceiver, IntentFilter(TokenManager.ACTION_TOKEN_CLEARED))
            } catch (_: Throwable) {}
        } catch (t: Throwable) {
            Log.e("WebLoginActivity", "onCreate failed", t)
            // ensure we finish so system doesn't keep a bad activity around
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (tokenClearedReceiver != null) {
                try { unregisterReceiver(tokenClearedReceiver) } catch (_: Throwable) {}
                tokenClearedReceiver = null
            }
        } catch (_: Throwable) {}
        try {
            webViewInstance?.destroy()
            webViewInstance = null
        } catch (_: Throwable) {}
    }
}
