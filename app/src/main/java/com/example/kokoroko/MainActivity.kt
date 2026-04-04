package com.example.kokoroko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.ui.viewinterop.AndroidView
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kokoroko.BuildConfig
import com.example.kokoroko.R
import com.example.kokoroko.ui.theme.KokorokoTheme
import com.example.kokoroko.ui.theme.OrangePrimary
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

private const val PKG_PHONEPE = "com.phonepe.app"
private const val PKG_GPay = "com.google.android.apps.nbu.paisa.user"
private const val PKG_PAYTM = "net.one97.paytm"

/** Wallet screen: warm brown ink (readable, no black/charcoal) */
private val WalletInkWarm = Color(0xFF6D4C41)

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

/** Live cricket feed */
private val CRICKET_ODDS_API_URL = apiUrl("/api/cricket/live/")

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
private val AUTH_LOGOUT_URL = apiUrl("/api/auth/logout/")
private val AUTH_BANK_DETAILS_URL = apiUrl("/api/auth/bank-details/")
private val AUTH_WITHDRAW_INITIATE_URL = apiUrl("/api/auth/withdraws/initiate/")
private val AUTH_DEPOSITS_MINE_URL = apiUrl("/api/auth/deposits/mine/")
private val AUTH_WITHDRAWS_MINE_URL = apiUrl("/api/auth/withdraws/mine/")
private val SUPPORT_CONTACTS_API_URL = apiUrl("/api/support/contacts/")
private val MAINTENANCE_STATUS_URL = apiUrl("/api/maintenance/status/")
private val GAME_VERSION_URL = apiUrl("/api/game/version/")

private const val PREFS_AUTH = "auth_prefs"
private const val PREFS_AUTH_TOKEN_KEY = "access_token"

private object AuthTokenStore {
    @Volatile
    var accessToken: String? = null

    fun load(context: Context) {
        accessToken = context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .getString(PREFS_AUTH_TOKEN_KEY, null)
    }

    fun save(context: Context, token: String) {
        accessToken = token
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit().putString(PREFS_AUTH_TOKEN_KEY, token).apply()
    }

    fun clear(context: Context) {
        accessToken = null
        context.getSharedPreferences(PREFS_AUTH, Context.MODE_PRIVATE)
            .edit().remove(PREFS_AUTH_TOKEN_KEY).apply()
    }
}

private data class BankDetailsUi(
    val accountHolder: String,
    val bankName: String,
    val accountNumber: String,
    val ifsc: String,
    val branch: String = ""
)

/** Parsed from [AUTH_WALLET_URL] — balances, bank, UPI/QR, and optional payment method rows */
private data class WalletApiResult(
    val bank: BankDetailsUi?,
    val upiId: String?,
    val qrImageUrl: String?,
    val paymentMethods: List<WalletPaymentMethodItem>?,
    val walletId: Int? = null,
    /** Total balance (string or number from API, e.g. "1500.50") */
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
    val walletId = if (data.has("id")) data.optInt("id") else null
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
                    resp.code == 401 ->
                        Pair(null, "Session expired. Please sign in again.")
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
                val access = j?.optString("access", "")?.ifBlank { j.optString("token", "") }.orEmpty()
                if (access.isNotBlank()) {
                    AuthTokenStore.save(context, access)
                    return@withContext LoginResult(true)
                }
                LoginResult(false, "No token in response. Please contact support.")
            }
        } catch (e: Exception) {
            LoginResult(false, "Network error: ${e.message}")
        }
    }

/** Matches GET/POST [AUTH_PROFILE_URL] — gender is MALE | FEMALE | OTHER | null. */
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
    val id = data.optInt("id", 0)
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

private suspend fun fetchAuthProfile(): Pair<ProfileDetailsApi?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load your profile.")
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
                    resp.code == 401 -> Pair(null, "Session expired. Please sign in again.")
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
    val isDefault: Boolean
) {
    fun toBankDetailsUi(): BankDetailsUi =
        BankDetailsUi(accountName, bankName, accountNumber, ifscCode, "")

    fun toBankDetailsUiOrNull(): BankDetailsUi? {
        val b = toBankDetailsUi()
        if (b.accountHolder.isBlank() && b.bankName.isBlank() && b.accountNumber.isBlank() && b.ifsc.isBlank()) {
            return null
        }
        return b
    }
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
    val root: JSONObject =
        when {
            t.startsWith("[") -> {
                val arr = JSONArray(t)
                if (arr.length() == 0) return null
                arr.getJSONObject(0)
            }
            else -> {
                val j = JSONObject(t)
                when {
                    j.has("data") && j.optJSONObject("data") != null -> j.getJSONObject("data")
                    (j.optJSONArray("results")?.length() ?: 0) > 0 -> j.getJSONArray("results").getJSONObject(0)
                    else -> j
                }
            }
        }
    return AuthBankDetailsApi(
        accountName = root.optString("account_name", "").trim(),
        bankName = root.optString("bank_name", "").trim(),
        accountNumber = root.optString("account_number", "").trim(),
        ifscCode = root.optString("ifsc_code", "").trim(),
        upiId = root.optString("upi_id", "").trim(),
        isDefault = root.optBoolean("is_default", false)
    )
}

private suspend fun fetchAuthBankDetails(): Pair<AuthBankDetailsApi?, String?> =
    withContext(Dispatchers.IO) {
        val token = AuthTokenStore.accessToken
            ?: return@withContext Pair(null, "Sign in to load bank details.")
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
                    resp.code == 401 -> Pair(null, "Session expired. Please sign in again.")
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

/** Fallback when /api/support/contacts/ fails or returns empty */
private const val CONTACT_PHONE_WHATSAPP_TELEGRAM = "91987654321"

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

private suspend fun fetchSupportContacts(): SupportContactsUi? =
    withContext(Dispatchers.IO) {
        try {
            val token = AuthTokenStore.accessToken
            val reqBuilder =
                Request.Builder()
                    .url(SUPPORT_CONTACTS_API_URL)
                    .header("Accept", "application/json")
            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }
            val req = reqBuilder.get().build()
            cricketOddsHttpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful || text.isBlank()) return@withContext null
                val root = JSONObject(text)
                val j = root.optJSONObject("data") ?: root
                val wa =
                    j.optString("whatsapp_number", "")
                        .ifBlank { j.optString("whatsapp", "") }
                        .trim()
                        .ifBlank { null }
                val tg = j.optString("telegram", "").trim().ifBlank { null }
                SupportContactsUi(
                    whatsappNumber = wa,
                    telegram = tg,
                    facebookUrl = j.optTrimmedUrlOrNull("facebook", "facebook_url"),
                    instagramUrl = j.optTrimmedUrlOrNull("instagram", "instagram_url"),
                    youtubeUrl = j.optTrimmedUrlOrNull("youtube", "youtube_url")
                )
            }
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
    contentScale: ContentScale = ContentScale.Crop
) {
    Image(
        painter = painterResource(id = resId),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
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
    val timeStr = String.format(Locale.US, "%02d:%02d:%02d", h, m, sec)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = OrangePrimary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Maintenance mode",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "The app is temporarily unavailable while we perform maintenance. Please check back soon.",
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.88f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(28.dp))
            Text(
                "Estimated time remaining",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                timeStr,
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = OrangePrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp
            )
            if (initialTotal == 0) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "No ETA provided — we’ll be back shortly.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
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
private fun AppRootWithMaintenanceGate(content: @Composable () -> Unit) {
    var maintenanceOn by remember { mutableStateOf(false) }
    var remHours by remember { mutableStateOf(0) }
    var remMinutes by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        val s = fetchMaintenanceStatus()
        if (s != null && s.maintenance) {
            maintenanceOn = true
            remHours = s.remainingHours
            remMinutes = s.remainingMinutes
        }
    }
    when {
        maintenanceOn ->
            MaintenanceModeScreen(initialHours = remHours, initialMinutes = remMinutes)
        else -> content()
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
    var selectedTab by remember { mutableStateOf("home") }
    var currentSubScreen by remember { mutableStateOf("main") }
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

    LaunchedEffect(selectedTab) {
        if (selectedTab != "wallet") return@LaunchedEffect
        preloadedPaymentLoading = true
        preloadedPaymentError = null
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
    BackHandler(enabled = !onHomeMain) {
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
            Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            when {
                currentSubScreen == "payment_options" ->
                    PaymentOptionsScreen(
                        onBack = {
                            walletDepositMethod = "upi"
                            currentSubScreen = "main"
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
                            "wallet" ->
                                WalletScreen(
                                    onBack = { selectedTab = "home" },
                                    onDepositClick = { method, amount ->
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
                        LiveStreamBlinkingDot()
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

    var cockfightWalletText by remember { mutableStateOf("₹0") }
    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        cockfightWalletText = formatRupeeBalanceForDisplay(w?.balance)
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
            WalletBalanceChip(onClick = onWallet, balanceText = cockfightWalletText, spacerBetweenIconAndText = 6.dp)
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
                        LiveStreamBlinkingDot()
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Live stream placeholder — full screen black canvas
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            LiveStreamBlinkingDot()
            Text(
                text = "LIVE",
                color = Color(0xFFE53935),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp
            )
        }
        // Back button overlay
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.padding(6.dp).size(22.dp)
                )
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
            HeaderIconItem("Tutorials", imageRes = R.drawable.gamelogo)
            HeaderIconItem("Cockfight", imageRes = R.drawable.category_cockfight)
            HeaderIconItem("Dice Play", imageRes = R.drawable.category_gunduata)
            HeaderIconItem("Cricket", imageRes = R.drawable.category_cricket)
        }
        Spacer(modifier = Modifier.height(40.dp))
        DrawableImage(
            R.drawable.gamelogo,
            contentDescription = "Logo",
            modifier = Modifier.size(150.dp).clip(CircleShape).border(2.dp, Color.Black, CircleShape),
            contentScale = ContentScale.Crop
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
                R.drawable.gamelogo,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.Black, CircleShape),
                contentScale = ContentScale.Crop
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
    val amount: Int,
    val status: String,
    val withdrawalMethod: String,
    val withdrawalDetails: String,
    val adminNote: String?,
    val processedByName: String?,
    val createdAt: String,
    val updatedAt: String
)

private fun parseDepositsResponse(text: String): List<DepositRecordApi> {
    val t = text.trim()
    if (t.isEmpty()) return emptyList()
    val arr: JSONArray =
        when {
            t.startsWith("[") -> JSONArray(t)
            else -> {
                val root = JSONObject(t)
                root.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("deposits")
                    ?: JSONArray()
            }
        }
    val out = ArrayList<DepositRecordApi>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(parseDepositRecord(o))
    }
    return out
}

private fun parseDepositRecord(o: JSONObject): DepositRecordApi =
    DepositRecordApi(
        id = o.optIntValue("id", -1),
        amount = o.optIntValue("amount", 0),
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
    val arr: JSONArray =
        when {
            t.startsWith("[") -> JSONArray(t)
            else -> {
                val root = JSONObject(t)
                root.optJSONArray("results")
                    ?: root.optJSONArray("data")
                    ?: root.optJSONArray("withdraws")
                    ?: root.optJSONArray("withdrawals")
                    ?: JSONArray()
            }
        }
    val out = ArrayList<WithdrawRecordApi>()
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        out.add(parseWithdrawRecord(o))
    }
    return out
}

private fun parseWithdrawRecord(o: JSONObject): WithdrawRecordApi =
    WithdrawRecordApi(
        id = o.optIntValue("id", -1),
        amount = o.optIntValue("amount", 0),
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
                    resp.code == 401 ->
                        Pair(emptyList(), "Session expired. Please sign in again.")
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
                    resp.code == 401 ->
                        Pair(emptyList(), "Session expired. Please sign in again.")
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
                                items(filteredDeposits, key = { it.id }) { row ->
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
                                items(filteredWithdrawals, key = { it.id }) { row ->
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
                    onClick = { context.openWhatsAppToContact(supportContacts?.whatsappNumber) }
                )
            }
            item {
                ProfileMenuItem(
                    "Telegram",
                    icon = Icons.Default.Send,
                    iconColor = Color(0xFF2196F3),
                    onClick = { context.openTelegramToContact(supportContacts?.telegram) }
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
private fun BankDetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = WalletInkWarm)
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
                    BankDetailLine("Account number", details.accountNumber)
                    BankDetailLine("IFSC", details.ifsc)
                    if (details.branch.isNotBlank()) BankDetailLine("Branch", details.branch)
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

    val screenshotLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> screenshotUri = uri; uploadError = null }

    LaunchedEffect(Unit) {
        while (timerSeconds > 0) { delay(1000); timerSeconds-- }
    }

    LaunchedEffect(walletRetryNonce) {
        if (walletRetryNonce == 0 && preloadedWallet != null) return@LaunchedEffect
        walletLoading = true; walletError = null; wallet = null
        val (w, err) = fetchPaymentOptionsFromApi()
        walletLoading = false; wallet = w; walletError = err
    }

    val bankDetails = wallet?.bank
    val methodsToShow = (wallet?.paymentMethods ?: emptyList())
        .filter { m ->
            val t = m.type.lowercase(Locale.US)
            t != "bank" && t != "qr" && !m.upiId.isNullOrBlank()
        }
        .distinctBy { it.type.lowercase(Locale.US) }

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
                    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp), shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 2.dp) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(qrImageUrl).crossfade(true).build(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(16.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { Toast.makeText(context, "QR saved to gallery", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 80.dp).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = OrangePrimary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
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
                detailsLoading && bank == null -> {
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
                bank != null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF8F0),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Bank account", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WalletInkWarm)
                            Spacer(Modifier.height(6.dp))
                            BankDetailLine("Account holder", bank.accountHolder)
                            BankDetailLine("Bank name", bank.bankName)
                            BankDetailLine("Account number", bank.accountNumber)
                            BankDetailLine("IFSC", bank.ifsc)
                            if (bank.branch.isNotBlank()) BankDetailLine("Branch", bank.branch)
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
                detailsLoading && upi == null -> {
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
                upi != null -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFFF8F0),
                        border = BorderStroke(1.dp, OrangePrimary.copy(alpha = 0.35f))
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("UPI ID", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = WalletInkWarm)
                            Spacer(Modifier.height(4.dp))
                            Text(upi, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = WalletInkWarm)
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
                        Triple("upi", "UPI", Icons.Default.QrCodeScanner),
                        Triple("bank", "Bank Account", Icons.Default.AccountBalance)
                    ).forEach { (method, label, icon) ->
                        val selected = paymentMethod == method
                        Surface(
                            modifier = Modifier.weight(1f).height(100.dp).clickable { paymentMethod = method },
                            shape = RoundedCornerShape(14.dp),
                            color = if (selected) Color(0xFFFFF8F2) else Color.White,
                            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) OrangePrimary else Color(0xFFEEEEEE)),
                            shadowElevation = 1.dp
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                                Icon(icon, null, tint = OrangePrimary, modifier = Modifier.size(28.dp))
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
    LaunchedEffect(Unit) {
        val (w, _) = fetchWalletFromApi()
        walletBalanceText = formatRupeeBalanceForDisplay(w?.balance)
    }
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
        item(key = "bottom_spacer", contentType = "spacer") { Spacer(modifier = Modifier.height(16.dp)) }
    }
    } // end Column
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo + name on left
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawableImage(
                R.drawable.gamelogo,
                "Logo",
                modifier = Modifier.size(40.dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(10.dp))
            Column {
                val infiniteTransition = rememberInfiniteTransition(label = "glow")
                val glowAlpha by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "glowAlpha"
                )
                // Alternate glow: orange → white → orange
                val glowColor = if (glowAlpha < 0.5f)
                    androidx.compose.ui.graphics.lerp(Color(0xFFFF6F00), Color.White, glowAlpha * 2f)
                else
                    androidx.compose.ui.graphics.lerp(Color.White, Color(0xFFFF6F00), (glowAlpha - 0.5f) * 2f)
                Text(
                    "KOKOROKO",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color.Black,
                    letterSpacing = 0.5.sp,
                    style = androidx.compose.ui.text.TextStyle(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp,
                        letterSpacing = 0.5.sp,
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = glowColor.copy(alpha = 0.85f),
                            offset = androidx.compose.ui.geometry.Offset(0f, 0f),
                            blurRadius = 22f
                        )
                    )
                )
                Text(
                    "Live Games",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    letterSpacing = 0.2.sp
                )
            }
        }
        // Wallet on right
        Surface(
            onClick = onWalletClick,
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF5F5F5),
            border = BorderStroke(1.dp, Color(0xFFEEEEEE))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Wallet, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(walletBalanceText, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(OrangePrimary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White, modifier = Modifier.size(14.dp))
                }
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
            R.drawable.banner_gundu
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
            snapshotFlow { homeListState.isScrollInProgress }
                .first { !it }
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
    val context = LocalContext.current
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            val uri = android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.live_video}")
            setMediaItem(androidx.media3.common.MediaItem.fromUri(uri))
            repeatMode = androidx.media3.exoplayer.ExoPlayer.REPEAT_MODE_ALL
            volume = 0f
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(Unit) { onDispose { exoPlayer.release() } }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .aspectRatio(16f / 9f)
            .clickable { onClick() }
    ) {
        AndroidView(
            factory = {
                androidx.media3.ui.PlayerView(it).apply {
                    player = exoPlayer
                    useController = false
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        // LIVE badge
        Row(
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            LiveStreamBlinkingDot()
            Text("LIVE", color = Color(0xFFE53935), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
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
