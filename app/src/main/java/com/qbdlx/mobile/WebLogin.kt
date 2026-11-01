package com.qbdlx.mobile

import android.annotation.SuppressLint
import androidx.core.net.toUri
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

private const val LOGIN_URL = "https://play.qobuz.com/login"
private const val PLAYER_HOST = "play.qobuz.com"

// The web player refuses to run below 1024 px wide (it covers the page with a "download the app"
// notice and the sign-in never completes), and it pins its viewport to the device width. So on
// the player's own pages, widen the layout viewport; the sign-in pages on www.qobuz.com are left alone.
private const val WIDEN_VIEWPORT_JS = """
(function () {
  function f() {
    var m = document.querySelector('meta[name=viewport]');
    if (!m) { m = document.createElement('meta'); m.name = 'viewport'; document.head.appendChild(m); }
    m.setAttribute('content', 'width=1280');
  }
  if (document.head) f(); else document.addEventListener('DOMContentLoaded', f);
})();
"""

// At that width the "Log in" button is tiny, so press it once the page has rendered it.
private const val PRESS_LOGIN_JS = """
(function () {
  var tries = 0;
  var t = setInterval(function () {
    var b = document.querySelector('button.ButtonPrimary');
    if (b) { clearInterval(t); b.click(); } else if (++tries > 60) clearInterval(t);
  }, 250);
})();
"""

// Google (and usually Apple/Facebook) refuse to sign in inside an app's embedded browser.
private val SOCIAL_HOSTS = setOf("accounts.google.com", "appleid.apple.com", "www.facebook.com", "m.facebook.com")

private const val SIGNIN_HOST = "www.qobuz.com"

// Qobuz's sign-in form is marked autocomplete="off", and its password field "new-password" (the hint
// for creating one), so password managers would only fill the email. Mark them as a sign-in form.
private const val SIGNIN_AUTOFILL_JS = """
(function () {
  function f() {
    var form = document.getElementById('loginForm');
    if (form) form.setAttribute('autocomplete', 'on');
    var user = document.getElementById('_username');
    if (user) user.setAttribute('autocomplete', 'username');
    var pass = document.getElementById('_password');
    if (pass) pass.setAttribute('autocomplete', 'current-password');
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', f); else f();
})();
"""

private class LoginWebViewClient : WebViewClient() {
    /** True while the page is a social sign-in that cannot work in a WebView. */
    var onSocialLogin: ((Boolean) -> Unit)? = null

    /** Reports the page being loaded (host + path, no query) and load errors, for the status line. */
    var onStatus: ((String) -> Unit)? = null
    /** Reports each main-frame navigation, to decide when to cover the page with the loading layer. */
    var onNavigate: ((Uri) -> Unit)? = null

    private fun isPlayer(url: String?) = url?.toUri()?.host == PLAYER_HOST
    private fun isSignIn(url: String?) = url?.toUri()?.let { it.host == SIGNIN_HOST && it.path.orEmpty().startsWith("/signin") } == true

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        url?.toUri()?.let {
            onStatus?.invoke("${it.host}${it.path.orEmpty()}")
            onNavigate?.invoke(it)
            onSocialLogin?.invoke(it.host in SOCIAL_HOSTS)
        }
        if (isPlayer(url)) view.evaluateJavascript(WIDEN_VIEWPORT_JS, null)
        if (isSignIn(url)) view.evaluateJavascript(SIGNIN_AUTOFILL_JS, null)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) onStatus?.invoke("Error ${error.errorCode}: ${error.description} (${request.url.host})")
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (isSignIn(url)) view.evaluateJavascript(SIGNIN_AUTOFILL_JS, null)
        if (!isPlayer(url)) return
        view.evaluateJavascript(WIDEN_VIEWPORT_JS, null)
        if (url?.toUri()?.path == "/login") view.evaluateJavascript(PRESS_LOGIN_JS, null)
    }
}

/**
 * The web player is a ~9 MB single-page app and the first WebView of a process is slow to start,
 * so the login page is loaded in the background while the native login screen is showing.
 */
object LoginWebViewPool {
    private var view: WebView? = null

    // Created on the activity, not the application: Android's autofill (saved emails/passwords)
    // binds to the WebView's context when it is built and can't fill fields without an activity.
    @SuppressLint("SetJavaScriptEnabled")
    private fun create(activity: Context) = WebView(MutableContextWrapper(activity)).apply {
        importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        webViewClient = LoginWebViewClient()
        loadUrl(LOGIN_URL)
    }

    private fun WebView.activity() = (context as MutableContextWrapper).baseContext

    fun prewarm(activity: Context) {
        val current = view
        if (current != null && current.activity() === activity) return
        // The activity was recreated (e.g. rotation): don't keep the old one alive.
        current?.destroy()
        view = create(activity)
    }

    /** Hands over the preloaded WebView if it belongs to this activity, or a fresh one. */
    fun take(activity: Context): WebView {
        val v = view?.takeIf { it.activity() === activity } ?: run { view?.destroy(); create(activity) }
        view = null
        return v
    }
}

/**
 * Shows the Qobuz web player login in an in-app browser. Once the page has signed in, the player
 * stores the session in localStorage["localuser"] as { id, token, ... }; we read both from there.
 */
@Composable
fun WebLoginScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val webView = remember { LoginWebViewPool.take(context) }
    BackHandler { if (webView.canGoBack()) webView.goBack() else vm.cancelWebLogin() }
    var progress by remember { mutableIntStateOf(webView.progress) }
    var status by remember { mutableStateOf(webView.url?.toUri()?.let { "${it.host}${it.path.orEmpty()}" }.orEmpty()) }
    var social by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(webView.url?.toUri()) }
    remember {
        (webView.webViewClient as? LoginWebViewClient)?.let { c ->
            c.onStatus = { status = it }
            c.onSocialLogin = { social = it }
            c.onNavigate = { page = it }
        }
    }

    // The web player's own pages are only a step on the way (its login button is pressed for you, and
    // after signing in it is where the session is read), so keep them covered until the sign-in form
    // shows. If the player hasn't moved on after a while, uncover it so it can be used by hand.
    val onPlayer = page?.host == PLAYER_HOST
    var stuck by remember { mutableStateOf(false) }
    LaunchedEffect(page) { stuck = false; delay(15_000); stuck = true }
    val covered = page == null || progress < 100 || (onPlayer && !stuck)
    val signingIn = onPlayer && page?.path != "/login"
    remember {
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
        }
    }
    DisposableEffect(Unit) { onDispose { webView.destroy() } }

    LaunchedEffect(Unit) {
        while (true) {
            val raw = suspendCancellableCoroutine<String?> { c ->
                webView.evaluateJavascript("localStorage.getItem('localuser')") { c.resume(it) }
            }
            readSession(raw)?.let { (id, token) ->
                vm.finishWebLogin(id, token)
                return@LaunchedEffect
            }
            delay(1000)
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.cancelWebLogin() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, LocalStrings.current.back) }
            Text(LocalStrings.current.logInToQobuz, modifier = Modifier.padding(start = 4.dp))
        }
        if (social) {
            Text(
                LocalStrings.current.socialNotSupported,
                color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        if (status.isNotBlank()) {
            Text(status, fontSize = 11.sp, maxLines = 2, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        }
        if (progress in 0..99) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView({ webView }, Modifier.fillMaxSize())
            if (covered) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Text(if (signingIn) LocalStrings.current.signingIn else LocalStrings.current.loadingSignIn)
                }
            }
        }
    }
}

/** `raw` is the JSON-encoded result of evaluateJavascript: a quoted string, or `null`. */
private fun readSession(raw: String?): Pair<String, String>? {
    if (raw.isNullOrBlank() || raw == "null") return null
    return runCatching {
        val user = JSONObject(JSONArray("[$raw]").getString(0))
        // The player stores a flat entity ({ id, token, ... }); older builds nested the id under "user".
        val id = user.optString("id").ifBlank { user.optJSONObject("user")?.optString("id").orEmpty() }
        val token = user.optString("token")
        if (id.isNotBlank() && token.isNotBlank()) id to token else null
    }.getOrNull()
}
