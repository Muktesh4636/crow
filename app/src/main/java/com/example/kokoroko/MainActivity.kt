@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.example.kokoroko

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import com.example.kokoroko.BuildConfig
import com.example.kokoroko.R
import com.example.kokoroko.ui.theme.KokorokoTheme
import com.example.kokoroko.ui.theme.OrangePrimary
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.roundToInt
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val PKG_PHONEPE = "com.phonepe.app"
private const val PKG_GPay = "com.google.android.apps.nbu.paisa.user"
private const val PKG_PAYTM = "net.one97.paytm"

/** Wallet screen: warm brown ink (readable, no black/charcoal) */
private val WalletInkWarm = Color(0xFF6D4C41)

/** Home top bar: soft gray-black (not pure black) */
private val HeaderInkLight = Color(0xFF5C5C5C)
/** Home top bar: light orange accent (softer than [OrangePrimary]) */
private val HeaderOrangeLight = Color(0xFFFFB74D)

/**
 * Backend API origin (scheme + host, no trailing slash).
 * Update this when you set your production domain; all API URLs are derived from it.
 */
private const val API_BASE_URL = "https://fight.pravoo.in"

/** Joins [API_BASE_URL] with a path (leading `/` optional). */
private fun apiUrl(path: String): String {
    val base = API_BASE_URL.trimEnd('/')
    val p = path.trim().let { if (it.startsWith("/")) it else "/$it" }
    return base + p
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Cock fight live stream (HLS). Same host as [API_BASE_URL]. */
private val COCKFIGHT_LIVE_HLS_URL = apiUrl("/hls/live/stream/index.m3u8")
private val GAME_MERON_WALA_INFO_URL = apiUrl("/api/game/meron-wala/info/")

data class CockfightRoundVideo(
    val roundId: Int,
    val start: String?,   // ISO-8601 datetime or null
    val url: String?,
    val requiresAuthentication: Boolean,
    /** ISO time from API; used with fetched-at millis for skew (matches mweb `server_time`). */
    val serverTime: String?,
    /** Optional per-video labels (e.g. Red / Black); merged with [CockfightInfoResponse.sideLabelsRoot]. */
    val sideLabels: CockfightSideLabels?,
)

/** API `side_labels` payload: display names for COCK1 / COCK2. */
data class CockfightSideLabels(
    val cock1: String,
    val cock2: String,
)

/** Prefer video-round labels over root-level `side_labels`; default Cock 1 / Cock 2. */
private fun CockfightInfoResponse.effectiveSideLabels(): CockfightSideLabels =
    CockfightSideLabels(
        cock1 = latestRoundVideo?.sideLabels?.cock1 ?: sideLabelsRoot?.cock1 ?: "Cock 1",
        cock2 = latestRoundVideo?.sideLabels?.cock2 ?: sideLabelsRoot?.cock2 ?: "Cock 2"
    )

private fun JSONObject.optNonBlank(key: String): String? =
    optString(key, "").trim().takeIf { it.isNotEmpty() && it != "null" }

/** Parses top-level `"side_labels": { "COCK1": "...", "COCK2": "..." }` if present. */
private fun JSONObject.parseSideLabelsField(): CockfightSideLabels? {
    if (!has("side_labels") || isNull("side_labels")) return null
    val sl = optJSONObject("side_labels") ?: return null
    val c1 = sl.optNonBlank("COCK1")
    val c2 = sl.optNonBlank("COCK2")
    if (c1 == null && c2 == null) return null
    return CockfightSideLabels(c1 ?: "Cock 1", c2 ?: "Cock 2")
}

data class CockfightLastResult(
    val session: Int,
    val winner: String?,   // Canonical: COCK1, COCK2, DRAW (+ legacy strings normalized at parse)
    val settledAt: String?
)
data class CockfightInfoResponse(
    val session: Int?,
    val roundId: Int?,
    val open: Boolean,
    /** Top-level labels when present; may be superseded per-round by `latest_round_video.side_labels`. */
    val sideLabelsRoot: CockfightSideLabels?,
    val latestRoundVideo: CockfightRoundVideo?,
    val lastResult: CockfightLastResult?,
    /** `server_now - device_now` at fetch, same as mweb `clockSkew` for countdown. */
    val clockSkewMillis: Long,
)

/** Parse API ISO-8601 timestamp to epoch ms (used for server clock skew). */
private fun cockfightParseIsoToEpochMillis(iso: String?): Long? {
    val s = iso?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    return try {
        java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli()
    } catch (_: Exception) {
        try {
            java.time.Instant.parse(s).toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }
}

/** Live cricket feed — new structured endpoint with scores & odds. */
private val CRICKET_ODDS_API_URL = apiUrl("/api/cricket/live-events/")
/** Pre-match cricket events */
private val CRICKET_PRE_EVENTS_API_URL = apiUrl("/api/cricket/pre-events/")

private val cricketOddsHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(14, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

private data class MaintenanceStatusResponse(
    val maintenance: Boolean,
    val remainingHours: Int,
    val remainingMinutes: Int
)

/** Public endpoint; on failure returns null (app continues — fail open). */
private suspend fun fetchMaintenanceStatus(): MaintenanceStatusResponse? =
    withContext(Dispatchers.IO) {
        try {
            val req =
                Request.Builder()
                    .url(MAINTENANCE_STATUS_URL)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || text.isBlank()) return@withContext null
                val root = JSONObject(text)
                val j = root.optJSONObject("data") ?: root
                MaintenanceStatusResponse(
                    maintenance = j.optBoolean("maintenance", false),
                    remainingHours = j.optInt("remaining_hours", 0).coerceAtLeast(0),
                    remainingMinutes = j.optInt("remaining_minutes", 0).coerceAtLeast(0)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

private data class GameVersionResponse(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val forceUpdate: Boolean
)

/** Public endpoint; null if request fails (fail open — no update prompt). */
private suspend fun fetchGameVersion(): GameVersionResponse? =
    withContext(Dispatchers.IO) {
        try {
            val req =
                Request.Builder()
                    .url(GAME_VERSION_URL)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || text.isBlank()) return@withContext null
                val root = JSONObject(text)
                val j = root.optJSONObject("data") ?: root
                GameVersionResponse(
                    versionCode = j.optIntValue("version_code", 0),
                    versionName = j.optString("version_name", "").trim(),
                    downloadUrl = j.optString("download_url", "").trim(),
                    forceUpdate = j.optBoolean("force_update", false)
                )
            }
        } catch (_: Exception) {
            null
        }
    }

private val AUTH_LOGIN_URL = apiUrl("/api/auth/login/")
private val AUTH_WALLET_URL = apiUrl("/api/auth/wallet/")
/** Deposit / payout method rows: GPAY, BANK, QR, PHONEPE, PAYTM, UPI, … */
private val AUTH_PAYMENT_DETAILS_URL = apiUrl("/api/auth/payment-details/")
private val AUTH_PAYMENT_METHODS_URL = apiUrl("/api/auth/payment-methods/")
private val AUTH_PROFILE_URL = apiUrl("/api/auth/profile/")
private val AUTH_REFERRAL_DATA_URL = apiUrl("/api/auth/referral-data/")
private val AUTH_LOGOUT_URL = apiUrl("/api/auth/logout/")
private val AUTH_BANK_DETAILS_URL = apiUrl("/api/auth/bank-details/")
private val AUTH_WITHDRAW_INITIATE_URL = apiUrl("/api/auth/withdraws/initiate/")
private val AUTH_DEPOSITS_MINE_URL = apiUrl("/api/auth/deposits/mine/")
private val AUTH_WITHDRAWS_MINE_URL = apiUrl("/api/auth/withdraws/mine/")
private val SUPPORT_CONTACTS_API_URL = apiUrl("/api/support/contacts/")
private val MAINTENANCE_STATUS_URL = apiUrl("/api/maintenance/status/")
private val GAME_VERSION_URL = apiUrl("/api/game/version/")
/** POST body `{ "side": "COCK1"|"COCK2"|"DRAW", ... }`; MERON/WALA accepted as legacy aliases server-side. */
private val GAME_MERON_WALA_BET_URL = apiUrl("/api/game/meron-wala/bet/")
/** GET last 50 bets for the signed-in user, newest first */
private val GAME_MERON_WALA_BETS_MINE_URL = apiUrl("/api/game/meron-wala/bets/mine/")
/** POST body: { "number": 1-6, "chip_amount": "50.00" } */
private val GAME_GUNDUATA_BET_URL = apiUrl("/api/game/bet/")
/** GET last 50 Gundu Ata bets for the signed-in user, newest first */
private val GAME_GUNDUATA_BETS_MINE_URL = apiUrl("/api/game/bets/mine/")
/** Virtual tab loads this URL in an in-app [WebView] via [gunduataVirtualWebLoad] (query params + [GunduataAuthWebViewClient] localStorage). */
private const val GUNDUATA_VIRTUAL_GAME_URL = "https://gunduata.club/game/index.html"
/**
 * Public JSON list of past cock-fight highlight videos (admin-configured).
 * Expected shape examples:
 * `{ "highlights": [ { "title": "...", "video_url": "https://...", "thumbnail_url": "https://..." } ] }`
 * or top-level `[ {...}, ... ]` — app uses at most 4 entries.
 */
private val COCKFIGHT_HIGHLIGHTS_API_URL = apiUrl("/api/cockfight/highlights/")

/** Up to four items from [COCKFIGHT_HIGHLIGHTS_API_URL]. */
private data class CockfightHighlightVideo(
    val title: String,
    val videoUrl: String,
    val thumbnailUrl: String?
)

private fun extractCockfightHighlightsArray(root: JSONObject): JSONArray? {
    for (key in listOf("highlights", "videos", "items", "results", "records")) {
        root.optJSONArray(key)?.let { return it }
    }
    val data = root.optJSONObject("data") ?: return null
    for (key in listOf("highlights", "videos", "items", "results", "records")) {
        data.optJSONArray(key)?.let { return it }
    }
    return null
}

private fun parseCockfightHighlightsJson(body: String): List<CockfightHighlightVideo> {
    val out = mutableListOf<CockfightHighlightVideo>()
    try {
        val trimmed = body.trim()
        val arr =
            when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> extractCockfightHighlightsArray(JSONObject(trimmed)) ?: return emptyList()
            }
        for (i in 0 until arr.length()) {
            if (out.size >= 4) break
            val o = arr.optJSONObject(i) ?: continue
            val url =
                o.optString("video_url", "")
                    .ifBlank { o.optString("url", "") }
                    .ifBlank { o.optString("src", "") }
                    .ifBlank { o.optString("link", "") }
                    .trim()
            if (url.isEmpty()) continue
            val title =
                o.optString("title", "").ifBlank { o.optString("name", "") }.trim()
                    .ifBlank { "Fight ${out.size + 1}" }
            val thumb =
                o.optString("thumbnail_url", "")
                    .ifBlank { o.optString("thumbnail", "") }
                    .ifBlank { o.optString("image_url", "") }
                    .ifBlank { o.optString("poster", "") }
                    .trim()
                    .ifBlank { null }
            out.add(CockfightHighlightVideo(title, url, thumb))
        }
    } catch (_: Exception) { }
    return out
}

private suspend fun fetchCockfightHighlights(): List<CockfightHighlightVideo> =
    withContext(Dispatchers.IO) {
        try {
            val req =
                Request.Builder()
                    .url(COCKFIGHT_HIGHLIGHTS_API_URL)
                    .header("Accept", "application/json")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || text.isBlank()) return@withContext emptyList()
                parseCockfightHighlightsJson(text)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

private const val COCKFIGHT_HIGHLIGHTS_SLOT_COUNT = 4

/** Home screen always shows four tiles: API videos first, then placeholders until four. */
private fun cockfightHighlightsPaddedToFour(api: List<CockfightHighlightVideo>): List<CockfightHighlightVideo> {
    val real = api.filter { it.videoUrl.isNotBlank() }.take(COCKFIGHT_HIGHLIGHTS_SLOT_COUNT)
    val out = real.toMutableList()
    var n = out.size + 1
    while (out.size < COCKFIGHT_HIGHLIGHTS_SLOT_COUNT) {
        out.add(
            CockfightHighlightVideo(
                title = "Cock fight highlight $n",
                videoUrl = "",
                thumbnailUrl = null
            )
        )
        n++
    }
    return out
}

private const val PREFS_AUTH = "auth_prefs"
private const val PREFS_AUTH_TOKEN_KEY = "access_token"
private const val PREFS_REFRESH_TOKEN_KEY = "refresh_token"
private const val PREFS_LOCAL_DEMO_USER = "local_demo_username"

/** Sentinel stored as [AuthTokenStore.accessToken] for embedded demo logins (not a real JWT). */
private const val LOCAL_DEMO_SESSION_TOKEN = "LOCAL_DEMO_SESSION"

private object AuthTokenStore {
    @Volatile
    var accessToken: String? = null

    @Volatile
    var refreshToken: String? = null

    /** Display name for the current local demo session (when [isLocalDemoSession]). */
    @Volatile
    var localDemoUsername: String? = null

    fun isLocalDemoSession(): Boolean = accessToken == LOCAL_DEMO_SESSION_TOKEN

    fun load(context: Context) {
        val sp = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
        accessToken = sp.getString(PREFS_AUTH_TOKEN_KEY, null)
        refreshToken = sp.getString(PREFS_REFRESH_TOKEN_KEY, null)
        localDemoUsername =
            if (accessToken == LOCAL_DEMO_SESSION_TOKEN) {
                sp.getString(PREFS_LOCAL_DEMO_USER, null)
            } else {
                null
            }
    }

    fun save(context: Context, access: String, refresh: String? = null) {
        accessToken = access
        refreshToken = refresh
        localDemoUsername = null
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_AUTH_TOKEN_KEY, access)
            .remove(PREFS_LOCAL_DEMO_USER)
            .apply {
                if (refresh != null) putString(PREFS_REFRESH_TOKEN_KEY, refresh) else remove(PREFS_REFRESH_TOKEN_KEY)
            }
    }

    fun saveLocalDemo(context: Context, username: String) {
        val u = username.trim().ifBlank { "user" }
        accessToken = LOCAL_DEMO_SESSION_TOKEN
        localDemoUsername = u
        refreshToken = null
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .putString(PREFS_AUTH_TOKEN_KEY, LOCAL_DEMO_SESSION_TOKEN)
            .putString(PREFS_LOCAL_DEMO_USER, u)
            .remove(PREFS_REFRESH_TOKEN_KEY)
            .apply()
    }

    fun clear(context: Context) {
        accessToken = null
        refreshToken = null
        localDemoUsername = null
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit()
            .remove(PREFS_AUTH_TOKEN_KEY)
            .remove(PREFS_REFRESH_TOKEN_KEY)
            .remove(PREFS_LOCAL_DEMO_USER)
            .apply()
    }
}

/**
 * Fires whenever any authenticated API call receives HTTP 401.
 * The root composable collects this and immediately navigates to the login screen.
 */
private val sessionExpiredEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

/** Call from any suspend function that receives a 401 to force the user back to login. */
private suspend fun notifySessionExpired() {
    sessionExpiredEvents.emit(Unit)
}

private data class GunduataVirtualWebLoad(
    val loadUrl: String,
    val accessToken: String,
    val refreshToken: String
)

/**
 * URL + same tokens for [GunduataAuthWebViewClient] (query string often misses what SPAs read from localStorage).
 * Local demo: empty strings (user must use a real account for the web game).
 */
private fun gunduataVirtualWebLoad(): GunduataVirtualWebLoad {
    val access = if (AuthTokenStore.isLocalDemoSession()) "" else AuthTokenStore.accessToken.orEmpty()
    val refresh = if (AuthTokenStore.isLocalDemoSession()) "" else AuthTokenStore.refreshToken.orEmpty()
    val json =
        JSONObject()
            .put("accessToken", access)
            .put("refreshToken", refresh)
            .toString()
    val loadUrl =
        Uri.parse(GUNDUATA_VIRTUAL_GAME_URL).buildUpon()
            .appendQueryParameter("auth", json)
            .appendQueryParameter("accessToken", access)
            .appendQueryParameter("refreshToken", refresh)
            .build()
            .toString()
    return GunduataVirtualWebLoad(loadUrl, access, refresh)
}

/**
 * Injects access/refresh into localStorage (and a few common key aliases) so the gunduata.club SPA can
 * read auth the same way as when launched from a browser with a stored session.
 */
private class GunduataAuthWebViewClient(
    private val access: String,
    private val refresh: String
) : WebViewClient() {
    private fun injectAuth(view: WebView) {
        if (access.isEmpty() && refresh.isEmpty()) return
        val qa = JSONObject.quote(access)
        val qr = JSONObject.quote(refresh)
        val script =
            """
            (function() {
              var a = $qa, r = $qr;
              function write() {
                try {
                  if (a) {
                    localStorage.setItem("accessToken", a);
                    localStorage.setItem("access_token", a);
                    localStorage.setItem("access", a);
                    localStorage.setItem("token", a);
                    sessionStorage.setItem("accessToken", a);
                    sessionStorage.setItem("token", a);
                  }
                  if (r) {
                    localStorage.setItem("refreshToken", r);
                    localStorage.setItem("refresh_token", r);
                    sessionStorage.setItem("refreshToken", r);
                  }
                  var both = JSON.stringify({ accessToken: a, refreshToken: r });
                  localStorage.setItem("auth", both);
                  localStorage.setItem("kokoroko_auth", both);
                  window.dispatchEvent(new CustomEvent("kokoroko-auth", { detail: { accessToken: a, refreshToken: r } }));
                } catch (e) {}
              }
              write();
            })();
            """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    override fun onPageCommitVisible(view: WebView?, url: String?) {
        super.onPageCommitVisible(view, url)
        if (Build.VERSION.SDK_INT >= 23) view?.let { injectAuth(it) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        view?.let { injectAuth(it) }
    }
}

@Composable
private fun GunduataVirtualWebOverlay(
    load: GunduataVirtualWebLoad,
    onClose: () -> Unit
) {
    var webView: WebView? by remember { mutableStateOf(null) }
    val url = load.loadUrl
    val access = load.accessToken
    val refresh = load.refreshToken
    BackHandler {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onClose()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFAFA))
            .statusBarsPadding()
    ) {
        Column(Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (webView?.canGoBack() == true) {
                                webView?.goBack()
                            } else {
                                onClose()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF2C2C2C)
                        )
                    }
                    Text(
                        "Gundu Ata – Virtual",
                        color = Color(0xFF1C1C1C),
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
            }
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webView = this
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        }
                        CookieManager.getInstance().setAcceptCookie(true)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = GunduataAuthWebViewClient(access, refresh)
                        if (access.isNotEmpty()) {
                            loadUrl(
                                url,
                                mapOf(
                                    "Authorization" to "Bearer $access",
                                    "X-Access-Token" to access
                                )
                            )
                        } else {
                            loadUrl(url)
                        }
                    }
                },
                onRelease = { v ->
                    (v as? WebView)?.let { wv ->
                        wv.stopLoading()
                        wv.removeAllViews()
                        wv.destroy()
                    }
                    webView = null
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .navigationBarsPadding()
            )
        }
    }
}

/** Case-insensitive username -> password for offline demo accounts (not on the server). */
private val LOCAL_APP_LOGIN_USERS: Map<String, String> = mapOf(
    "svs" to "svs"
)

private data class BankDetailsUi(
    val accountHolder: String,
    val bankName: String,
    val accountNumber: String,
    val ifsc: String,
    val branch: String = ""
)

/** Parsed from GET [AUTH_WALLET_URL]: id, balance, unavailable_balance, withdrawable_balance (+ optional payment_methods, bank/UPI fields). */
private data class WalletApiResult(
    val bank: BankDetailsUi?,
    val upiId: String?,
    val qrImageUrl: String?,
    val paymentMethods: List<WalletPaymentMethodItem>?,
    val walletId: Int? = null,
    /** Total balance (decimal string or JSON number from API). */
    val balance: String? = null,
    val unavailableBalance: String? = null,
    val withdrawableBalance: String? = null
)

private fun JSONObject.optAmountString(vararg keys: String): String? {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        when (val v = opt(k)) {
            is Number -> return v.toString()
            is String -> return v.trim().takeIf { it.isNotBlank() }
            else -> { }
        }
    }
    return null
}

/** "1500.50" → "₹1,500.50" for display. */
private fun formatRupeeBalanceForDisplay(raw: String?): String {
    if (raw.isNullOrBlank()) return "₹0"
    val t = raw.trim()
    if (t.startsWith("₹")) return t
    val num = t.removePrefix("₹").trim().replace(",", "").toDoubleOrNull()
    return if (num != null) {
        "₹" + String.format(Locale.US, "%,.2f", num)
    } else {
        "₹$t"
    }
}

private data class WalletPaymentMethodItem(
    val id: Int = 0,
    val name: String,
    val type: String,
    val packageName: String? = null,
    val deepLink: String? = null,
    /** Per-method UPI / VPA when API returns one row per app (e.g. GPAY). */
    val upiId: String? = null
)

private fun JSONObject.pickString(vararg keys: String): String? {
    for (k in keys) {
        if (!has(k) || isNull(k)) continue
        val s = optString(k, "").trim()
        if (s.isNotBlank()) return s
    }
    return null
}

private fun JSONObject.optIntValue(key: String, default: Int = 0): Int {
    if (!has(key) || isNull(key)) return default
    return when (val v = opt(key)) {
        is Int -> v
        is Long -> v.toInt()
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }
}

/** Parses list withdrawal amounts: int/long/double in JSON or decimal strings like "500.00". */
/** Deposit row amount as rupees int (API may send JSON number or decimal string). */
private fun JSONObject.optRupeeAmountInt(key: String, default: Int = 0): Int {
    if (!has(key) || isNull(key)) return default
    val s = optRupeeWithdrawAmount(key)
    return s.toDoubleOrNull()?.roundToInt() ?: s.toIntOrNull() ?: default
}

private fun JSONObject.optRupeeWithdrawAmount(key: String): String {
    if (!has(key) || isNull(key)) return "0"
    return when (val v = opt(key)) {
        is String -> {
            val s = v.trim().replace(",", "").replace(" ", "")
            if (s.isBlank()) return "0"
            runCatching { BigDecimal(s).stripTrailingZeros().toPlainString() }.getOrElse { s }
        }
        is Int -> v.toString()
        is Long -> v.toString()
        is Double -> BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
        is Float -> BigDecimal.valueOf(v.toDouble()).stripTrailingZeros().toPlainString()
        is Number -> {
            val d = v.toDouble()
            val l = v.toLong()
            if (!d.isNaN() && d == l.toDouble()) {
                l.toString()
            } else {
                BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
            }
        }
        else -> "0"
    }
}

private fun resolvePaymentMediaUrl(path: String): String {
    val p = path.trim()
    if (p.isEmpty()) return p
    if (p.startsWith("http://", ignoreCase = true) || p.startsWith("https://", ignoreCase = true)) return p
    val base = API_BASE_URL.trimEnd('/')
    return if (p.startsWith("/")) base + p else "$base/$p"
}

private fun parsePaymentMethodsArray(arr: JSONArray): List<WalletPaymentMethodItem> {
    val out = ArrayList<WalletPaymentMethodItem>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val name = o.pickString("name", "title", "label") ?: continue
        val type = o.pickString("type", "app", "id")?.lowercase(Locale.US) ?: "custom"
        val pkg = o.pickString("package", "package_name", "android_package")
        val deep = o.pickString("deep_link", "url", "link")
        val rowUpi = o.pickString("upi_id", "upi", "vpa")
        out.add(WalletPaymentMethodItem(name = name, type = type, packageName = pkg, deepLink = deep, upiId = rowUpi))
    }
    return out
}

/** True when array rows use `method_type` (GPAY, BANK, QR, …). */
private fun isPaymentDetailsMethodArray(arr: JSONArray): Boolean =
    arr.length() > 0 && arr.optJSONObject(0)?.has("method_type") == true

/**
 * Parsed from an array of payment detail objects (see API: GPAY with upi_id, BANK with account fields, QR with qr_image).
 */
private fun parsePaymentDetailsArray(arr: JSONArray): WalletApiResult {
    var bank: BankDetailsUi? = null
    var qrUrl: String? = null
    var defaultUpi: String? = null
    val methodItems = ArrayList<WalletPaymentMethodItem>()

    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        if (!o.optBoolean("is_active", true)) continue
        val rowId = o.optIntValue("id")
        val mt = o.optString("method_type", "").trim().uppercase(Locale.US)
        when (mt) {
            "BANK" -> {
                val holder = o.optString("account_name", "").trim()
                val bn = o.optString("bank_name", "").trim()
                val acc = o.optString("account_number", "").trim()
                val ifsc = o.optString("ifsc_code", "").trim()
                if (holder.isNotBlank() || bn.isNotBlank() || acc.isNotBlank() || ifsc.isNotBlank()) {
                    bank = BankDetailsUi(holder, bn, acc, ifsc, "")
                }
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = o.optString("name", "Bank Account").ifBlank { "Bank Account" }, type = "bank"))
            }
            "QR" -> {
                val path = o.pickString("qr_image", "qr_url", "qr_code")?.trim()
                if (!path.isNullOrBlank()) qrUrl = resolvePaymentMediaUrl(path)
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = o.optString("name", "QR Code").ifBlank { "QR Code" }, type = "qr"))
            }
            "GPAY", "GOOGLE_PAY" -> {
                val name = o.optString("name", "Google Pay").ifBlank { "Google Pay" }
                val upi = o.optString("upi_id", "").trim()
                val link = o.optString("link", "").trim()
                if (defaultUpi.isNullOrBlank() && upi.isNotBlank()) defaultUpi = upi
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = name, type = "gpay", deepLink = link.ifBlank { null }, upiId = upi.ifBlank { null }))
            }
            "PHONEPE" -> {
                val name = o.optString("name", "PhonePe").ifBlank { "PhonePe" }
                val upi = o.optString("upi_id", "").trim()
                val link = o.optString("link", "").trim()
                if (defaultUpi.isNullOrBlank() && upi.isNotBlank()) defaultUpi = upi
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = name, type = "phonepe", deepLink = link.ifBlank { null }, upiId = upi.ifBlank { null }))
            }
            "PAYTM" -> {
                val name = o.optString("name", "Paytm").ifBlank { "Paytm" }
                val upi = o.optString("upi_id", "").trim()
                val link = o.optString("link", "").trim()
                if (defaultUpi.isNullOrBlank() && upi.isNotBlank()) defaultUpi = upi
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = name, type = "paytm", deepLink = link.ifBlank { null }, upiId = upi.ifBlank { null }))
            }
            "UPI" -> {
                val name = o.optString("name", "UPI").ifBlank { "UPI" }
                val upi = o.optString("upi_id", "").trim()
                val link = o.optString("link", "").trim()
                if (defaultUpi.isNullOrBlank() && upi.isNotBlank()) defaultUpi = upi
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = name, type = "upi", deepLink = link.ifBlank { null }, upiId = upi.ifBlank { null }))
            }
            else -> {
                val upi = o.optString("upi_id", "").trim()
                val link = o.optString("link", "").trim()
                val name = o.optString("name", mt.ifBlank { "Pay" }).ifBlank { "Pay" }
                if (defaultUpi.isNullOrBlank() && upi.isNotBlank()) defaultUpi = upi
                val normType = when {
                    mt.contains("GPAY") || mt.contains("GOOGLE") -> "gpay"
                    mt.contains("PHONE") -> "phonepe"
                    mt.contains("PAYTM") -> "paytm"
                    else -> mt.lowercase(Locale.US).ifBlank { "upi" }
                }
                methodItems.add(WalletPaymentMethodItem(id = rowId, name = name, type = normType, deepLink = link.ifBlank { null }, upiId = upi.ifBlank { null }))
            }
        }
    }

    return WalletApiResult(
        bank = bank,
        upiId = defaultUpi,
        qrImageUrl = qrUrl,
        paymentMethods = methodItems.takeIf { it.isNotEmpty() }
    )
}

private fun mergeWalletApiResults(detail: WalletApiResult, legacy: WalletApiResult): WalletApiResult =
    WalletApiResult(
        bank = detail.bank ?: legacy.bank,
        upiId = detail.upiId ?: legacy.upiId,
        qrImageUrl = detail.qrImageUrl ?: legacy.qrImageUrl,
        paymentMethods = detail.paymentMethods?.takeIf { it.isNotEmpty() } ?: legacy.paymentMethods,
        walletId = detail.walletId ?: legacy.walletId,
        balance = detail.balance ?: legacy.balance,
        unavailableBalance = detail.unavailableBalance ?: legacy.unavailableBalance,
        withdrawableBalance = detail.withdrawableBalance ?: legacy.withdrawableBalance
    )

private fun findPaymentDetailsArrayInRoot(root: JSONObject): JSONArray? {
    val data = root.optJSONObject("data") ?: root.optJSONObject("wallet")
    val candidates =
        listOfNotNull(
            root.optJSONArray("payment_details"),
            root.optJSONArray("payment_methods"),
            data?.optJSONArray("payment_details"),
            data?.optJSONArray("payment_methods"),
            root.optJSONArray("results"),
            data?.optJSONArray("results")
        )
    return candidates.firstOrNull { arr -> isPaymentDetailsMethodArray(arr) }
}

private fun parseWalletApiResultLegacy(root: JSONObject): WalletApiResult {
    val data = root.optJSONObject("data") ?: root.optJSONObject("wallet") ?: root
    val bank = parseBankDetailsFromWalletJson(root)
    val upiId =
        data.pickString("upi_id", "upi", "vpa", "merchant_upi", "upi_address")
            ?: root.pickString("upi_id", "upi", "vpa")
    val qrUrlRaw =
        data.pickString("qr_code", "qr_url", "qr_image", "qr_image_url", "payment_qr", "upi_qr")
            ?: root.pickString("qr_url", "qr_code")
    val qrUrl = qrUrlRaw?.takeIf { it.isNotBlank() }?.let { resolvePaymentMediaUrl(it) }
    val arr =
        data.optJSONArray("payment_methods")
            ?: data.optJSONArray("methods")
            ?: data.optJSONArray("upi_apps")
            ?: root.optJSONArray("payment_methods")
    val methods =
        when {
            arr == null || arr.length() == 0 -> null
            isPaymentDetailsMethodArray(arr) -> null
            else -> parsePaymentMethodsArray(arr)
        }
    val walletId =
        if (data.has("id") && !data.isNull("id")) data.optIntValue("id") else null
    val balance = data.optAmountString("balance", "available_balance", "total_balance")
    val unavailable = data.optAmountString("unavailable_balance", "locked_balance", "pending_balance")
    val withdrawable = data.optAmountString("withdrawable_balance", "available_withdrawal")
    return WalletApiResult(
        bank = bank,
        upiId = upiId,
        qrImageUrl = qrUrl,
        paymentMethods = methods,
        walletId = walletId,
        balance = balance,
        unavailableBalance = unavailable,
        withdrawableBalance = withdrawable
    )
}

private fun parseWalletApiResult(root: JSONObject): WalletApiResult {
    val detailArr = findPaymentDetailsArrayInRoot(root)
    val fromDetails =
        detailArr?.takeIf { isPaymentDetailsMethodArray(it) }?.let { parsePaymentDetailsArray(it) }
    val legacy = parseWalletApiResultLegacy(root)
    return if (fromDetails != null) mergeWalletApiResults(fromDetails, legacy) else legacy
}

private fun parseWalletResponseBody(text: String): WalletApiResult {
    val t = text.trim()
    if (t.isEmpty()) return WalletApiResult(null, null, null, null)
    if (t.startsWith("[")) {
        val arr = JSONArray(t)
        return if (isPaymentDetailsMethodArray(arr)) {
            parsePaymentDetailsArray(arr)
        } else {
            WalletApiResult(
                bank = null,
                upiId = null,
                qrImageUrl = null,
                paymentMethods = parsePaymentMethodsArray(arr).takeIf { it.isNotEmpty() }
            )
        }
    }
    return parseWalletApiResult(JSONObject(t))
}

private fun defaultWalletPaymentMethods(): List<WalletPaymentMethodItem> =
    listOf(
        WalletPaymentMethodItem(name = "PhonePe", type = "phonepe"),
        WalletPaymentMethodItem(name = "Google Pay", type = "gpay"),
        WalletPaymentMethodItem(name = "UPI ID", type = "upi"),
        WalletPaymentMethodItem(name = "Paytm", type = "paytm")
    )

private fun parseBankDetailsFromWalletJson(root: JSONObject): BankDetailsUi? {
    val data = root.optJSONObject("data") ?: root.optJSONObject("wallet") ?: root
    val bank =
        data.optJSONObject("bank_account")
            ?: data.optJSONObject("bank")
            ?: data.optJSONObject("bank_details")
            ?: data.optJSONObject("user_bank")
    val obj =
        bank
            ?: if (data.has("account_number") || data.has("ifsc")) data else null
            ?: return null
    fun pick(vararg keys: String): String {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val v = obj.opt(k)
            val s =
                when (v) {
                    is String -> v.trim()
                    is Number -> v.toString()
                    else -> v?.toString()?.trim()?.takeIf { it != "null" } ?: ""
                }
            if (s.isNotBlank()) return s
        }
        return ""
    }
    val holder = pick("account_holder", "account_holder_name", "account_name", "holder_name", "beneficiary_name", "name")
    val bankName = pick("bank_name", "bank", "bankName")
    val acc = pick("account_number", "account_no", "acc_number", "accountNumber")
    val ifsc = pick("ifsc", "ifsc_code", "IFSC", "ifscCode")
    val branch = pick("branch", "branch_name", "branchName")
    if (holder.isBlank() && bankName.isBlank() && acc.isBlank() && ifsc.isBlank()) return null
    return BankDetailsUi(holder, bankName, acc, ifsc, branch)
}

private suspend fun fetchWalletFromApi(): Pair<WalletApiResult?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load payment details.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(
                WalletApiResult(
                    bank = null,
                    upiId = null,
                    qrImageUrl = null,
                    paymentMethods = emptyList(),
                    walletId = null,
                    balance = "0",
                    unavailableBalance = "0",
                    withdrawableBalance = "0"
                ),
                null
            )
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_WALLET_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> {
                        notifySessionExpired()
                        Pair(null, "Session expired. Please sign in again.")
                    }
                    !resp.isSuccessful || text.isBlank() ->
                        Pair(null, "Could not load payment details (${resp.code}).")
                    else -> Pair(parseWalletResponseBody(text), null)
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

/** Single call to [AUTH_PAYMENT_METHODS_URL] — no wallet balance needed for the payment screen. */
private suspend fun fetchPaymentOptionsFromApi(): Pair<WalletApiResult?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load payment details.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(
                WalletApiResult(
                    bank = null,
                    upiId = null,
                    qrImageUrl = null,
                    paymentMethods = emptyList(),
                    walletId = null,
                    balance = "0",
                    unavailableBalance = "0",
                    withdrawableBalance = "0"
                ),
                null
            )
        }
        try {
            val req = Request.Builder()
                .url(AUTH_PAYMENT_METHODS_URL)
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful && text.isNotBlank()) {
                    return@withContext Pair(parseWalletResponseBody(text), null)
                }
                Pair(null, "Could not load payment methods (${resp.code}).")
            }
        } catch (e: Exception) {
            Pair(null, "Network error: ${e.message}")
        }
    }

private data class LoginResult(val success: Boolean, val errorMsg: String = "")

private suspend fun postAuthLogin(context: Context, phone: String, password: String): LoginResult =
    withContext(Dispatchers.IO) {
        try {
            val input = phone.trim()
            val localKey = input.lowercase(Locale.US)
            LOCAL_APP_LOGIN_USERS[localKey]?.let { expected ->
                if (password == expected) {
                    AuthTokenStore.saveLocalDemo(context, input.ifBlank { localKey })
                    return@withContext LoginResult(true)
                }
            }
            val json = JSONObject().apply {
                put("username", input)
                put("password", password)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder().url(AUTH_LOGIN_URL).post(body).header("Accept", "application/json").build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val j = runCatching { JSONObject(text) }.getOrNull()
                val apiError = j?.optString("error", "")?.takeIf { it.isNotBlank() }
                if (!resp.isSuccessful) {
                    return@withContext LoginResult(false, apiError ?: "Login failed (HTTP ${resp.code})")
                }
                if (apiError != null) return@withContext LoginResult(false, apiError)
                val access = j?.optString("access", "")?.ifBlank { j?.optString("token", "") }.orEmpty()
                val refresh: String? =
                    j?.let { jo ->
                        listOf("refresh", "refresh_token")
                            .map { jo.optString(it, "") }
                            .firstOrNull { it.isNotBlank() }
                    }
                if (access.isNotBlank()) {
                    AuthTokenStore.save(context, access, refresh)
                    return@withContext LoginResult(true)
                }
                LoginResult(false, "No token in response. Please contact support.")
            }
        } catch (e: Exception) {
            LoginResult(false, "Network error: ${e.message}")
        }
    }

/** Matches GET [AUTH_PROFILE_URL] payload: id, username, email, phone_number, gender, is_staff. */
private data class ProfileDetailsApi(
    val id: Int,
    val username: String,
    val email: String,
    val phoneNumber: String,
    val gender: String?,
    val isStaff: Boolean
)

/** UI labels for gender radios; maps to API enum on save. */
private fun profileGenderApiToUi(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return when (raw.trim().uppercase(Locale.US)) {
        "MALE", "M" -> "Male"
        "FEMALE", "F" -> "Female"
        "OTHER" -> "Other"
        else -> {
            val g = raw.trim().lowercase(Locale.US)
            when {
                g in listOf("f", "female", "woman", "2") -> "Female"
                g in listOf("m", "male", "man", "1") -> "Male"
                else -> "Other"
            }
        }
    }
}

private fun profileGenderUiToApi(ui: String?): String? =
    when (ui) {
        "Male" -> "MALE"
        "Female" -> "FEMALE"
        "Other" -> "OTHER"
        else -> null
    }

private fun parseProfileFromJson(root: JSONObject): ProfileDetailsApi {
    val data = root.optJSONObject("data") ?: root.optJSONObject("profile") ?: root.optJSONObject("user") ?: root
    val id = data.optIntValue("id", 0)
    val username =
        data.pickString("username", "name", "full_name", "display_name").orEmpty().trim()
    val phone = data.pickString("phone_number", "phone", "mobile", "contact_number").orEmpty().trim()
    val email = data.pickString("email", "email_address").orEmpty().trim()
    val genderApi: String? =
        when {
            !data.has("gender") || data.isNull("gender") -> null
            else -> data.optString("gender", "").trim().takeIf { it.isNotBlank() }
        }
    val isStaff = data.optBoolean("is_staff", false)
    return ProfileDetailsApi(
        id = id,
        username = username,
        email = email,
        phoneNumber = phone,
        gender = genderApi,
        isStaff = isStaff
    )
}

private fun localDemoProfile(): ProfileDetailsApi {
    val u = AuthTokenStore.localDemoUsername?.trim().orEmpty().ifBlank { "user" }
    return ProfileDetailsApi(
        id = -1,
        username = u,
        email = "",
        phoneNumber = "",
        gender = null,
        isStaff = false
    )
}

private data class ReferralFriendRow(
    val username: String,
    val hasDeposit: Boolean,
    val dateJoined: String? = null
)

private data class ReferralCommissionSlab(
    val minReferrals: Int,
    val maxReferrals: Int?,
    val commissionPercent: Double
)

private data class ReferralDailyCommissionRow(
    val commissionDate: String,
    val refereeUsername: String,
    val lossAmountDisplay: String,
    val commissionPercent: Double,
    val commissionAmountDisplay: String
)

private data class ReferralDataApi(
    val referralCode: String,
    val commissionRatePercent: Double,
    val totalReferrals: Int,
    val activeReferrals: Int,
    val totalEarnings: String,
    val commissionEarnedToday: String,
    val instantReferralBonusPerReferee: Int,
    val totalCommissionEarnings: String,
    val totalDailyCommissionEarnings: String,
    val commissionTodayIst: String?,
    val totalLegacyReferralBonusEarnings: String,
    val commissionSlabs: List<ReferralCommissionSlab>,
    val recentDailyCommissions: List<ReferralDailyCommissionRow>,
    val referrals: List<ReferralFriendRow> = emptyList()
)

private fun JSONObject.optAnyAmountString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return when (val v = opt(key)) {
        is Number -> {
            val d = v.toDouble()
            if (kotlin.math.abs(d - kotlin.math.floor(d)) < 1e-9) kotlin.math.round(d).toLong().toString()
            else v.toString().trim()
        }

        else -> optString(key, "").trim()
    }
}

private fun spacedReferralCode(raw: String): String =
    raw.trim().toCharArray().joinToString(" ")

private fun formatReferralCommissionPctForUi(rate: Double): String {
    if (rate.isNaN()) return "—"
    if (kotlin.math.abs(rate - kotlin.math.round(rate)) < 1e-6) {
        return kotlin.math.round(rate).toInt().toString()
    }
    val t = kotlin.math.round(rate * 10) / 10.0
    return t.toString().removeSuffix(".0")
}

private fun rupeeLabelForReferral(amount: String): String {
    val t = amount.trim()
    return if (t.isEmpty()) "₹0" else if (t.startsWith("₹")) t else "₹$t"
}

private fun localDemoReferral(): ReferralDataApi {
    val u = AuthTokenStore.localDemoUsername?.trim().orEmpty().ifBlank { "demo" }
    return ReferralDataApi(
        referralCode = u,
        commissionRatePercent = 2.0,
        totalReferrals = 0,
        activeReferrals = 0,
        totalEarnings = "0",
        commissionEarnedToday = "0",
        instantReferralBonusPerReferee = 0,
        totalCommissionEarnings = "0",
        totalDailyCommissionEarnings = "0",
        commissionTodayIst = null,
        totalLegacyReferralBonusEarnings = "0",
        commissionSlabs = emptyList(),
        recentDailyCommissions = emptyList(),
        referrals = emptyList()
    )
}

private fun parseReferralFromJson(root: JSONObject): ReferralDataApi {
    val data = root.optJSONObject("data") ?: root
    val code = data.pickString("referral_code")?.trim().orEmpty()
    val rate =
        when {
            data.has("commission_rate_percent") && !data.isNull("commission_rate_percent") ->
                data.optDouble("commission_rate_percent", 0.0)
            else -> {
                val slabsInit = data.optJSONArray("commission_slabs")
                if (slabsInit != null && slabsInit.length() > 0) {
                    slabsInit.optJSONObject(0)?.optDouble("commission_percent", 0.0) ?: 0.0
                } else {
                    0.0
                }
            }
        }
    val totalEarn =
        data.pickString("total_earnings")?.takeIf { it.isNotBlank() }
            ?: data.pickString("total_commission_earnings")?.takeIf { it.isNotBlank() }
            ?: "0"
    val todayEarn = data.pickString("commission_earned_today")?.takeIf { it.isNotBlank() } ?: "0"
    val instantBonus = data.optIntValue("instant_referral_bonus_per_referee", 0)
    val totalCommEarn = data.pickString("total_commission_earnings")?.takeIf { it.isNotBlank() } ?: "0"
    val totalDailyEarn = data.pickString("total_daily_commission_earnings")?.takeIf { it.isNotBlank() } ?: "0"
    val legacyEarn = data.pickString("total_legacy_referral_bonus_earnings")?.takeIf { it.isNotBlank() } ?: "0"
    val ist = data.pickString("commission_today_ist")?.takeIf { it.isNotBlank() }

    val slabs = mutableListOf<ReferralCommissionSlab>()
    val slabArr = data.optJSONArray("commission_slabs")
    if (slabArr != null) {
        for (i in 0 until slabArr.length()) {
            val s = slabArr.optJSONObject(i) ?: continue
            val minR = s.optIntValue("min_referrals", 1)
            val maxR: Int? =
                when {
                    !s.has("max_referrals") || s.isNull("max_referrals") -> null
                    else -> s.optInt("max_referrals", -999).takeUnless { it < 0 }
                }
            val cp =
                if (s.has("commission_percent") && !s.isNull("commission_percent")) {
                    s.optDouble("commission_percent", 0.0)
                } else {
                    0.0
                }
            slabs.add(ReferralCommissionSlab(minReferrals = minR, maxReferrals = maxR, commissionPercent = cp))
        }
    }

    val dailies = mutableListOf<ReferralDailyCommissionRow>()
    val dcArr = data.optJSONArray("recent_daily_commissions")
    if (dcArr != null) {
        for (i in 0 until dcArr.length()) {
            val o = dcArr.optJSONObject(i) ?: continue
            val pct =
                if (o.has("commission_percent") && !o.isNull("commission_percent")) {
                    o.optDouble("commission_percent", 0.0)
                } else {
                    0.0
                }
            dailies.add(
                ReferralDailyCommissionRow(
                    commissionDate = o.pickString("commission_date")?.trim().orEmpty(),
                    refereeUsername = o.pickString("referee_username")?.trim().orEmpty(),
                    lossAmountDisplay = o.optAnyAmountString("loss_amount"),
                    commissionPercent = pct,
                    commissionAmountDisplay = o.optAnyAmountString("commission_amount")
                )
            )
        }
    }

    val refs = mutableListOf<ReferralFriendRow>()
    val arr = data.optJSONArray("referrals")
    if (arr != null) {
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val un = o.pickString("username")?.trim().orEmpty()
            if (un.isEmpty()) continue
            val dj = o.pickString("date_joined", "joined_at")?.takeIf { it.isNotBlank() }
            refs.add(
                ReferralFriendRow(username = un, hasDeposit = o.optBoolean("has_deposit", false), dateJoined = dj)
            )
        }
    }
    return ReferralDataApi(
        referralCode = code,
        commissionRatePercent = rate,
        totalReferrals = data.optIntValue("total_referrals", 0),
        activeReferrals = data.optIntValue("active_referrals", 0),
        totalEarnings = totalEarn,
        commissionEarnedToday = todayEarn,
        instantReferralBonusPerReferee = instantBonus,
        totalCommissionEarnings = totalCommEarn,
        totalDailyCommissionEarnings = totalDailyEarn,
        commissionTodayIst = ist,
        totalLegacyReferralBonusEarnings = legacyEarn,
        commissionSlabs = slabs,
        recentDailyCommissions = dailies,
        referrals = refs
    )
}

private suspend fun fetchAuthReferralData(): Pair<ReferralDataApi?, String?> =
    withContext(Dispatchers.IO) {
        val token =
            AuthTokenStore.accessToken
                ?: return@withContext Pair(null, "Sign in to view referral information.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(localDemoReferral(), null)
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_REFERRAL_DATA_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> {
                        notifySessionExpired()
                        Pair(null, "Session expired. Please sign in again.")
                    }
                    !resp.isSuccessful || text.isBlank() ->
                        Pair(null, "Could not load referral data (${resp.code}).")
                    else -> Pair(parseReferralFromJson(JSONObject(text)), null)
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private suspend fun fetchAuthProfile(): Pair<ProfileDetailsApi?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load your profile.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(localDemoProfile(), null)
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_PROFILE_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> { notifySessionExpired(); Pair(null, "Session expired. Please sign in again.") }
                    !resp.isSuccessful || text.isBlank() ->
                        Pair(null, "Could not load profile (${resp.code}).")
                    else -> Pair(parseProfileFromJson(JSONObject(text)), null)
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

/** Partial update via POST [AUTH_PROFILE_URL] — writable: username, email, phone_number, gender. */
private suspend fun postAuthProfile(
    username: String,
    phoneNumber: String,
    email: String,
    genderUi: String?
): Pair<Boolean, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(false, "Sign in required.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(false, "Offline demo account — profile is not saved to the server.")
        }
        try {
            val json =
                JSONObject().apply {
                    put("username", username.trim())
                    put("phone_number", phoneNumber.trim())
                    put("email", email.trim())
                    profileGenderUiToApi(genderUi)?.let { put("gender", it) }
                }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req =
                Request.Builder()
                    .url(AUTH_PROFILE_URL)
                    .post(body)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $token")
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return@withContext Pair(true, null)
                val err =
                    try {
                        val j = JSONObject(text)
                        j.optString("detail", "").ifBlank {
                            j.optString("error", "").ifBlank {
                                j.keys().asSequence().firstOrNull()?.let { k ->
                                    j.optJSONArray(k)?.let { arr ->
                                        if (arr.length() > 0) arr.getString(0) else null
                                    }
                                }.orEmpty()
                            }
                        }
                    } catch (_: Exception) {
                        text
                    }
                Pair(false, err.ifBlank { "Update failed (${resp.code})" })
            }
        } catch (e: Exception) {
            Pair(false, e.message)
        }
    }

/** POST [AUTH_LOGOUT_URL] with Bearer token, then clear [AuthTokenStore] (always clears locally). */
private suspend fun performAuthLogout(context: Context) {
    withContext(Dispatchers.IO) {
        if (AuthTokenStore.isLocalDemoSession()) {
            AuthTokenStore.clear(context)
            return@withContext
        }
        val token = AuthTokenStore.accessToken
        if (token != null) {
            try {
                val body = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())
                val req =
                    Request.Builder()
                        .url(AUTH_LOGOUT_URL)
                        .post(body)
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()
                cricketOddsHttpClient.newCall(req).execute().use { }
            } catch (_: Exception) {
            }
        }
        AuthTokenStore.clear(context)
    }
}

/** Saved payout / withdrawal destination from [AUTH_BANK_DETAILS_URL] */
private data class AuthBankDetailsApi(
    val accountName: String,
    val bankName: String,
    val accountNumber: String,
    val ifscCode: String,
    val upiId: String,
    val isDefault: Boolean,
    /** From `bank_accounts[]` — default-first order when present */
    val allBanks: List<BankDetailsUi> = emptyList(),
    /** From `upi_accounts[].upi_id` — default-first order when present */
    val allUpiIds: List<String> = emptyList()
) {
    fun toBankDetailsUi(): BankDetailsUi =
        BankDetailsUi(accountName, bankName, accountNumber, ifscCode, "")

    fun toBankDetailsUiOrNull(): BankDetailsUi? {
        if (allBanks.isNotEmpty()) return allBanks.first()
        val b = toBankDetailsUi()
        if (b.accountHolder.isBlank() && b.bankName.isBlank() && b.accountNumber.isBlank() && b.ifsc.isBlank()) {
            return null
        }
        return b
    }
}

private fun bankRowFromBankDetailsJson(o: JSONObject): Pair<BankDetailsUi, Boolean> {
    val b = BankDetailsUi(
        accountHolder = o.optString("account_name", "").trim(),
        bankName = o.optString("bank_name", "").trim(),
        accountNumber = o.optString("account_number", "").trim(),
        ifsc = o.optString("ifsc_code", "").trim(),
        branch = ""
    )
    return b to o.optBoolean("is_default", false)
}

private data class WithdrawInitiateResult(
    val id: Int,
    val amount: Int,
    val status: String,
    val withdrawalMethod: String,
    val withdrawalDetails: String
)

private fun parseAuthBankDetailsBody(body: String): AuthBankDetailsApi? {
    val t = body.trim()
    if (t.isEmpty()) return null
    if (t.startsWith("[")) {
                val arr = JSONArray(t)
                if (arr.length() == 0) return null
        return parseAuthBankDetailsBody(arr.getJSONObject(0).toString())
            }
                val j = JSONObject(t)
    val payload =
                when {
                    j.has("data") && j.optJSONObject("data") != null -> j.getJSONObject("data")
                    (j.optJSONArray("results")?.length() ?: 0) > 0 -> j.getJSONArray("results").getJSONObject(0)
                    else -> j
                }

    // New shape: bank_accounts[] + upi_accounts[]
    if (payload.has("bank_accounts") || payload.has("upi_accounts")) {
        val banksArr = payload.optJSONArray("bank_accounts") ?: JSONArray()
        val bankPairs = ArrayList<Pair<BankDetailsUi, Boolean>>()
        for (i in 0 until banksArr.length()) {
            val o = banksArr.optJSONObject(i) ?: continue
            val (bk, def) = bankRowFromBankDetailsJson(o)
            if (bk.accountHolder.isNotBlank() || bk.bankName.isNotBlank() || bk.accountNumber.isNotBlank() || bk.ifsc.isNotBlank()) {
                bankPairs.add(bk to def)
            }
        }
        val sortedBanks = bankPairs.sortedByDescending { it.second }.map { it.first }

        val upiArr = payload.optJSONArray("upi_accounts") ?: JSONArray()
        val upiPairs = ArrayList<Pair<String, Boolean>>()
        for (i in 0 until upiArr.length()) {
            val o = upiArr.optJSONObject(i) ?: continue
            val vpa = o.optString("upi_id", "").trim()
            if (vpa.isNotBlank()) upiPairs.add(vpa to o.optBoolean("is_default", false))
        }
        val sortedUpis = upiPairs.sortedByDescending { it.second }.map { it.first }

        val pb = sortedBanks.firstOrNull()
        val pu = sortedUpis.firstOrNull().orEmpty()

        if (sortedBanks.isEmpty() && sortedUpis.isEmpty()) return null

        return AuthBankDetailsApi(
            accountName = pb?.accountHolder.orEmpty(),
            bankName = pb?.bankName.orEmpty(),
            accountNumber = pb?.accountNumber.orEmpty(),
            ifscCode = pb?.ifsc.orEmpty(),
            upiId = pu,
            isDefault = true,
            allBanks = sortedBanks,
            allUpiIds = sortedUpis
        )
    }

    val root = payload
    return AuthBankDetailsApi(
        accountName = root.optString("account_name", "").trim(),
        bankName = root.optString("bank_name", "").trim(),
        accountNumber = root.optString("account_number", "").trim(),
        ifscCode = root.optString("ifsc_code", "").trim(),
        upiId = root.optString("upi_id", "").trim(),
        isDefault = root.optBoolean("is_default", false),
        allBanks = emptyList(),
        allUpiIds = emptyList()
    )
}

private suspend fun fetchAuthBankDetails(): Pair<AuthBankDetailsApi?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load bank details.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(null, "Offline demo account — bank details are not available.")
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_BANK_DETAILS_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> { notifySessionExpired(); Pair(null, "Session expired. Please sign in again.") }
                    !resp.isSuccessful || text.isBlank() ->
                        Pair(null, "Could not load bank details (${resp.code}).")
                    else -> Pair(parseAuthBankDetailsBody(text), null)
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private suspend fun postWithdrawInitiate(
    amount: Int,
    withdrawalMethod: String,
    withdrawalDetails: String
): Pair<WithdrawInitiateResult?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in required.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(null, "Offline demo account cannot withdraw.")
        }
        if (amount <= 0) return@withContext Pair(null, "Enter a valid amount.")
        if (withdrawalDetails.isBlank()) return@withContext Pair(null, "Add withdrawal destination details.")
        try {
            val json =
                JSONObject().apply {
                    put("amount", amount)
                    put("withdrawal_method", withdrawalMethod)
                    put("withdrawal_details", withdrawalDetails)
                }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req =
                Request.Builder()
                    .url(AUTH_WITHDRAW_INITIATE_URL)
                    .post(body)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $token")
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    val err =
                        try {
                            JSONObject(text).optString("detail", "").ifBlank {
                                JSONObject(text).optString("error", "")
                            }
                        } catch (_: Exception) {
                            text
                        }
                    return@withContext Pair(null, err.ifBlank { "Withdrawal failed (${resp.code})" })
                }
                val j = JSONObject(text)
                Pair(
                    WithdrawInitiateResult(
                        id = j.optInt("id", -1),
                        amount = j.optInt("amount", amount),
                        status = j.optString("status", ""),
                        withdrawalMethod = j.optString("withdrawal_method", withdrawalMethod),
                        withdrawalDetails = j.optString("withdrawal_details", withdrawalDetails)
                    ),
                    null
                )
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private val AUTH_DEPOSIT_UPLOAD_URL = apiUrl("/api/auth/deposits/upload-proof/")

private data class DepositUploadResult(
    val id: Int,
    val amount: String,
    val status: String,
    val paymentMethod: Int
)

private suspend fun postDepositUploadProof(
    context: android.content.Context,
    imageUri: android.net.Uri,
    amount: String,
    paymentMethodId: Int
): Pair<DepositUploadResult?, String?> = withContext(Dispatchers.IO) {
    val token = AuthTokenStore.accessToken
        ?: return@withContext Pair(null, "Sign in required.")
    if (AuthTokenStore.isLocalDemoSession()) {
        return@withContext Pair(null, "Offline demo account cannot deposit.")
    }
    val amountInt = amount.toIntOrNull() ?: 0
    if (amountInt < 100) return@withContext Pair(null, "Minimum deposit amount is ₹100.")
    try {
        val stream = context.contentResolver.openInputStream(imageUri)
            ?: return@withContext Pair(null, "Could not read screenshot file.")
        val bytes = stream.readBytes()
        stream.close()
        val mimeType = context.contentResolver.getType(imageUri) ?: "image/jpeg"
        val ext = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"
        val fileBody = bytes.toRequestBody(mimeType.toMediaType())
        val multipart = okhttp3.MultipartBody.Builder()
            .setType(okhttp3.MultipartBody.FORM)
            .addFormDataPart("screenshot", "screenshot.$ext", fileBody)
            .addFormDataPart("amount", amountInt.toString())
            .addFormDataPart("payment_method", paymentMethodId.toString())
            .build()
        val req = Request.Builder()
            .url(AUTH_DEPOSIT_UPLOAD_URL)
            .post(multipart)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .build()
        cricketOddsHttpClient.newCall(req).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val msg = try {
                    JSONObject(text).optString("error", "").ifBlank {
                        JSONObject(text).optString("detail", "")
                    }
                } catch (_: Exception) { text }
                return@withContext Pair(null, msg.ifBlank { "Upload failed (${resp.code})" })
            }
            val j = JSONObject(text)
            Pair(
                DepositUploadResult(
                    id = j.optInt("id", -1),
                    amount = j.optString("amount", amountInt.toString()),
                    status = j.optString("status", "PENDING"),
                    paymentMethod = j.optInt("payment_method", paymentMethodId)
                ),
                null
            )
        }
    } catch (e: Exception) {
        Pair(null, e.message ?: "Network error")
    }
}

/** IPL / cricket screen: light blue & pink panels; accent for badges & buttons. */
private val CricketOddsAccent = Color(0xFF42A5F5)
private val CricketOddsBlue = Color(0xFF90CAF9)
private val CricketOddsPink = Color(0xFFF48FB1)
private val CricketOddsOnBlue = Color(0xFF0D47A1)
private val CricketOddsOnPink = Color(0xFF880E4F)
private val CricketStreamLiveRed = Color(0xFFE53935)

private enum class CricketOddsFilterTab {
    All,
    Main,
    OverByOver
}

/** Over-by-over style markets (ball/over granularity); excluded from Main. */
private fun isOverByOverMarket(question: String): Boolean {
    val q = question.lowercase()
    if (
        listOf(
            "over by over",
            "over-by-over",
            "ball by ball",
            "next over",
            "next ball",
            "current over",
            "this over",
            "runs in over",
            "runs in the over",
            "in this over",
            "per over",
            "over / under",
            "over under",
            "o/u",
            "odd over",
            "even over"
        ).any { q.contains(it) }
    ) {
        return true
    }
    if (Regex("\\d+(st|nd|rd|th)?\\s+over\\b").containsMatchIn(q)) return true
    if (q.contains(" over ") && (q.contains("ball") || q.contains("delivery"))) return true
    return false
}

private fun filterCricketMarkets(
    markets: List<CricketMarketUi>,
    tab: CricketOddsFilterTab
): List<CricketMarketUi> =
    when (tab) {
        CricketOddsFilterTab.All -> markets
        CricketOddsFilterTab.Main -> markets.filter { !isOverByOverMarket(it.question) }
        CricketOddsFilterTab.OverByOver -> markets.filter { isOverByOverMarket(it.question) }
    }

private data class CricketOutcomeUi(val label: String, val odd: String)

private data class CricketMarketUi(val question: String, val outcomes: List<CricketOutcomeUi>)

private data class CricketScoreUi(val team: String, val score: String, val batting: Boolean)

private data class CricketEventUi(
    val matchTitle: String,
    val leagueLabel: String,
    val markets: List<CricketMarketUi>,
    val scores: List<CricketScoreUi> = emptyList(),
    val clockStatus: String = "",
    val inningsLabel: String = "",
    val eventId: Long = 0L
)

private data class CricketBetSelection(
    val matchTitle: String,
    val marketQuestion: String,
    val selectionLabel: String,
    val odd: String
)

private val FALLBACK_CRICKET_ODDS_JSON = """
{"matches":[
  {"league":"IPL 2025 — Qualifier","teamBlue":"MI","teamPink":"CSK","oddBlue":"1.85","oddPink":"2.00"},
  {"league":"T20 World Cup","teamBlue":"IND","teamPink":"AUS","oddBlue":"1.72","oddPink":"2.10"},
  {"league":"The Hundred","teamBlue":"LNS","teamPink":"OVI","oddBlue":"1.95","oddPink":"1.88"},
  {"league":"BBL","teamBlue":"SYT","teamPink":"PRS","oddBlue":"2.05","oddPink":"1.78"}
]}
""".trimIndent()

private fun cleanOpponentFeedText(raw: String): String =
    raw.replace(Regex("/\\*[^*]*\\*/"), "").trim()

private fun leagueFromEventPaths(paths: JSONArray?): String {
    if (paths == null) return ""
    return buildString {
        for (i in 0 until paths.length()) {
            val p = paths.optJSONObject(i) ?: continue
            val desc = p.optString("description", "")
            if (desc.equals("Cricket", ignoreCase = true)) continue
            if (desc.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(desc)
            }
        }
    }.trim()
}

private fun marketQuestionText(m: JSONObject): String {
    val descs = m.optJSONObject("descriptions")
    val en = descs?.optString("en", "")?.trim().orEmpty()
    val q = if (en.isNotBlank()) en else m.optString("description", "")
    return cleanOpponentFeedText(q).ifBlank { "Market" }
}

private fun outcomeDisplayName(o: JSONObject): String {
    val descs = o.optJSONObject("descriptions")
    val en = descs?.optString("en", "")?.trim().orEmpty()
    val name = if (en.isNotBlank()) en else o.optString("description", "")
    return cleanOpponentFeedText(name).ifBlank { "-" }
}

/** Reads gunduata / BP-style `consolidatedPrice.currentPrice.decimal`. */
private fun extractOutcomeOdd(o: JSONObject): String {
    val cp = o.optJSONObject("consolidatedPrice")
    if (cp != null) {
        val cur = cp.optJSONObject("currentPrice") ?: cp.optJSONObject("price")
        if (cur != null) {
            if (cur.has("decimal") && !cur.isNull("decimal")) {
                try {
                    return String.format("%.2f", cur.getDouble("decimal"))
                } catch (_: Exception) { }
            }
            val fmt = cur.optString("format", "").trim()
            if (fmt.isNotBlank()) return fmt
        }
    }
    val s = formatOdd(o, "decimalOdds", "price", "odds", "backPrice", "trueOdds", "decimal")
    return if (s.isNotBlank() && s != "-") s else "-"
}

private fun parseMatchObject(item: JSONObject): CricketEventUi {
    val league = item.optString("league", item.optString("tournament", item.optString("competition", item.optString("title", "Match"))))
    val tb = item.optString("teamBlue", item.optString("team1", item.optString("home", item.optString("t1", ""))))
    val tp = item.optString("teamPink", item.optString("team2", item.optString("away", item.optString("t2", ""))))
    val ob = formatOdd(item, "oddBlue", "odds1", "odd1", "price1")
    val op = formatOdd(item, "oddPink", "odds2", "odd2", "price2")
    val teamBlue = if (tb.isNotBlank()) tb else "Team A"
    val teamPink = if (tp.isNotBlank()) tp else "Team B"
    val eventId = item.optLong("event_id", item.optLong("id", item.optLong("eventId", 0L)))
    return CricketEventUi(
        matchTitle = league,
        leagueLabel = "",
        markets = listOf(
            CricketMarketUi(
                question = "Match odds",
                outcomes = listOf(
                    CricketOutcomeUi(teamBlue, ob),
                    CricketOutcomeUi(teamPink, op)
                )
            )
        ),
        eventId = eventId
    )
}

/** Full API event: `description`, `eventPaths`, `markets[].description`, `markets[].outcomes[]`. */
private fun parseGunduataEventToUi(data: JSONObject): CricketEventUi {
    val matchTitle = data.optString("description", "").ifBlank { "Cricket" }
    val leagueLabel = leagueFromEventPaths(data.optJSONArray("eventPaths"))
    val eventId = data.optLong("event_id", data.optLong("id", data.optLong("eventId", 0L)))
    val marketsArr = data.optJSONArray("markets") ?: JSONArray()
    val markets = mutableListOf<CricketMarketUi>()
    for (i in 0 until marketsArr.length()) {
        val m = marketsArr.optJSONObject(i) ?: continue
        val outcomesArr = m.optJSONArray("outcomes")
            ?: m.optJSONArray("selections")
            ?: m.optJSONArray("runners")
            ?: continue
        val outs = mutableListOf<CricketOutcomeUi>()
        for (j in 0 until outcomesArr.length()) {
            val o = outcomesArr.optJSONObject(j) ?: continue
            if (o.optBoolean("withdrawn", false)) continue
            if (o.optBoolean("hidden", false)) continue
            val label = outcomeDisplayName(o)
            val odd = extractOutcomeOdd(o)
            outs.add(CricketOutcomeUi(label, odd))
        }
        if (outs.isNotEmpty()) {
            markets.add(CricketMarketUi(question = marketQuestionText(m), outcomes = outs))
        }
    }
    return CricketEventUi(matchTitle = matchTitle, leagueLabel = leagueLabel, markets = markets, eventId = eventId)
}

private fun parseEventDataObject(o: JSONObject): CricketEventUi? {
    if (o.has("markets") && o.optJSONArray("markets") != null) {
        return parseGunduataEventToUi(o)
    }
    return parseMatchObject(o)
}

private fun parseLiveEventsFormat(obj: JSONObject): List<CricketEventUi> {
    val eventsArr = obj.optJSONArray("events") ?: return emptyList()
    val out = mutableListOf<CricketEventUi>()
    for (i in 0 until eventsArr.length()) {
        val e = eventsArr.optJSONObject(i) ?: continue
        val eventId = e.optLong("event_id", 0L)
        val matchName = e.optString("match_name", "").ifBlank { "Cricket" }
        val innings = e.optString("current_innings", "")
        val clockStatus = e.optString("clock_status", "")

        val scoresArr = e.optJSONArray("scores")
        val scores = mutableListOf<CricketScoreUi>()
        if (scoresArr != null) {
            for (j in 0 until scoresArr.length()) {
                val s = scoresArr.optJSONObject(j) ?: continue
                scores.add(CricketScoreUi(
                    team = s.optString("team", ""),
                    score = s.optString("score", "0-0"),
                    batting = s.optBoolean("batting", false)
                ))
            }
        }

        val oddsArr = e.optJSONArray("match_odds")
        val outcomes = mutableListOf<CricketOutcomeUi>()
        if (oddsArr != null) {
            for (j in 0 until oddsArr.length()) {
                val o = oddsArr.optJSONObject(j) ?: continue
                val team = o.optString("team", "")
                val oddVal = o.optString("price_format", "").ifBlank {
                    try { String.format("%.2f", o.getDouble("decimal")) } catch (_: Exception) { "-" }
                }
                outcomes.add(CricketOutcomeUi(team, oddVal))
            }
        }
        val markets = if (outcomes.isNotEmpty()) listOf(CricketMarketUi("Match odds", outcomes)) else emptyList()

        out.add(CricketEventUi(
            matchTitle = matchName,
            leagueLabel = "",
            markets = markets,
            scores = scores,
            clockStatus = clockStatus,
            inningsLabel = innings,
            eventId = eventId
        ))
    }
    return out
}

private fun parseCricketFeedJson(json: String): List<CricketEventUi> {
    val out = mutableListOf<CricketEventUi>()
    try {
        val trimmed = json.trim()
        if (trimmed.startsWith("[")) {
            val arr = JSONArray(trimmed)
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { parseEventDataObject(it) }?.let { out.add(it) }
            }
            if (out.isNotEmpty()) return out
        }
        val obj = JSONObject(trimmed)
        // New live-events format: {"count": N, "events": [...]}
        if (obj.has("events")) {
            val parsed = parseLiveEventsFormat(obj)
            if (parsed.isNotEmpty()) return parsed
        }
        if (obj.has("data")) {
            when (val raw = obj.get("data")) {
                is JSONObject -> parseEventDataObject(raw)?.let { out.add(it) }
                is JSONArray -> {
                    for (i in 0 until raw.length()) {
                        raw.optJSONObject(i)?.let { parseEventDataObject(it) }?.let { out.add(it) }
                    }
                }
            }
        }
        if (out.isNotEmpty()) return out
        val arr = when {
            obj.has("matches") -> obj.getJSONArray("matches")
            obj.has("odds") -> obj.getJSONArray("odds")
            obj.has("results") -> obj.getJSONArray("results")
            else -> null
        }
        if (arr != null) {
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { parseEventDataObject(it) }?.let { out.add(it) }
            }
        }
    } catch (_: Exception) { }
    return out
}

/** In-memory cricket feed: prefetch on main shell + TTL so Cricket opens with odds already warm. */
private object CricketFeedStore {
    @Volatile
    private var cachedEvents: List<CricketEventUi>? = null
    @Volatile
    private var cachedAtMs: Long = 0L
    private val mutex = Mutex()
    private const val CACHE_TTL_MS = 30_000L

    fun peek(): List<CricketEventUi>? = cachedEvents

    suspend fun load(): List<CricketEventUi> {
        val now = System.currentTimeMillis()
        val snap = cachedEvents
        if (snap != null && now - cachedAtMs < CACHE_TTL_MS) return snap
        return mutex.withLock {
            val t = System.currentTimeMillis()
            val warm = cachedEvents
            if (warm != null && t - cachedAtMs < CACHE_TTL_MS) return warm
            val fresh = fetchCricketFromNetwork()
            cachedEvents = fresh
            cachedAtMs = System.currentTimeMillis()
            fresh
        }
    }

    private suspend fun fetchCricketFromNetwork(): List<CricketEventUi> = withContext(Dispatchers.IO) {
        if (CRICKET_ODDS_API_URL.isBlank()) {
            return@withContext parseCricketFeedJson(FALLBACK_CRICKET_ODDS_JSON)
        }
        try {
            val req = Request.Builder()
                .url(CRICKET_ODDS_API_URL)
                .header("Accept", "application/json")
                .get()
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return@withContext parseCricketFeedJson(FALLBACK_CRICKET_ODDS_JSON)
                }
                val parsed = parseCricketFeedJson(body)
                if (parsed.isEmpty()) parseCricketFeedJson(FALLBACK_CRICKET_ODDS_JSON) else parsed
            }
        } catch (_: Exception) {
            parseCricketFeedJson(FALLBACK_CRICKET_ODDS_JSON)
        }
    }
}

private suspend fun fetchCricketPreEvents(): List<CricketEventUi> = withContext(Dispatchers.IO) {
    try {
        val req = Request.Builder()
            .url(CRICKET_PRE_EVENTS_API_URL)
            .header("Accept", "application/json")
            .get()
            .build()
        cricketOddsHttpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) return@withContext emptyList()
            parseCricketFeedJson(body)
        }
    } catch (_: Exception) { emptyList() }
}

// ── Live odds detail ──────────────────────────────────────────────────────────

private val CRICKET_LIVE_ODDS_API_URL = apiUrl("/api/cricket/live-odds/")
private val CRICKET_PREEVENT_ODDS_API_URL = apiUrl("/api/cricket/preevent-odds/")

private data class CricketOddsOutcomeUi(
    val name: String,
    val priceFormat: String,
    val status: String
)

private data class CricketOddsMarketUi(
    val marketId: Long,
    val marketName: String,
    val marketStatus: String,
    val outcomes: List<CricketOddsOutcomeUi>
)

private data class CricketMatchOddsDetail(
    val eventId: Long,
    val matchName: String,
    val currentInnings: String,
    val clockStatus: String,
    val scores: List<CricketScoreUi>,
    val allMarkets: List<CricketOddsMarketUi>,
    val pollIntervalSeconds: Int
)

private suspend fun fetchLiveOddsDetail(eventId: Long): Pair<CricketMatchOddsDetail?, String?> =
    withContext(Dispatchers.IO) {
        try {
            val url = "$CRICKET_LIVE_ODDS_API_URL?event_id=$eventId"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return@withContext Pair(null, "Failed to load odds (${resp.code})")
                }
                Pair(parseLiveOddsDetail(body), null)
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private suspend fun fetchPreEventOddsDetail(eventId: Long): Pair<CricketMatchOddsDetail?, String?> =
    withContext(Dispatchers.IO) {
        try {
            val url = "$CRICKET_PREEVENT_ODDS_API_URL?event_id=$eventId"
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .get()
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || body.isBlank()) {
                    return@withContext Pair(null, "Failed to load pre-event odds (${resp.code})")
                }
                Pair(parseLiveOddsDetail(body), null)
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private fun parseLiveOddsDetail(json: String): CricketMatchOddsDetail? {
    return try {
        val o = JSONObject(json)
        val scoresArr = o.optJSONArray("scores")
        val scores = mutableListOf<CricketScoreUi>()
        if (scoresArr != null) {
            for (i in 0 until scoresArr.length()) {
                val s = scoresArr.optJSONObject(i) ?: continue
                scores.add(CricketScoreUi(s.optString("team"), s.optString("score", "0-0"), s.optBoolean("batting")))
            }
        }
        val marketsArr = o.optJSONArray("all_markets")
        val markets = mutableListOf<CricketOddsMarketUi>()
        if (marketsArr != null) {
            for (i in 0 until marketsArr.length()) {
                val m = marketsArr.optJSONObject(i) ?: continue
                val outcomesArr = m.optJSONArray("outcomes")
                val outcomes = mutableListOf<CricketOddsOutcomeUi>()
                if (outcomesArr != null) {
                    for (j in 0 until outcomesArr.length()) {
                        val oc = outcomesArr.optJSONObject(j) ?: continue
                        outcomes.add(CricketOddsOutcomeUi(
                            name = oc.optString("name"),
                            priceFormat = oc.optString("price_format", "-"),
                            status = oc.optString("status", "")
                        ))
                    }
                }
                markets.add(CricketOddsMarketUi(
                    marketId = m.optLong("market_id"),
                    marketName = m.optString("market_name"),
                    marketStatus = m.optString("market_status", "open"),
                    outcomes = outcomes
                ))
            }
        }
        CricketMatchOddsDetail(
            eventId = o.optLong("event_id"),
            matchName = o.optString("match_name"),
            currentInnings = o.optString("current_innings", ""),
            clockStatus = o.optString("clock_status", ""),
            scores = scores,
            allMarkets = markets,
            pollIntervalSeconds = o.optInt("poll_interval_seconds", 10)
        )
    } catch (_: Exception) { null }
}

private fun formatOdd(item: JSONObject, vararg keys: String): String {
    for (k in keys) {
        if (!item.has(k) || item.isNull(k)) continue
        return try {
            when (val v = item.get(k)) {
                is Number -> String.format("%.2f", v.toDouble())
                is String -> v.trim().ifBlank { "-" }
                else -> v.toString()
            }
        } catch (_: Exception) {
            "-"
        }
    }
    return "-"
}

/** Fallback when /api/support/contacts/ fails or returns empty (must match production support WhatsApp). */
private const val CONTACT_PHONE_WHATSAPP_TELEGRAM = "9182351381"

private data class SupportContactsUi(
    val whatsappNumber: String?,
    val telegram: String?,
    val facebookUrl: String?,
    val instagramUrl: String?,
    val youtubeUrl: String?
)

private fun JSONObject.optTrimmedUrlOrNull(vararg keys: String): String? {
    for (k in keys) {
        val s = optString(k, "").trim()
        if (s.isNotBlank()) return s
    }
    return null
}

private fun parseSupportContactsResponseBody(text: String): SupportContactsUi? {
    if (text.isBlank()) return null
                val root = JSONObject(text)
    val j = root.optJSONObject("data") ?: root.optJSONObject("contacts") ?: root
                val wa =
                    j.optString("whatsapp_number", "")
                        .ifBlank { j.optString("whatsapp", "") }
            .ifBlank { j.optString("support_whatsapp", "") }
            .ifBlank { j.optString("whatsapp_mobile", "") }
                        .trim()
                        .ifBlank { null }
                val tg = j.optString("telegram", "").trim().ifBlank { null }
    return SupportContactsUi(
                    whatsappNumber = wa,
                    telegram = tg,
                    facebookUrl = j.optTrimmedUrlOrNull("facebook", "facebook_url"),
                    instagramUrl = j.optTrimmedUrlOrNull("instagram", "instagram_url"),
                    youtubeUrl = j.optTrimmedUrlOrNull("youtube", "youtube_url")
                )
            }

private suspend fun fetchSupportContacts(): SupportContactsUi? =
    withContext(Dispatchers.IO) {
        try {
            // /api/support/contacts/ is a public endpoint — no auth needed.
            // Try without token first; fall back to with-token if 401.
            val token =
                AuthTokenStore.accessToken?.takeUnless { AuthTokenStore.isLocalDemoSession() }

            fun buildContactsRequest(includeBearer: Boolean): Request {
                val b =
                    Request.Builder()
                        .url(SUPPORT_CONTACTS_API_URL)
                        .header("Accept", "application/json")
                if (includeBearer && !token.isNullOrBlank()) {
                    b.header("Authorization", "Bearer $token")
                }
                return b.get().build()
            }

            // First attempt: no auth (works for public endpoint)
            cricketOddsHttpClient.newCall(buildContactsRequest(false)).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful && text.isNotBlank()) {
                    return@withContext parseSupportContactsResponseBody(text)
                }
            }
            // Second attempt: with Bearer token (in case server requires auth)
            if (!token.isNullOrBlank()) {
                cricketOddsHttpClient.newCall(buildContactsRequest(true)).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (resp.isSuccessful && text.isNotBlank()) {
                        return@withContext parseSupportContactsResponseBody(text)
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

private fun phoneDigitsForChat(raw: String): String = raw.filter { it.isDigit() }

/** Opens https/http URL in a browser; returns true if an activity was started. */
private fun Context.tryOpenExternalUrl(url: String?): Boolean {
    val u = url?.trim()?.takeIf { it.isNotBlank() } ?: return false
    return try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
        true
    } catch (_: Exception) {
        false
    }
}

/** @param apiPhone optional number from [fetchSupportContacts]; falls back to [CONTACT_PHONE_WHATSAPP_TELEGRAM] */
private fun Context.openWhatsAppToContact(apiPhone: String? = null) {
    val raw = apiPhone?.trim()?.takeIf { it.isNotBlank() } ?: CONTACT_PHONE_WHATSAPP_TELEGRAM
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        if (!tryOpenExternalUrl(raw)) {
            Toast.makeText(this, "Unable to open WhatsApp link", Toast.LENGTH_SHORT).show()
        }
        return
    }
    try {
        val digits = phoneDigitsForChat(raw).ifEmpty { CONTACT_PHONE_WHATSAPP_TELEGRAM }
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$digits")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(this, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

/** @param apiPhone optional handle, number, or t.me URL from [fetchSupportContacts]; falls back to [CONTACT_PHONE_WHATSAPP_TELEGRAM] */
private fun Context.openTelegramToContact(apiPhone: String? = null) {
    val raw = apiPhone?.trim()?.takeIf { it.isNotBlank() } ?: CONTACT_PHONE_WHATSAPP_TELEGRAM
    if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
        if (!tryOpenExternalUrl(raw)) {
            Toast.makeText(this, "Unable to open Telegram link", Toast.LENGTH_SHORT).show()
        }
        return
    }
    // "/api/support/contacts/" may return "@username" or bare username → https://t.me/username
    if (raw.startsWith("@")) {
        val h = raw.removePrefix("@").trim()
        if (h.isNotBlank()) {
            if (!tryOpenExternalUrl("https://t.me/$h")) {
                Toast.makeText(this, "Unable to open Telegram", Toast.LENGTH_SHORT).show()
            }
            return
        }
    }
    val bare = raw.trim()
    if (bare.matches(Regex("^[A-Za-z][A-Za-z0-9_]{3,31}$"))) {
        if (!tryOpenExternalUrl("https://t.me/$bare")) {
            Toast.makeText(this, "Unable to open Telegram", Toast.LENGTH_SHORT).show()
        }
        return
    }
    val digits = phoneDigitsForChat(raw).ifEmpty { CONTACT_PHONE_WHATSAPP_TELEGRAM }
    val tg = Uri.parse("tg://msg?to=+$digits")
    try {
        startActivity(Intent(Intent.ACTION_VIEW, tg))
    } catch (e: Exception) {
        try {
            val https = Uri.parse("https://t.me/+$digits")
            startActivity(Intent(Intent.ACTION_VIEW, https))
        } catch (e2: Exception) {
            Toast.makeText(this, "Unable to open Telegram", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun Context.openInstagramApp() {
    try {
        val launch = packageManager.getLaunchIntentForPackage("com.instagram.android")
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
        }
    } catch (e: Exception) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/")))
        } catch (e2: Exception) {
            Toast.makeText(this, "Unable to open Instagram", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun Context.openYoutubeApp() {
    try {
        val launch = packageManager.getLaunchIntentForPackage("com.google.android.youtube")
        if (launch != null) {
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")))
        }
    } catch (e: Exception) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/")))
        } catch (e2: Exception) {
            Toast.makeText(this, "Unable to open YouTube", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun DrawableImage(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment
    )
}

private fun Context.launchAppOrToast(packageName: String) {
    try {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            startActivity(launch)
        } else {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(this, "Unable to open app", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.launchUpiPaymentChooser(pa: String? = null, amount: String = "") {
    val payee = pa?.trim()?.takeIf { it.isNotBlank() }
    if (payee == null) {
        Toast.makeText(this, "No UPI ID configured for this payment method.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val amt = amount.trim().toDoubleOrNull()?.let { "%.2f".format(it) } ?: ""
        val uriBuilder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", payee)
            .appendQueryParameter("pn", "Hen Fight")
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Wallet Deposit")
        if (amt.isNotBlank()) uriBuilder.appendQueryParameter("am", amt)
        val intent = Intent(Intent.ACTION_VIEW, uriBuilder.build())
        startActivity(Intent.createChooser(intent, "Complete payment with"))
    } catch (e: Exception) {
        Toast.makeText(this, "No UPI app available", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.launchWalletPaymentMethod(item: WalletPaymentMethodItem, apiUpiId: String?, amount: String = "") {
    val vpa = item.upiId?.trim()?.takeIf { it.isNotBlank() } ?: apiUpiId
    val t = item.type.lowercase(Locale.US)
    val pkg = when {
        t.contains("phonepe") -> PKG_PHONEPE
        t.contains("gpay") || t.contains("google") -> PKG_GPay
        t.contains("paytm") -> PKG_PAYTM
        else -> null
    }
    when {
        pkg != null -> launchUpiWithPackage(vpa, pkg, amount)
        t == "upi" || t.contains("upi_id") -> launchUpiPaymentChooser(vpa, amount)
        !item.deepLink.isNullOrBlank() ->
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.deepLink)))
            } catch (e: Exception) {
                Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show()
            }
        !item.packageName.isNullOrBlank() -> launchAppOrToast(item.packageName)
        else -> launchUpiPaymentChooser(vpa, amount)
    }
}

private fun Context.launchUpiWithPackage(pa: String?, packageName: String, amount: String = "") {
    val payee = pa?.trim()?.takeIf { it.isNotBlank() }
    if (payee == null) {
        Toast.makeText(this, "No UPI ID configured for this payment method.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val amt = amount.trim().toDoubleOrNull()?.let { "%.2f".format(it) } ?: ""
        val uriBuilder = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", payee)
            .appendQueryParameter("pn", "Hen Fight")
            .appendQueryParameter("cu", "INR")
            .appendQueryParameter("tn", "Wallet Deposit")
        if (amt.isNotBlank()) uriBuilder.appendQueryParameter("am", amt)
        val intent = Intent(Intent.ACTION_VIEW, uriBuilder.build()).apply {
            setPackage(packageName)
        }
        if (packageManager.getLaunchIntentForPackage(packageName) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        launchUpiPaymentChooser(pa, amount)
    }
}

private fun iconForWalletPaymentType(type: String): ImageVector {
    return when (type.lowercase(Locale.US)) {
        "phonepe" -> Icons.Default.AccountBalanceWallet
        "gpay", "google_pay" -> Icons.Default.Payment
        "paytm" -> Icons.Default.AccountBalance
        "upi" -> Icons.Default.QrCodeScanner
        else -> Icons.Default.Payment
    }
}

private fun colorForWalletPaymentType(type: String): Color {
    return when (type.lowercase(Locale.US)) {
        "phonepe" -> Color(0xFF673AB7)
        "gpay", "google_pay" -> Color(0xFF4285F4)
        "paytm" -> Color(0xFF00B9F1)
        "upi" -> Color(0xFF4CAF50)
        else -> OrangePrimary
    }
}

@Composable
private fun MaintenanceModeScreen(initialHours: Int, initialMinutes: Int) {
    val initialTotal =
        remember(initialHours, initialMinutes) {
            (initialHours * 3600 + initialMinutes * 60).coerceAtLeast(0)
        }
    var remainingSeconds by remember(initialHours, initialMinutes) {
        mutableStateOf((initialHours * 3600 + initialMinutes * 60).coerceAtLeast(0))
    }
    LaunchedEffect(initialHours, initialMinutes) {
        var left = (initialHours * 3600 + initialMinutes * 60).coerceAtLeast(0)
        while (left > 0) {
            delay(1000)
            left--
            remainingSeconds = left
        }
    }
    val h = remainingSeconds / 3600
    val m = (remainingSeconds % 3600) / 60
    val sec = remainingSeconds % 60

    val infiniteTransition = rememberInfiniteTransition(label = "maint_pulse")
    val iconAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "icon_alpha"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0D0D0D), Color(0xFF1A1205), Color(0xFF0D0D0D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                Modifier
                    .size(90.dp)
                    .background(OrangePrimary.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                    tint = OrangePrimary.copy(alpha = iconAlpha),
                    modifier = Modifier.size(48.dp)
            )
            }
            Spacer(Modifier.height(28.dp))
            Text(
                "App Under Maintenance",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Sorry for the inconvenience!\nWe are currently performing maintenance to improve your experience.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.80f),
                textAlign = TextAlign.Center,
                lineHeight = 23.sp
            )
            Spacer(Modifier.height(36.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF242010),
                border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier.padding(vertical = 24.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
            Text(
                        "Please come back in",
                fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.55f),
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    if (initialTotal > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MaintenanceTimeUnit(value = h, label = "HRS")
                            Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                            MaintenanceTimeUnit(value = m, label = "MIN")
                            Text(":", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = OrangePrimary)
                            MaintenanceTimeUnit(value = sec, label = "SEC")
                        }
                    } else {
            Text(
                            "We'll be back shortly",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OrangePrimary
                        )
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
                Text(
                "Thank you for your patience \uD83D\uDE4F",
                    fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.45f),
                    textAlign = TextAlign.Center
                )
            }
        }
}

@Composable
private fun MaintenanceTimeUnit(value: Int, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2E1F00)
        ) {
            Text(
                text = String.format(Locale.US, "%02d", value),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 10.sp, color = Color.White.copy(alpha = 0.45f), letterSpacing = 1.sp)
    }
}

@Composable
private fun UpdateAvailableDialog(
    info: GameVersionResponse,
    onDownload: () -> Unit,
    onLater: (() -> Unit)?
) {
    Dialog(
        onDismissRequest = { onLater?.invoke() },
        properties =
            DialogProperties(
                dismissOnBackPress = onLater != null,
                dismissOnClickOutside = onLater != null
            )
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    "Update available",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Version ${info.versionName} is ready (build ${info.versionCode}).",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    lineHeight = 20.sp
                )
                if (info.forceUpdate) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This update is required to continue using the app.",
                        fontSize = 13.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onLater != null) {
                        TextButton(onClick = onLater) {
                            Text("Later", color = Color.Gray)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(
                        onClick = onDownload,
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("Download", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateDialog(info: GameVersionResponse, onDownload: () -> Unit, onLater: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onLater) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            modifier = Modifier.fillMaxWidth().padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Update Available", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Version ${info.versionName} is available. Update for the latest features.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 14.sp, color = Color.Gray)
                Button(onClick = onDownload, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)) {
                    Text("Download Update", fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onLater) { Text("Later", color = Color.Gray) }
            }
        }
    }
}

@Composable
private fun AppRootWithMaintenanceGate(content: @Composable () -> Unit) {
    // null = still checking
    var maintenanceOn by remember { mutableStateOf<Boolean?>(null) }
    var remHours by remember { mutableStateOf(0) }
    var remMinutes by remember { mutableStateOf(0) }

    // Version update gate — null = checking, false = ok/skippable, true = force update
    var forceUpdateInfo by remember { mutableStateOf<GameVersionResponse?>(null) }
    var optionalUpdateInfo by remember { mutableStateOf<GameVersionResponse?>(null) }
    var dismissedOptional by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        // Run maintenance + version check in parallel
        val s = fetchMaintenanceStatus()
        val v = fetchGameVersion()

        if (s != null && s.maintenance) {
            remHours = s.remainingHours
            remMinutes = s.remainingMinutes
            maintenanceOn = true
        } else {
            maintenanceOn = false
        }

        if (v != null && v.versionCode > BuildConfig.VERSION_CODE) {
            if (v.forceUpdate) forceUpdateInfo = v
            else optionalUpdateInfo = v
        }
    }

    when (maintenanceOn) {
        null -> {
            // Show a simple full-screen loading indicator while we check
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = OrangePrimary)
            }
        }
        true -> MaintenanceModeScreen(initialHours = remHours, initialMinutes = remMinutes)
        false -> {
            // Force update blocks the app completely
            val fv = forceUpdateInfo
            if (fv != null) {
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = OrangePrimary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(Modifier.height(24.dp))
                        Text(
                            "Update Required",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Version ${fv.versionName} is required to continue.\nPlease update the app to proceed.",
                            color = Color(0xFFBBBBBB),
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(32.dp))
                        Button(
                            onClick = {
                                val url = fv.downloadUrl.let {
                                    if (it.startsWith("http")) it else "https://fight.pravoo.in$it"
                                }
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Download Update", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            } else {
                // Optional update — show dialog over the app, dismissible
                content()
                val ov = optionalUpdateInfo
                if (ov != null && !dismissedOptional) {
                    AppUpdateDialog(
                        info = ov,
                        onDownload = {
                            val url = ov.downloadUrl.let {
                                if (it.startsWith("http")) it else "https://fight.pravoo.in$it"
                            }
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        },
                        onLater = { dismissedOptional = true }
                    )
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AuthTokenStore.load(this)
        setContent {
            KokorokoTheme {
                AppRootWithMaintenanceGate {
                    val activityContext = LocalContext.current
                    val alreadyLoggedIn = AuthTokenStore.accessToken != null
                    var currentScreen by remember {
                        mutableStateOf(if (alreadyLoggedIn) "home" else "splash")
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val logoutScope = rememberCoroutineScope()

                        // Listen for session-expiry signals from any API call that gets a 401
                        LaunchedEffect(Unit) {
                            sessionExpiredEvents.asSharedFlow().collect {
                                AuthTokenStore.clear(activityContext)
                                currentScreen = "login"
                                Toast.makeText(
                                    activityContext,
                                    "Session expired. Please sign in again.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }

                        when (currentScreen) {
                        "splash" -> SplashScreen { currentScreen = "login" }
                        "login" -> LoginScreen(
                            onLoginSuccess = { currentScreen = "home" },
                            onSignUp = { currentScreen = "signup" }
                        )
                        "signup" -> SignupScreen(
                            onBack = { currentScreen = "login" },
                            onRegistered = {
                                Toast.makeText(
                                    activityContext,
                                    "Account created. Please log in.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                currentScreen = "login"
                            }
                        )
                        "home" ->
                            MainScreen(
                                onLogout = {
                                    logoutScope.launch {
                                        performAuthLogout(activityContext)
                                        currentScreen = "login"
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    val context = LocalContext.current
    val mainScreenScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("home") }
    var currentSubScreen by remember { mutableStateOf("main") }
    /** When opening [PaymentOptionsScreen], optional sub-screen to restore on back (e.g. cock fight). */
    var paymentReturnSubScreen by remember { mutableStateOf<String?>(null) }
    /** Wallet deposit flow: `"upi"` = QR + UPI apps only; `"bank"` = bank details only */
    var walletDepositMethod by remember { mutableStateOf("upi") }
    var walletDepositAmount by remember { mutableStateOf("") }
    var preloadedPaymentOptions by remember { mutableStateOf<WalletApiResult?>(null) }
    var preloadedPaymentError by remember { mutableStateOf<String?>(null) }
    var preloadedPaymentLoading by remember { mutableStateOf(false) }
    var cachedSupportContacts by remember { mutableStateOf<SupportContactsUi?>(null) }

    LaunchedEffect(Unit) {
        if (cachedSupportContacts == null) {
            cachedSupportContacts = fetchSupportContacts()
        }
    }

    /** Refresh support contacts when opening Profile so WhatsApp/Telegram use [SUPPORT_CONTACTS_API_URL], not stale null + fallback. */
    LaunchedEffect(selectedTab, currentSubScreen) {
        if (selectedTab == "profile" && currentSubScreen == "main") {
            cachedSupportContacts = fetchSupportContacts()
        }
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab != "wallet") return@LaunchedEffect
        /** Always refetch fresh — never serve stale payment methods from a previous session. */
        preloadedPaymentOptions = null
        preloadedPaymentError = null
        preloadedPaymentLoading = true
        val (w, err) = fetchPaymentOptionsFromApi()
        preloadedPaymentLoading = false
        preloadedPaymentOptions = w
        preloadedPaymentError = err
    }
    var pendingVersion by remember { mutableStateOf<GameVersionResponse?>(null) }
    var dismissedOptionalUpdate by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CricketFeedStore.load()
        }
    }

    /** Check for app updates while user is on the home tab (main stack). Re-runs when returning from Wallet/Profile/etc. */
    LaunchedEffect(selectedTab, currentSubScreen) {
        if (selectedTab != "home" || currentSubScreen != "main") return@LaunchedEffect
        val v = fetchGameVersion()
        if (v != null && v.versionCode > BuildConfig.VERSION_CODE) {
            if (pendingVersion?.versionCode != v.versionCode) {
                dismissedOptionalUpdate = false
            }
            pendingVersion = v
        } else {
            pendingVersion = null
        }
    }

    val onHomeMain = selectedTab == "home" && currentSubScreen == "main"
    // Cock fight fullscreen uses nested BackHandlers — parent must not compete or the first edge-swipe is eaten.
    BackHandler(enabled = !onHomeMain && currentSubScreen != "cock_fight_live") {
        currentSubScreen = "main"
        selectedTab = "home"
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (currentSubScreen == "main" && selectedTab == "home") {
                    BottomNavigationBar(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it }
                    )
                }
            }
        ) { paddingValues ->
            // Cock fight: horizontal safe-area padding narrows the player (side black bars). Keep top/bottom insets only.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (currentSubScreen == "cock_fight_live") {
                            Modifier.padding(
                                top = paddingValues.calculateTopPadding(),
                                bottom = paddingValues.calculateBottomPadding()
                            )
                        } else Modifier.padding(paddingValues)
                    )
            ) {
            when {
                currentSubScreen == "payment_options" ->
                    PaymentOptionsScreen(
                        onBack = {
                            walletDepositMethod = "upi"
                            currentSubScreen = paymentReturnSubScreen ?: "main"
                            paymentReturnSubScreen = null
                        },
                        walletDepositMethod = walletDepositMethod,
                        depositAmount = walletDepositAmount,
                        preloadedWallet = preloadedPaymentOptions,
                        preloadedLoading = preloadedPaymentLoading,
                        preloadedError = preloadedPaymentError
                    )
                currentSubScreen == "profile_details" ->
                    ProfileDetailsScreen(
                        onBack = { currentSubScreen = "main" },
                        onHome = {
                            selectedTab = "home"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "referral" ->
                    ReferralScreen(
                        onBack = { currentSubScreen = "main" },
                        onHome = {
                            selectedTab = "home"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "transactional_records" ->
                    TransactionalRecordsScreen(onBack = { currentSubScreen = "main" })
                currentSubScreen == "gundata_live" ->
                    GundataLiveScreen(
                        onBack = { currentSubScreen = "main" },
                        onWallet = {
                            selectedTab = "wallet"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "cock_fight_live" ->
                    CockFightLiveScreen(
                        onBack = { currentSubScreen = "main" },
                        onWallet = {
                            paymentReturnSubScreen = "cock_fight_live"
                            mainScreenScope.launch {
                                preloadedPaymentLoading = true
                                preloadedPaymentError = null
                                val (w, err) = fetchPaymentOptionsFromApi()
                                preloadedPaymentLoading = false
                                preloadedPaymentOptions = w
                                preloadedPaymentError = err
                                walletDepositMethod = "upi"
                                walletDepositAmount = "1000"
                                currentSubScreen = "payment_options"
                            }
                        },
                        onOpenProfile = {
                            selectedTab = "profile"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "cricket" ->
                    CricketOddsScreen(onBack = { currentSubScreen = "main" })
                else -> {
                    val tabOrder = listOf("home", "promotion", "wallet", "profile")
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            val initialIndex = tabOrder.indexOf(initialState).coerceAtLeast(0)
                            val targetIndex = tabOrder.indexOf(targetState).coerceAtLeast(0)
                            val forward = targetIndex > initialIndex
                            if (forward) {
                                (slideInHorizontally(tween(280)) { full -> full } + fadeIn(tween(280))) togetherWith
                                    (slideOutHorizontally(tween(280)) { full -> -full } + fadeOut(tween(280)))
                            } else {
                                (slideInHorizontally(tween(280)) { full -> -full } + fadeIn(tween(280))) togetherWith
                                    (slideOutHorizontally(tween(280)) { full -> full } + fadeOut(tween(280)))
                            }
                        },
                        label = "main_tab",
                        modifier = Modifier.fillMaxSize()
                    ) { tab ->
                        when (tab) {
                            "home" -> HomeScreen(
                                onOpenGundata = { currentSubScreen = "gundata_live" },
                                onOpenCricket = { currentSubScreen = "cricket" },
                                onOpenCockfight = { currentSubScreen = "cock_fight_live" },
                                onWalletClick = { selectedTab = "wallet" },
                                onPromotionsClick = { selectedTab = "promotion" }
                            )
                            "promotion" -> PromotionsScreen(onBack = { selectedTab = "home" })
                            "wallet" ->
                                WalletScreen(
                                    onBack = { selectedTab = "home" },
                                    onDepositClick = { method, amount ->
                                        paymentReturnSubScreen = null
                                        walletDepositMethod = method
                                        walletDepositAmount = amount
                                        currentSubScreen = "payment_options"
                                    }
                                )
                            "profile" -> ProfileScreen(
                                onBack = { selectedTab = "home" },
                                onLogout = onLogout,
                                onOpenProfileDetails = { currentSubScreen = "profile_details" },
                                onOpenReferralEarn = { currentSubScreen = "referral" },
                                onOpenTransactionalRecords = { currentSubScreen = "transactional_records" },
                                supportContacts = cachedSupportContacts
                            )
                            else -> HomeScreen(
                                onOpenGundata = { currentSubScreen = "gundata_live" },
                                onOpenCricket = { currentSubScreen = "cricket" },
                                onOpenCockfight = { currentSubScreen = "cock_fight_live" },
                                onWalletClick = { selectedTab = "wallet" },
                                onPromotionsClick = { selectedTab = "promotion" }
                            )
                        }
                    }
                }
            }
        }
        }

        val pv = pendingVersion
        if (pv != null && pv.downloadUrl.isNotBlank() && onHomeMain) {
            val showUpdate = pv.forceUpdate || !dismissedOptionalUpdate
            if (showUpdate) {
                UpdateAvailableDialog(
                    info = pv,
                    onDownload = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pv.downloadUrl)))
                        } catch (_: Exception) {
                            Toast.makeText(context, "Could not open download link", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onLater = if (pv.forceUpdate) null else ({ dismissedOptionalUpdate = true })
                )
            }
        }
    }
}

private val GUNDATA_DICE_FACE = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

@Composable
private fun CricketBetCardDialog(
    selection: CricketBetSelection,
    onDismiss: () -> Unit,
    onPlaceBet: () -> Unit
) {
    val context = LocalContext.current
    var stake by remember(selection) { mutableStateOf(100) }
    val stakeChips = listOf(100, 200, 300, 500, 1000, 2000)
    val oddDecimal = selection.odd.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
    val potentialReturn = if (oddDecimal > 0) stake * oddDecimal else 0.0

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bet slip", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(selection.matchTitle, fontSize = 14.sp, color = Color.DarkGray, maxLines = 2)
                Spacer(Modifier.height(8.dp))
                Text(selection.marketQuestion, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.Black)
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = CricketOddsAccent.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(selection.selectionLabel, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "@ ${selection.odd}",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CricketOddsAccent
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Stake (₹)", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.DarkGray)
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stakeChips.size) { i ->
                        val v = stakeChips[i]
                        val sel = stake == v
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (sel) OrangePrimary else Color(0xFFF0F0F0),
                            border = if (sel) BorderStroke(2.dp, Color.Black) else null,
                            modifier = Modifier.clickable { stake = v }
                        ) {
                            Text(
                                "₹$v",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Potential return", fontSize = 13.sp, color = Color.DarkGray)
                    Text(
                        "₹${String.format("%.2f", potentialReturn)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.Black
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel", color = Color.Black)
                    }
                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Bet ₹$stake @ ${selection.odd} — ${selection.selectionLabel}",
                                Toast.LENGTH_SHORT
                            ).show()
                            onPlaceBet()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CricketOddsAccent)
                    ) {
                        Text("Place bet", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketMatchDetailScreen(
    eventId: Long,
    matchTitle: String,
    onBack: () -> Unit,
    isPreEvent: Boolean = false
) {
    var detail by remember { mutableStateOf<CricketMatchOddsDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var betSelection by remember { mutableStateOf<CricketBetSelection?>(null) }
    val scope = rememberCoroutineScope()

    fun reload() {
        scope.launch {
            loading = true; error = null
            val (d, err) = if (isPreEvent)
                fetchPreEventOddsDetail(eventId)
            else
                fetchLiveOddsDetail(eventId)
            detail = d; error = err; loading = false
            // auto-refresh according to poll_interval (pre-events refresh less aggressively)
            val interval = if (isPreEvent) 30000L
            else ((d?.pollIntervalSeconds ?: 10) * 1000L).coerceIn(3000L, 30000L)
            delay(interval)
            if (detail != null) reload()
        }
    }

    LaunchedEffect(eventId) { reload() }

    BackHandler { if (betSelection != null) betSelection = null else onBack() }

    betSelection?.let { sel ->
        CricketBetCardDialog(selection = sel, onDismiss = { betSelection = null }, onPlaceBet = { betSelection = null })
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Header
        Surface(Modifier.fillMaxWidth(), color = Color.White) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                    Text(
                        text = detail?.matchName ?: matchTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.Black,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        lineHeight = 20.sp
                    )
                    if (loading && detail != null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp).padding(end = 8.dp),
                            color = CricketOddsAccent,
                            strokeWidth = 2.dp
                        )
                    }
                }
                // score bar
                detail?.let { d ->
                    val statusColor = when (d.clockStatus.uppercase(Locale.US)) {
                        "STARTED" -> Color(0xFF2E7D32); "PAUSED" -> Color(0xFFF57C00); else -> Color(0xFF546E7A)
                    }
                    val statusLabel = when (d.clockStatus.uppercase(Locale.US)) {
                        "STARTED" -> "LIVE"; "PAUSED" -> "PAUSED"; "NOT_STARTED" -> "UPCOMING"; else -> d.clockStatus
                    }
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFF5F5F5)).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (d.currentInnings.isNotBlank()) {
                                Text(d.currentInnings, fontSize = 11.sp, color = Color.Gray)
                                Spacer(Modifier.height(4.dp))
                            }
                            d.scores.forEach { s ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (s.batting) Text("🏏 ", fontSize = 12.sp)
                                        Text(
                                            s.team,
                                            fontSize = 13.sp,
                                            fontWeight = if (s.batting) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (s.batting) Color(0xFF1A1A1A) else Color(0xFF757575)
                                        )
                                    }
                                    Text(
                                        s.score,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (s.batting) CricketOddsAccent else Color(0xFF757575)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.12f)) {
                            Text(
                                statusLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = statusColor
                            )
                        }
                    }
                }
                Divider(color = Color(0xFFE0E0E0))
            }
        }

        when {
            loading && detail == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = CricketOddsAccent)
                }
            }
            error != null && detail == null -> {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error!!, color = Color(0xFFC62828), fontSize = 14.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { reload() }) { Text("Retry", color = CricketOddsAccent) }
                    }
                }
            }
            else -> {
                val markets = detail?.allMarkets ?: emptyList()
                if (markets.isEmpty()) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No markets available for this match.", fontSize = 15.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(count = markets.size, key = { markets[it].marketId }) { idx ->
                            val market = markets[idx]
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            market.marketName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1A1A1A),
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (market.marketStatus == "open") {
                                            Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFE8F5E9)) {
                                                Text("OPEN", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        market.outcomes.forEach { outcome ->
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        betSelection = CricketBetSelection(
                                                            matchTitle = detail?.matchName ?: matchTitle,
                                                            marketQuestion = market.marketName,
                                                            selectionLabel = outcome.name,
                                                            odd = outcome.priceFormat
                                                        )
                                                    },
                                                shape = RoundedCornerShape(8.dp),
                                                color = CricketOddsAccent.copy(alpha = 0.07f),
                                                border = BorderStroke(1.dp, CricketOddsAccent.copy(alpha = 0.25f))
                                            ) {
                                                Column(
                                                    Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(outcome.name, fontSize = 11.sp, color = Color(0xFF424242), maxLines = 2, textAlign = TextAlign.Center, lineHeight = 15.sp)
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(outcome.priceFormat, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CricketOddsAccent)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketMatchCard(
    event: CricketEventUi,
    onOddClick: (CricketBetSelection) -> Unit,
    onMatchClick: ((CricketEventUi) -> Unit)? = null
) {
    val statusColor = when (event.clockStatus.uppercase(Locale.US)) {
        "STARTED" -> Color(0xFF2E7D32)
        "PAUSED"  -> Color(0xFFF57C00)
        else      -> Color(0xFF546E7A)
    }
    val statusLabel = when (event.clockStatus.uppercase(Locale.US)) {
        "STARTED"     -> "LIVE"
        "PAUSED"      -> "PAUSED"
        "NOT_STARTED" -> "UPCOMING"
        else          -> event.clockStatus.ifBlank { "—" }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onMatchClick != null) Modifier.clickable { onMatchClick(event) } else Modifier),
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE0E0E0))
    ) {
        Column(Modifier.padding(16.dp)) {
            // Title row + status badge + "View Odds" chevron
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = event.matchTitle,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A1A),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    lineHeight = 22.sp
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusLabel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
                if (onMatchClick != null) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View odds",
                        tint = Color(0xFFBDBDBD),
                        modifier = Modifier.size(20.dp).padding(start = 4.dp)
                    )
                }
            }

            if (event.inningsLabel.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = event.inningsLabel,
                    fontSize = 12.sp,
                    color = Color(0xFF757575)
                )
            }

            // Score rows
            if (event.scores.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(Modifier.height(10.dp))
                event.scores.forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (s.batting) {
                                Text("🏏 ", fontSize = 13.sp)
                            }
                            Text(
                                text = s.team,
                                fontSize = 14.sp,
                                fontWeight = if (s.batting) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (s.batting) Color(0xFF1A1A1A) else Color(0xFF616161)
                            )
                        }
                        Text(
                            text = s.score,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (s.batting) CricketOddsAccent else Color(0xFF616161)
                        )
                    }
                }
            }

            // Odds section
            val market = event.markets.firstOrNull()
            if (market != null && market.outcomes.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Match Odds",
                    fontSize = 12.sp,
                    color = Color(0xFF9E9E9E),
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    market.outcomes.forEach { outcome ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    onOddClick(CricketBetSelection(
                                        matchTitle = event.matchTitle,
                                        marketQuestion = "Match Odds",
                                        selectionLabel = outcome.label,
                                        odd = outcome.odd
                                    ))
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = CricketOddsAccent.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, CricketOddsAccent.copy(alpha = 0.3f))
                        ) {
                            Column(
                                Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = outcome.label,
                                    fontSize = 12.sp,
                                    color = Color(0xFF424242),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = outcome.odd,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CricketOddsAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketMarketOddsCard(
    market: CricketMarketUi,
    matchTitle: String,
    onOddClick: (CricketBetSelection) -> Unit
) {
    fun fire(outcome: CricketOutcomeUi) {
        onOddClick(
            CricketBetSelection(
                matchTitle = matchTitle,
                marketQuestion = market.question,
                selectionLabel = outcome.label,
                odd = outcome.odd
            )
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                market.question,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.Black,
                lineHeight = 20.sp
            )
            Spacer(Modifier.height(12.dp))
            when (market.outcomes.size) {
                2 -> {
                    val a = market.outcomes[0]
                    val b = market.outcomes[1]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        Column(
                            Modifier
                                .weight(1f)
                                .background(CricketOddsBlue)
                                .clickable { fire(a) }
                                .padding(vertical = 14.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                a.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CricketOddsOnBlue,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                a.odd,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CricketOddsOnBlue
                            )
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .background(CricketOddsPink)
                                .clickable { fire(b) }
                                .padding(vertical = 14.dp, horizontal = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                b.label,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = CricketOddsOnPink,
                                textAlign = TextAlign.Center,
                                maxLines = 3,
                                lineHeight = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                b.odd,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = CricketOddsOnPink
                            )
                        }
                    }
                }
                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        market.outcomes.forEachIndexed { idx, o ->
                            val bg =
                                if (idx % 2 == 0) CricketOddsBlue.copy(alpha = 0.12f)
                                else CricketOddsPink.copy(alpha = 0.12f)
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .clickable { fire(o) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    o.label,
                                    modifier = Modifier.weight(1f),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = Color.Black,
                                    maxLines = 3
                                )
                                Text(
                                    o.odd,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 18.sp,
                                    color = CricketOddsAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CricketOddsFilterTabs(
    selected: CricketOddsFilterTab,
    onSelect: (CricketOddsFilterTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            CricketOddsFilterTab.All to "All",
            CricketOddsFilterTab.Main to "Main",
            CricketOddsFilterTab.OverByOver to "Over by over"
        ).forEach { (tab, label) ->
            val sel = selected == tab
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(tab) },
                shape = RoundedCornerShape(10.dp),
                color = if (sel) CricketOddsAccent else Color(0xFFECEFF1),
                border = if (sel) BorderStroke(1.5.dp, CricketOddsAccent) else BorderStroke(1.dp, Color(0xFFB0BEC5))
            ) {
                Text(
                    text = label,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 10.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) Color.White else Color(0xFF37474F),
                    maxLines = 2,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun LiveStreamBlinkingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "live_stream_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blink"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(dotAlpha)
            .background(CricketStreamLiveRed)
    )
}

@Composable
private fun CricketLiveStreamTopBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LiveStreamBlinkingDot()
        Text(
            text = "LIVE",
            color = CricketStreamLiveRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
    }
}

@Composable
fun CricketOddsScreen(onBack: () -> Unit) {
    var betSelection by remember { mutableStateOf<CricketBetSelection?>(null) }
    var selectedCricketTab by remember { mutableStateOf(1) } // 0 = Live, 1 = Pre Events
    var selectedEvent by remember { mutableStateOf<CricketEventUi?>(null) }
    var selectedEventIsPreEvent by remember { mutableStateOf(false) }

    // Show detail screen when a match is tapped
    selectedEvent?.let { event ->
        CricketMatchDetailScreen(
            eventId = event.eventId,
            matchTitle = event.matchTitle,
            onBack = { selectedEvent = null },
            isPreEvent = selectedEventIsPreEvent
        )
        return
    }

    // Live events state
    val cachedFirst = remember { CricketFeedStore.peek().orEmpty() }
    var liveEvents by remember { mutableStateOf(cachedFirst) }
    var liveLoading by remember { mutableStateOf(cachedFirst.isEmpty()) }

    // Pre-events state
    var preEvents by remember { mutableStateOf<List<CricketEventUi>>(emptyList()) }
    var preLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        liveEvents = CricketFeedStore.load()
        liveLoading = false
    }
    LaunchedEffect(selectedCricketTab) {
        if (selectedCricketTab == 1 && preEvents.isEmpty()) {
            preLoading = true
            preEvents = fetchCricketPreEvents()
            preLoading = false
        }
    }

    BackHandler {
        if (betSelection != null) betSelection = null else onBack()
    }

    betSelection?.let { sel ->
        CricketBetCardDialog(
            selection = sel,
            onDismiss = { betSelection = null },
            onPlaceBet = { betSelection = null }
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("Cricket", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.Black)
                Text("Live markets & odds", fontSize = 13.sp, color = Color.DarkGray)
            }
        }

        // Live / Pre Events tab row
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            listOf("Live", "Pre Events").forEachIndexed { idx, label ->
                val isSelected = selectedCricketTab == idx
                val tabColor = if (isSelected) CricketOddsAccent else Color.Transparent
                val textColor = if (isSelected) CricketOddsAccent else Color(0xFF757575)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCricketTab = idx }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (idx == 0) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFF2E7D32) else Color(0xFF9E9E9E))
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                Text(
                            text = label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(tabColor)
                    )
                }
            }
        }
        Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)

        // Content
        val events = if (selectedCricketTab == 0) liveEvents else preEvents
        val loading = if (selectedCricketTab == 0) liveLoading else preLoading
        val emptyMsg = if (selectedCricketTab == 0) "No live matches right now." else "No pre-match events available."

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CricketOddsAccent)
            }
        } else if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(emptyMsg, fontSize = 15.sp, color = Color.DarkGray, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(count = events.size, key = { it }) { eIdx ->
                    CricketMatchCard(
                        event = events[eIdx],
                        onOddClick = { betSelection = it },
                        onMatchClick = if (selectedCricketTab == 0) ({
                            selectedEventIsPreEvent = false
                            selectedEvent = it
                        }) else ({
                            selectedEventIsPreEvent = true
                            selectedEvent = it
                        })
                    )
                }
            }
        }
    }
}

private val CockMeronRed = Color(0xFFD32F2F)
private val CockWalaBlue = Color(0xFF1976D2)
private val CockDrawGreen = Color(0xFF2E7D32)
private val CockDarkBg = Color(0xFF0D0D0D)

/** Muted fills for portrait cock fight tiles — less saturated than pure primaries. */
private val CockfightPortraitPlainRed = Color(0xFFB74A4A)
private val CockfightPortraitPlainGreen = Color(0xFF3F8F5C)
private val CockfightPortraitPlainBlue = Color(0xFF3D73B5)

private data class CockfightOdd(
    val label: String,
    val odd: String,
    val color: Color,
    /** COCK1 / COCK2 / DRAW — stable for POST body even when label is dynamic (Red, Black, …). */
    val canonicalSide: String,
)
private data class CockfightBetSelection(
    val label: String,
    val odd: String,
    val color: Color,
    val canonicalSide: String,
)

/** Canonical sides: COCK1, COCK2, DRAW (+ COMPLETED for overlays). Normalize API/legacy strings once. */
private fun canonicalCockfightSide(raw: String?): String =
    when (raw?.trim()?.uppercase(Locale.US) ?: "") {
        "", "NULL", "UNDEFINED" -> ""
        "MERON", "RED", "M", "COCK1", "SIDE1" -> "COCK1"
        "WALA", "BLUE", "W", "COCK2", "SIDE2" -> "COCK2"
        "DRAW", "D", "TIE" -> "DRAW"
        "COMPLETED" -> "COMPLETED"
        else -> raw?.trim()?.uppercase(Locale.US).orEmpty()
    }

private fun uiDisplaySideFor(canonicalUpper: String): String =
    when (canonicalUpper) {
        "COCK1" -> "Meron"
        "COCK2" -> "Wala"
        "DRAW" -> "Draw"
        else -> canonicalUpper
    }

private data class MeronWalaBetSuccess(
    val betId: Int,
    val sessionId: Int,
    val side: String,
    val stake: Int,
    val odds: String,
    val potentialPayout: Int,
    val walletBalance: String
)

/** One entry from GET /api/game/meron-wala/bets/mine/ */
private data class MeronWalaBetRecord(
    val id: Int,
    val session: Int,
    val side: String,
    /** Display string from API when present (`side_label`). */
    val sideLabel: String?,
    val stake: Int,
    val odds: String,
    val potentialPayout: Int,
    val status: String,       // PENDING / WON / LOST / VOID
    val payoutAmount: Int?,
    val createdAt: String
)

private suspend fun fetchMeronWalaInfo(): CockfightInfoResponse? =
    withContext(Dispatchers.IO) {
        try {
            val token = AuthTokenStore.accessToken
            val req = okhttp3.Request.Builder()
                .url(GAME_MERON_WALA_INFO_URL)
                .apply { if (token != null) addHeader("Authorization", "Bearer $token") }
                .addHeader("Accept", "application/json")
                .get().build()
            val resp = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build().newCall(req).execute()
            val fetchedAtMillis = System.currentTimeMillis()
            val body = resp.body?.string() ?: return@withContext null
            val j = org.json.JSONObject(body)
            val lvJ = j.optJSONObject("latest_round_video")
            val lv = lvJ?.let { o ->
                val urlStr = sequenceOf(o.optString("hls_url"), o.optString("url"))
                    .firstOrNull { it.isNotEmpty() && it != "null" }
                CockfightRoundVideo(
                    roundId = o.optInt("round_id", 0),
                    start = o.optString("start").takeIf { s -> s.isNotEmpty() && s != "null" },
                    url = urlStr,
                    requiresAuthentication = o.optBoolean("requires_authentication", false),
                    serverTime = o.optString("server_time").takeIf { s -> s.isNotEmpty() && s != "null" },
                    sideLabels = o.parseSideLabelsField()
                )
            }
            val lrJ = j.optJSONObject("last_result")
            val lr = lrJ?.let {
                CockfightLastResult(
                    session = it.optInt("session", 0),
                    winner = it.optString("winner").takeIf { s -> s.isNotEmpty() && s != "null" }
                        ?.let { w -> canonicalCockfightSide(w).takeIf { out -> out.isNotEmpty() } },
                    settledAt = it.optString("settled_at").takeIf { s -> s.isNotEmpty() && s != "null" }
                )
            }
            // Same skew as mweb: clockSkew = serverMs - fetchedAt when server_time set, else 0
            val serverEpochForSkew = cockfightParseIsoToEpochMillis(
                lv?.serverTime ?: j.optString("server_time").takeIf { s -> s.isNotEmpty() && s != "null" }
            )
            val clockSkewMillis = (serverEpochForSkew ?: fetchedAtMillis) - fetchedAtMillis
            CockfightInfoResponse(
                session = j.optInt("session").takeIf { j.has("session") && !j.isNull("session") },
                roundId = j.optInt("round_id").takeIf { j.has("round_id") && !j.isNull("round_id") },
                open = j.optBoolean("open", false),
                sideLabelsRoot = j.parseSideLabelsField(),
                latestRoundVideo = lv,
                lastResult = lr,
                clockSkewMillis = clockSkewMillis
            )
        } catch (e: Exception) { null }
    }

private suspend fun fetchMeronWalaBetHistory(): Pair<List<MeronWalaBetRecord>, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(emptyList(), "Sign in to view your bets.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(emptyList(), "No history for demo accounts.")
        }
        try {
            val req = Request.Builder()
                .url(GAME_MERON_WALA_BETS_MINE_URL)
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when (resp.code) {
                    200 -> {
                        val arr = runCatching { JSONArray(text) }.getOrNull()
                            ?: return@withContext Pair(emptyList(), "Invalid response.")
                        val list = (0 until arr.length()).mapNotNull { i ->
                            val j = arr.optJSONObject(i) ?: return@mapNotNull null
                            MeronWalaBetRecord(
                                id = j.optIntValue("id", -1),
                                session = j.optIntValue("session", -1),
                                side = canonicalCockfightSide(j.optString("side", "").trim()),
                                sideLabel = j.optNonBlank("side_label"),
                                stake = j.optIntValue("stake", 0),
                                odds = j.optString("odds", "").trim(),
                                potentialPayout = j.optIntValue("potential_payout", 0),
                                status = j.optString("status", "").trim().uppercase(Locale.US),
                                payoutAmount =
                                    if (!j.has("payout_amount") || j.isNull("payout_amount"))
                                        null
                                    else j.optIntValue("payout_amount", 0),
                                createdAt = j.optString("created_at", "").trim()
                            )
                        }
                        Pair(list, null)
                    }
                    401 -> Pair(emptyList(), "Session expired. Please sign in again.")
                    else -> {
                        val errMsg = runCatching {
                            JSONObject(text).optString("error", "").ifBlank {
                                JSONObject(text).optString("detail", "")
                            }
                        }.getOrNull().orEmpty()
                        Pair(emptyList(), errMsg.ifBlank { "Failed to load history (${resp.code})." })
                    }
                }
            }
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: "Network error")
        }
    }

private fun formatBetDateTime(isoDate: String): String = runCatching {
    val clean = isoDate.substringBefore(".").let { s ->
        if (s.length >= 19) s else isoDate.take(19)
    }
    val inFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    val date = inFmt.parse(clean) ?: return@runCatching isoDate.take(16).replace("T", " ")
    SimpleDateFormat("MMM d, h:mm a", Locale.US).format(date)
}.getOrDefault(isoDate.take(16).replace("T", " "))

private data class GundataBetSuccess(
    val message: String,
    val walletBalance: String,
    val roundId: String,
    val number: Int,
    val chipAmount: String,
    val maxBet: Double
)

private fun extractErrorBody(text: String): String {
    val j = runCatching { org.json.JSONObject(text) }.getOrNull() ?: return ""
    j.optString("error", "").ifBlank { null }?.let { return it }
    j.optString("detail", "").ifBlank { null }?.let { return it }
    // field-level validation errors: collect first non-empty array / string value
    val keys = j.keys()
    while (keys.hasNext()) {
        val k = keys.next()
        val v = j.opt(k)
        val msg = when (v) {
            is org.json.JSONArray -> if (v.length() > 0) v.optString(0) else ""
            is String -> v
            else -> ""
        }
        if (msg.isNotBlank()) return "$k: $msg"
    }
    return ""
}

private suspend fun postGundataBet(number: Int, stakeInt: Int): Pair<GundataBetSuccess?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to place a bet.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(null, "Offline demo account cannot place bets.")
        }
        if (number !in 1..6) return@withContext Pair(null, "Pick a number between 1 and 6.")
        if (stakeInt <= 0) return@withContext Pair(null, "Enter a valid stake.")
        if (stakeInt > 50_000) return@withContext Pair(null, "Maximum bet amount is ₹50,000.")
        try {
            val chipFormatted = "%.2f".format(stakeInt.toDouble())
            val json = org.json.JSONObject().apply {
                put("number", number)
                put("chip_amount", chipFormatted)
            }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url(GAME_GUNDUATA_BET_URL)
                .post(body)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                val errBody = extractErrorBody(text)
                when (resp.code) {
                    201, 200 -> {
                        val j = runCatching { org.json.JSONObject(text) }.getOrNull()
                            ?: return@withContext Pair(null, "Invalid response.")
                        Pair(
                            GundataBetSuccess(
                                message   = j.optString("message", "Bet placed"),
                                walletBalance = j.optString("wallet_balance", "").trim(),
                                roundId   = j.optString("round_id", "").trim(),
                                number    = j.optInt("number", number),
                                chipAmount = j.optString("chip_amount", chipFormatted).trim(),
                                maxBet    = j.optDouble("max_bet", 50_000.0)
                            ),
                            null
                        )
                    }
                    400 -> Pair(null, errBody.ifBlank { "Betting is closed for this round." })
                    401 -> Pair(null, errBody.ifBlank { "Session expired. Please sign in again." })
                    403 -> Pair(null, errBody.ifBlank { "Admins cannot place bets." })
                    else -> Pair(null, errBody.ifBlank { "Bet failed (${resp.code})." })
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

private suspend fun postMeronWalaBet(side: String, stake: Int): Pair<MeronWalaBetSuccess?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to place a bet.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(null, "Offline demo account cannot place bets.")
        }
        if (stake <= 0) return@withContext Pair(null, "Enter a valid stake.")
        if (stake > 50_000) return@withContext Pair(null, "Maximum bet amount is 50000.")
        try {
            val json =
                JSONObject().apply {
                    put("side", side)
                    put("stake", stake)
                }
            val body = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            android.util.Log.d("CockfightBet", "Request body: ${json}")
            val req =
                Request.Builder()
                    .url(GAME_MERON_WALA_BET_URL)
                    .post(body)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer $token")
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                android.util.Log.d("CockfightBet", "Response ${resp.code}: $text")
                val errBody =
                    runCatching {
                        JSONObject(text).optString("error", "").ifBlank {
                            JSONObject(text).optString("detail", "")
                        }
                    }.getOrNull().orEmpty()
                when (resp.code) {
                    201 -> {
                        val j = runCatching { JSONObject(text) }.getOrNull()
                            ?: return@withContext Pair(null, "Invalid response.")
                        Pair(
                            MeronWalaBetSuccess(
                                betId = j.optIntValue("bet_id", -1),
                                sessionId =
                                    when {
                                        j.has("session") && !j.isNull("session") ->
                                            j.optIntValue("session", -1)

                                        j.has("session_id") && !j.isNull("session_id") ->
                                            j.optIntValue("session_id", -1)

                                        else -> -1
                                    },
                                side = canonicalCockfightSide(j.optString("side", "").trim()),
                                stake = j.optIntValue("stake", stake),
                                odds = j.optString("odds", "").trim(),
                                potentialPayout = j.optIntValue("potential_payout", 0),
                                walletBalance = j.optString("wallet_balance", "").trim()
                            ),
                            null
                        )
                    }
                    401 -> Pair(null, errBody.ifBlank { "Session expired. Please sign in again." })
                    else -> Pair(null, errBody.ifBlank { "Bet failed (${resp.code})." })
                }
            }
        } catch (e: Exception) {
            Pair(null, e.message ?: "Network error")
        }
    }

@Composable
private fun FullscreenBetSlip(
    selection: CockfightBetSelection,
    onDismiss: () -> Unit,
    /** Return `null` on success, or an error message. */
    onPlaceBet: suspend (stake: Int) -> String?
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var placing by remember { mutableStateOf(false) }
    var stakeText by remember { mutableStateOf("100") }
    val chips = listOf(100, 200, 500, 1000, 2000, 5000)
    val stake = stakeText.toIntOrNull() ?: 0
    val payout = if (stake > 0) "₹%.2f".format(stake * (selection.odd.toDoubleOrNull() ?: 1.0)) else "—"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .imePadding()
                .navigationBarsPadding()
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(
                            Modifier
                                .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 10.dp, bottom = 20.dp)
            ) {
                // Handle bar
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF444444), CircleShape)
                )
                Spacer(Modifier.height(8.dp))
                // Header row: title + close + pick pill all compact
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bet Slip", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Inline pick badge
                        Surface(shape = RoundedCornerShape(6.dp), color = selection.color) {
                            Text(
                                "${selection.label}  ${selection.odd}X",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                // Amount input + payout
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Amount", color = Color(0xFFAAAAAA), fontSize = 12.sp)
                    Text("Payout: $payout", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = stakeText,
                    onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 7) stakeText = it },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    placeholder = { Text("₹ 0", color = Color(0xFF666666)) },
                    prefix = { Text("₹ ", color = Color.White) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selection.color,
                        unfocusedBorderColor = Color(0xFF444444),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = selection.color
                    )
                )
                Spacer(Modifier.height(8.dp))
                // Quick chips — compact
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    chips.forEach { v ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (stakeText == v.toString()) selection.color else Color(0xFF2A2A2A),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .padding(horizontal = 3.dp)
                                .clickable { stakeText = v.toString() }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                    if (v >= 1000) "${v / 1000}K" else "$v",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // ── PLACE BET BUTTON ── large, prominent, always visible
                Button(
                    onClick = {
                        if (stake <= 0 || stake > 50_000) {
                            Toast.makeText(
                                context,
                                if (stake > 50_000) "Maximum bet is ₹50,000" else "Enter a valid amount",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }
                        if (placing) return@Button
                        scope.launch {
                            placing = true
                            val err = onPlaceBet(stake)
                            placing = false
                            if (err == null) {
                                Toast.makeText(context, "Bet placed", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = stake > 0 && !placing,
                                modifier = Modifier
                                    .fillMaxWidth()
                        .height(60.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = selection.color,
                        disabledContainerColor = Color(0xFF333333)
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    if (placing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (placing) "Placing…" else "PLACE BET  ₹$stake",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }

@Composable
private fun CockfightBetHistoryPanel(onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var bets by remember { mutableStateOf<List<MeronWalaBetRecord>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (list, err) = fetchMeronWalaBetHistory()
        bets = list
        errorMsg = err
        loading = false
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
        ) {
            Surface(
                        modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF444444), CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Recent Bets", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Divider(color = Color(0xFF333333), thickness = 1.dp)
                Spacer(Modifier.height(4.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OrangePrimary)
                    }
                    errorMsg != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(errorMsg ?: "", color = Color(0xFFAAAAAA), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                    bets.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No bets placed yet", color = Color(0xFF888888), fontSize = 15.sp)
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(bets) { bet -> CockfightBetHistoryRow(bet) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CockfightBetHistoryRow(bet: MeronWalaBetRecord) {
    val canon = canonicalCockfightSide(bet.side)
    val sideColor = when (canon) {
        "COCK1" -> CockMeronRed
        "COCK2" -> CockWalaBlue
        "DRAW" -> CockDrawGreen
        else -> Color.Gray
    }
    val statusColor = when (bet.status) {
        "WON"  -> Color(0xFF4CAF50)
        "LOST" -> Color(0xFFE53935)
        "VOID" -> Color(0xFF9E9E9E)
        else   -> OrangePrimary
    }
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF252525)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
            Surface(shape = RoundedCornerShape(6.dp), color = sideColor.copy(alpha = 0.15f)) {
                        Text(
                    (
                        bet.sideLabel?.takeIf { it.isNotBlank() }
                            ?: canon.takeIf { it.isNotEmpty() }?.let { uiDisplaySideFor(it) }
                            ?: bet.side.ifBlank { "?" }
                    ).uppercase(Locale.US),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = sideColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp,
                    letterSpacing = 0.3.sp
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "₹${bet.stake}  ×  ${bet.odds}",
                    color = Color.White,
                            fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatBetDateTime(bet.createdAt),
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        bet.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                    Text(
                    when {
                        bet.status == "WON" && bet.payoutAmount != null -> "+₹${bet.payoutAmount}"
                        bet.status == "WON" -> "Won — pending payout"
                        else -> "pot. ₹${bet.potentialPayout}"
                    },
                    color = if (bet.status == "WON") Color(0xFF4CAF50) else Color(0xFF666666),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Pulsing border / scale “lightning” highlight for Meron / Draw / Wala in fullscreen. */
@Composable
private fun CockfightOddsBetCard(
    label: String,
    odd: String,
    baseColor: Color,
    staggerIndex: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "cockfightOddGlow_$staggerIndex")
    val glow by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 950,
                delayMillis = staggerIndex * 320,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val surfaceColor = lerp(baseColor, Color.White, glow * 0.12f)
    Surface(
        modifier = modifier
            .height(48.dp)
            .scale(1f + glow * 0.05f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = surfaceColor,
        border = BorderStroke(
            width = 2.dp,
            color = Color.White.copy(alpha = 0.35f + glow * 0.55f)
        ),
        shadowElevation = (glow * 6).dp
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 12.sp
            )
            Text(
                "${odd}X",
                color = Color.White.copy(alpha = 0.88f + glow * 0.12f),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/** Tall portrait card: odds on top, label on bottom — sizing aligned with mweb `.cockfight-side-btn` (~150px tall, 21px/15px type). */
@Composable
private fun CockfightPortraitLargeBetCard(
    label: String,
    odd: String,
    baseColor: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 148.dp,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(cardHeight)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = baseColor,
        border = if (selected) BorderStroke(3.dp, OrangePrimary) else null,
        shadowElevation = 0.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(vertical = 14.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${odd}X",
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 21.sp
            )
            Text(
                label,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
private fun CockfightBetAmountChip(
    amount: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) OrangePrimary else Color(0xFF5C5C5C),
                shape = CircleShape
            )
            .background(Color(0xFF2C2C2C))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "$amount",
            color = Color.White,
            fontSize = if (amount >= 1000) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Continuous horizontal marquee for first-deposit promo (cock fight). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CockfightFirstDepositMarqueeBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = OrangePrimary.copy(alpha = 0.95f),
        shadowElevation = 2.dp
    ) {
        Text(
            text = "  Deposit now and get ₹500 bonus on your first deposit!  " +
                "Limited time — deposit and get ₹500 bonus on your first deposit!  " +
                "Deposit now and get ₹500 bonus on your first deposit!  ",
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 7.dp)
                .basicMarquee(
                    initialDelayMillis = 400,
                    delayMillis = 30
                ),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun CockfightCountdownOverlay(startMs: Long, clockSkewMillis: Long, onDone: () -> Unit) {
    fun remainingSecs() = maxOf(0L, (startMs - System.currentTimeMillis() - clockSkewMillis) / 1000L)
    var remaining by remember(startMs, clockSkewMillis) { mutableStateOf(remainingSecs()) }
    LaunchedEffect(startMs, clockSkewMillis) {
        while (remaining > 0) {
            kotlinx.coroutines.delay(1000)
            remaining = remainingSecs()
        }
        onDone()
    }
    val h = remaining / 3600
    val m = (remaining % 3600) / 60
    val s = remaining % 60
    val timeText = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
    ) {
        Image(
            painter = painterResource(R.drawable.cockfight_banner),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        // Cf. mweb `.cf-countdown__bg` filter brightness(0.35) + scrim
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.62f)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🐓", fontSize = 36.sp)
                Text(
                    "VS",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 20.sp,
                    letterSpacing = 2.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = Shadow(Color.Black.copy(0.9f), blurRadius = 8f)
                    )
                )
                Text("🐓", fontSize = 36.sp, modifier = Modifier.scale(scaleX = -1f, scaleY = 1f))
            }
            Text(
                "Next match starts in",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = Shadow(Color.Black.copy(0.8f), blurRadius = 4f))
            )
            Text(timeText,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 52.sp,
                fontFamily = FontFamily.Monospace,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = Shadow(Color(0xFFE53935), offset = Offset(0f, 2f), blurRadius = 16f),
                    letterSpacing = 2.sp,
                ))
            Text(
                "Get ready to place your bets!",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 12.sp,
                style = androidx.compose.ui.text.TextStyle(shadow = Shadow(Color.Black.copy(0.8f), blurRadius = 4f))
            )
        }
    }
}

@Composable
private fun CockfightWaitingForStream() {
    val dots = listOf("●○○", "○●○", "○○●")
    var idx by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(400); idx = (idx + 1) % 3 } }
    Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("🐓", fontSize = 36.sp)
            Text(dots[idx], color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Match is starting...", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text("Waiting for live stream", color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun CockfightWinnerOverlay(
    winner: String,
    userBetStatus: String?,
    userPayout: String?,
    onDismissRequest: () -> Unit,
    sideTitles: CockfightSideLabels = CockfightSideLabels("Cock 1", "Cock 2"),
) {
    val w = canonicalCockfightSide(winner)
    val winnerLabel = when (w) {
        "COCK1" -> "🐓 ${sideTitles.cock1} Wins!"
        "COCK2" -> "🐓 ${sideTitles.cock2} Wins!"
        "DRAW" -> "🤝 It's a Draw!"
        "COMPLETED" -> "Match Completed 🏆"
        else -> "${winner.ifBlank { "?" }.trim()} Wins!"
    }
    var secs by remember { mutableStateOf(60) }
    LaunchedEffect(Unit) {
        while (secs > 0) { kotlinx.coroutines.delay(1000); secs-- }
        onDismissRequest()
    }
    val m = secs / 60; val s = secs % 60
    Box(
        Modifier.fillMaxSize().background(Color(0xDD000000)).clickable {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text("🏆", fontSize = 52.sp)
            Text("Match Result", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(winnerLabel, color = Color(0xFFFFD700), fontWeight = FontWeight.Black, fontSize = 28.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFFFFD700), offset = androidx.compose.ui.geometry.Offset(0f, 0f), blurRadius = 20f
                    )
                )
            )
            if (w != "COMPLETED") {
                when {
                    userBetStatus == "WON" -> Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF43A047)).padding(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("🎉 You Won ${userPayout?.let { "₹$it" } ?: ""}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    userBetStatus == "LOST" -> Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFFE53935)).padding(horizontal = 16.dp, vertical = 6.dp)
                    ) { Text("😔 Better luck next time", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            val subLine = if (w == "COMPLETED")
                "Results will be announced shortly."
            else
                "Congratulations to all winners!"
            Text(subLine, color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFFE53935)))
                Text("%d:%02d".format(m, s), color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun CockFightLiveScreen(
    onBack: () -> Unit,
    onWallet: () -> Unit,
    onOpenProfile: () -> Unit = {}
) {
    var isVideoFullscreen by remember { mutableStateOf(false) }
    var cockfightSideLabelsUi by remember {
        mutableStateOf(CockfightSideLabels("Meron", "Wala"))
    }
    var cockfightOdds by remember {
        mutableStateOf(
            listOf(
                CockfightOdd("Meron", "1.90", CockMeronRed, "COCK1"),
                CockfightOdd("Draw", "4.46", CockDrawGreen, "DRAW"),
                CockfightOdd("Wala", "1.92", CockWalaBlue, "COCK2"),
            )
        )
    }
    var selectedCockfightOdd by remember { mutableStateOf<CockfightOdd?>(cockfightOdds.firstOrNull()) }
    var selectedChipAmount by remember { mutableStateOf(100) }
    val chipAmounts = remember { listOf(50, 100, 200, 300, 500, 1000, 2500, 5000) }
    var placingCockfightBet by remember { mutableStateOf(false) }
    val cockfightBetScope = rememberCoroutineScope()
    val cockfightCtx = LocalContext.current

    // ── Live stream state ────────────────────────────────────
    // Phase: "loading" | "countdown" | "polling" | "playing" | "winner"
    var streamPhase by remember { mutableStateOf("loading") }
    var countdownStartMs by remember { mutableStateOf(0L) }
    /** Same as mweb countdown: `Date.now() + clockSkew`. */
    var countdownClockSkewMs by remember { mutableStateOf(0L) }
    var liveVideoUrl by remember { mutableStateOf<String?>(null) }
    var liveSeekSeconds by remember { mutableStateOf(0) }
    var winnerLabel by remember { mutableStateOf("") }
    var userBetStatus by remember { mutableStateOf<String?>(null) }
    var userBetPayout by remember { mutableStateOf<String?>(null) }

    // Fullscreen: inner handler exits landscape only. Portrait: first back stays on cock fight (same idea as Gundu Ata).
    BackHandler(enabled = !isVideoFullscreen) { onBack() }

    var cockfightWalletText by remember { mutableStateOf("₹0") }
    var showCockfightBetHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        cockfightWalletText = formatRupeeBalanceForDisplay(w?.balance)
    }

    // ── Initial API fetch ────────────────────────────────────
    LaunchedEffect(Unit) {
        val info = fetchMeronWalaInfo()
        if (info != null) {
            val esl = info.effectiveSideLabels()
            cockfightSideLabelsUi = esl
            cockfightOdds = listOf(
                CockfightOdd(esl.cock1, "1.90", CockMeronRed, "COCK1"),
                CockfightOdd("Draw", "4.46", CockDrawGreen, "DRAW"),
                CockfightOdd(esl.cock2, "1.92", CockWalaBlue, "COCK2"),
            )
            selectedCockfightOdd = cockfightOdds.firstOrNull()
        }
        if (info == null) { streamPhase = "polling"; return@LaunchedEffect }
        // Check if match just ended (winner screen)
        if (!info.open && info.lastResult?.winner != null) {
            winnerLabel = info.lastResult.winner
            userBetStatus = null; userBetPayout = null
            try {
                val (bets, _) = fetchMeronWalaBetHistory()
                val last = bets.firstOrNull()
                userBetStatus = last?.status
                userBetPayout = last?.payoutAmount?.toString()
            } catch (_: Exception) {}
            streamPhase = "winner"; return@LaunchedEffect
        }
        val lv = info.latestRoundVideo
        val startMs = lv?.start?.let { cockfightParseIsoToEpochMillis(it) }
        val skew = info.clockSkewMillis
        val nowMs = System.currentTimeMillis() + skew
        when {
            startMs != null && startMs > nowMs -> {
                countdownStartMs = startMs
                countdownClockSkewMs = skew
                streamPhase = "countdown"
            }
            lv?.requiresAuthentication == true && lv.url == null -> streamPhase = "login"
            lv?.url != null -> {
                liveVideoUrl = lv.url
                liveSeekSeconds = startMs?.let { maxOf(0, ((nowMs - it) / 1000).toInt()) } ?: 0
                streamPhase = "playing"
            }
            info.open -> {
                // Match is live but API didn't return a specific URL — use the hardcoded HLS stream
                liveVideoUrl = COCKFIGHT_LIVE_HLS_URL
                liveSeekSeconds = 0
                streamPhase = "playing"
            }
            else -> { streamPhase = "polling" }
        }
    }

    // ── Poll every second for URL after countdown or when waiting ──
    LaunchedEffect(streamPhase) {
        if (streamPhase != "polling") return@LaunchedEffect
        while (streamPhase == "polling") {
            kotlinx.coroutines.delay(1000)
            val info = fetchMeronWalaInfo() ?: continue
            val lv = info.latestRoundVideo
            when {
                lv?.url != null -> {
                    liveVideoUrl = lv.url
                    liveSeekSeconds = 0
                    streamPhase = "playing"
                }
                lv?.requiresAuthentication == true -> streamPhase = "login"
                info.open -> {
                    // Match is live — fallback to hardcoded HLS stream
                    liveVideoUrl = COCKFIGHT_LIVE_HLS_URL
                    liveSeekSeconds = 0
                    streamPhase = "playing"
                }
            }
        }
    }

    // ── Poll every 3 seconds during playback for match result ──
    LaunchedEffect(streamPhase) {
        if (streamPhase != "playing") return@LaunchedEffect
        while (streamPhase == "playing") {
            kotlinx.coroutines.delay(3000)
            val info = fetchMeronWalaInfo() ?: continue
            if (!info.open && info.lastResult?.winner != null) {
                winnerLabel = info.lastResult.winner
                try {
                    val (bets, _) = fetchMeronWalaBetHistory()
                    val last = bets.firstOrNull()
                    userBetStatus = last?.status
                    userBetPayout = last?.payoutAmount?.toString()
                } catch (_: Exception) {}
                streamPhase = "winner"
            }
        }
    }
    Box(Modifier.fillMaxSize().background(CockDarkBg)) {
        Column(Modifier.fillMaxSize()) {
            if (!isVideoFullscreen) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                "COCK FIGHT LIVE",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                letterSpacing = 0.6.sp
            )
            WalletBalanceChip(onClick = onWallet, balanceText = cockfightWalletText, spacerBetweenIconAndText = 6.dp)
        }
        }
        LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (!isVideoFullscreen) {
                    item(key = "cockfight_deposit_marquee") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            CockfightFirstDepositMarqueeBanner()
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                // Stable key: fullscreen hides sibling items; without this the stream slot is recreated,
                // onDispose forces portrait and drops fullscreen — user stays in portrait.
                item(key = "cockfight_hls_stream") {
                // Full-bleed 16:9 like mweb (.cockfight-stream__box) — no side padding
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black)
                ) {
                    when (streamPhase) {
                        "countdown" -> CockfightCountdownOverlay(
                            startMs = countdownStartMs,
                            clockSkewMillis = countdownClockSkewMs,
                            onDone = { streamPhase = "polling" }
                        )
                        "polling" -> CockfightWaitingForStream()
                        "loading" -> Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = OrangePrimary)
                        }
                        "login" -> Box(Modifier.fillMaxSize().background(Color(0xFF1A1A1A)),
                            contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("🔐", fontSize = 36.sp)
                                Text("Login to watch the live match",
                                    color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        "playing" -> {
                            val url = liveVideoUrl
                            if (url != null) {
                                CockFightHlsStream(
                                    modifier = Modifier.fillMaxSize(),
                                    onSurfaceClick = null,
                                    usePlaybackControls = true,
                                    useHomeLocalVideo = false,
                                    useLiveLocalVideo = false,
                                    liveStreamUrl = url,
                                    liveSeekSeconds = liveSeekSeconds,
                                    disallowPlaybackSeeking = true,
                                    odds = cockfightOdds,
                                    walletBalance = cockfightWalletText,
                                    onWalletBalanceClick = onWallet,
                                    onWalletBalanceAfterBet = { bal ->
                                        cockfightWalletText = formatRupeeBalanceForDisplay(bal)
                                    },
                                    onFullscreenChanged = { isVideoFullscreen = it },
                                    startFullscreen = false,
                                    cropVideoToFill = true,
                                    onPlaybackEnded = {
                                        winnerLabel = "COMPLETED"
                                        userBetStatus = null
                                        userBetPayout = null
                                        streamPhase = "winner"
                                    },
                                )
                            }
                        }
                        "winner" -> {
                            // Black screen — no frozen video frame behind the overlay
                            Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A)))
                            CockfightWinnerOverlay(
                                winner = winnerLabel,
                                userBetStatus = userBetStatus,
                                userPayout = userBetPayout,
                                onDismissRequest = { onBack() },
                                sideTitles = cockfightSideLabelsUi,
                            )
                        }
                    }
                }
                }
                if (!isVideoFullscreen) {
                item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 8.dp, bottom = 12.dp)
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                        colors = listOf(
                                            CockMeronRed.copy(alpha = 0.95f),
                                            Color(0xFFFF6F00).copy(alpha = 0.85f),
                                            CockWalaBlue.copy(alpha = 0.95f)
                                        )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "${cockfightSideLabelsUi.cock1}  VS  ${cockfightSideLabelsUi.cock2}",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        letterSpacing = 0.3.sp
                    )
                }
            }
                }
                if (!isVideoFullscreen) {
                    item(key = "cockfight_bet_cards") {
                        /** Match mweb: three equal columns ~150px tall; gap/padding similar to `.cock-cards-row`. */
                        val cardH = 150.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                                .padding(horizontal = 12.dp)
                                .padding(top = 10.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            cockfightOdds.forEachIndexed { index, o ->
                                val h = cardH
                                val plain = when (index) {
                                    0 -> CockfightPortraitPlainRed
                                    1 -> CockfightPortraitPlainGreen
                                    else -> CockfightPortraitPlainBlue
                                }
                                CockfightPortraitLargeBetCard(
                                    label = o.label,
                                    odd = o.odd,
                                    baseColor = plain,
                                    selected = selectedCockfightOdd?.label == o.label,
                                    modifier = Modifier.weight(1f),
                                    cardHeight = h,
                                    onClick = { selectedCockfightOdd = o }
                                )
                            }
                        }
                    }
                }
            }

            if (!isVideoFullscreen) {
                        Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 8.dp)
                ) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chipAmounts.size) { idx ->
                            val amt = chipAmounts[idx]
                            CockfightBetAmountChip(
                                amount = amt,
                                selected = selectedChipAmount == amt,
                                onClick = { selectedChipAmount = amt }
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFE8E8E8),
                        shadowElevation = 2.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                                IconButton(
                                    onClick = onOpenProfile,
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = Color(0xFF424242),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { showCockfightBetHistory = true },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        Icons.Default.History,
                                        contentDescription = "Bet history",
                                        tint = Color(0xFF424242),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    val pick = selectedCockfightOdd ?: run {
                                        Toast.makeText(cockfightCtx, "Pick Meron, Draw, or Wala", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val side = pick.canonicalSide
                                    cockfightBetScope.launch {
                                        placingCockfightBet = true
                                        val (ok, err) = postMeronWalaBet(side, selectedChipAmount)
                                        placingCockfightBet = false
                                        if (ok != null) {
                                            cockfightWalletText = formatRupeeBalanceForDisplay(ok.walletBalance)
                                            Toast.makeText(
                                                cockfightCtx,
                                                "Bet placed: ${pick.label} ₹$selectedChipAmount",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                cockfightCtx,
                                                err ?: "Could not place bet",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                },
                                enabled = !placingCockfightBet && selectedCockfightOdd != null,
                        modifier = Modifier
                            .weight(1f)
                                    .padding(horizontal = 6.dp)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                            ) {
                                if (placingCockfightBet) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "Place Bet…",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                            IconButton(
                                onClick = onWallet,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    Icons.Default.AccountBalanceWallet,
                                    contentDescription = "Wallet",
                                    tint = Color(0xFF424242),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showCockfightBetHistory) {
            CockfightBetHistoryPanel(onDismiss = { showCockfightBetHistory = false })
        }
    }
}

private data class GundataBetRecord(
    val id: Int,
    val roundId: String,
    val number: Int,
    val chipAmount: String,
    val status: String,       // PENDING / WON / LOST / VOID
    val payoutAmount: String,
    val createdAt: String
)

private suspend fun fetchGundataBetHistory(): Pair<List<GundataBetRecord>, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(emptyList(), "Sign in to view your bets.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(emptyList(), "No history for demo accounts.")
        }
        try {
            val req = Request.Builder()
                .url(GAME_GUNDUATA_BETS_MINE_URL)
                .get()
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $token")
                .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when (resp.code) {
                    200 -> {
                        val arr = runCatching { JSONArray(text) }.getOrNull()
                            ?: return@withContext Pair(emptyList(), "Invalid response.")
                        val list = (0 until arr.length()).mapNotNull { i ->
                            val j = arr.optJSONObject(i) ?: return@mapNotNull null
                            GundataBetRecord(
                                id          = j.optIntValue("id", -1),
                                roundId     = j.optString("round_id", "").trim(),
                                number      = j.optInt("number", 0),
                                chipAmount  = j.optString("chip_amount", "0").trim(),
                                status      = j.optString("status", "").trim().uppercase(Locale.US),
                                payoutAmount = j.optString("payout_amount", "0").trim(),
                                createdAt   = j.optString("created_at", "").trim()
                            )
                        }
                        Pair(list, null)
                    }
                    401 -> Pair(emptyList(), "Session expired. Please sign in again.")
                    404 -> Pair(emptyList(), "Bet history is not available yet.")
                    else -> {
                        val errMsg = runCatching {
                            JSONObject(text).optString("error", "").ifBlank {
                                JSONObject(text).optString("detail", "")
                            }
                        }.getOrNull().orEmpty()
                        Pair(emptyList(), errMsg.ifBlank { "Failed to load history (${resp.code})." })
                    }
                }
            }
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: "Network error")
        }
    }

/** One column in last-round strip: round id header + six pip dice, top→bottom = faces[0]..[5] (1–6 in demo). */
@Composable
private fun GunduLastResultColumn(
    round: Int,
    faces: List<Int>,
    roundStyle: Int = 0
) {
    val six = (0..5).map { i -> (faces.getOrNull(i) ?: 1).coerceIn(1, 6) }
    // roundStyle: 0 = in-page “Last round results” (generous spacing), 1 = recent bottom sheet
    val dieH = if (roundStyle == 1) 34.dp else 34.dp
    val colW = if (roundStyle == 1) 36.dp else 36.dp
    val headerH = if (roundStyle == 1) 30.dp else 30.dp
    val pipD = if (roundStyle == 1) 5.2.dp else 5.dp
    val labelSize = if (roundStyle == 1) 16.sp else 14.5.sp
    val borderC = Color(0xFFE0E0E0)
    val borderW = 0.5.dp
    val cellShape = RoundedCornerShape(0.dp)
    // In-page strip uses much larger gaps so rounds and rows don’t run together
    val gapHeaderToDice = if (roundStyle == 1) 8.dp else 14.dp
    val gapBetweenDice = if (roundStyle == 1) 6.dp else 12.dp
    val dieInnerPad = if (roundStyle == 1) 3.dp else 3.dp
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .width(colW)
                .height(headerH)
                .background(Color.White, cellShape)
                .border(borderW, borderC, cellShape),
            contentAlignment = Alignment.Center
        ) {
                                Text(
                round.toString(),
                color = if (roundStyle == 1) Color(0xFF333333) else Color(0xFF444444),
                fontSize = labelSize,
                fontWeight = if (roundStyle == 1) FontWeight.Bold else FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(gapHeaderToDice))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gapBetweenDice)
        ) {
            six.forEach { v ->
                Box(
                    Modifier
                        .width(colW)
                        .height(dieH)
                        .background(Color.White, cellShape)
                        .border(borderW, borderC, cellShape),
                    contentAlignment = Alignment.Center
                ) {
                    GunduStripMiniPipFace(
                        value = v,
                        pipDiameter = pipD,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(dieInnerPad)
                    )
                }
            }
        }
    }
}

@Composable
private fun GunduStripMiniPipFace(
    value: Int,
    modifier: Modifier = Modifier,
    pipDiameter: Dp = 3.2.dp
) {
    // Darker core + light rim so pips read clearly on the light die face
    val pipCore = Color(0xFF424242)
    val pipRim = Color(0xFF9E9E9E)
    val pips9: BooleanArray = when (value.coerceIn(1, 6)) {
        1 -> booleanArrayOf(false, false, false, false, true, false, false, false, false)
        2 -> booleanArrayOf(true, false, false, false, false, false, false, false, true)
        3 -> booleanArrayOf(true, false, false, false, true, false, false, false, true)
        4 -> booleanArrayOf(true, false, true, false, false, false, true, false, true)
        5 -> booleanArrayOf(true, false, true, false, true, false, true, false, true)
        else -> booleanArrayOf(true, false, true, true, false, true, true, false, true)
    }
    val facePad = if (pipDiameter >= 4.5.dp) 2.dp else 1.5.dp
    val pipHalo = 0.5.dp
    Box(
        modifier
            .background(Color(0xFFFAFAFA), RoundedCornerShape(4.dp))
            .border(0.5.dp, Color(0xFFCCCCCC), RoundedCornerShape(3.dp))
            .padding(facePad)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0..2) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0..2) {
                        val on = pips9[r * 3 + c]
                        Box(
                            Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) {
                                // Halo (rim) + core — reads as a slightly lit dot at small sizes
                                Box(Modifier.size(pipDiameter), contentAlignment = Alignment.Center) {
                                    Box(Modifier.fillMaxSize().background(pipRim, CircleShape))
                                    val coreSize = (pipDiameter * 0.78f - pipHalo).coerceAtLeast(1.4.dp)
                                    Box(
                                        Modifier
                                            .align(Alignment.Center)
                                            .size(coreSize)
                                            .background(pipCore, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                            }
                        }
                    }
                }
            }

@Composable
private fun GundataRecentRoundsPanel(
    onDismiss: () -> Unit,
    resultStrip: List<Pair<Int, List<Int>>>
) {
    val hScroll = rememberScrollState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) { },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFFFAFAFA)
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent results",
                        color = Color(0xFF333333),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF666666))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "20 recent rounds — six dice per round, stacked 1 (top) through 6 (bottom).",
                    color = Color(0xFF888888),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(hScroll)
                        .background(Color.White, RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    resultStrip.forEach { (r, f) ->
                        GunduLastResultColumn(round = r, faces = f, roundStyle = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun GundataBetHistoryPanel(onDismiss: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var bets by remember { mutableStateOf<List<GundataBetRecord>>(emptyList()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val (list, err) = fetchGundataBetHistory()
        bets = list
        errorMsg = err
        loading = false
                                    }
                                    Box(
                                        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.70f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = Color(0xFF1A1A1A)
        ) {
            Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 10.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .background(Color(0xFF444444), CircleShape)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("My Recent Bets", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Divider(color = Color(0xFF333333), thickness = 1.dp)
                            Spacer(Modifier.height(4.dp))
                when {
                    loading -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OrangePrimary)
                    }
                    errorMsg != null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(errorMsg ?: "", color = Color(0xFFAAAAAA), textAlign = TextAlign.Center, fontSize = 14.sp)
                    }
                    bets.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF555555), modifier = Modifier.size(52.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("No bets placed yet", color = Color(0xFF888888), fontSize = 15.sp)
                        }
                    }
                    else -> LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(bets) { bet -> GundataBetHistoryRow(bet) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GundataBetHistoryRow(bet: GundataBetRecord) {
    val diceEmojis = mapOf(1 to "⚀", 2 to "⚁", 3 to "⚂", 4 to "⚃", 5 to "⚄", 6 to "⚅")
    val statusColor = when (bet.status) {
        "WON"  -> Color(0xFF4CAF50)
        "LOST" -> Color(0xFFE53935)
        "VOID" -> Color(0xFF9E9E9E)
        else   -> OrangePrimary
    }
    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFF252525)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Dice face + number
            Surface(shape = RoundedCornerShape(8.dp), color = OrangePrimary.copy(alpha = 0.15f)) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(diceEmojis[bet.number] ?: "?", fontSize = 20.sp)
                    Text(
                        bet.number.toString(),
                        color = OrangePrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    "₹${bet.chipAmount}  ×  6.0",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatBetDateTime(bet.createdAt),
                    color = Color(0xFF888888),
                    fontSize = 10.sp
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Surface(shape = RoundedCornerShape(6.dp), color = statusColor.copy(alpha = 0.15f)) {
                    Text(
                        bet.status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (bet.status == "WON") "+₹${bet.payoutAmount}" else "₹${bet.chipAmount}",
                    color = if (bet.status == "WON") Color(0xFF4CAF50) else Color(0xFF666666),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

/** Small region pills under the video: selected = orange + top caret; unselected = light grey (reference UI). */
@Composable
private fun GunduRegionTabPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .height(5.dp)
                .width(20.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            if (selected) {
                Canvas(Modifier.size(8.dp, 4.dp)) {
                    val path =
                        Path().apply {
                            moveTo(size.width * 0.5f, 0f)
                            lineTo(0f, size.height)
                            lineTo(size.width, size.height)
                            close()
                        }
                    drawPath(path = path, color = OrangePrimary)
                }
            }
        }
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(6.dp),
            color = if (selected) OrangePrimary else Color(0xFFE1E1E1),
            border = if (selected) null else BorderStroke(0.5.dp, Color(0xFFD0D0D0))
        ) {
            Text(
                label,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                color = if (selected) Color.White else Color(0xFF1A1A1A),
                fontSize = 13.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/** Top bar for Gundata LIVE: centered title + LIVE pill, tile-style back, wallet; white bar + orange accent line. */
@Composable
private fun GundataLiveTopBar(
    walletText: String,
    onBack: () -> Unit,
    onWallet: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color.White,
            shadowElevation = 1.dp
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 8.dp)
            ) {
                Surface(
                    onClick = onBack,
        modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFEFEFEF),
                    border = BorderStroke(1.dp, Color(0xFFE0E0E0))
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = Color(0xFF2C2C2C),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
        ) {
            Text(
                        "Gundata",
                        color = Color(0xFF1C1C1C),
                fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        color = OrangePrimary,
                        shape = RoundedCornerShape(5.dp)
                    ) {
                        Text(
                            "LIVE",
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(start = 4.dp)
                ) {
                    WalletBalanceChip(
                        onClick = onWallet,
                        balanceText = walletText,
                        spacerBetweenIconAndText = 6.dp
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(OrangePrimary)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GundataLiveScreen(
    onBack: () -> Unit,
    onWallet: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    // Per face (1–6): total ₹ stacked before PLACE BET; each tap adds the selected chip value.
    var gunduStakes by remember { mutableStateOf(mapOf<Int, Int>()) }
    /** LIFO: each entry is one tap (face, chip value added) so refresh can remove the last add only. */
    var gunduBetTapStack by remember { mutableStateOf(listOf<Pair<Int, Int>>()) }
    /** Only the most recently tapped face (1–6) gets the orange “selected” border; amounts on other faces are unchanged. */
    var gunduFocusedFace by remember { mutableStateOf<Int?>(null) }
    var showGundataBetHistory by remember { mutableStateOf(false) }
    var showGundataRecentRounds by remember { mutableStateOf(false) }
    var walletText by remember { mutableStateOf("₹0") }
    var regionTab by remember { mutableStateOf(0) }
    var selectedChip by remember { mutableStateOf(100) }
    var gunduVideoFullscreen by remember { mutableStateOf(false) }
    /** In-app [WebView] for Virtual tab (not external browser). */
    var gunduVirtualWebVisible by remember { mutableStateOf(false) }
    // Width/height of decoded video; black frame uses this so it matches the picture (replaces fixed 272dp).
    var gunduVideoAspect by remember { mutableStateOf(16f / 9f) }
    var placing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val chipOptions = listOf(100, 200, 300, 500, 800, 1000)
    val chipRowScroll = rememberScrollState()
    val bodyScroll = rememberScrollState()
    val resultsStripScroll = rememberScrollState()
    // Demo: last 20 rounds (round id → six face values, top to bottom). Replace with API later.
    val resultStrip = remember {
        (1..20).map { r -> r to (1..6).toList() }
    }

    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        walletText = formatRupeeBalanceForDisplay(w?.balance)
    }
    // Fullscreen video = landscape; main Gundu UI = portrait. Reset on leave screen.
    LaunchedEffect(gunduVideoFullscreen) {
        activity?.requestedOrientation =
            if (gunduVideoFullscreen) {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
    }

    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.gunduata_live}")
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_ALL
            volume = 1f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(exoPlayer) {
        val listener =
            object : androidx.media3.common.Player.Listener {
                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    val w = videoSize.width
                    val h = videoSize.height
                    if (w > 0 && h > 0) {
                        gunduVideoAspect = w.toFloat() / h.toFloat()
                    }
                }
            }
        exoPlayer.addListener(listener)
        val vs = exoPlayer.videoSize
        if (vs.width > 0 && vs.height > 0) {
            gunduVideoAspect = vs.width.toFloat() / vs.height.toFloat()
        }
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    BackHandler(enabled = !gunduVirtualWebVisible) {
        when {
            gunduVideoFullscreen -> gunduVideoFullscreen = false
            showGundataRecentRounds -> showGundataRecentRounds = false
            showGundataBetHistory -> showGundataBetHistory = false
            else -> onBack()
        }
    }

    val diceItems = remember {
        listOf(
            Triple(1, "One", "⚀"),
            Triple(2, "Two", "⚁"),
            Triple(3, "Three", "⚂"),
            Triple(4, "Four", "⚃"),
            Triple(5, "Five", "⚄"),
            Triple(6, "Six", "⚅")
        )
    }

    val gunduHasStakes = gunduStakes.values.any { it > 0 }
    val canPlace = gunduHasStakes && !placing
    val pageTopBg = Color(0xFFFAFAFA)
    val marbleBg = Brush.verticalGradient(
        colors = listOf(Color(0xFFF2F2F2), Color(0xFFE6E6E6), Color(0xFFEDEDED))
    )

    Box(Modifier.fillMaxSize().background(pageTopBg)) {
        Column(Modifier.fillMaxSize()) {
            GundataLiveTopBar(
                walletText = walletText,
                onBack = onBack,
                onWallet = onWallet
            )

            // Scrolling first-deposit promo (above video)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFF8E1),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE0B2))
            ) {
                Text(
                    text = "  Deposit ₹500 and get ₹500 bonus on your first deposit!  " +
                        "Your first-deposit bonus — limited time.  " +
                        "Deposit ₹500 and get ₹500 bonus on your first deposit!  ",
            modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp, horizontal = 0.dp)
                        .basicMarquee(
                            initialDelayMillis = 800,
                            delayMillis = 30
                        ),
                    color = Color(0xFF8B4513),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }

            // Black dashboard: video + Live badge, or black placeholder when maximized; maximize opens fullscreen player
            if (!gunduVideoFullscreen) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(gunduVideoAspect)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = {
                            androidx.media3.ui.PlayerView(it).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        onRelease = { (it as? androidx.media3.ui.PlayerView)?.player = null },
                        update = { (it as? androidx.media3.ui.PlayerView)?.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    Row(
                        Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                            .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(6.dp)
                                .background(Color(0xFFFF3B30), CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Live", color = Color(0xFFFF3B30), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(
                        onClick = { gunduVideoFullscreen = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Surface(shape = CircleShape, color = Color.White, shadowElevation = 2.dp) {
                            Icon(
                                Icons.Default.Fullscreen,
                                contentDescription = "Fullscreen",
                                tint = Color.Black,
                                modifier = Modifier.padding(8.dp).size(22.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(gunduVideoAspect)
                        .background(Color.Black)
                ) {
                    Text(
                        "Playing fullscreen",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }

            // Live / Virtual pills (flush under video — no top gap)
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(pageTopBg)
                    .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Live" to 0, "Virtual" to 1).forEachIndexed { i, (label, idx) ->
                    if (i > 0) Spacer(Modifier.width(6.dp))
                    GunduRegionTabPill(
                        label = label,
                        selected = regionTab == idx,
                        onClick = {
                            regionTab = idx
                            if (idx == 1) {
                                gunduVirtualWebVisible = true
                            }
                        }
                    )
                }
            }

            // Marbled area: grid + chips + action bar, then last-round results below
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(marbleBg)
                    .verticalScroll(bodyScroll)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                for (row in 0..1) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (col in 0..2) {
                            val idx = row * 3 + col
                            val (num, word, emoji) = diceItems[idx]
                            val stakeOnBox = gunduStakes[num] ?: 0
                            val chipShow = if (stakeOnBox > 0) stakeOnBox else null
                            GundataDiceCard(
                                num = num,
                                word = word,
                                emoji = emoji,
                                selected = gunduFocusedFace == num,
                                chipOnFace = chipShow,
                                onClick = {
                                    gunduFocusedFace = num
                                    val add = selectedChip
                                    val next = stakeOnBox + add
                                    gunduStakes = gunduStakes + (num to next)
                                    gunduBetTapStack = gunduBetTapStack + (num to add)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    if (row == 0) Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipRowScroll),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    chipOptions.forEach { c ->
                        val sel = selectedChip == c
            Surface(
                            onClick = { selectedChip = c },
                shape = CircleShape,
                            color = if (sel) OrangePrimary else Color(0xFF3A3A3A),
                            border = if (sel) BorderStroke(2.dp, Color.White.copy(alpha = 0.5f)) else BorderStroke(1.dp, Color(0xFF2A2A2A))
                        ) {
                            Text(
                                c.toString(),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                                color = if (sel) Color.White else Color(0xFFBDBDBD),
                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                // Bet history / undo last tap (refresh) / PLACE BET / wallet (above last-round results)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFEFEFEF), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    GundataSquareIconButton(
                        icon = Icons.Default.Assignment,
                        onClick = {
                            showGundataRecentRounds = false
                            showGundataBetHistory = true
                        }
                    )
                    val gunduBetHasSelection = gunduHasStakes
                    val canUndoLastTap = gunduBetTapStack.isNotEmpty() && !placing
                    GundataSquareIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = {
                            if (gunduBetTapStack.isEmpty() || placing) return@GundataSquareIconButton
                            val (face, amt) = gunduBetTapStack.last()
                            gunduBetTapStack = gunduBetTapStack.dropLast(1)
                            val cur = gunduStakes[face] ?: 0
                            val newVal = (cur - amt).coerceAtLeast(0)
                            gunduStakes =
                                if (newVal <= 0) gunduStakes - face else gunduStakes + (face to newVal)
                            gunduFocusedFace = gunduBetTapStack.lastOrNull()?.first
                        },
                        enabled = canUndoLastTap,
                        contentDescription = "Undo last bet"
                    )
                    Button(
                        onClick = {
                            if (placing) return@Button
                            val lineItems =
                                (1..6)
                                    .mapNotNull { n -> gunduStakes[n]?.let { a -> if (a > 0) n to a else null } }
                            if (lineItems.isEmpty()) return@Button
                            scope.launch {
                                placing = true
                                var remaining = gunduStakes.toMap()
                                var lastGoodBalance: String? = null
                                var failedErr: String? = null
                                for ((n, amount) in lineItems.sortedBy { it.first }) {
                                    val (result, err) = postGundataBet(n, amount)
                                    if (result != null) {
                                        lastGoodBalance = result.walletBalance
                                        remaining = remaining - n
                                    } else {
                                        failedErr = err
                                        break
                                    }
                                }
                                gunduStakes = remaining
                                gunduBetTapStack = emptyList()
                                if (remaining.isEmpty() || remaining.values.none { it > 0 }) {
                                    gunduFocusedFace = null
                                }
                                placing = false
                                when {
                                    failedErr != null -> {
                                        lastGoodBalance?.let {
                                            walletText = formatRupeeBalanceForDisplay(it.toBigDecimalOrNull()?.toString())
                                        }
                                        Toast.makeText(context, failedErr, Toast.LENGTH_LONG).show()
                                    }
                                    else -> {
                                        lastGoodBalance?.let {
                                            walletText = formatRupeeBalanceForDisplay(it.toBigDecimalOrNull()?.toString())
                                        }
                                        Toast.makeText(
                                            context,
                                            if (lineItems.size == 1) "Bet placed" else "Bets placed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            }
                        },
                        enabled = canPlace,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = OrangePrimary,
                            contentColor = Color.White,
                            disabledContainerColor = if (gunduBetHasSelection) OrangePrimary else Color(0xFFCFCFCF),
                            disabledContentColor = if (gunduBetHasSelection) Color.White else Color(0xFF8A8A8A)
                        )
                    ) {
                        if (placing) {
                            CircularProgressIndicator(
                                Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                "PLACE BET",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (gunduBetHasSelection) Color.White else Color(0xFF8A8A8A)
                            )
                        }
                    }
                    GundataSquareIconButton(icon = Icons.Default.AccountBalanceWallet, onClick = onWallet)
                }
                // Last round results — below the action bar
                Spacer(Modifier.height(20.dp))
                Text(
                    "Last round results",
                    color = Color(0xFF666666),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                // Padding inside the card; large spacedBetween columns — scrolls horizontally for 20 rounds
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp))
                        .border(0.5.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
                        .padding(vertical = 18.dp, horizontal = 16.dp)
                        .horizontalScroll(resultsStripScroll)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.CenterVertically) {
                        resultStrip.forEach { (r, f) ->
                            GunduLastResultColumn(round = r, faces = f, roundStyle = 0)
                        }
                    }
                }
            }
        }

        if (gunduVideoFullscreen) {
            Dialog(
                onDismissRequest = { gunduVideoFullscreen = false },
                properties =
                    DialogProperties(
                        usePlatformDefaultWidth = false,
                        decorFitsSystemWindows = false,
                        dismissOnBackPress = true
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = {
                            androidx.media3.ui.PlayerView(it).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        onRelease = { (it as? androidx.media3.ui.PlayerView)?.player = null },
                        update = { (it as? androidx.media3.ui.PlayerView)?.player = exoPlayer },
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { gunduVideoFullscreen = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
            ) {
                Icon(
                            Icons.Default.Close,
                            contentDescription = "Exit fullscreen",
                    tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
        if (showGundataRecentRounds) {
            GundataRecentRoundsPanel(
                onDismiss = { showGundataRecentRounds = false },
                resultStrip = resultStrip
            )
        }
        if (showGundataBetHistory) {
            GundataBetHistoryPanel(onDismiss = { showGundataBetHistory = false })
        }
        if (gunduVirtualWebVisible) {
            val webLoad = remember(gunduVirtualWebVisible) { gunduataVirtualWebLoad() }
            GunduataVirtualWebOverlay(
                load = webLoad,
                onClose = { gunduVirtualWebVisible = false }
            )
        }
    }
}

/** Classic 1–6 pip layout on a 3×3 grid (dice like the reference). */
@Composable
private fun GundataDicePipFace(
    value: Int,
    modifier: Modifier = Modifier
) {
    // Light grey pips; diameter kept bold on the off-white face.
    val pip = Color(0xFFC2C2C2)
    val pipDiameter = 9.dp
    val pips9: BooleanArray = when (value.coerceIn(1, 6)) {
        1 -> booleanArrayOf(false, false, false, false, true, false, false, false, false)
        2 -> booleanArrayOf(true, false, false, false, false, false, false, false, true)
        3 -> booleanArrayOf(true, false, false, false, true, false, false, false, true)
        4 -> booleanArrayOf(true, false, true, false, false, false, true, false, true)
        5 -> booleanArrayOf(true, false, true, false, true, false, true, false, true)
        else -> booleanArrayOf(true, false, true, true, false, true, true, false, true) // 6
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(Color(0xFFF7F7F7), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(8.dp))
            .padding(2.5.dp)
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            for (r in 0..2) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (c in 0..2) {
                        val on = pips9[r * 3 + c]
                        Box(
                            Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (on) {
                                Box(
                                    Modifier
                                        .size(pipDiameter)
                                        .background(pip, CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GundataDiceCard(
    num: Int,
    word: String,
    @Suppress("UNUSED_PARAMETER") emoji: String,
    selected: Boolean,
    chipOnFace: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(132.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = Color.White,
        border = BorderStroke(
            width = if (selected) 2.5.dp else 1.dp,
            color = if (selected) OrangePrimary else Color(0xFFDDDDDD)
        )
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                num.toString(),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color(0xFF9E9E9E)
            )
            Box(
                Modifier
                    .height(56.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (chipOnFace != null) {
                    // Stake shown: no pip dots — only the amount.
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF5F5F5),
                            border = BorderStroke(1.dp, Color(0xFFE8E8E8)),
                            shadowElevation = 1.dp
                        ) {
                            val stakeFont =
                                when {
                                    chipOnFace >= 10_000 -> 10.sp
                                    chipOnFace >= 1_000 -> 12.sp
                                    chipOnFace >= 100 -> 14.sp
                                    else -> 16.sp
                                }
                            Text(
                                chipOnFace.toString(),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = stakeFont,
                                color = Color(0xFF1A1A1A),
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    GundataDicePipFace(
                        value = num,
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(1f)
                    )
                }
            }
            Text(
                word,
                fontSize = 11.sp,
                color = Color(0xFF333333),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun GundataSquareIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    enabled: Boolean = true,
    contentDescription: String? = null
) {
    val a = if (enabled) 1f else 0.4f
    Surface(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    enabled = enabled,
                    role = androidx.compose.ui.semantics.Role.Button,
                    onClick = onClick
                ),
        shape = RoundedCornerShape(8.dp),
        color = if (enabled) Color(0xFFF0F0F0) else Color(0xFFE8E8E8),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = if (enabled) 0.5f else 0.35f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = contentDescription,
                tint = Color.Black.copy(alpha = a),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    val title = stringResource(R.string.splash_game_name)
    var animateIn by remember { mutableStateOf(false) }
    val glow = rememberInfiniteTransition(label = "splash_glow")
    val glowAlpha by glow.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    val textAlpha by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "splash_fade"
    )
    val textScale by animateFloatAsState(
        targetValue = if (animateIn) 1f else 0.86f,
        animationSpec = tween(950, easing = FastOutSlowInEasing),
        label = "splash_scale"
    )

    LaunchedEffect(Unit) {
        animateIn = true
        delay(2700)
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141414), Color.Black, Color(0xFF0D0D0D))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(320.dp)
                .alpha(glowAlpha)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            OrangePrimary.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )
        Text(
            text = title,
            modifier = Modifier
                .scale(textScale)
                .alpha(textAlpha),
            fontSize = 40.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            letterSpacing = 5.sp,
            textAlign = TextAlign.Center,
            style = TextStyle(
                shadow = Shadow(
                    color = OrangePrimary.copy(alpha = 0.75f),
                    offset = Offset(0f, 5f),
                    blurRadius = 28f
                )
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onSignUp: () -> Unit = {}
) {
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        disabledTextColor = Color.Black,
        focusedBorderColor = Color.LightGray,
        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.7f),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = Color.Black
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconItem("Promotions", imageRes = R.mipmap.ic_launcher)
            HeaderIconItem("Cockfight", imageRes = R.drawable.category_cockfight)
            HeaderIconItem("Dice Play", imageRes = R.drawable.category_gunduata)
            HeaderIconItem("Cricket", imageRes = R.drawable.category_cricket)
        }
        Spacer(modifier = Modifier.height(40.dp))
        // Same artwork as launcher icon ([R.mipmap.ic_launcher]), circular frame
        DrawableImage(
            R.mipmap.ic_launcher,
            contentDescription = "Hen Fight",
            modifier = Modifier
                .size(150.dp)
                .clip(CircleShape)
                .border(2.dp, Color.Black, CircleShape),
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text("Login", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text(
            "Enter your phone number or username and password",
            fontSize = 13.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 6.dp),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Phone number or Username", color = Color.Gray) },
            placeholder = { Text("e.g. 9876543210 or john123", color = Color.LightGray, fontSize = 14.sp) },
            colors = fieldColors,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Password", color = Color.Gray) },
            colors = fieldColors,
            shape = RoundedCornerShape(12.dp),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                        tint = Color.DarkGray
                    )
                }
            }
        )

        Spacer(modifier = Modifier.height(22.dp))

        if (errorMessage != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFEBEE),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = errorMessage!!,
                    color = Color(0xFFB71C1C),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable(enabled = !isLoading) {
                    if (phone.isBlank()) {
                        errorMessage = "Please enter your phone number."
                        return@clickable
                    }
                    if (password.isBlank()) {
                        errorMessage = "Please enter your password."
                        return@clickable
                    }
                    errorMessage = null
                    isLoading = true
                    scope.launch {
                        val result = postAuthLogin(context, phone, password)
                        isLoading = false
                        if (result.success) {
                            onLoginSuccess()
                        } else {
                            errorMessage = result.errorMsg
                        }
                    }
                },
            shape = RoundedCornerShape(26.dp),
            color = if (isLoading) Color.Gray else Color.Black
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Login", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account? ", fontSize = 14.sp, color = Color.Gray)
            Text(
                "Sign up",
                fontSize = 14.sp,
                color = OrangePrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onSignUp() }
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(onBack: () -> Unit, onRegistered: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var otpSendCount by remember { mutableStateOf(0) }
    var passwordVisible by remember { mutableStateOf(false) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.Black,
        unfocusedTextColor = Color.Black,
        disabledTextColor = Color.Black,
        focusedBorderColor = Color.LightGray,
        unfocusedBorderColor = Color.LightGray.copy(alpha = 0.7f),
        focusedContainerColor = Color.White,
        unfocusedContainerColor = Color.White,
        cursorColor = Color.Black
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "Sign up",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.weight(1f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DrawableImage(
                R.mipmap.ic_launcher,
                contentDescription = "Hen Fight",
                modifier = Modifier
                    .size(150.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Black, CircleShape),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Create your account",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                "Username, mobile, password & OTP",
                fontSize = 13.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 6.dp),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = { username = it.take(32) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Username", color = Color.Gray) },
                placeholder = { Text("Choose a username", color = Color.LightGray, fontSize = 14.sp) },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it.filter { ch -> ch.isDigit() }.take(15) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Phone number", color = Color.Gray) },
                placeholder = { Text("10-digit mobile number", color = Color.LightGray, fontSize = 14.sp) },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Password", color = Color.Gray) },
                colors = fieldColors,
                shape = RoundedCornerShape(12.dp),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.DarkGray
                        )
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.filter { ch -> ch.isDigit() }.take(6) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    label = { Text("OTP code", color = Color.Gray) },
                    placeholder = { Text("6-digit code", color = Color.LightGray, fontSize = 14.sp) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                TextButton(
                    onClick = {
                        val firstRequest = otpSendCount == 0
                        otpSendCount++
                        Toast.makeText(
                            context,
                            if (firstRequest) "Verification code sent (demo)" else "Code resent (demo)",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.padding(top = 6.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = if (otpSendCount == 0) "Get code" else "Resend",
                        color = OrangePrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clickable { onRegistered() },
                shape = RoundedCornerShape(26.dp),
                color = OrangePrimary
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text("Create account", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Already have an account? ", fontSize = 14.sp, color = Color.Gray)
                Text(
                    "Log in",
                    fontSize = 14.sp,
                    color = OrangePrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onBack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailsScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    /** "Male" | "Female" | "Other", or null if API gender unset. */
    var gender by remember { mutableStateOf<String?>(null) }
    var profileLoading by remember { mutableStateOf(true) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profileLoading = true
        profileError = null
        val (data, err) = fetchAuthProfile()
        profileLoading = false
        if (data != null) {
            username = data.username
            phone = data.phoneNumber
            email = data.email
            gender = profileGenderApiToUi(data.gender)
        } else {
            profileError = err
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text("Profile", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Surface(
                onClick = onHome,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = OrangePrimary
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            if (profileLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = OrangePrimary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Loading profile…", fontSize = 14.sp, color = Color.Gray)
                }
            }
            profileError?.let { err ->
                Text(
                    err,
                    color = Color(0xFFC62828),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !profileLoading,
                label = { Text("Username", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    disabledTextColor = Color.Black,
                    focusedBorderColor = Color.LightGray,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.7f),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !profileLoading,
                label = { Text("Phone Number", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    disabledTextColor = Color.Black,
                    focusedBorderColor = Color.LightGray,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.7f),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !profileLoading,
                label = { Text("Email", color = Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    disabledTextColor = Color.Black,
                    focusedBorderColor = Color.LightGray,
                    unfocusedBorderColor = Color.LightGray.copy(alpha = 0.7f),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    cursorColor = Color.Black
                ),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
            )
            Spacer(Modifier.height(24.dp))
            Text("Gender", color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !profileLoading) { gender = "Male" }
                ) {
                    RadioButton(
                        selected = gender == "Male",
                        onClick = { gender = "Male" },
                        enabled = !profileLoading,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OrangePrimary,
                            unselectedColor = OrangePrimary
                        )
                    )
                    Text("Male", color = Color.Black, fontSize = 16.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !profileLoading) { gender = "Female" }
                ) {
                    RadioButton(
                        selected = gender == "Female",
                        onClick = { gender = "Female" },
                        enabled = !profileLoading,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OrangePrimary,
                            unselectedColor = OrangePrimary
                        )
                    )
                    Text("Female", color = Color.Black, fontSize = 16.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(enabled = !profileLoading) { gender = "Other" }
                ) {
                    RadioButton(
                        selected = gender == "Other",
                        onClick = { gender = "Other" },
                        enabled = !profileLoading,
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OrangePrimary,
                            unselectedColor = OrangePrimary
                        )
                    )
                    Text("Other", color = Color.Black, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        Button(
            onClick = {
                scope.launch {
                    saving = true
                    val (ok, msg) = postAuthProfile(username, phone, email, gender)
                    saving = false
                    if (ok) {
                        profileLoading = true
                        profileError = null
                        val (data, err) = fetchAuthProfile()
                        profileLoading = false
                        if (data != null) {
                            username = data.username
                            phone = data.phoneNumber
                            email = data.email
                            gender = profileGenderApiToUi(data.gender)
                        } else {
                            profileError = err
                        }
                    }
                    Toast.makeText(
                        context,
                        if (ok) "Profile updated" else (msg ?: "Could not update profile"),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            enabled = !profileLoading && !saving,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Update Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var data by remember { mutableStateOf<ReferralDataApi?>(null) }
    LaunchedEffect(Unit) {
        loading = true
        err = null
        val (d, e) = fetchAuthReferralData()
        data = d
        err = e
        loading = false
    }
    val ref = data
    val errText = err
    val pctLabel = ref?.let { formatReferralCommissionPctForUi(it.commissionRatePercent) } ?: "—"
    val headline =
        when {
            ref != null && pctLabel != "—" -> "Receive $pctLabel% Commission."
            ref != null -> "Receive —% Commission."
            errText != null && !loading -> "Referral"
            else -> "Receive —% Commission."
        }
    val codeDisplay =
        when {
            ref != null && ref.referralCode.isNotBlank() -> spacedReferralCode(ref.referralCode)
            loading && ref == null -> "…"
            else -> "—"
        }
    val codeCopy = ref?.referralCode?.trim().orEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text("Referral", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Surface(
                onClick = onHome,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = OrangePrimary
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFF0E8)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (loading && ref == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = OrangePrimary,
                        strokeWidth = 3.dp
                    )
                    Spacer(Modifier.height(16.dp))
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(44.dp)
                        )
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    headline,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "For each bet your referred friend wins, you earn a reward making every one of their victories a win for you too!",
                    fontSize = 14.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                if (errText != null && ref == null && !loading) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        errText,
                        fontSize = 13.sp,
                        color = Color(0xFFC62828),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
                if (ref != null) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReferralStatCell(
                                value = "${ref.totalReferrals}",
                                label = "Total referrals",
                                modifier = Modifier.weight(1f)
                            )
                            ReferralStatCell(
                                value = "${ref.activeReferrals}",
                                label = "Active referrals",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.totalEarnings),
                                label = "Total earnings",
                                modifier = Modifier.weight(1f)
                            )
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.commissionEarnedToday),
                                label = "Today's commission",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.instantReferralBonusPerReferee.toString()),
                                label = "Bonus per referee (instant)",
                                modifier = Modifier.weight(1f)
                            )
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.totalCommissionEarnings),
                                label = "Total commission earnings",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.totalDailyCommissionEarnings),
                                label = "Total daily commission",
                                modifier = Modifier.weight(1f)
                            )
                            ReferralStatCell(
                                value = rupeeLabelForReferral(ref.totalLegacyReferralBonusEarnings),
                                label = "Legacy referral bonus",
                                modifier = Modifier.weight(1f)
                            )
                        }
                        ref.commissionTodayIst?.takeIf { it.isNotBlank() }?.let { ist ->
                            Text(
                                "Today's commission date (IST): $ist",
                                fontSize = 12.sp,
                                color = Color.DarkGray,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = codeDisplay,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        letterSpacing = 2.sp
                    )
                    IconButton(
                        onClick = { if (codeCopy.isNotEmpty()) clipboard.setText(AnnotatedString(codeCopy)) },
                        enabled = codeCopy.isNotEmpty()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            onClick = {
                val code = ref?.referralCode?.trim().orEmpty()
                val text =
                    if (code.isNotEmpty()) {
                        "Join me on Kokoroko! Code: $code"
                    } else {
                        "Join me on Kokoroko!"
                    }
                val send =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                ctx.startActivity(Intent.createChooser(send, "Share"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Share & Earn Lifetime",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = Color.Black
                )
                Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
            }
        }

        if (ref != null && ref.commissionSlabs.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Commission slabs", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Min refs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "Max refs",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    ref.commissionSlabs.forEachIndexed { idx, slab ->
                        if (idx > 0) Divider(color = Color(0xFFEEEEEE))
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${slab.minReferrals}", fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(
                                slab.maxReferrals?.toString() ?: "∞",
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "${formatReferralCommissionPctForUi(slab.commissionPercent)}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        if (ref != null && ref.recentDailyCommissions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color.LightGray)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Recent daily commissions", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Spacer(Modifier.height(10.dp))
                    ref.recentDailyCommissions.take(50).forEachIndexed { idx, row ->
                        if (idx > 0) Divider(color = Color(0xFFEEEEEE))
                        Column(Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "${row.commissionDate} · ${row.refereeUsername}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Loss ${rupeeLabelForReferral(row.lossAmountDisplay)}",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    "${formatReferralCommissionPctForUi(row.commissionPercent)}%",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                                Text(
                                    rupeeLabelForReferral(row.commissionAmountDisplay),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }
            }
        }

        if (ref != null && ref.referrals.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text(
                "Your referrals",
                modifier = Modifier.padding(horizontal = 16.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = Color.Black
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                val rows = ref.referrals.take(50)
                rows.forEachIndexed { index, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                row.username,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            row.dateJoined?.takeIf { it.isNotBlank() }?.let { dj ->
                                val short =
                                    if (dj.contains("T")) dj.substringBefore("T") else dj.take(10).ifBlank { dj.take(16) }
                                Text(
                                    "Joined $short",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                        }
                        if (row.hasDeposit) {
                            Surface(
                                color = Color(0xFFE8F5E9),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Deposited",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            }
                        }
                    }
                    if (index < rows.lastIndex) {
                        Divider(color = Color(0xFFEEEEEE))
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "How does it work?",
            modifier = Modifier.padding(horizontal = 16.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color.Black
        )
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color.LightGray)
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color(0xFFFFC107), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Surface(
                        color = Color(0xFF7B1FA2),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "REFER A FRIEND",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5))
                        .padding(14.dp)
                ) {
                    Text(
                        "How referral works and how to earn?",
                        fontSize = 13.sp,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ReferralStatCell(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color.White.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, Color(0x0F000000))
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF666666))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResetPinScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    var pin by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text("RE-SET PIN", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Surface(
                onClick = onHome,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                color = OrangePrimary
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Home, contentDescription = "Home", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(16.dp))
            Text("Set New Login PIN", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(
                "Protect from children & other people!",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(36.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Surface(
                        modifier = Modifier.size(60.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (pin.length > index) {
                                Box(Modifier.size(12.dp).clip(CircleShape).background(Color.Black))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
            val digitRows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                digitRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { d ->
                            Surface(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clickable {
                                        if (pin.length < 4) pin += d
                                    },
                                shape = CircleShape,
                                color = Color(0xFFF5F5F5),
                                border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text(d, fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.size(56.dp))
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                if (pin.length < 4) pin += "0"
                            },
                        shape = CircleShape,
                        color = Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text("0", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                if (pin.isNotEmpty()) pin = pin.dropLast(1)
                            },
                        shape = CircleShape,
                        color = Color(0xFFF5F5F5),
                        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.6f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = Color.Black)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        Button(
            onClick = { if (pin.length == 4) onBack() },
            enabled = pin.length == 4,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black,
                disabledContainerColor = Color.Gray.copy(alpha = 0.5f)
            )
        ) {
            Text("Reset pin", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

private enum class TxRecordKind { Deposit, Withdraw }

private enum class TxStatusFilter { All, Success, Failed }

private data class DepositRecordApi(
    val id: Int,
    val amount: Int,
    val status: String,
    val screenshotUrl: String?,
    val paymentMethod: Int?,
    val adminNote: String?,
    val createdAt: String,
    val updatedAt: String
)

private data class WithdrawRecordApi(
    val id: Int,
    /** Normalized for display after ₹ (handles API string decimals e.g. "500.00"). */
    val amount: String,
    val status: String,
    val withdrawalMethod: String,
    val withdrawalDetails: String,
    val adminNote: String?,
    val processedByName: String?,
    val createdAt: String,
    val updatedAt: String
)

/**
 * Extracts the array of transaction rows from JSON envelopes.
 * Handles: plain array, DRF `results`, `data`, `deposits`, `withdrawals`,
 * and the split-by-status format {"successful":[…],"rejected":[…]} used by this API.
 */
private fun extractTransactionListArray(text: String): JSONArray {
    val t = text.trim()
    if (t.isEmpty()) return JSONArray()
    return try {
        // plain array
        if (t.startsWith("[")) return JSONArray(t)

                val root = JSONObject(t)

        // flat top-level array keys
        for (key in listOf("results", "data", "deposits", "withdrawals", "withdraws",
                            "items", "records", "list")) {
            root.optJSONArray(key)?.let { return it }
        }

        // nested under "data" object
        root.optJSONObject("data")?.let { d ->
            for (key in listOf("deposits", "withdrawals", "withdraws", "results",
                                "items", "records", "list", "data")) {
                d.optJSONArray(key)?.let { return it }
            }
        }

        // nested under "payload" object
        root.optJSONObject("payload")?.let { p ->
            for (key in listOf("results", "data")) {
                p.optJSONArray(key)?.let { return it }
            }
        }

        // split-by-status envelope: {"successful":[…],"rejected":[…],"pending":[…],…}
        val statusKeys = listOf(
            "successful", "rejected", "pending", "approved",
            "completed", "failed", "cancelled", "processing", "all"
        )
        val merged = JSONArray()
        var found = false
        for (key in statusKeys) {
            val arr = root.optJSONArray(key) ?: continue
            found = true
            for (i in 0 until arr.length()) merged.put(arr.get(i))
        }
        if (found) return merged

        JSONArray()
    } catch (_: Exception) {
        JSONArray()
    }
}

private fun parseDepositsResponse(text: String): List<DepositRecordApi> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    return try {
        val arr = extractTransactionListArray(t)
    val out = ArrayList<DepositRecordApi>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(parseDepositRecord(o))
    }
        out
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseDepositRecord(o: JSONObject): DepositRecordApi =
    DepositRecordApi(
        id = o.optIntValue("id", -1),
        amount = o.optRupeeAmountInt("amount", 0),
        status = o.optString("status", "").trim(),
        screenshotUrl = o.pickString("screenshot_url", "screenshot", "proof_url")?.trim()?.takeIf { it.isNotBlank() },
        paymentMethod =
            if (o.has("payment_method") && !o.isNull("payment_method")) {
                o.optIntValue("payment_method")
            } else {
                null
            },
        adminNote = o.optString("admin_note", "").trim().takeIf { it.isNotBlank() },
        createdAt = o.optString("created_at", "").trim(),
        updatedAt = o.optString("updated_at", "").trim()
    )

private fun parseWithdrawalsResponse(text: String): List<WithdrawRecordApi> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    return try {
        val arr = extractTransactionListArray(t)
    val out = ArrayList<WithdrawRecordApi>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(parseWithdrawRecord(o))
    }
        out
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseWithdrawRecord(o: JSONObject): WithdrawRecordApi =
    WithdrawRecordApi(
        id = o.optIntValue("id", -1),
        amount = o.optRupeeWithdrawAmount("amount"),
        status = o.optString("status", "").trim(),
        withdrawalMethod = o.optString("withdrawal_method", "").trim(),
        withdrawalDetails = o.optString("withdrawal_details", "").trim(),
        adminNote = o.optString("admin_note", "").trim().takeIf { it.isNotBlank() },
        processedByName = o.pickString("processed_by_name", "processed_by")?.trim()?.takeIf { it.isNotBlank() },
        createdAt = o.optString("created_at", "").trim(),
        updatedAt = o.optString("updated_at", "").trim()
    )

private suspend fun fetchMyWithdrawals(): Pair<List<WithdrawRecordApi>, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(emptyList(), "Sign in to view withdrawals.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(
                emptyList(),
                "Offline demo login cannot load withdrawal history. Sign in with your real account."
            )
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_WITHDRAWS_MINE_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> {
                        notifySessionExpired()
                        Pair(emptyList(), "Session expired. Please sign in again.")
                    }
                    !resp.isSuccessful ->
                        Pair(emptyList(), "Could not load withdrawals (${resp.code}).")
                    else -> Pair(parseWithdrawalsResponse(text), null)
                }
            }
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: "Network error")
        }
    }

private suspend fun fetchMyDeposits(): Pair<List<DepositRecordApi>, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(emptyList(), "Sign in to view deposits.")
        if (AuthTokenStore.isLocalDemoSession()) {
            return@withContext Pair(
                emptyList(),
                "Offline demo login cannot load deposit history. Sign in with your real account."
            )
        }
        try {
            val req =
                Request.Builder()
                    .url(AUTH_DEPOSITS_MINE_URL)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                when {
                    resp.code == 401 -> {
                        notifySessionExpired()
                        Pair(emptyList(), "Session expired. Please sign in again.")
                    }
                    !resp.isSuccessful ->
                        Pair(emptyList(), "Could not load deposits (${resp.code}).")
                    else -> Pair(parseDepositsResponse(text), null)
                }
            }
        } catch (e: Exception) {
            Pair(emptyList(), e.message ?: "Network error")
        }
    }

/** Deposit / withdraw list: Success vs Failed vs All (pending only in All). */
private fun matchesTxStatusFilter(status: String, filter: TxStatusFilter): Boolean {
    if (filter == TxStatusFilter.All) return true
    val s = status.trim().uppercase(Locale.US)
    val isSuccess =
        s in
            listOf(
                "SUCCESS",
                "COMPLETED",
                "COMPLETE",
                "APPROVED",
                "SUCCESSFUL",
                "DONE",
                "CONFIRMED",
                "PAID",
                "PROCESSED"
            )
    val isFailed =
        s in
            listOf(
                "FAILED",
                "FAILURE",
                "REJECTED",
                "CANCELLED",
                "CANCELED",
                "DECLINED"
            )
    return when (filter) {
        TxStatusFilter.Success -> isSuccess
        TxStatusFilter.Failed -> isFailed
        TxStatusFilter.All -> true
    }
}

private fun formatIsoDateTimeShort(iso: String): String {
    if (iso.isBlank()) return "—"
    return try {
        val clean = iso.replace("Z", "").substringBefore(".").trim()
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        parser.timeZone = TimeZone.getTimeZone("UTC")
        val parsed = parser.parse(clean) ?: return iso
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(parsed)
    } catch (_: Exception) {
        iso
    }
}

@Composable
private fun DepositRecordRow(record: DepositRecordApi) {
    val context = LocalContext.current
    val statusColor =
        when (record.status.trim().uppercase(Locale.US)) {
            in listOf("SUCCESS", "COMPLETED", "APPROVED", "SUCCESSFUL", "DONE", "CONFIRMED", "COMPLETE") ->
                Color(0xFF2E7D32)
            in listOf("FAILED", "REJECTED", "CANCELLED", "DECLINED") -> Color(0xFFC62828)
            else -> Color(0xFFF57C00)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "₹${record.amount}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    record.status.ifBlank { "—" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatIsoDateTimeShort(record.createdAt),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
            record.paymentMethod?.let { pm ->
                Text(
                    "Payment method #$pm",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            record.screenshotUrl?.takeIf { it.isNotBlank() }?.let { url ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "View payment screenshot",
                    fontSize = 13.sp,
                    color = OrangePrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable {
                            if (!context.tryOpenExternalUrl(url)) {
                                Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(top = 2.dp)
                )
            }
            record.adminNote?.let { note ->
                Text(
                    "Note: $note",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun WithdrawRecordRow(record: WithdrawRecordApi) {
    val statusColor =
        when (record.status.trim().uppercase(Locale.US)) {
            in listOf("SUCCESS", "COMPLETED", "APPROVED", "SUCCESSFUL", "DONE", "CONFIRMED", "COMPLETE", "PAID", "PROCESSED") ->
                Color(0xFF2E7D32)
            in listOf("FAILED", "REJECTED", "CANCELLED", "DECLINED") -> Color(0xFFC62828)
            else -> Color(0xFFF57C00)
        }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "₹${record.amount}",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    record.status.ifBlank { "—" },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = statusColor
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                formatIsoDateTimeShort(record.createdAt),
                fontSize = 12.sp,
                color = Color.DarkGray
            )
            if (record.withdrawalMethod.isNotBlank() || record.withdrawalDetails.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    buildString {
                        if (record.withdrawalMethod.isNotBlank()) append(record.withdrawalMethod)
                        if (record.withdrawalMethod.isNotBlank() && record.withdrawalDetails.isNotBlank()) {
                            append(" · ")
                        }
                        append(record.withdrawalDetails)
                    },
                    fontSize = 13.sp,
                    color = WalletInkWarm,
                    fontWeight = FontWeight.Medium
                )
            }
            record.processedByName?.let { name ->
                Text(
                    "Processed by: $name",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            record.adminNote?.let { note ->
                Text(
                    "Note: $note",
                    fontSize = 12.sp,
                    color = Color.DarkGray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionalRecordsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    var recordKind by remember { mutableStateOf(TxRecordKind.Deposit) }
    var statusFilter by remember { mutableStateOf(TxStatusFilter.All) }
    var deposits by remember { mutableStateOf<List<DepositRecordApi>>(emptyList()) }
    var depositsLoading by remember { mutableStateOf(false) }
    var depositsError by remember { mutableStateOf<String?>(null) }
    var depositsRefreshNonce by remember { mutableStateOf(0) }
    var withdrawals by remember { mutableStateOf<List<WithdrawRecordApi>>(emptyList()) }
    var withdrawalsLoading by remember { mutableStateOf(false) }
    var withdrawalsError by remember { mutableStateOf<String?>(null) }
    var withdrawalsRefreshNonce by remember { mutableStateOf(0) }

    LaunchedEffect(recordKind, depositsRefreshNonce) {
        if (recordKind != TxRecordKind.Deposit) return@LaunchedEffect
        depositsLoading = true
        depositsError = null
        val (list, err) = fetchMyDeposits()
        deposits = list
        depositsError = err
        depositsLoading = false
    }

    LaunchedEffect(recordKind, withdrawalsRefreshNonce) {
        if (recordKind != TxRecordKind.Withdraw) return@LaunchedEffect
        withdrawalsLoading = true
        withdrawalsError = null
        val (list, err) = fetchMyWithdrawals()
        withdrawals = list
        withdrawalsError = err
        withdrawalsLoading = false
    }

    val filteredDeposits =
        remember(deposits, statusFilter) {
            deposits.filter { matchesTxStatusFilter(it.status, statusFilter) }
        }

    val filteredWithdrawals =
        remember(withdrawals, statusFilter) {
            withdrawals.filter { matchesTxStatusFilter(it.status, statusFilter) }
        }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "Transactional records",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Text(
            "Type",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(TxRecordKind.Deposit to "Deposit", TxRecordKind.Withdraw to "Withdraw").forEach { (kind, label) ->
                val sel = recordKind == kind
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { recordKind = kind },
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) OrangePrimary else Color(0xFFECEFF1),
                    border = if (sel) BorderStroke(1.5.dp, OrangePrimary) else BorderStroke(1.dp, Color(0xFFB0BEC5))
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) Color.White else Color(0xFF37474F)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            if (recordKind == TxRecordKind.Deposit) "Deposit status" else "Withdraw status",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.DarkGray
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                TxStatusFilter.All to "All",
                TxStatusFilter.Success to "Success",
                TxStatusFilter.Failed to "Failed"
            ).forEach { (filter, label) ->
                val sel = statusFilter == filter
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { statusFilter = filter },
                    shape = RoundedCornerShape(10.dp),
                    color = if (sel) OrangePrimary.copy(alpha = 0.18f) else Color(0xFFF5F5F5),
                    border = BorderStroke(
                        width = if (sel) 1.5.dp else 1.dp,
                        color = if (sel) OrangePrimary else Color.LightGray.copy(alpha = 0.6f)
                    )
                ) {
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 12.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) OrangePrimary else Color.DarkGray,
                        maxLines = 1
                    )
                }
            }
        }

        val kindLabel = if (recordKind == TxRecordKind.Deposit) "deposit" else "withdraw"
        val statusLabel = when (statusFilter) {
            TxStatusFilter.All -> "all statuses"
            TxStatusFilter.Success -> "successful"
            TxStatusFilter.Failed -> "failed"
        }
        when (recordKind) {
            TxRecordKind.Deposit ->
                when {
                    depositsLoading ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OrangePrimary)
                        }
                    depositsError != null ->
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                depositsError!!,
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { depositsRefreshNonce++ }) {
                                Text("Retry", color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    else ->
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (filteredDeposits.isEmpty()) {
                                item {
                                    Text(
                                        "No $kindLabel transactions for $statusLabel yet.",
                                        fontSize = 15.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                                    )
                                }
                            } else {
                                items(
                                    filteredDeposits,
                                    key = { d -> "${d.id}_${d.createdAt}_${d.amount}" }
                                ) { row ->
                                    DepositRecordRow(row)
                                }
                            }
                        }
                }
            TxRecordKind.Withdraw ->
                when {
                    withdrawalsLoading ->
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = OrangePrimary)
                        }
                    withdrawalsError != null ->
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                withdrawalsError!!,
                                color = Color(0xFFC62828),
                                textAlign = TextAlign.Center,
                                fontSize = 15.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            TextButton(onClick = { withdrawalsRefreshNonce++ }) {
                                Text("Retry", color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    else ->
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (filteredWithdrawals.isEmpty()) {
                                item {
                                    Text(
                                        "No $kindLabel transactions for $statusLabel yet.",
                                        fontSize = 15.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 22.sp,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp)
                                    )
                                }
                            } else {
                                items(
                                    filteredWithdrawals,
                                    key = { w -> "${w.id}_${w.createdAt}_${w.amount}" }
                                ) { row ->
                                    WithdrawRecordRow(row)
                                }
                            }
                        }
                }
        }
    }
}

@Composable
private fun ProfileScreen(
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onOpenProfileDetails: () -> Unit,
    onOpenReferralEarn: () -> Unit,
    onOpenTransactionalRecords: () -> Unit,
    supportContacts: SupportContactsUi? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val profileScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) }
            Text("Profile & Settings", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Surface(color = OrangePrimary, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(100.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F0F0))
                ) {
                    DrawableImage(
                        R.drawable.profile_top,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            item { SectionHeader("Settings:") }
            item { ProfileMenuItem("Profile", icon = Icons.Default.Person, onClick = onOpenProfileDetails) }
            item {
                ProfileMenuItem(
                    "Transactional records",
                    icon = Icons.Default.ReceiptLong,
                    onClick = onOpenTransactionalRecords
                )
            }
            item { ProfileMenuItem("Referral & Earn", icon = Icons.Default.Share, onClick = onOpenReferralEarn) }

            item { Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = Color.LightGray) }

            item { SectionHeader("Contact / Support:") }
            item {
                ProfileMenuItem(
                    "Whatsapp",
                    icon = Icons.Default.Chat,
                    iconColor = Color(0xFF4CAF50),
                    onClick = {
                        profileScope.launch {
                            var phone = supportContacts?.whatsappNumber?.trim()?.takeIf { it.isNotBlank() }
                            if (phone.isNullOrBlank()) {
                                phone = fetchSupportContacts()?.whatsappNumber?.trim()?.takeIf { it.isNotBlank() }
                            }
                            context.openWhatsAppToContact(phone)
                        }
                    }
                )
            }
            item {
                ProfileMenuItem(
                    "Telegram",
                    icon = Icons.Default.Send,
                    iconColor = Color(0xFF2196F3),
                    onClick = {
                        profileScope.launch {
                            var tg = supportContacts?.telegram?.trim()?.takeIf { it.isNotBlank() }
                            if (tg.isNullOrBlank()) {
                                tg = fetchSupportContacts()?.telegram?.trim()?.takeIf { it.isNotBlank() }
                            }
                            context.openTelegramToContact(tg)
                        }
                    }
                )
            }
            item {
                ProfileMenuItem(
                    "Facebook",
                    icon = Icons.Default.Facebook,
                    iconColor = Color(0xFF1877F2),
                    onClick = {
                        val url = supportContacts?.facebookUrl
                        if (!url.isNullOrBlank()) {
                            if (!context.tryOpenExternalUrl(url)) {
                                Toast.makeText(context, "Unable to open Facebook", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "Facebook link not available", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            item {
                ProfileMenuItem(
                    "Instagram",
                    iconRes = R.drawable.social_instagram,
                    onClick = {
                        val url = supportContacts?.instagramUrl
                        if (!url.isNullOrBlank()) {
                            if (!context.tryOpenExternalUrl(url)) {
                                Toast.makeText(context, "Unable to open Instagram", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            context.openInstagramApp()
                        }
                    }
                )
            }
            item {
                ProfileMenuItem(
                    "Youtube",
                    iconRes = R.drawable.social_youtube,
                    onClick = {
                        val url = supportContacts?.youtubeUrl
                        if (!url.isNullOrBlank()) {
                            if (!context.tryOpenExternalUrl(url)) {
                                Toast.makeText(context, "Unable to open YouTube", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            context.openYoutubeApp()
                        }
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp)
                        .clickable { onLogout() },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("Log out", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        Icon(Icons.Default.PowerSettingsNew, null, tint = Color.Red)
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) { Text(text = title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp) }

@Composable
fun ProfileMenuItem(
    title: String,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    iconColor: Color = Color.Black,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            DrawableImage(
                iconRes,
                contentDescription = title,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(16.dp))
        }
        Text(text = title, modifier = Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
    }
}

@Composable
private fun BankDetailLine(label: String, value: String, copyable: Boolean = false) {
    if (value.isBlank()) return
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WalletInkWarm)
        }
        if (copyable) {
            IconButton(
                onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(value))
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy $label",
                    tint = OrangePrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun BankDetailsPanel(
    loading: Boolean,
    error: String?,
    details: BankDetailsUi?,
    onRetry: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF8F0),
        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Your bank details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WalletInkWarm)
            Spacer(Modifier.height(10.dp))
            when {
                loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = OrangePrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Loading from server…", color = WalletInkWarm, fontSize = 14.sp)
                    }
                }
                error != null -> {
                    Text(error, color = Color(0xFFC62828), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("Retry", color = OrangePrimary) }
                }
                details != null -> {
                    BankDetailLine("Account holder", details.accountHolder)
                    BankDetailLine("Bank name", details.bankName)
                    BankDetailLine("Account number", details.accountNumber, copyable = true)
                    BankDetailLine("IFSC", details.ifsc, copyable = true)
                    if (details.branch.isNotBlank()) BankDetailLine("Branch", details.branch, copyable = true)
                }
            }
        }
    }
}

@Composable
private fun UpiQrAndVpaFromApi(
    loading: Boolean,
    error: String?,
    qrImageUrl: String?,
    upiId: String?,
    onRetry: () -> Unit
) {
    val ctx = LocalContext.current
    Card(
        modifier = Modifier.padding(16.dp).size(250.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(color = OrangePrimary)
                error != null -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                        Text(error, color = Color(0xFFC62828), fontSize = 13.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onRetry) { Text("Retry", color = OrangePrimary) }
                    }
                }
                !qrImageUrl.isNullOrBlank() ->
                    AsyncImage(
                        model =
                            ImageRequest.Builder(ctx)
                                .data(qrImageUrl)
                                .crossfade(true)
                                .build(),
                        contentDescription = "Payment QR",
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                else -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(160.dp), tint = Color.Black)
                        Text("Scan QR to Pay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                    }
                }
            }
        }
    }
    if (!upiId.isNullOrBlank()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFFFF8F0),
            border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("UPI / VPA", fontSize = 12.sp, color = Color.Gray)
                Text(upiId, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WalletInkWarm)
            }
        }
    }
}

private val PaymentHeaderBlue = Color(0xFF3F51B5)
private val PaymentAmountBlue = Color(0xFF1565C0)
private val OrangePrimary = Color(0xFFFF6F00)

/** Downloads [url] and saves the image into the device's Photos/Gallery. Returns true on success. */
private suspend fun saveQrImageToGallery(context: Context, url: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url(url).get().build()
            val bytes = cricketOddsHttpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext false
                resp.body?.bytes() ?: return@withContext false
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return@withContext false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "QR_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kokoroko")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return@withContext false
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
            } else {
                @Suppress("DEPRECATION")
                android.provider.MediaStore.Images.Media.insertImage(
                    context.contentResolver, bitmap, "QR_${System.currentTimeMillis()}", "Kokoroko QR"
                ) ?: return@withContext false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

@Composable
private fun PaymentOptionsScreen(
    onBack: () -> Unit,
    walletDepositMethod: String = "upi",
    depositAmount: String = "",
    preloadedWallet: WalletApiResult? = null,
    preloadedLoading: Boolean = false,
    preloadedError: String? = null
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val isBankPath = walletDepositMethod == "bank"
    var wallet by remember { mutableStateOf(preloadedWallet) }
    var walletLoading by remember { mutableStateOf(preloadedWallet == null && preloadedLoading) }
    var walletError by remember { mutableStateOf(preloadedError) }
    var walletRetryNonce by remember { mutableStateOf(0) }
    var selectedMethodType by remember { mutableStateOf<String?>(null) }
    var selectedMethodId by remember { mutableStateOf<Int?>(null) }
    var screenshotUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var timerSeconds by remember { mutableStateOf(600) }
    var uploadSubmitting by remember { mutableStateOf(false) }
    var uploadError by remember { mutableStateOf<String?>(null) }
    val uploadScope = rememberCoroutineScope()
    var savingQr by remember { mutableStateOf(false) }

    val screenshotLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> screenshotUri = uri; uploadError = null }

    LaunchedEffect(Unit) {
        while (timerSeconds > 0) { delay(1000); timerSeconds-- }
    }

    LaunchedEffect(walletRetryNonce) {
        /** Always fetch fresh on first load too — never trust preloaded stale data. */
        walletLoading = true; walletError = null; wallet = null
        val (w, err) = fetchPaymentOptionsFromApi()
        walletLoading = false; wallet = w; walletError = err
    }

    val bankDetails = wallet?.bank
    // Don't require per-row upi_id: backend often sends one global VPA on the wallet; [launchWalletPaymentMethod] uses row ?: apiUpiId.
    // Also allow deep links / package-only rows.
    val methodsToShow = run {
        val raw = wallet?.paymentMethods ?: emptyList()
        val filtered = raw.filter { m ->
            val t = m.type.lowercase(Locale.US)
            if (t == "bank" || t == "qr") return@filter false
            !m.upiId.isNullOrBlank()
                || !wallet?.upiId.isNullOrBlank()
                || !m.deepLink.isNullOrBlank()
                || !m.packageName.isNullOrBlank()
        }.distinctBy { it.type.lowercase(Locale.US) }
        if (filtered.isNotEmpty()) filtered
        else if (!walletLoading && walletError == null && !wallet?.upiId.isNullOrBlank()) {
            defaultWalletPaymentMethods()
        } else {
            emptyList()
        }
    }

    val qrImageUrl = wallet?.qrImageUrl
    val timerMins = timerSeconds / 60
    val timerSecs = timerSeconds % 60
    val timerText = "%02d:%02d".format(timerMins, timerSecs)
    val amountDisplay = depositAmount.ifBlank { "0" }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text("Payment", modifier = Modifier.align(Alignment.Center), color = Color.Black, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Surface(
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0F0F0)
            ) {
                Text(timerText, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Color.DarkGray, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
        Divider(color = Color(0xFFEEEEEE))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            // Amount banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF7F7F7),
                    border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                    shadowElevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Amount Payable", fontSize = 15.sp, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("₹$amountDisplay", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                        Spacer(Modifier.height(6.dp))
                        Text("Fill in UTR after successful payment", fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }

            // QR Code section (UPI path only)
            if (!isBankPath && !qrImageUrl.isNullOrBlank()) {
                item {
                    Text("Scan QR To Pay", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(modifier = Modifier.size(200.dp), shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(qrImageUrl).crossfade(true).build(),
                            contentDescription = "QR Code",
                                modifier = Modifier.fillMaxSize().padding(12.dp)
                        )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (!savingQr && qrImageUrl != null) {
                                savingQr = true
                                uploadScope.launch {
                                    val ok = saveQrImageToGallery(context, qrImageUrl)
                                    savingQr = false
                                    val msg = if (ok) "QR saved to gallery" else "Failed to save QR"
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !savingQr,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 80.dp).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (savingQr) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Bank details (bank path)
            if (isBankPath) {
                item {
                    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        BankDetailsPanel(
                            loading = walletLoading,
                            error = walletError ?: if (!walletLoading && bankDetails == null && wallet != null) "No bank account details configured." else null,
                            details = bankDetails,
                            onRetry = { walletRetryNonce++ }
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // Payment methods (UPI path)
            if (!isBankPath && (walletLoading || methodsToShow.isNotEmpty())) {
                item {
                    Text("Choose Payment Method", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                    Spacer(Modifier.height(10.dp))
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = RoundedCornerShape(14.dp), color = Color.White, shadowElevation = 1.dp, border = BorderStroke(1.dp, Color(0xFFEEEEEE))) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            if (walletLoading) {
                                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = OrangePrimary, modifier = Modifier.size(32.dp))
                                }
                            } else {
                                methodsToShow.forEachIndexed { index, m ->
                                    val isSelected = selectedMethodType == m.type
                                    Surface(
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            selectedMethodType = m.type
                                            selectedMethodId = m.id
                                            context.launchWalletPaymentMethod(m, wallet?.upiId, depositAmount)
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color.White,
                                        border = BorderStroke(if (isSelected) 1.5.dp else 0.dp, if (isSelected) OrangePrimary else Color.Transparent)
                                    ) {
                                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(8.dp), color = colorForWalletPaymentType(m.type).copy(alpha = 0.12f)) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(iconForWalletPaymentType(m.type), null, tint = colorForWalletPaymentType(m.type), modifier = Modifier.size(22.dp))
                                                }
                                            }
                                            Spacer(Modifier.width(14.dp))
                                            Text(m.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black, modifier = Modifier.weight(1f))
                                            Icon(Icons.Default.TouchApp, null, tint = OrangePrimary, modifier = Modifier.size(24.dp))
                                        }
                                    }
                                    if (index < methodsToShow.lastIndex) Divider(color = Color(0xFFF5F5F5))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            // Screenshot upload section
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Upload Payment Screenshot", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Help, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    }
                    Text("Max 10MB · JPG, PNG", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth().height(150.dp).clickable { screenshotLauncher.launch("image/*") },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF7F7F7),
                        border = BorderStroke(1.5.dp, Color(0xFFDDDDDD))
                    ) {
                        if (screenshotUri != null) {
                            AsyncImage(model = screenshotUri, contentDescription = "Screenshot", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        } else {
                            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.AddPhotoAlternate, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("Tap to select screenshot", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    if (uploadError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(uploadError!!, color = Color(0xFFD32F2F), fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val uri = screenshotUri
                            if (uri == null) {
                                uploadError = "Please select a payment screenshot first."
                                return@Button
                            }
                            val finalMethodId = selectedMethodId
                                ?: methodsToShow.firstOrNull()?.id
                                ?: 7
                            uploadScope.launch {
                                uploadSubmitting = true
                                uploadError = null
                                val (result, err) = postDepositUploadProof(context, uri, depositAmount, finalMethodId)
                                uploadSubmitting = false
                                if (result != null) {
                                    Toast.makeText(context, "Deposit #${result.id} submitted — ${result.status}. Amount: ₹${result.amount}", Toast.LENGTH_LONG).show()
                                    onBack()
                                } else {
                                    uploadError = err ?: "Upload failed. Please try again."
                                }
                            }
                        },
                        enabled = !uploadSubmitting,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (uploadSubmitting) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Submit Payment Proof", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentMethodItem(name: String, icon: ImageVector, color: Color, upiId: String? = null, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFF5F5F5),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = color.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = WalletInkWarm)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

private const val PREFS_WITHDRAW_NAME = "kokoroko_withdraw"

private fun mergedWithdrawBank(api: BankDetailsUi?, local: BankDetailsUi?): BankDetailsUi? {
    val a = api
    if (a != null && (a.accountNumber.isNotBlank() || a.bankName.isNotBlank() || a.accountHolder.isNotBlank())) {
        return a
    }
    return local?.takeIf {
        it.accountNumber.isNotBlank() || it.bankName.isNotBlank() || it.accountHolder.isNotBlank()
    }
}

private fun mergedWithdrawUpi(api: String?, local: String?): String? {
    val a = api?.trim()
    if (!a.isNullOrBlank()) return a
    return local?.trim()?.takeIf { it.isNotBlank() }
}

private fun Context.readLocalWithdrawBank(): BankDetailsUi? {
    val p = getSharedPreferences(PREFS_WITHDRAW_NAME, Context.MODE_PRIVATE)
    val holder = p.getString("bank_holder", "")?.trim().orEmpty()
    val bankName = p.getString("bank_name", "")?.trim().orEmpty()
    val acc = p.getString("bank_account", "")?.trim().orEmpty()
    val ifsc = p.getString("bank_ifsc", "")?.trim().orEmpty()
    val branch = p.getString("bank_branch", "")?.trim().orEmpty()
    if (holder.isBlank() && bankName.isBlank() && acc.isBlank() && ifsc.isBlank()) return null
    return BankDetailsUi(holder, bankName, acc, ifsc, branch)
}

private fun Context.readLocalWithdrawUpi(): String? =
    getSharedPreferences(PREFS_WITHDRAW_NAME, Context.MODE_PRIVATE)
        .getString("upi_vpa", null)
        ?.trim()
        ?.takeIf { it.isNotBlank() }

private fun Context.saveLocalWithdrawBank(b: BankDetailsUi) {
    getSharedPreferences(PREFS_WITHDRAW_NAME, Context.MODE_PRIVATE).edit().apply {
        putString("bank_holder", b.accountHolder)
        putString("bank_name", b.bankName)
        putString("bank_account", b.accountNumber)
        putString("bank_ifsc", b.ifsc)
        putString("bank_branch", b.branch)
        apply()
    }
}

private fun Context.saveLocalWithdrawUpi(vpa: String) {
    getSharedPreferences(PREFS_WITHDRAW_NAME, Context.MODE_PRIVATE).edit()
        .putString("upi_vpa", vpa.trim())
        .apply()
}

private fun mergedWithdrawUpiFromSaved(
    saved: AuthBankDetailsApi?,
    local: String?
): String? {
    val fromApi = saved?.upiId?.trim()?.takeIf { it.isNotBlank() }
    return mergedWithdrawUpi(fromApi, local)
}

private fun mergedWithdrawBankFromSaved(
    saved: AuthBankDetailsApi?,
    local: BankDetailsUi?
): BankDetailsUi? {
    val fromApi = saved?.toBankDetailsUiOrNull()
    return mergedWithdrawBank(fromApi, local)
}

private fun withdrawalDetailsForApi(
    paymentMethod: String,
    saved: AuthBankDetailsApi?,
    localBank: BankDetailsUi?,
    localUpi: String?
): String? {
    val bank = mergedWithdrawBankFromSaved(saved, localBank)
    val upi = mergedWithdrawUpiFromSaved(saved, localUpi)
    return when (paymentMethod) {
        "upi" -> upi
        "bank" ->
            bank?.let { b ->
                buildString {
                    append(b.accountNumber)
                    if (b.ifsc.isNotBlank()) {
                        append(" | ")
                        append(b.ifsc)
                    }
                }
            }
        else -> null
    }
}

@Composable
private fun WithdrawalDestinationCard(
    paymentMethod: String,
    savedBankDetails: AuthBankDetailsApi?,
    detailsLoading: Boolean,
    detailsError: String?,
    localBank: BankDetailsUi?,
    localUpi: String?,
    onRetryWallet: () -> Unit,
    onAddBank: () -> Unit,
    onAddUpi: () -> Unit
) {
    val bank = mergedWithdrawBankFromSaved(savedBankDetails, localBank)
    val upi = mergedWithdrawUpiFromSaved(savedBankDetails, localUpi)
    val bankRows =
        when {
            !savedBankDetails?.allBanks.isNullOrEmpty() -> savedBankDetails!!.allBanks
            bank != null -> listOf(bank)
            else -> emptyList()
        }
    val upiRows =
        when {
            !savedBankDetails?.allUpiIds.isNullOrEmpty() -> savedBankDetails!!.allUpiIds
            upi != null -> listOf(upi)
            else -> emptyList()
        }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            "Withdrawal destination",
            fontWeight = FontWeight.Bold,
            color = WalletInkWarm,
            fontSize = 15.sp
        )
        Spacer(Modifier.height(8.dp))
        if (paymentMethod == "bank") {
            when {
                detailsLoading && bankRows.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = OrangePrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Loading bank account…", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                bankRows.isNotEmpty() -> {
                    bankRows.forEachIndexed { index, bk ->
                    Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (index < bankRows.lastIndex) 10.dp else 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF8F0),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                                Text(
                                    if (bankRows.size > 1) "Bank account ${index + 1}" else "Bank account",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = WalletInkWarm
                                )
                            Spacer(Modifier.height(6.dp))
                                BankDetailLine("Account holder", bk.accountHolder)
                                BankDetailLine("Bank name", bk.bankName)
                                BankDetailLine("Account number", bk.accountNumber, copyable = true)
                                BankDetailLine("IFSC", bk.ifsc, copyable = true)
                                if (bk.branch.isNotBlank()) BankDetailLine("Branch", bk.branch, copyable = true)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onAddBank) {
                            Text("Change", color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                else -> {
                    detailsError?.let {
                        Text(it, color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    OutlinedButton(
                        onClick = onAddBank,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, OrangePrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add bank account", fontWeight = FontWeight.SemiBold)
                    }
                    if (detailsError != null) {
                        TextButton(onClick = onRetryWallet) { Text("Retry loading", color = OrangePrimary) }
                    }
                }
            }
        } else {
            when {
                detailsLoading && upiRows.isEmpty() -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                            color = OrangePrimary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Loading UPI…", fontSize = 13.sp, color = Color.Gray)
                    }
                }
                upiRows.isNotEmpty() -> {
                    upiRows.forEachIndexed { index, vpa ->
                    Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = if (index < upiRows.lastIndex) 10.dp else 0.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF8F0),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                                Text(
                                    if (upiRows.size > 1) "UPI ID ${index + 1}" else "UPI ID",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = WalletInkWarm
                                )
                            Spacer(Modifier.height(4.dp))
                                Text(vpa, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = WalletInkWarm)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onAddUpi) {
                            Text("Change", color = OrangePrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                else -> {
                    detailsError?.let {
                        Text(it, color = Color(0xFFC62828), fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    OutlinedButton(
                        onClick = onAddUpi,
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, OrangePrimary),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangePrimary)
                    ) {
                        Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add UPI ID", fontWeight = FontWeight.SemiBold)
                    }
                    if (detailsError != null) {
                        TextButton(onClick = onRetryWallet) { Text("Retry loading", color = OrangePrimary) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWithdrawBankDialog(
    initial: BankDetailsUi?,
    onDismiss: () -> Unit,
    onSave: (BankDetailsUi) -> Unit
) {
    var holder by remember { mutableStateOf(initial?.accountHolder.orEmpty()) }
    var bankName by remember { mutableStateOf(initial?.bankName.orEmpty()) }
    var account by remember { mutableStateOf(initial?.accountNumber.orEmpty()) }
    var ifsc by remember { mutableStateOf(initial?.ifsc.orEmpty()) }
    var branch by remember { mutableStateOf(initial?.branch.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState())
            ) {
                Text("Add bank account", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = holder,
                    onValueChange = { holder = it },
                    label = { Text("Account holder") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bankName,
                    onValueChange = { bankName = it },
                    label = { Text("Bank name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = account,
                    onValueChange = { account = it.filter { c -> c.isDigit() }.take(18) },
                    label = { Text("Account number") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ifsc,
                    onValueChange = { ifsc = it.uppercase(Locale.US).filter { c -> c.isLetterOrDigit() }.take(11) },
                    label = { Text("IFSC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = branch,
                    onValueChange = { branch = it },
                    label = { Text("Branch (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                BankDetailsUi(
                                    accountHolder = holder.trim(),
                                    bankName = bankName.trim(),
                                    accountNumber = account.trim(),
                                    ifsc = ifsc.trim(),
                                    branch = branch.trim()
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddWithdrawUpiDialog(
    initial: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var vpa by remember { mutableStateOf(initial.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("Add UPI ID", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = vpa,
                    onValueChange = { vpa = it.trim().take(50) },
                    label = { Text("UPI / VPA") },
                    singleLine = true,
                    placeholder = { Text("name@bank") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(vpa.trim()) },
                        enabled = vpa.trim().isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun WalletScreen(onBack: () -> Unit, onDepositClick: (walletPaymentMethod: String, amount: String) -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    val withdrawScope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf("deposit") }
    var depositAmount by remember { mutableStateOf("1000") }
    var withdrawAmount by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("upi") }
    var withdrawSavedDetails by remember { mutableStateOf<AuthBankDetailsApi?>(null) }
    var withdrawDetailsLoading by remember { mutableStateOf(false) }
    var withdrawDetailsError by remember { mutableStateOf<String?>(null) }
    var withdrawDetailsNonce by remember { mutableStateOf(0) }
    var withdrawSubmitting by remember { mutableStateOf(false) }
    var localPrefsEpoch by remember { mutableStateOf(0) }
    var showAddBankDialog by remember { mutableStateOf(false) }
    var showAddUpiDialog by remember { mutableStateOf(false) }
    var walletSnapshot by remember { mutableStateOf<WalletApiResult?>(null) }

    val localBank = remember(localPrefsEpoch) { context.readLocalWithdrawBank() }
    val localUpi = remember(localPrefsEpoch) { context.readLocalWithdrawUpi() }

    LaunchedEffect(selectedTab, withdrawDetailsNonce) {
        if (selectedTab != "withdrawal") return@LaunchedEffect
        withdrawDetailsLoading = true
        withdrawDetailsError = null
        val (saved, err) = fetchAuthBankDetails()
        withdrawDetailsLoading = false
        withdrawSavedDetails = saved
        withdrawDetailsError = err
    }

    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        walletSnapshot = w
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp, vertical = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.Black)
            }
            Text("Wallet", modifier = Modifier.align(Alignment.Center), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Divider(color = Color(0xFFEEEEEE))

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Spacer(Modifier.height(8.dp)) }

            // Tab switcher
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    listOf("deposit" to "Deposit", "withdrawal" to "Withdrawal").forEach { (tab, label) ->
                        val selected = selectedTab == tab
                        Column(
                            modifier = Modifier.weight(1f).clickable { selectedTab = tab },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(vertical = 12.dp),
                                color = if (selected) OrangePrimary else Color.Gray,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 17.sp
                            )
                            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(if (selected) OrangePrimary else Color.Transparent, RoundedCornerShape(2.dp)))
                        }
                    }
                }
                Divider(color = Color(0xFFEEEEEE))
                Spacer(Modifier.height(12.dp))
            }

            // Info banner
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF5F5F5)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (selectedTab == "deposit") "Minimum deposit amount is ₹100." else "Withdrawal processed in 4–48 hours.",
                            fontSize = 14.sp, color = Color.DarkGray
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (selectedTab == "withdrawal") {
                item {
                    Text(
                        "Available for withdrawal: ${formatRupeeBalanceForDisplay(walletSnapshot?.withdrawableBalance)}",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        fontSize = 15.sp, color = Color.Gray
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }

            // Payment method cards
            item {
                Text("Select Payment Method", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(
                        Triple("upi", "UPI", R.drawable.upi_logo),
                        Triple("bank", "Bank Account", R.drawable.bank_logo)
                    ).forEach { (method, label, drawableRes) ->
                        val selected = paymentMethod == method
                        Surface(
                            modifier = Modifier.weight(1f).height(100.dp).clickable { paymentMethod = method },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color(0xFFFFF8F2) else Color.White,
                            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) OrangePrimary else Color(0xFFEEEEEE)),
                            shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Image(
                                    painter = painterResource(id = drawableRes),
                                    contentDescription = label,
                                    modifier = Modifier.height(32.dp).widthIn(max = 80.dp),
                                    contentScale = ContentScale.Fit
                                )
                                Column {
                                    Text(label, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("0% commission", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            if (selectedTab == "withdrawal") {
                item {
                    WithdrawalDestinationCard(
                        paymentMethod = paymentMethod,
                        savedBankDetails = withdrawSavedDetails,
                        detailsLoading = withdrawDetailsLoading,
                        detailsError = withdrawDetailsError,
                        localBank = localBank,
                        localUpi = localUpi,
                        onRetryWallet = { withdrawDetailsNonce++ },
                        onAddBank = { showAddBankDialog = true },
                        onAddUpi = { showAddUpiDialog = true }
                    )
                }
            }
            item {
                val amountValue = if (selectedTab == "deposit") depositAmount else withdrawAmount
                Text("Enter Amount", modifier = Modifier.padding(horizontal = 16.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amountValue,
                    onValueChange = { new ->
                        val filtered = new.filter { it.isDigit() }.take(9)
                        if (selectedTab == "deposit") depositAmount = filtered else withdrawAmount = filtered
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    singleLine = true,
                    label = { Text("Amount (₹)", color = Color.Gray, fontSize = 15.sp) },
                    placeholder = { Text(if (selectedTab == "deposit") "e.g. 500" else "e.g. 200", color = Color.LightGray, fontSize = 16.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.Black),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrangePrimary,
                        unfocusedBorderColor = Color(0xFFDDDDDD),
                        focusedLabelColor = OrangePrimary,
                        unfocusedLabelColor = Color.Gray,
                        cursorColor = OrangePrimary,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            if (selectedTab == "deposit") {
                item {
                    Spacer(Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("500", "1000", "2000", "5000", "10000").forEach { valStr ->
                            Surface(
                                modifier = Modifier.weight(1f).height(40.dp).clickable { depositAmount = valStr },
                                color = if (depositAmount == valStr) Color(0xFFFFF8F2) else Color.White,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, if (depositAmount == valStr) OrangePrimary else Color(0xFFDDDDDD))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("₹$valStr", color = if (depositAmount == valStr) OrangePrimary else Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (selectedTab == "deposit") "How to Deposit" else "How to Withdraw", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.Black)
                    Surface(border = BorderStroke(1.dp, Color(0xFFDDDDDD)), shape = RoundedCornerShape(8.dp), color = Color.White) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Watch Tutorial", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                        }
                    }
                }
            }
            if (selectedTab == "withdrawal") {
                item {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(56.dp)
                            .clickable(enabled = !withdrawSubmitting) {
                                withdrawScope.launch {
                                    val amt = withdrawAmount.toIntOrNull() ?: 0
                                    val details =
                                        withdrawalDetailsForApi(
                                            paymentMethod,
                                            withdrawSavedDetails,
                                            localBank,
                                            localUpi
                                        ).orEmpty()
                                    if (details.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Add or load a bank account / UPI for this method first.",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        return@launch
                                    }
                                    withdrawSubmitting = true
                                    val method = if (paymentMethod == "upi") "UPI" else "BANK"
                                    val (res, err) = postWithdrawInitiate(amt, method, details)
                                    withdrawSubmitting = false
                                    Toast.makeText(
                                        context,
                                        when {
                                            res != null ->
                                                "Withdrawal #${res.id} — ${res.status} (₹${res.amount})"
                                            else -> err ?: "Could not submit withdrawal"
                                        },
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (withdrawSubmitting) OrangePrimary.copy(alpha = 0.65f) else OrangePrimary
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (withdrawSubmitting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Request Withdrawal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (selectedTab == "deposit") {
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(
                        onClick = { onDepositClick(paymentMethod, depositAmount) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Proceed to Deposit", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
        if (showAddBankDialog) {
            AddWithdrawBankDialog(
                initial = mergedWithdrawBank(withdrawSavedDetails?.toBankDetailsUiOrNull(), localBank),
                onDismiss = { showAddBankDialog = false },
                onSave = { b ->
                    context.saveLocalWithdrawBank(b)
                    localPrefsEpoch++
                    showAddBankDialog = false
                    Toast.makeText(context, "Bank account saved for withdrawal", Toast.LENGTH_SHORT).show()
                }
            )
        }
        if (showAddUpiDialog) {
            AddWithdrawUpiDialog(
                initial = mergedWithdrawUpi(withdrawSavedDetails?.upiId?.takeIf { it.isNotBlank() }, localUpi),
                onDismiss = { showAddUpiDialog = false },
                onSave = { v ->
                    context.saveLocalWithdrawUpi(v)
                    localPrefsEpoch++
                    showAddUpiDialog = false
                    Toast.makeText(context, "UPI ID saved for withdrawal", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenGundata: () -> Unit = {},
    onOpenCricket: () -> Unit = {},
    onOpenCockfight: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    onPromotionsClick: () -> Unit = {}
) {
    var liveEnabled by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    var walletBalanceText by remember { mutableStateOf("₹0") }
    var cockfightHighlights by remember { mutableStateOf<List<CockfightHighlightVideo>>(emptyList()) }
    var playingCockfightHighlight by remember { mutableStateOf<CockfightHighlightVideo?>(null) }
    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        walletBalanceText = formatRupeeBalanceForDisplay(w?.balance)
    }
    LaunchedEffect(Unit) { cockfightHighlights = fetchCockfightHighlights() }
    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopHeader(
            onDicePlayClick = onOpenGundata,
            onCricketClick = onOpenCricket,
            onCockfightClick = onOpenCockfight,
            onWalletClick = onWalletClick,
            walletBalanceText = walletBalanceText
        )
        LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        state = listState
    ) {
        item(key = "search_bar", contentType = "search_bar") {
            var searchQuery by remember { mutableStateOf("") }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                border = BorderStroke(1.dp, Color(0xFFEEEEEE)),
                shadowElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 15.sp, color = Color.Black),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search games...", fontSize = 15.sp, color = Color(0xFFAAAAAA))
                            }
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp).clickable { searchQuery = "" }
                        )
                    }
                }
            }
        }
        item(key = "banner", contentType = "banner") {
            BannerCarousel(homeListState = listState, onGundataClick = onOpenGundata, onCockfightClick = onOpenCockfight)
        }
        item(key = "popular_games", contentType = "games") {
            PopularGamesSection(
                onGundataClick = onOpenGundata,
                onCricketClick = onOpenCricket,
                onCockfightClick = onOpenCockfight,
                onPromotionsClick = onPromotionsClick
            )
        }
        item(key = "live_header", contentType = "live_header") {
            LiveCockFightHeader(liveEnabled = liveEnabled, onLiveChange = { liveEnabled = it })
        }
        if (liveEnabled) {
            item(key = "live_match", contentType = "match") { LiveMatchCard(onClick = onOpenCockfight) }
        } else {
            item(key = "live_off", contentType = "placeholder") {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEEEEE),
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Text(
                        "Live is off. Turn on LIVE above to see the match card.",
                        modifier = Modifier.padding(16.dp),
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                }
            }
        }
        item(key = "cockfight_highlights", contentType = "cockfight_highlights") {
            CockfightHighlightsGridSection(
                videos = cockfightHighlightsPaddedToFour(cockfightHighlights),
                onPlay = { v ->
                    if (v.videoUrl.isNotBlank()) playingCockfightHighlight = v
                }
            )
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
        }
        playingCockfightHighlight?.let { h ->
            CockfightHighlightVideoDialog(
                videoUrl = h.videoUrl,
                title = h.title,
                onDismiss = { playingCockfightHighlight = null }
            )
        }
    }
}

@Composable
private fun WalletBalanceChip(
    onClick: () -> Unit,
    balanceText: String = "₹0",
    modifier: Modifier = Modifier,
    spacerBetweenIconAndText: Dp = 6.dp
) {
    Surface(
        onClick = onClick,
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier,
        shadowElevation = 2.dp,
        border = BorderStroke(1.dp, Color(0xFFEEEEEE))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(34.dp).background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Wallet, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(balanceText, color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, letterSpacing = 0.sp)
        }
    }
}

@Composable
fun TopHeader(
    onDicePlayClick: () -> Unit = {},
    onCricketClick: () -> Unit = {},
    onCockfightClick: () -> Unit = {},
    onWalletClick: () -> Unit = {},
    walletBalanceText: String = "₹0"
) {
    Column(Modifier.fillMaxWidth()) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFAFAFA))
            .padding(start = 0.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Single-line wordmark; K/R soft black, O light orange
        val kokorokoWordmark = buildAnnotatedString {
            val w = SpanStyle(color = HeaderInkLight, fontWeight = FontWeight.Black)
            val o = SpanStyle(color = HeaderOrangeLight, fontWeight = FontWeight.Black)
            withStyle(w) { append("K") }
            withStyle(o) { append("O") }
            withStyle(w) { append("K") }
            withStyle(o) { append("O") }
            withStyle(w) { append("R") }
            withStyle(o) { append("O") }
            withStyle(w) { append("K") }
            withStyle(o) { append("O") }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .padding(start = 8.dp, end = 4.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                // Same launcher artwork as login page ([R.mipmap.ic_launcher])
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .border(2.dp, Color.Black, CircleShape)
                ) {
                    DrawableImage(
                        R.mipmap.ic_launcher,
                        contentDescription = "Hen Fight",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.Center
                    )
                }
                Spacer(Modifier.width(10.dp))
            Text(
                text = kokorokoWordmark,
                fontSize = 28.sp,
                letterSpacing = 0.5.sp,
                maxLines = 1,
                    modifier = Modifier.weight(1f, fill = false)
            )
        }
        }
        // Wallet on right — light chip so it reads on white bar
        Surface(
            onClick = onWalletClick,
            color = Color(0xFFF5F5F5),
            shape = RoundedCornerShape(14.dp),
            shadowElevation = 1.dp,
            border = BorderStroke(1.dp, Color(0xFFE0E0E0))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val amountDigits = walletBalanceText.trim().removePrefix("₹").trim()
                Text("₹", color = HeaderOrangeLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.sp)
                Text(amountDigits, color = HeaderInkLight, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = 0.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier.size(22.dp).background(HeaderOrangeLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
    Divider(color = Color(0xFFEBEBEB), thickness = 1.dp)
    }
}

@Composable
fun HeaderIconItem(label: String, icon: ImageVector? = null, imageRes: Int? = null, onClick: (() -> Unit)? = null) {
    val tileShape = RoundedCornerShape(10.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(modifier = Modifier.size(50.dp).border(1.dp, Color.LightGray, tileShape).padding(3.dp), contentAlignment = Alignment.Center) {
            if (imageRes != null) {
                DrawableImage(
                    imageRes,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize().clip(tileShape),
                    contentScale = ContentScale.Crop
                )
            } else if (icon != null) Icon(icon, label, modifier = Modifier.size(30.dp), tint = Color.Black)
        }
        Text(label, fontSize = 10.sp, color = Color.Black)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BannerCarousel(
    homeListState: LazyListState,
    onGundataClick: () -> Unit = {},
    onCockfightClick: () -> Unit = {}
) {
    val totalBanners = 2
    val rowState = rememberLazyListState()
    val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)
    LaunchedEffect(totalBanners) {
        if (totalBanners <= 1) return@LaunchedEffect
        while (true) {
            delay(3500)
            snapshotFlow { homeListState.isScrollInProgress }
                .first { !it }
            val current = rowState.firstVisibleItemIndex.coerceIn(0, totalBanners - 1)
            val next = (current + 1) % totalBanners
            rowState.scrollToItem(next)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
    ) {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = snapFling,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slide 0: Cockfight banner — opens Cockfight screen
            item(key = "cockfight") {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .fillMaxHeight()
                        .clickable { onCockfightClick() }
                ) {
                    DrawableImage(
                        R.drawable.cockfight_banner,
                        contentDescription = "Cockfight Championship",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // Slide 1: Gundu Ata banner — opens Gundu Ata game
            item(key = "gundu") {
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .fillMaxHeight()
                        .clickable { onGundataClick() }
                ) {
                    DrawableImage(
                        R.drawable.banner_gundu,
                        contentDescription = "Gundu Ata",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        // Dot indicators
        Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(totalBanners) { i ->
                val isActive = rowState.firstVisibleItemIndex == i
                Box(modifier = Modifier.size(if (isActive) 18.dp else 6.dp, 6.dp).background(if (isActive) Color.White else Color.White.copy(alpha = 0.4f), RoundedCornerShape(3.dp)))
            }
        }
    }
}

@Composable
fun PopularGamesSection(
    onGundataClick: () -> Unit = {},
    onCricketClick: () -> Unit = {},
    onCockfightClick: () -> Unit = {},
    onPromotionsClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Popular Games:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.LightGray), shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.School, null, tint = Color.Black, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Watch Tutorials", color = Color.Black, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GameIconItem("Cockfight", R.drawable.category_cockfight, onClick = onCockfightClick)
            GameIconItem("Gundata", R.drawable.category_gunduata, onClick = onGundataClick)
            GameIconItem("Cricket", R.drawable.category_cricket, onClick = onCricketClick)
            GameIconItem("Promotions", R.drawable.category_promotions, onClick = onPromotionsClick)
        }
    }
}

@Composable
fun GameIconItem(
    label: String,
    imageRes: Int,
    onClick: (() -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Surface(
            modifier = Modifier.size(70.dp),
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            shadowElevation = 0.dp
        ) {
            DrawableImage(
                imageRes,
                contentDescription = label,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun LiveCockFightHeader(liveEnabled: Boolean, onLiveChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Cock Fight", fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, color = Color.Black)
        }
        // Right: LIVE toggle
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("LIVE", fontWeight = FontWeight.Bold, color = if (liveEnabled) Color.Black else Color.Gray)
            Spacer(Modifier.width(8.dp))
            Switch(
                checked = liveEnabled,
                onCheckedChange = onLiveChange,
                colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OrangePrimary)
            )
        }
    }
}

/** Live cock fight: hide FF / rewind / skip and disable scrubbing on the default controller. */
private fun androidx.media3.ui.PlayerView.applyDisallowPlaybackSeeking() {
    setShowFastForwardButton(false)
    setShowRewindButton(false)
    setShowPreviousButton(false)
    setShowNextButton(false)
    // Hide and disable the seek/progress bar entirely
    post {
        val seekBar = findViewById<android.view.View>(androidx.media3.ui.R.id.exo_progress)
        seekBar?.isEnabled = false
        seekBar?.visibility = android.view.View.GONE
        // Also hide the time labels next to the seek bar
        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_position)?.visibility = android.view.View.GONE
        findViewById<android.view.View>(androidx.media3.ui.R.id.exo_duration)?.visibility = android.view.View.GONE
    }
}

@Composable
private fun CockFightHlsStream(
    modifier: Modifier = Modifier,
    onSurfaceClick: (() -> Unit)? = null,
    usePlaybackControls: Boolean = false,
    /** Home page: loop packaged `cock_fight.mp4`. Cock Fight screen: live HLS. */
    useHomeLocalVideo: Boolean = false,
    /** Cock Fight screen: play packaged `cockfight_live.mp4` instead of HLS stream. */
    useLiveLocalVideo: Boolean = false,
    /** Dynamic URL from API (takes priority over HLS fallback when set). */
    liveStreamUrl: String? = null,
    /** Seconds to seek into the video for simulated-live joining. */
    liveSeekSeconds: Int = 0,
    /** When true (cock fight screen), user cannot skip forward / scrub on the timeline. */
    disallowPlaybackSeeking: Boolean = false,
    odds: List<CockfightOdd> = emptyList(),
    showFullscreenButton: Boolean = true,
    walletBalance: String = "",
    /** Tap balance pill in fullscreen (e.g. open deposit). Null = not clickable. */
    onWalletBalanceClick: (() -> Unit)? = null,
    /** After a successful Meron/Wala bet, server `wallet_balance` (e.g. "900.00") for UI refresh. */
    onWalletBalanceAfterBet: ((String) -> Unit)? = null,
    onFullscreenChanged: ((Boolean) -> Unit)? = null,
    startFullscreen: Boolean = false,
    /** Like mweb object-fit: cover — fill the 16:9 frame without side letterboxing (cock fight live screen). */
    cropVideoToFill: Boolean = false,
    /** Matches mweb `video` `ended`: show "Match Completed" when playback finishes (VoD/HLS end). */
    onPlaybackEnded: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var fullscreen by remember { mutableStateOf(false) }
    var betSelection by remember { mutableStateOf<CockfightBetSelection?>(null) }
    var showHistory by remember { mutableStateOf(false) }

    // Re-apply if something else touched orientation during recomposition
    LaunchedEffect(fullscreen) {
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Auto-enter fullscreen when opened from the cock fight screen
    LaunchedEffect(startFullscreen) {
        if (startFullscreen && !fullscreen) {
            fullscreen = true
            onFullscreenChanged?.invoke(true)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.let {
                    it.hide(android.view.WindowInsets.Type.systemBars())
                    it.systemBarsBehavior =
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN or
                    android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
            }
        }
    }

    val exoPlayer = remember(useHomeLocalVideo, useLiveLocalVideo, liveStreamUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            when {
                useHomeLocalVideo -> {
                val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.cock_fight}")
                setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_ALL
                volume = 0f
                }
                useLiveLocalVideo -> {
                    val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.cockfight_live}")
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
                    repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_ALL
                    volume = 1f
                }
                liveStreamUrl != null -> {
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(liveStreamUrl)))
                    repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_OFF
                    volume = 1f
                }
                else -> {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.parse(COCKFIGHT_LIVE_HLS_URL)))
                repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_OFF
                volume = 1f
                }
            }
            prepare()
            playWhenReady = true
        }
    }
    // Seek to simulated-live position once ready
    LaunchedEffect(exoPlayer, liveSeekSeconds) {
        if (liveSeekSeconds > 0) {
            var waited = 0
            while (exoPlayer.duration <= 0L && waited < 10000) {
                kotlinx.coroutines.delay(200); waited += 200
            }
            if (exoPlayer.duration > 0) {
                val seekMs = minOf(liveSeekSeconds * 1000L, exoPlayer.duration - 1000L)
                if (seekMs > 0) exoPlayer.seekTo(seekMs)
            }
        }
    }
    val onPlaybackEndedCb = androidx.compose.runtime.rememberUpdatedState(onPlaybackEnded)
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState != androidx.media3.common.Player.STATE_ENDED) return
                Handler(Looper.getMainLooper()).post {
                    onPlaybackEndedCb.value?.invoke()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, cropVideoToFill) {
        exoPlayer.videoScalingMode = if (cropVideoToFill) {
            androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        } else {
            androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        }
    }

    fun exitFullscreen() {
        fullscreen = false
        onFullscreenChanged?.invoke(false)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.show(
                android.view.WindowInsets.Type.systemBars()
            )
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
        }
    }

    fun enterFullscreen() {
        fullscreen = true
        onFullscreenChanged?.invoke(true)
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            activity?.window?.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.systemBars())
                it.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            activity?.window?.decorView?.systemUiVisibility = (
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                activity?.window?.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                activity?.window?.decorView?.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    if (fullscreen) {
        Popup(
            alignment = Alignment.Center,
            properties = PopupProperties(
                focusable = true,
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                clippingEnabled = false
            )
        ) {
            // System / predictive back: first gesture exits fullscreen only (second back on portrait screen leaves cock fight — like Gundu Ata).
            BackHandler {
                when {
                    betSelection != null -> betSelection = null
                    showHistory -> showHistory = false
                    else -> exitFullscreen()
                }
            }
            val view = LocalView.current
            SideEffect {
                val window = (view.parent as? android.view.ViewGroup)
                    ?.rootView
                    ?.let { (it.context as? android.app.Activity)?.window }
                window?.let { w ->
                    w.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        w.insetsController?.let {
                            it.hide(android.view.WindowInsets.Type.systemBars())
                            it.systemBarsBehavior =
                                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        w.decorView.systemUiVisibility = (
                            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        )
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Video player — controller fully disabled, touch intercepted below
                AndroidView(
                    factory = {
                        androidx.media3.ui.PlayerView(it).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = if (cropVideoToFill)
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else
                                androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                // Transparent overlay — consumes all taps so nothing pauses/stops the video
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) { /* swallow tap — video keeps playing */ }
                )

                // Right-edge swipe-left to go back (many phones use the right bezel for back; PlayerView can steal touches).
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(0.62f)
                        .width(44.dp)
                        .pointerInput(betSelection) {
                            var accumulated = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { accumulated = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    accumulated += dragAmount
                                    if (accumulated < -72f) {
                                        accumulated = 0f
                                        if (betSelection != null) {
                                            betSelection = null
                                        } else {
                                            exitFullscreen()
                                        }
                                    }
                                },
                                onDragEnd = { accumulated = 0f },
                                onDragCancel = { accumulated = 0f }
                            )
                        }
                )

                // Top bar: back arrow (left) + wallet balance (right, white bg)
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // ← Back arrow — exit fullscreen only; second back (portrait) leaves cock fight.
                    IconButton(onClick = { exitFullscreen() }) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.padding(8.dp))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = { showHistory = true }) {
                            Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f)) {
                                Icon(Icons.Default.History, contentDescription = "My Bets", tint = Color.White, modifier = Modifier.padding(8.dp).size(18.dp))
                            }
                        }
                        // Wallet balance — white pill on the top-right
                        if (walletBalance.isNotEmpty()) {
                            Surface(
                                onClick = { onWalletBalanceClick?.invoke() },
                                enabled = onWalletBalanceClick != null,
                                shape = RoundedCornerShape(20.dp),
                                color = Color.White
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = OrangePrimary, modifier = Modifier.size(16.dp))
                                    Text(walletBalance, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // LIVE badge below top bar (no deposit marquee in fullscreen)
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 48.dp, start = 8.dp, end = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LiveStreamBlinkingDot()
                            Text(
                                "LIVE",
                                color = Color(0xFFE53935),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                }

                // Bottom odds bar (only when no bet slip open) — sit lower, above system nav / home gesture
                if (betSelection == null && odds.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        odds.forEachIndexed { index, o ->
                            CockfightOddsBetCard(
                                label = o.label,
                                odd = o.odd,
                                baseColor = o.color,
                                staggerIndex = index,
                                modifier = Modifier.weight(1f),
                                onClick = { betSelection = CockfightBetSelection(o.label, o.odd, o.color, o.canonicalSide) }
                            )
                        }
                    }
                }

                // Bet slip overlay
                betSelection?.let { sel ->
                    FullscreenBetSlip(
                        selection = sel,
                        onDismiss = { betSelection = null },
                        onPlaceBet = { amount ->
                                val (ok, err) = postMeronWalaBet(sel.canonicalSide, amount)
                                if (ok != null) {
                                    onWalletBalanceAfterBet?.invoke(ok.walletBalance)
                                    null
                                } else {
                                    err
                                }
                        }
                    )
                }

                // Betting history overlay
                if (showHistory) {
                    CockfightBetHistoryPanel(onDismiss = { showHistory = false })
                }
            }
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .then(
                if (onSurfaceClick != null && !fullscreen) Modifier.clickable { onSurfaceClick() }
                else Modifier
            )
    ) {
        if (!fullscreen) {
            AndroidView(
                factory = {
                    androidx.media3.ui.PlayerView(it).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = if (cropVideoToFill)
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else
                            androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        if (!fullscreen) {
            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                LiveStreamBlinkingDot()
                Text("LIVE", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
            if (showFullscreenButton) {
            IconButton(
                onClick = { enterFullscreen() },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
            ) {
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.45f)) {
                    Icon(Icons.Default.Fullscreen, contentDescription = "Fullscreen", tint = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun CockfightHighlightVideoDialog(videoUrl: String, title: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val player = remember(videoUrl) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            setMediaItem(androidx.media3.common.MediaItem.fromUri(videoUrl))
            repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_OFF
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    BackHandler { onDismiss() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = {
                    androidx.media3.ui.PlayerView(it).apply {
                        this.player = player
                        useController = true
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                text = title,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(16.dp),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
private fun CockfightHighlightThumbnailCard(
    video: CockfightHighlightVideo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val hasVideo = video.videoUrl.isNotBlank()
    Card(
        modifier = modifier
            .height(132.dp)
            .then(
                if (hasVideo) Modifier.clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!video.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(ctx)
                        .data(video.thumbnailUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = video.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors =
                                    if (hasVideo) {
                                        listOf(Color(0xFF6A1B9A), Color(0xFFB71C1C))
                                    } else {
                                        listOf(Color(0xFF424242), Color(0xFF212121))
                                    }
                            )
                        )
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (hasVideo) 0.22f else 0.35f))
            )
            if (hasVideo) {
                Icon(
                    imageVector = Icons.Default.PlayCircleFilled,
                    contentDescription = "Play",
                    modifier = Modifier.align(Alignment.Center).size(44.dp),
                    tint = Color.White.copy(alpha = 0.95f)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SmartDisplay,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(36.dp),
                    tint = Color.White.copy(alpha = 0.45f)
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    video.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    if (hasVideo) "Rooster fight replay" else "Video coming soon",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun CockfightHighlightsGridSection(
    videos: List<CockfightHighlightVideo>,
    onPlay: (CockfightHighlightVideo) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Fight highlights",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            "Rooster fight replays",
            fontSize = 12.sp,
            color = Color(0xFF757575),
            modifier = Modifier.padding(top = 2.dp)
        )
        Spacer(Modifier.height(10.dp))
        val rows = videos.chunked(2)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { v ->
                        CockfightHighlightThumbnailCard(
                            video = v,
                            onClick = { onPlay(v) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMatchCard(onClick: () -> Unit = {}) {
    CockFightHlsStream(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .aspectRatio(16f / 9f),
        onSurfaceClick = onClick,
        usePlaybackControls = false,
        useHomeLocalVideo = true,
        showFullscreenButton = false
    )
}

@Composable
fun HighlightsSection(
    title: String,
    leftLabel: String,
    rightLabel: String,
    leftImage: Int,
    rightImage: Int,
    leftViews: String = "1.2k views",
    rightViews: String = "890 views"
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HenVideoCard(
                imageRes = leftImage,
                title = leftLabel,
                viewsLabel = leftViews,
                modifier = Modifier.weight(1f).height(132.dp)
            )
            HenVideoCard(
                imageRes = rightImage,
                title = rightLabel,
                viewsLabel = rightViews,
                modifier = Modifier.weight(1f).height(132.dp)
            )
        }
    }
}

@Composable
private fun HenVideoCard(
    imageRes: Int,
    title: String,
    viewsLabel: String,
    modifier: Modifier = Modifier.width(168.dp).height(132.dp)
) {
    Card(
        modifier = modifier.clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            DrawableImage(
                imageRes,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
            Icon(
                Icons.Default.PlayCircleFilled,
                contentDescription = "Play",
                modifier = Modifier.align(Alignment.Center).size(44.dp),
                tint = Color.White.copy(alpha = 0.95f)
            )
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                Text(
                    title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(viewsLabel, color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun PromotionsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text(
                "PROMOTIONS",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PromotionCard(
                title = "E‑Bike",
                referralBanner = "Refer 100 members",
                rewardDescription = "Get an e‑bike when 100 successful referrals complete signup.",
                imageRes = R.drawable.promotion_bike,
                modifier = Modifier.weight(1f)
            )
            PromotionCard(
                title = "Laptop",
                referralBanner = "Refer 50 members",
                rewardDescription = "Get a laptop when 50 successful referrals complete signup.",
                imageRes = R.drawable.promotion_laptop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun PromotionCard(
    title: String,
    referralBanner: String,
    rewardDescription: String,
    imageRes: Int,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.height(300.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = Color(0xFFFFD54F), shape = RoundedCornerShape(bottomEnd = 12.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    referralBanner,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF5D4037)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF5F5F5)),
                contentAlignment = Alignment.Center
            ) {
                DrawableImage(
                    imageRes,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Spacer(Modifier.height(6.dp))
                Text(
                    rewardDescription,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(selectedTab: String, onTabSelected: (String) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        val selectedColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.White,
            selectedTextColor = Color.White,
            unselectedIconColor = Color.Gray,
            unselectedTextColor = Color.Gray,
            indicatorColor = Color.Transparent
        )
        NavigationBarItem(selected = selectedTab == "home", onClick = { onTabSelected("home") }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") }, colors = selectedColors)
        NavigationBarItem(
            selected = selectedTab == "promotion",
            onClick = { onTabSelected("promotion") },
            icon = {
                Icon(
                    Icons.Filled.SportsSoccer,
                    contentDescription = "Promotion",
                    modifier = Modifier.size(26.dp)
                )
            },
            label = { Text("Promotion") },
            colors = selectedColors
        )
        NavigationBarItem(selected = selectedTab == "wallet", onClick = { onTabSelected("wallet") }, icon = { Icon(Icons.Default.Wallet, null) }, label = { Text("Wallet") }, colors = selectedColors)
        NavigationBarItem(selected = selectedTab == "profile", onClick = { onTabSelected("profile") }, icon = { Icon(Icons.Default.Person, null) }, label = { Text("Profile") }, colors = selectedColors)
    }
}
