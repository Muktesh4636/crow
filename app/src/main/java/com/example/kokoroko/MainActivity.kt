package com.example.kokoroko

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import com.example.kokoroko.R
import com.example.kokoroko.ui.theme.KokorokoTheme
import com.example.kokoroko.ui.theme.OrangePrimary
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay

private const val PKG_PHONEPE = "com.phonepe.app"
private const val PKG_GPay = "com.google.android.apps.nbu.paisa.user"
private const val PKG_PAYTM = "net.one97.paytm"

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

/** Drawables loaded via Coil; decode size matches layout pixels (capped) to cut memory and scroll jank. */
@Composable
private fun DrawableImage(
    resId: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier) {
        val decodeSize = run {
            val wPx =
                if (maxWidth != Dp.Unspecified && maxWidth.value.isFinite() && maxWidth.value > 0f) {
                    with(density) { maxWidth.roundToPx() }.coerceIn(32, 2048)
                } else null
            val hPx =
                if (maxHeight != Dp.Unspecified && maxHeight.value.isFinite() && maxHeight.value > 0f) {
                    with(density) { maxHeight.roundToPx() }.coerceIn(32, 2048)
                } else null
            when {
                wPx != null && hPx != null -> CoilSize(wPx, hPx)
                wPx != null -> CoilSize(wPx, wPx)
                hPx != null -> CoilSize(hPx, hPx)
                else -> CoilSize(512, 512)
            }
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(resId)
                .size(decodeSize)
                .crossfade(false)
                .build(),
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
        Box(modifier = Modifier.padding(paddingValues)) {
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
                        }
                    )
                else -> when (selectedTab) {
                    "home" -> HomeScreen(onOpenGundata = { currentSubScreen = "gundata_live" })
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
                }
            }
        }
    }
}

private val GUNDATA_DICE_FACE = listOf("⚀", "⚁", "⚂", "⚃", "⚄", "⚅")

@Composable
fun GundataLiveScreen(onBack: () -> Unit, onWallet: () -> Unit) {
    BackHandler { onBack() }
    var region by remember { mutableStateOf("andhra") }
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
                    DrawableImage(
                        R.drawable.gundu_live,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    Surface(
                        color = OrangePrimary,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                    ) {
                        Text(
                            "Live",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    IconButton(
                        onClick = { },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                    ) {
                        Surface(shape = CircleShape, color = Color.White) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.Black,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..2) {
                            GundataDiceCard(
                                num = i + 1,
                                word = diceWords[i],
                                emoji = GUNDATA_DICE_FACE[i],
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 3..5) {
                            GundataDiceCard(
                                num = i + 1,
                                word = diceWords[i],
                                emoji = GUNDATA_DICE_FACE[i],
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
                    GundataSquareIconButton(icon = Icons.Default.Settings) { mainAction = "Settings opened" }
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
private fun GundataDiceCard(num: Int, word: String, emoji: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(102.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color.LightGray),
        shadowElevation = 1.dp
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clickable { },
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
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        DrawableImage(
            R.drawable.opening_photo,
            contentDescription = "Splash Screen",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds
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
        Spacer(modifier = Modifier.height(24.dp))
        Text("Login Password", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Text("Logout & login if you forgot your pin!", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(top = 8.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(4) { index ->
                Surface(
                    modifier = Modifier.size(60.dp).clickable { if (pin.length < 4) pin += correctPin[pin.length] },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    color = Color.White
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (pin.length > index) Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color.Black))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row {
            Text("Didn't remember code? ", fontSize = 14.sp, color = Color.Gray)
            Text("Logout!", fontSize = 14.sp, color = OrangePrimary, fontWeight = FontWeight.Bold)
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
            item { ProfileMenuItem("Instagram", icon = Icons.Default.CameraAlt, iconColor = Color(0xFFE1306C)) }
            item { ProfileMenuItem("Youtube", icon = Icons.Default.PlayCircleFilled, iconColor = Color(0xFFFF0000)) }

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
            DrawableImage(iconRes, null, modifier = Modifier.size(32.dp).clip(CircleShape))
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
fun HomeScreen(onOpenGundata: () -> Unit = {}) {
    var liveEnabled by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF5F5F5)),
        state = listState
    ) {
        item(key = "top_header", contentType = "header") { TopHeader(onDicePlayClick = onOpenGundata) }
        item(key = "banner", contentType = "banner") { BannerCarousel() }
        item(key = "popular_games", contentType = "games") { PopularGamesSection(onGundataClick = onOpenGundata) }
        item(key = "live_header", contentType = "live_header") {
            LiveCockFightHeader(liveEnabled = liveEnabled, onLiveChange = { liveEnabled = it })
        }
        if (liveEnabled) {
            item(key = "filter", contentType = "filter") { FilterBar() }
            item(key = "live_match", contentType = "match") { LiveMatchCard() }
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
fun TopHeader(onDicePlayClick: () -> Unit = {}) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DrawableImage(R.drawable.gamelogo, "Logo", modifier = Modifier.size(50.dp).clip(CircleShape).border(1.dp, Color.LightGray, CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(8.dp))
            HeaderIconItem("Cockfight", imageRes = R.drawable.category_cockfight)
            HeaderIconItem("Dice Play", imageRes = R.drawable.category_gunduata, onClick = onDicePlayClick)
            HeaderIconItem("Cricket", imageRes = R.drawable.category_cricket)
        }
        Surface(color = OrangePrimary, shape = RoundedCornerShape(8.dp), modifier = Modifier.height(40.dp)) { Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Wallet, null, tint = Color.Black); Spacer(Modifier.width(8.dp)); Text("₹0", color = Color.Black, fontWeight = FontWeight.Bold) } }
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
fun BannerCarousel() {
    val banners = remember {
        listOf(
            R.drawable.banner_home_1,
            R.drawable.banner_home_2,
            R.drawable.banner_home_3
        )
    }
    val pagerState = rememberPagerState(pageCount = { banners.size })
    LaunchedEffect(banners.size) {
        if (banners.size <= 1) return@LaunchedEffect
        while (true) {
            delay(3000)
            val next = (pagerState.currentPage + 1) % banners.size
            pagerState.scrollToPage(next)
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
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            DrawableImage(
                banners[page],
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
fun PopularGamesSection(onGundataClick: () -> Unit = {}) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Popular Games:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Button(onClick = { }, colors = ButtonDefaults.buttonColors(containerColor = Color.White), border = BorderStroke(1.dp, Color.LightGray), shape = RoundedCornerShape(4.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) { Icon(Icons.Default.School, null, tint = Color.Black, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Watch Tutorials", color = Color.Black, fontSize = 12.sp) }
        }
        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            GameIconItem("Cockfight", R.drawable.category_cockfight)
            GameIconItem("Gundata", R.drawable.category_gunduata, onClick = onGundataClick)
            GameIconItem("Cricket", R.drawable.category_cricket)
            GameIconItem("Promotions", R.drawable.category_promotions)
        }
    }
}

@Composable
fun GameIconItem(label: String, imageRes: Int, isSoon: Boolean = false, onClick: (() -> Unit)? = null) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box {
            Surface(modifier = Modifier.size(70.dp), shape = RoundedCornerShape(12.dp), color = Color.White, shadowElevation = 0.dp) {
                DrawableImage(
                    imageRes,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            if (isSoon) Surface(color = OrangePrimary, shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp)) { Text("Soon", color = Color.Black, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 4.dp)) }
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
fun FilterBar() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).background(Color.White, RoundedCornerShape(4.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(color = OrangePrimary, shape = RoundedCornerShape(4.dp)) { Text("24/7", color = Color.Black, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), fontSize = 12.sp) }
        Spacer(Modifier.width(16.dp)); Text("Telugu", color = Color.Black, fontSize = 14.sp)
    }
}

@Composable
fun LiveMatchCard() {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
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
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
