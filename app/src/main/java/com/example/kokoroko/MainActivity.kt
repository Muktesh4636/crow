package com.example.kokoroko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kokoroko.R
import com.example.kokoroko.ui.theme.KokorokoTheme
import com.example.kokoroko.ui.theme.OrangePrimary
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val PKG_PHONEPE = "com.phonepe.app"
private const val PKG_GPay = "com.google.android.apps.nbu.paisa.user"
private const val PKG_PAYTM = "net.one97.paytm"

/** Live cricket feed: https://gunduata.club/api/cricket/live/ */
private const val CRICKET_ODDS_API_URL = "https://gunduata.club/api/cricket/live/"

private val cricketOddsHttpClient: OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(14, TimeUnit.SECONDS)
        .writeTimeout(14, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

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

private data class CricketEventUi(
    val matchTitle: String,
    val leagueLabel: String,
    val markets: List<CricketMarketUi>
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
        )
    )
}

/** Full API event: `description`, `eventPaths`, `markets[].description`, `markets[].outcomes[]`. */
private fun parseGunduataEventToUi(data: JSONObject): CricketEventUi {
    val matchTitle = data.optString("description", "").ifBlank { "Cricket" }
    val leagueLabel = leagueFromEventPaths(data.optJSONArray("eventPaths"))
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
    return CricketEventUi(matchTitle = matchTitle, leagueLabel = leagueLabel, markets = markets)
}

private fun parseEventDataObject(o: JSONObject): CricketEventUi? {
    if (o.has("markets") && o.optJSONArray("markets") != null) {
        return parseGunduataEventToUi(o)
    }
    return parseMatchObject(o)
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
    private const val CACHE_TTL_MS = 90_000L

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

/** WhatsApp / Telegram: digits only, international (91 = India + 987654321). */
private const val CONTACT_PHONE_WHATSAPP_TELEGRAM = "91987654321"

private fun Context.openWhatsAppToContact() {
    try {
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$CONTACT_PHONE_WHATSAPP_TELEGRAM")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(this, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

private fun Context.openTelegramToContact() {
    val tg = Uri.parse("tg://msg?to=+$CONTACT_PHONE_WHATSAPP_TELEGRAM")
    try {
        startActivity(Intent(Intent.ACTION_VIEW, tg))
    } catch (e: Exception) {
        try {
            val https = Uri.parse("https://t.me/+${CONTACT_PHONE_WHATSAPP_TELEGRAM}")
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

/** Drawables loaded via Coil; stable px sizing + remembered request reduce flicker during fast scroll. */
@Composable
private fun DrawableImage(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier) {
        val decodeSize = remember(constraints.maxWidth, constraints.maxHeight) {
            fun bucket(px: Int): Int = (((px.coerceIn(32, 2048) + 16) / 32) * 32).coerceIn(32, 2048)
            when {
                constraints.hasBoundedWidth && constraints.hasBoundedHeight ->
                    CoilSize(bucket(constraints.maxWidth), bucket(constraints.maxHeight))
                constraints.hasBoundedWidth -> {
                    val w = bucket(constraints.maxWidth)
                    CoilSize(w, w)
                }
                constraints.hasBoundedHeight -> {
                    val h = bucket(constraints.maxHeight)
                    CoilSize(h, h)
                }
                else -> CoilSize(512, 512)
            }
        }
        val imageRequest = remember(resId, decodeSize, context) {
            ImageRequest.Builder(context)
                .data(resId)
                .size(decodeSize)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(false)
                .build()
        }
        AsyncImage(
            model = imageRequest,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale
        )
    }
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

private fun Context.launchUpiPaymentChooser() {
    try {
        val uri =
            Uri.parse("upi://pay?pa=test@upi&pn=Hen%20Fight&am=1.00&cu=INR&tn=Wallet%20payment")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(Intent.createChooser(intent, "Complete payment with"))
    } catch (e: Exception) {
        Toast.makeText(this, "No UPI app available", Toast.LENGTH_SHORT).show()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KokorokoTheme {
                var currentScreen by remember { mutableStateOf("splash") }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "splash" -> SplashScreen { currentScreen = "login" }
                        "login" -> LoginScreen { currentScreen = "home" }
                        "home" -> MainScreen(onLogout = { currentScreen = "login" })
                    }
                }
            }
        }
    }
}

@Composable
fun MainScreen(onLogout: () -> Unit) {
    var selectedTab by remember { mutableStateOf("home") }
    var currentSubScreen by remember { mutableStateOf("main") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            CricketFeedStore.load()
        }
    }

    val onHomeMain = selectedTab == "home" && currentSubScreen == "main"
    BackHandler(enabled = !onHomeMain) {
        currentSubScreen = "main"
        selectedTab = "home"
    }

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
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when {
                currentSubScreen == "payment_options" ->
                    PaymentOptionsScreen(onBack = { currentSubScreen = "main" })
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
                currentSubScreen == "reset_pin" ->
                    ResetPinScreen(
                        onBack = { currentSubScreen = "main" },
                        onHome = {
                            selectedTab = "home"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "gundata_live" ->
                    GundataLiveScreen(
                        onBack = { currentSubScreen = "main" },
                        onWallet = {
                            selectedTab = "wallet"
                            currentSubScreen = "main"
                        },
                        onOpenProfile = {
                            selectedTab = "profile"
                            currentSubScreen = "main"
                        }
                    )
                currentSubScreen == "cock_fight_live" ->
                    CockFightLiveScreen(
                        onBack = { currentSubScreen = "main" },
                        onWallet = {
                            selectedTab = "wallet"
                            currentSubScreen = "main"
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
                            "wallet" -> WalletScreen(
                                onBack = { selectedTab = "home" },
                                onDepositClick = { currentSubScreen = "payment_options" }
                            )
                            "profile" -> ProfileScreen(
                                onBack = { selectedTab = "home" },
                                onLogout = onLogout,
                                onOpenProfileDetails = { currentSubScreen = "profile_details" },
                                onOpenReferralEarn = { currentSubScreen = "referral" },
                                onOpenResetPin = { currentSubScreen = "reset_pin" }
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
private fun CricketLiveBlinkingDot() {
    val infiniteTransition = rememberInfiniteTransition(label = "cricket_live_dot")
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
        CricketLiveBlinkingDot()
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
    var expandedStreamEventIndex by remember { mutableStateOf<Int?>(null) }
    var oddsFilterTab by remember { mutableStateOf(CricketOddsFilterTab.Main) }
    BackHandler {
        when {
            betSelection != null -> betSelection = null
            expandedStreamEventIndex != null -> expandedStreamEventIndex = null
            else -> onBack()
        }
    }
    val cachedFirst = remember { CricketFeedStore.peek().orEmpty() }
    var events by remember { mutableStateOf(cachedFirst) }
    var loading by remember { mutableStateOf(cachedFirst.isEmpty()) }
    LaunchedEffect(Unit) {
        events = CricketFeedStore.load()
        loading = false
    }
    betSelection?.let { sel ->
        CricketBetCardDialog(
            selection = sel,
            onDismiss = { betSelection = null },
            onPlaceBet = { betSelection = null }
        )
    }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
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
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = CricketOddsAccent.copy(alpha = 0.14f)
            ) {
                Text(
                    "LIVE",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = CricketOddsAccent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CricketOddsAccent)
            }
        } else if (events.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No cricket odds available right now.",
                    fontSize = 15.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                events.forEachIndexed { eIdx, event ->
                    item(key = "ev_head_$eIdx") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text(
                                event.matchTitle,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 18.sp,
                                color = Color(0xFF1A1A1A),
                                lineHeight = 24.sp,
                                letterSpacing = 0.2.sp
                            )
                            if (event.leagueLabel.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    event.leagueLabel,
                                    fontSize = 13.sp,
                                    color = Color(0xFF757575),
                                    fontWeight = FontWeight.Normal,
                                    maxLines = 3,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                    item(key = "ev_stream_$eIdx") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                                .clickable { expandedStreamEventIndex = eIdx }
                        ) {
                            CricketLiveStreamTopBar(
                                modifier = Modifier.align(Alignment.TopEnd)
                            )
                            Text(
                                text = "Match starts at 6:00 PM",
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White.copy(alpha = 0.88f),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    item(key = "ev_odds_tabs_$eIdx") {
                        CricketOddsFilterTabs(
                            selected = oddsFilterTab,
                            onSelect = { oddsFilterTab = it }
                        )
                    }
                    val filteredMarkets = filterCricketMarkets(event.markets, oddsFilterTab)
                    if (filteredMarkets.isEmpty()) {
                        item(key = "ev_markets_empty_$eIdx") {
                            Text(
                                text = "No markets in this category.",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                fontSize = 14.sp,
                                color = Color(0xFF78909C),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(
                            count = filteredMarkets.size,
                            key = { mIdx ->
                                val q = filteredMarkets[mIdx].question
                                "ev_${eIdx}_m_${mIdx}_${q.hashCode()}"
                            }
                        ) { mIdx ->
                            CricketMarketOddsCard(
                                market = filteredMarkets[mIdx],
                                matchTitle = event.matchTitle,
                                onOddClick = { betSelection = it }
                            )
                        }
                    }
                }
            }
        }
    }
    expandedStreamEventIndex?.let { idx ->
        val event = events.getOrNull(idx) ?: return@let
        Dialog(
            onDismissRequest = { expandedStreamEventIndex = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black
            ) {
                Box(Modifier.fillMaxSize()) {
                    IconButton(
                        onClick = { expandedStreamEventIndex = null },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        CricketLiveBlinkingDot()
                        Text(
                            text = "LIVE",
                            color = CricketStreamLiveRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                    Text(
                        text = "Match starts at 6:00 PM",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = event.matchTitle,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp, vertical = 36.dp),
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 3
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

@Composable
fun CockFightLiveScreen(
    onBack: () -> Unit,
    onWallet: () -> Unit,
    onOpenProfile: () -> Unit
) {
    BackHandler { onBack() }
    val context = LocalContext.current
    var selectedChip by remember { mutableStateOf(100) }
    val chips = listOf(50, 100, 200, 300, 500, 1000, 2500, 5000)
    val roadmapPattern = remember {
        listOf(
            listOf("M", "W", "M", "D", "W", "M", "M", "W", "W", "M", "D", "M"),
            listOf("W", "M", "W", "W", "D", "M", "W", "M", "M", "W", "M", "W"),
            listOf("M", "M", "W", "D", "W", "W", "M", "M", "D", "W", "M", "M"),
            listOf("W", "W", "M", "M", "W", "D", "M", "W", "M", "M", "W", "D"),
            listOf("M", "D", "W", "M", "M", "W", "W", "M", "W", "M", "W", "M"),
            listOf("W", "M", "M", "W", "W", "M", "D", "W", "M", "W", "M", "W")
        )
    }

    Column(Modifier.fillMaxSize().background(CockDarkBg)) {
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
            Surface(
                color = OrangePrimary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).clickable { onWallet() }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wallet, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("₹0", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = OrangePrimary.copy(alpha = 0.35f)
        ) {
            Text(
                "Welcome bonus — bet ₹500 free on your first deposit!",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                fontSize = 11.sp,
                color = Color.Black,
                maxLines = 2
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(248.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black)
                ) {
                    Text(
                        text = "Live coming soon",
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                        Text(
                            text = "LIVE",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(CockMeronRed.copy(alpha = 0.95f), CockWalaBlue.copy(alpha = 0.95f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Meron  VS  Wala",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable {
                                Toast.makeText(context, "Meron @ 1.90", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = CockMeronRed
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("1.90X", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("Meron", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable {
                                Toast.makeText(context, "Draw @ 4.46", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = CockDrawGreen
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("4.46X", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("Draw", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable {
                                Toast.makeText(context, "Wala @ 1.92", Toast.LENGTH_SHORT).show()
                            },
                        shape = RoundedCornerShape(10.dp),
                        color = CockWalaBlue
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("1.92X", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Text("Wala", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(chips.size) { i ->
                        val v = chips[i]
                        val sel = selectedChip == v
                        Surface(
                            shape = CircleShape,
                            color = if (sel) OrangePrimary else Color(0xFF2A2A2A),
                            modifier = Modifier
                                .size(44.dp)
                                .clickable { selectedChip = v },
                            border = if (sel) BorderStroke(2.dp, Color.White) else null
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "$v",
                                    color = if (sel) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onOpenProfile) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2A2A2A),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Button(
                        onClick = {
                            Toast.makeText(
                                context,
                                "Place bet ₹$selectedChip",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Place Bet…", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    IconButton(onClick = onWallet) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF2A2A2A),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(Icons.Default.Wallet, null, tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RoadmapLegendDot(CockMeronRed, "Meron")
                            RoadmapLegendDot(CockDrawGreen, "Draw")
                            RoadmapLegendDot(CockWalaBlue, "Wala")
                            RoadmapLegendDot(Color.Gray, "Cancel")
                        }
                        Spacer(Modifier.height(10.dp))
                        roadmapPattern.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                row.forEach { cell ->
                                    val c = when (cell) {
                                        "M" -> CockMeronRed
                                        "W" -> CockWalaBlue
                                        "D" -> CockDrawGreen
                                        else -> Color.Gray
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoadmapLegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, fontSize = 10.sp, color = Color.DarkGray)
    }
}

@Composable
fun GundataLiveScreen(
    onBack: () -> Unit,
    onWallet: () -> Unit,
    onOpenProfile: () -> Unit
) {
    BackHandler { onBack() }
    var region by remember { mutableStateOf("andhra") }
    var selectedDice by remember { mutableStateOf<Int?>(null) }
    var selectedChip by remember { mutableStateOf(100) }
    val chips = listOf(100, 200, 300, 500, 800, 1000)
    var mainAction by remember { mutableStateOf("Please wait...") }
    val diceWords = listOf("One", "Two", "Three", "Four", "Five", "Six")
    val historyResults = listOf(3, 4, 1, 6, 2, 5, 4, 2)

    Column(Modifier.fillMaxSize().background(Color.White)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Text("Gundata LIVE", fontWeight = FontWeight.Medium, fontSize = 18.sp, color = Color.DarkGray)
            Surface(
                color = OrangePrimary,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(40.dp).clickable { onWallet() }
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Wallet, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("₹0", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE53935))
                        )
                        Text(
                            text = "LIVE",
                            color = Color(0xFFE53935),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            color = if (region == "andhra") OrangePrimary else Color(0xFFF0F0F0),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { region = "andhra" }
                        ) {
                            Text(
                                "Andhra",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (region == "andhra") Color.White else Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Surface(
                            color = if (region == "telangana") OrangePrimary else Color(0xFFF0F0F0),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.clickable { region = "telangana" }
                        ) {
                            Text(
                                "Telangana",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                color = if (region == "telangana") Color.White else Color.Black,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    IconButton(onClick = { }) {
                        Icon(Icons.Default.VolumeUp, contentDescription = "Sound", tint = Color.Black)
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select number (1–6)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..2) {
                            val n = i + 1
                            GundataDiceCard(
                                num = n,
                                word = diceWords[i],
                                emoji = GUNDATA_DICE_FACE[i],
                                selected = selectedDice == n,
                                onClick = { selectedDice = n },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 3..5) {
                            val n = i + 1
                            GundataDiceCard(
                                num = n,
                                word = diceWords[i],
                                emoji = GUNDATA_DICE_FACE[i],
                                selected = selectedDice == n,
                                onClick = { selectedDice = n },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            item {
                LazyRow(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chips) { v ->
                        val sel = selectedChip == v
                        Surface(
                            shape = CircleShape,
                            color = if (sel) OrangePrimary else Color(0xFF424242),
                            modifier = Modifier
                                .size(48.dp)
                                .clickable { selectedChip = v },
                            border = if (sel) BorderStroke(3.dp, Color.Black) else null
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "$v",
                                    color = if (sel) Color.Black else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GundataSquareIconButton(icon = Icons.Default.Settings, onClick = onOpenProfile)
                    GundataSquareIconButton(icon = Icons.Default.History) { mainAction = "History opened" }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable {
                                mainAction = when (mainAction) {
                                    "Please wait..." -> "Select amount (${selectedChip})"
                                    else -> "Please wait..."
                                }
                            },
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFE8E8E8),
                        border = BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                            Text(
                                mainAction,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                maxLines = 2
                            )
                        }
                    }
                    GundataSquareIconButton(icon = Icons.Default.Wallet) { onWallet() }
                }
            }

            item {
                Text(
                    "Recent results",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(historyResults) { index, result ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${9 + index}", fontSize = 11.sp, color = Color.Gray)
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = RoundedCornerShape(4.dp),
                                border = BorderStroke(1.dp, Color.LightGray),
                                color = Color.White
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Text("$result", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
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
private fun GundataDiceCard(
    num: Int,
    word: String,
    emoji: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(102.dp).clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = if (selected) OrangePrimary.copy(alpha = 0.35f) else Color.White,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) OrangePrimary else Color.LightGray
        ),
        shadowElevation = if (selected) 2.dp else 1.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text("$num", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Text(emoji, fontSize = 26.sp, textAlign = TextAlign.Center)
            Text(word, fontSize = 11.sp, color = Color.DarkGray)
        }
    }
}

@Composable
private fun GundataSquareIconButton(icon: ImageVector, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.size(48.dp).clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF0F0F0),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
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
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val correctPin = "4636"

    LaunchedEffect(pin) {
        if (pin.length == 4) {
            if (pin == correctPin) {
                onLoginSuccess()
            } else {
                delay(500)
                pin = ""
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.White).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeaderIconItem("Tutorials", imageRes = R.drawable.gamelogo)
            HeaderIconItem("Cockfight", imageRes = R.drawable.category_cockfight)
            HeaderIconItem("Dice Play", imageRes = R.drawable.category_gunduata)
            HeaderIconItem("Cricket", imageRes = R.drawable.category_cricket)
        }
        Spacer(modifier = Modifier.height(48.dp))
        DrawableImage(
            R.drawable.gamelogo,
            contentDescription = "Logo",
            modifier = Modifier.size(150.dp).clip(CircleShape).border(2.dp, Color.Black, CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text("Login Password", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Logout & login if you forgot your pin!", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                Surface(
                    modifier = Modifier.size(48.dp).clickable { if (pin.length < 4) pin += correctPin[pin.length] },
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (pin.length > index) Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Color.Black))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row {
            Text("Didn't remember code? ", fontSize = 13.sp, color = Color.Gray)
            Text("Logout!", fontSize = 13.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.weight(1f))
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(56.dp), shape = CircleShape, border = BorderStroke(1.dp, Color(0xFFFFEBEE)), color = Color.White) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.PowerSettingsNew, contentDescription = null, tint = Color.Red) }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Surface(
                modifier = Modifier.width(220.dp).height(56.dp).clickable(enabled = pin == correctPin) { onLoginSuccess() },
                shape = RoundedCornerShape(28.dp),
                color = if (pin == correctPin) Color.Black else Color.Gray
            ) {
                Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Login", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
                }
            }
        }
        Row(modifier = Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayCircle, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Watch Tutorials", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailsScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }

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
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
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
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Name", color = Color.Gray) },
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
            Spacer(Modifier.height(24.dp))
            Text("Gender", color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { gender = "Male" }
                ) {
                    RadioButton(
                        selected = gender == "Male",
                        onClick = { gender = "Male" },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OrangePrimary,
                            unselectedColor = OrangePrimary
                        )
                    )
                    Text("Male", color = Color.Black, fontSize = 16.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { gender = "Female" }
                ) {
                    RadioButton(
                        selected = gender == "Female",
                        onClick = { gender = "Female" },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = OrangePrimary,
                            unselectedColor = OrangePrimary
                        )
                    )
                    Text("Female", color = Color.Black, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(32.dp))
        }

        Button(
            onClick = { },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp),
            shape = RoundedCornerShape(28.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text("Update Details", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferralScreen(onBack: () -> Unit, onHome: () -> Unit) {
    BackHandler { onBack() }
    val referralCodeDisplay = "A G H M U 5 4 5"
    val referralCodeCopy = "AGHMU545"
    val clipboard = LocalClipboardManager.current

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
                Text(
                    "Receive 2% Commission.",
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
                        text = referralCodeDisplay,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        letterSpacing = 2.sp
                    )
                    IconButton(onClick = { clipboard.setText(AnnotatedString(referralCodeCopy)) }) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            onClick = { },
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

@Composable
fun ProfileScreen(onBack: () -> Unit, onLogout: () -> Unit, onOpenProfileDetails: () -> Unit, onOpenReferralEarn: () -> Unit, onOpenResetPin: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
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
            item { ProfileMenuItem("Reset Login PIN", icon = Icons.Default.LockReset, onClick = onOpenResetPin) }
            item { ProfileMenuItem("Referral & Earn", icon = Icons.Default.Share, onClick = onOpenReferralEarn) }

            item { Divider(modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), color = Color.LightGray) }

            item { SectionHeader("Contact / Support:") }
            item { ProfileMenuItem("Whatsapp", icon = Icons.Default.Chat, iconColor = Color(0xFF4CAF50), onClick = { context.openWhatsAppToContact() }) }
            item { ProfileMenuItem("Telegram", icon = Icons.Default.Send, iconColor = Color(0xFF2196F3), onClick = { context.openTelegramToContact() }) }
            item { ProfileMenuItem("Facebook", icon = Icons.Default.Facebook, iconColor = Color(0xFF1877F2)) }
            item { ProfileMenuItem("Instagram", iconRes = R.drawable.social_instagram, onClick = { context.openInstagramApp() }) }
            item { ProfileMenuItem("Youtube", iconRes = R.drawable.social_youtube, onClick = { context.openYoutubeApp() }) }

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
fun PaymentOptionsScreen(onBack: () -> Unit) {
    BackHandler { onBack() }
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.Black) }
            Spacer(modifier = Modifier.width(8.dp))
            Text("Payment Options", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        LazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Card(modifier = Modifier.padding(16.dp).size(250.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)), elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(180.dp), tint = Color.Black)
                            Text("Scan QR to Pay", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
                        }
                    }
                }
            }
            item { Text("Select Payment Method:", modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black) }
            item { PaymentMethodItem("PhonePe", Icons.Default.AccountBalanceWallet, Color(0xFF673AB7)) { context.launchAppOrToast(PKG_PHONEPE) } }
            item { PaymentMethodItem("Google Pay", Icons.Default.Payment, Color(0xFF4285F4)) { context.launchAppOrToast(PKG_GPay) } }
            item { PaymentMethodItem("UPI ID", Icons.Default.QrCodeScanner, Color(0xFF4CAF50)) { context.launchUpiPaymentChooser() } }
            item { PaymentMethodItem("Paytm", Icons.Default.AccountBalance, Color(0xFF00B9F1)) { context.launchAppOrToast(PKG_PAYTM) } }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun PaymentMethodItem(name: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(72.dp).clickable { onClick() }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF5F5F5), border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(48.dp), shape = CircleShape, color = color.copy(alpha = 0.1f)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp)) }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(name, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f), color = Color.Black)
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
        }
    }
}

@Composable
fun WalletScreen(onBack: () -> Unit, onDepositClick: () -> Unit) {
    BackHandler { onBack() }
    var selectedTab by remember { mutableStateOf("deposit") }
    var amount by remember { mutableStateOf("1000") }
    var paymentMethod by remember { mutableStateOf("upi") }
    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null, tint = Color.Black) }
            Text("Wallet", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Surface(color = OrangePrimary, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.History, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("₹0", fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp), shape = RoundedCornerShape(8.dp), color = Color(0xFFF5F5F5)) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Surface(modifier = Modifier.weight(1f).fillMaxHeight().clickable { selectedTab = "deposit" }, color = if (selectedTab == "deposit") Color.Black else Color.Transparent, shape = RoundedCornerShape(8.dp)) { Box(contentAlignment = Alignment.Center) { Text("Deposit", color = if (selectedTab == "deposit") Color.White else Color.Black, fontWeight = FontWeight.Bold) } }
                        Surface(modifier = Modifier.weight(1f).fillMaxHeight().clickable { selectedTab = "withdrawal" }, color = if (selectedTab == "withdrawal") Color.Black else Color.Transparent, shape = RoundedCornerShape(8.dp)) { Box(contentAlignment = Alignment.Center) { Text("Withdrawal", color = if (selectedTab == "withdrawal") Color.White else Color.Black, fontWeight = FontWeight.Bold) } }
                    }
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth().padding(16.dp), color = Color(0xFFFFEBEE), shape = RoundedCornerShape(8.dp)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            if (selectedTab == "deposit") "Minimum amount of 100 required to deposit." else "Withdrawal will be processed in 4 - 48 Hrs.",
                            fontSize = 12.sp,
                            color = Color.Black
                        )
                    }
                }
            }
            if (selectedTab == "withdrawal") { item { Text("• Max amount available for withdrawal - ₹0.", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontSize = 14.sp, color = Color.Black) } }
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    val upiSelected = paymentMethod == "upi"
                    val bankSelected = paymentMethod == "bank"
                    val upiTint = if (upiSelected) Color.White else Color.Black
                    val bankTint = if (bankSelected) Color.White else Color.Black
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable { paymentMethod = "upi" },
                        color = if (upiSelected) OrangePrimary else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp),
                        border = if (upiSelected) null else BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Row { Icon(Icons.Default.QrCode, null, tint = upiTint); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.QrCodeScanner, null, tint = upiTint) }
                                Icon(Icons.Default.Star, null, tint = upiTint)
                            }
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text("UPI", color = upiTint, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Commission 0%", color = if (upiSelected) Color.White.copy(alpha = 0.8f) else Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(120.dp)
                            .clickable { paymentMethod = "bank" },
                        color = if (bankSelected) OrangePrimary else Color(0xFFF5F5F5),
                        shape = RoundedCornerShape(12.dp),
                        border = if (bankSelected) null else BorderStroke(1.dp, Color.LightGray)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Icon(Icons.Default.CreditCard, null, tint = bankTint)
                                if (selectedTab == "withdrawal") Icon(Icons.Default.Star, null, tint = if (bankSelected) Color.White.copy(alpha = 0.7f) else Color.LightGray)
                            }
                            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                                Text("Bank Account", color = bankTint, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Commission 0%", color = if (bankSelected) Color.White.copy(alpha = 0.8f) else Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color.LightGray)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            if (selectedTab == "deposit") amount else "0",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }
            if (selectedTab == "deposit") { item { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("500", "1000", "2000", "5000", "10000").forEach { valStr -> Surface(modifier = Modifier.weight(1f).height(40.dp).clickable { amount = valStr }, color = if (amount == valStr) Color.Black else Color(0xFFF5F5F5), shape = RoundedCornerShape(4.dp)) { Box(contentAlignment = Alignment.Center) { Text("+₹$valStr", color = if (amount == valStr) Color.White else Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold) } } } } } }
            item { Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(if (selectedTab == "deposit") "How to Deposit:" else "How to withdrawal :", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black); Surface(border = BorderStroke(1.dp, Color.LightGray), shape = RoundedCornerShape(4.dp), color = Color.White) { Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.PlayCircle, null, tint = Color.Red, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Watch Tutorial", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Black) } } } }
            if (selectedTab == "deposit") { item { Spacer(modifier = Modifier.height(32.dp)); Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp).clickable { onDepositClick() }, shape = RoundedCornerShape(28.dp), color = Color.Black) { Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text("Deposit amount", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Icon(Icons.Default.ArrowForward, null, tint = Color.White) } }; Spacer(modifier = Modifier.height(32.dp)) } }
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
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5)),
        state = listState
    ) {
        item(key = "top_header", contentType = "header") {
            TopHeader(
                onDicePlayClick = onOpenGundata,
                onCricketClick = onOpenCricket,
                onCockfightClick = onOpenCockfight,
                onWalletClick = onWalletClick
            )
        }
        item(key = "banner", contentType = "banner") { BannerCarousel(homeListState = listState) }
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
        item(key = "highlights_cf_1", contentType = "highlights") {
            HighlightsSection(
                title = "Popular Cock Fight Highlights -",
                leftLabel = "KOKOROKO TRAILER",
                rightLabel = "BAAHUBALI",
                leftImage = R.drawable.category_cockfight,
                rightImage = R.drawable.match_meron
            )
        }
        item(key = "highlights_dice_1", contentType = "highlights") {
            HighlightsSection(
                title = "Popular Diceplay Highlights -",
                leftLabel = "GUNDATA",
                rightLabel = "SANKRANTI",
                leftImage = R.drawable.category_gunduata,
                rightImage = R.drawable.banner_home_2
            )
        }
        item(key = "highlights_cf_2", contentType = "highlights") {
            HighlightsSection(
                title = "Popular Cock Fight Highlights -",
                leftLabel = "Arena highlight",
                rightLabel = "Best moments",
                leftImage = R.drawable.match_wala,
                rightImage = R.drawable.banner_home_1
            )
        }
        item(key = "highlights_dice_2", contentType = "highlights") {
            HighlightsSection(
                title = "Popular Diceplay Highlights -",
                leftLabel = "GUNDATA",
                rightLabel = "SANKRANTI",
                leftImage = R.drawable.category_gunduata,
                rightImage = R.drawable.banner_home_3
            )
        }
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
}

@Composable
fun TopHeader(
    onDicePlayClick: () -> Unit = {},
    onCricketClick: () -> Unit = {},
    onCockfightClick: () -> Unit = {},
    onWalletClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawableImage(R.drawable.gamelogo, "Logo", modifier = Modifier.size(50.dp).clip(CircleShape).border(1.dp, Color.LightGray, CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(8.dp))
            HeaderIconItem("Cockfight", imageRes = R.drawable.category_cockfight, onClick = onCockfightClick)
            HeaderIconItem("Dice Play", imageRes = R.drawable.category_gunduata, onClick = onDicePlayClick)
            HeaderIconItem("Cricket", imageRes = R.drawable.category_cricket, onClick = onCricketClick)
        }
        Surface(
            color = OrangePrimary,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .height(40.dp)
                .clickable { onWalletClick() },
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wallet, contentDescription = "Wallet", tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("₹0", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
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
fun BannerCarousel(homeListState: LazyListState) {
    val banners = remember {
        listOf(
            R.drawable.banner_home_1,
            R.drawable.banner_home_2,
            R.drawable.banner_home_3
        )
    }
    val rowState = rememberLazyListState()
    val snapFling = rememberSnapFlingBehavior(lazyListState = rowState)
    // LazyRow (horizontal) inside LazyColumn (vertical) avoids HorizontalPager nested-scroll fighting
    // the feed. Do not advance while the home list is scrolling.
    LaunchedEffect(banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(3000)
            while (homeListState.isScrollInProgress) {
                delay(48)
            }
            val current = rowState.firstVisibleItemIndex.coerceIn(0, banners.lastIndex)
            val next = (current + 1) % banners.size
            rowState.scrollToItem(next)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.LightGray)
    ) {
        LazyRow(
            state = rowState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = snapFling,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(
                count = banners.size,
                key = { index -> banners[index] }
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .fillMaxHeight()
                ) {
                    DrawableImage(
                        banners[page],
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
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
            GameIconItem(
                "Cricket",
                R.drawable.category_cricket,
                onClick = onCricketClick,
                hideSoonBadge = true
            )
            GameIconItem("Promotions", R.drawable.category_promotions, onClick = onPromotionsClick)
        }
    }
}

@Composable
fun GameIconItem(
    label: String,
    imageRes: Int,
    onClick: (() -> Unit)? = null,
    hideSoonBadge: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
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
            if (hideSoonBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 3.dp)
                        .width(44.dp)
                        .height(20.dp)
                        .background(Color(0xFFECECEC), RoundedCornerShape(6.dp))
                )
            }
        }
    }
}

@Composable
fun LiveCockFightHeader(liveEnabled: Boolean, onLiveChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Surface(color = Color.White, shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color.Black)) {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                DrawableImage(
                    R.drawable.category_cockfight,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    contentScale = ContentScale.Fit
                )
                Spacer(Modifier.width(8.dp))
                Text("Cock Fight", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
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

@Composable
fun LiveMatchCard(onClick: () -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DrawableImage(
                        R.drawable.match_meron,
                        contentDescription = "Meron",
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text("Meron", fontWeight = FontWeight.Bold, color = Color.Black)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text("31-03-2026", fontSize = 12.sp, color = Color.Gray); Text("Vs", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black); Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(OrangePrimary)); Spacer(Modifier.width(4.dp)); Text("1 : 4.5", fontSize = 12.sp, color = Color.Black) } }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    DrawableImage(
                        R.drawable.match_wala,
                        contentDescription = "Wala",
                        modifier = Modifier.size(80.dp).clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Text("Wala", fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Surface(modifier = Modifier.weight(1f).height(50.dp), color = Color(0xFFFFF0F0), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFFFCCCC))) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("1 : 1.9300000", color = Color.Red, fontSize = 12.sp); Text("000000002", color = Color.Red, fontSize = 10.sp) } }
                Spacer(Modifier.width(8.dp)); Surface(modifier = Modifier.weight(1f).height(50.dp), color = OrangePrimary, shape = RoundedCornerShape(4.dp)) { Box(contentAlignment = Alignment.Center) { Text("24/7 Live", color = Color.Black, fontWeight = FontWeight.Bold) } }
                Spacer(Modifier.width(8.dp)); Surface(modifier = Modifier.weight(1f).height(50.dp), color = Color(0xFFF0F8FF), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFFCCE5FF))) { Box(contentAlignment = Alignment.Center) { Text("1 : 1.94", color = Color(0xFF007BFF)) } }
            }
        }
    }
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
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = null) }
            Text("PROMOTIONS", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Surface(color = OrangePrimary, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(40.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    DrawableImage(
                        R.drawable.category_promotions,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp).clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            PromotionCard("Bike", "₹155000", "₹20000", "₹0", R.drawable.promotion_bike, Modifier.weight(1f))
            PromotionCard("Laptop", "₹80000", "₹100", "₹10", R.drawable.promotion_laptop, Modifier.weight(1f))
        }
    }
}

@Composable
fun PromotionCard(title: String, originalPrice: String, discountPrice: String, minWallet: String, imageRes: Int, modifier: Modifier = Modifier) {
    Card(modifier = modifier.height(280.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Surface(color = Color(0xFFFFD54F), shape = RoundedCornerShape(bottomEnd = 12.dp), modifier = Modifier.padding(bottom = 8.dp)) { Text("Min Wallet - $minWallet", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontWeight = FontWeight.Bold) }
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
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text(originalPrice, fontSize = 12.sp, color = Color.LightGray, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough); Text(discountPrice, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 12.dp), color = Color.LightGray.copy(alpha = 0.5f)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(20.dp)) } }
                }
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
