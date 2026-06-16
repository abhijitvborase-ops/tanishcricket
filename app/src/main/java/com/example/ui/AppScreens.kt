package com.example.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import androidx.activity.compose.rememberLauncherForActivityResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.example.ui.theme.GoldCap
import com.example.ui.theme.PurpleCap
import com.example.ui.theme.TealPrimary
import com.example.ui.theme.TealDark
import com.example.ui.theme.TealPrimaryContainer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

// --- MOCK DRAWER FOR QR CODE ---
@Composable
fun SimulatedQRCode(modifier: Modifier = Modifier, payload: String = "TanishScorerPlayer") {
    // Elegant Canvas that draws a realistic QR Code using styled black and white blocks!
    val rand = remember(payload) { Random(payload.hashCode()) }
    Canvas(modifier = modifier.aspectRatio(1f)) {
        val sizePx = size.width
        val cellsCount = 17
        val cellSize = sizePx / cellsCount

        // 1. Draw solid background
        drawRect(Color.White)

        // 2. Clear corners for QR alignment highlights
        val primaryCol = Color(0xFF1E293B)

        // Outer frames
        for (col in 0..16) {
            for (row in 0..16) {
                val isFinderPattern = (col < 5 && row < 5) || 
                                      (col > 11 && row < 5) || 
                                      (col < 5 && row > 11)
                if (isFinderPattern) {
                    val isBorder = col == 0 || col == 4 || row == 0 || row == 4 ||
                                   (col == 12 && row == 0) || (col == 16 && row == 0) || (col == 12 && row == 4) || (col == 16 && row == 4) ||
                                   (col == 0 && row == 12) || (col == 4 && row == 12) || (col == 0 && row == 16) || (col == 4 && row == 16)
                    val isInnerCorner = col == 2 && row == 2 || 
                                       col == 14 && row == 2 || 
                                       col == 2 && row == 14
                    
                    if (isBorder || isInnerCorner) {
                        drawRect(
                            color = primaryCol,
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                } else {
                    // Random blocks with fixed seed
                    if (rand.nextBoolean()) {
                        drawRect(
                            color = primaryCol,
                            topLeft = Offset(col * cellSize, row * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

// --- STANDARD AVATAR PROVIDER ---
@Composable
fun PlayerAvatar(
    avatarIndex: String, 
    size: Int = 40, 
    borderActive: Boolean = false,
    borderColor: Color = TealPrimary
) {
    val characters = listOf("🏏", "🦁", "🔥", "⚡", "🌟", "🎯", "🤖", "🦊", "🏆")
    val characterIndex = avatarIndex.toIntOrNull() ?: 0
    val displayIcon = characters.getOrElse(characterIndex % characters.size) { "🏏" }

    val bgGradient = remember(avatarIndex) {
        val colors = listOf(
            listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
            listOf(Color(0xFFFEF3C7), Color(0xFFFDE68A)),
            listOf(Color(0xFFFEE2E2), Color(0xFFFCA5A5)),
            listOf(Color(0xFFE0F2FE), Color(0xFF7DD3FC)),
            listOf(Color(0xFFDCFCE7), Color(0xFF86EFAC)),
            listOf(Color(0xFFF3E8FF), Color(0xFFD8B4FE)),
            listOf(Color(0xFFECE9E6), Color(0xFFFFFFFF))
        )
        val idx = (avatarIndex.toIntOrNull() ?: 0) % colors.size
        colors[idx]
    }

    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Brush.radialGradient(bgGradient))
            .then(
                if (borderActive) Modifier.border(2.dp, borderColor, CircleShape) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayIcon,
            fontSize = (size * 0.5).sp,
            textAlign = TextAlign.Center
        )
    }
}

// --- HELPER FORMATS ---
fun formatMatchDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM, yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

// --- COMMONS WHATSAPP SHARING INTENT ---
fun shareMatchToWhatsApp(context: Context, match: Match, inningsList: List<Innings>, mvpName: String?) {
    val stringBuilder = StringBuilder()
    stringBuilder.append("🏏 *Tanish Scorer* 🏏\n")
    stringBuilder.append("🏆 *${match.name}*\n")
    stringBuilder.append("📅 ${formatMatchDate(match.date)}\n\n")

    val firstInn = inningsList.find { it.inningsNumber == 1 }
    val secondInn = inningsList.find { it.inningsNumber == 2 }

    if (firstInn != null) {
        val overs = firstInn.totalBallsBowled / 6
        val balls = firstInn.totalBallsBowled % 6
        stringBuilder.append("🟢 *1st Innings:* ${firstInn.battingTeam}\n")
        stringBuilder.append("👉 ${firstInn.totalRuns}/${firstInn.totalWickets} in $overs.$balls overs\n\n")
    }

    if (secondInn != null) {
        val overs = secondInn.totalBallsBowled / 6
        val balls = secondInn.totalBallsBowled % 6
        stringBuilder.append("🟡 *2nd Innings:* ${secondInn.battingTeam}\n")
        stringBuilder.append("👉 ${secondInn.totalRuns}/${secondInn.totalWickets} in $overs.$balls overs\n\n")
    }

    if (match.isCompleted) {
        stringBuilder.append("📝 *Result:* ${match.resultText.ifBlank { "Completed" }}\n")
    } else {
        stringBuilder.append("📝 *Result:* ${match.resultText.ifBlank { "Live Match in progress" }}\n")
    }
    if (!mvpName.isNullOrBlank()) {
        stringBuilder.append("⭐ *Man of the Match MVP:* $mvpName\n")
    }
    stringBuilder.append("\nScored live on *Tanish Scorer* 📲")

    val sendIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, stringBuilder.toString())
        type = "text/plain"
    }
    
    try {
        val chooser = Intent.createChooser(sendIntent, "Share Match via")
        context.startActivity(chooser)
    } catch (e: Exception) {
        Toast.makeText(context, "No app available to share match details.", Toast.LENGTH_SHORT).show()
    }
}

// --- CORE SPLASH SCREEN ---
@Composable
fun SplashScreen(onDismiss: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2200)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        TealDark,
                        TealPrimary,
                        Color(0xFF0F172A)  // Slate Dark
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .shadow(12.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                // Draws custom ball and wickets
                Icon(
                    imageVector = Icons.Default.SportsCricket,
                    contentDescription = "App Icon",
                    tint = TealPrimary,
                    modifier = Modifier.size(72.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Tanish Orchid Phase 1",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Official Cricket Scoring App",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2DD4BF),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            CircularProgressIndicator(
                color = Color(0xFF2DD4BF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(32.dp)
            )
        }

        // Developer footer aligned to bottom of splash screen
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Tanish Scorer",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Developed by Abhijit Borase",
                    fontSize = 14.sp,
                    color = Color(0xFF2DD4BF),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- CORE AUTH / LOGIN & ROLE CONFIG ---
@Composable
fun LoginScreen(
    viewModel: ScorerViewModel,
    onLoginSuccess: () -> Unit,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit
) {
    val currentRole by viewModel.userRole.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var alertMsg by remember { mutableStateOf<String?>(null) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val webClientId = remember {
        try {
            val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
            if (id != 0) context.getString(id) else ""
        } catch (e: Exception) {
            ""
        }
    }

    val gso = remember(webClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId.ifBlank { "140131729045-dummy.apps.googleusercontent.com" })
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(com.google.android.gms.common.api.ApiException::class.java)
            val idToken = account?.idToken
            if (idToken != null) {
                isAuthLoading = true
                viewModel.signInWithGoogle(idToken) { success, errorMsg ->
                    isAuthLoading = false
                    if (!success) {
                        alertMsg = errorMsg ?: "Google Sign-In failed"
                    }
                }
            } else {
                alertMsg = "Failed to obtain ID token from Google account."
            }
        } catch (e: com.google.android.gms.common.api.ApiException) {
            alertMsg = "Google Sign-In failed: ${e.message} (status: ${e.statusCode})"
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header with Theme Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🏏 Tanish Scorer",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                IconButton(onClick = onThemeToggle) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Theme Toggle"
                    )
                }
            }

            // Central block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 440.dp)
                    .padding(vertical = 24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SportsCricket,
                    contentDescription = "Cricket Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(72.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Tanish Cricket Scorer",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Live Scoring, Match Management & Player Statistics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isSignUpMode) "Create your account below to access real-time cloud-sync cricket scoring" else "Sign in below to score matches and view real-time stats",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Tab Selector for Mode
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { isSignUpMode = false; alertMsg = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("tab_signin")
                        ) {
                            Text("Sign In", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { isSignUpMode = true; alertMsg = null },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSignUpMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isSignUpMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("tab_signup")
                        ) {
                            Text("Sign Up", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Input fields
                TextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = "Email") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_email"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                    trailingIcon = {
                        val image = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(imageVector = image, contentDescription = "Toggle password visibility")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("auth_password"),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Authenticate Button with spinner
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            alertMsg = "Please fill in all details to proceed."
                            return@Button
                        }
                        if (password.length < 6) {
                            alertMsg = "Password must be at least 6 characters long."
                            return@Button
                        }
                        isAuthLoading = true
                        alertMsg = null
                        if (isSignUpMode) {
                            viewModel.signUp(email, password) { success, errorMsg ->
                                isAuthLoading = false
                                if (!success) {
                                    alertMsg = errorMsg ?: "Failed to sign up."
                                }
                            }
                        } else {
                            viewModel.signIn(email, password) { success, errorMsg ->
                                isAuthLoading = false
                                if (!success) {
                                    alertMsg = errorMsg ?: "Invalid email or password."
                                }
                            }
                        }
                    },
                    enabled = !isAuthLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("submit_login_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isAuthLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = if (isSignUpMode) "Register Scorer" else "Authenticate & Enter",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                    Text(
                        text = " OR ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    HorizontalDivider(
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedButton(
                    onClick = {
                        alertMsg = null
                        val signInIntent = googleSignInClient.signInIntent
                        launcher.launch(signInIntent)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("google_sign_in_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Google Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sign In with Google",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (alertMsg != null) {
                    Text(
                        text = alertMsg!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
            }


        }
    }
}

// --- MAIN DASHBOARD SCREEN ---
@Composable
fun DashboardScreen(
    viewModel: ScorerViewModel,
    onCreateMatch: () -> Unit,
    onPlayerManagement: () -> Unit,
    onMatchScoring: () -> Unit,
    onMatchHistory: () -> Unit,
    onStatsDashboard: () -> Unit,
    onLogout: () -> Unit
) {
    val players by viewModel.allPlayers.collectAsState()
    val matches by viewModel.allMatches.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val syncMsg by viewModel.syncMessage.collectAsState()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    val activeMatchId by viewModel.activeMatchId.collectAsState()
    val careerStats by viewModel.careerStats.collectAsState()
    val role by viewModel.userRole.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    var showProfileDialog by remember { mutableStateOf(false) }

    val completedMatches = matches.filter { it.isCompleted }

    // Dynamic Leaders calculation
    val orangeCapLeader = careerStats.filter { it.battingRuns > 0 }.maxByOrNull { it.battingRuns }
    val purpleCapLeader = careerStats.filter { it.bowlingWickets > 0 }.maxByOrNull { it.bowlingWickets }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Dashboard Top Green/Teal Cricket Gradient Banner (Professional Custom Branding)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val brush = Brush.verticalGradient(
                            colors = listOf(TealPrimary, TealDark)
                        )
                        drawRect(brush)
                    }
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sports/Cricket branding layout
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0x22FFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsCricket,
                                contentDescription = "Cricket Logo",
                                tint = Color(0xFFFDE047), // Vibrant Gold
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Tanish Orchid Phase 1",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Society Cricket Scorer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF99F6E4)
                            )
                        }
                    }

                    // Account Settings Profile icon in top-right
                    IconButton(
                        onClick = { showProfileDialog = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("profile_icon_button"),
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0x22FFFFFF))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Open Profile & Settings",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            // Quick Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Matches", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Text("${matches.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Total Players", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Text("${players.size}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Highlighting active match if there's any active
            if (activeMatchId != null && role == "ADMIN") {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable { onMatchScoring() }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Circle,
                                contentDescription = "Live matches",
                                tint = Color.Red,
                                modifier = Modifier
                                    .size(12.dp)
                                    .align(Alignment.CenterVertically)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("In Progress Live Scoreboard", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("Click to resume scoring match", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.8f))
                            }
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // Core Menu Grid (Quick Access buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (role == "ADMIN") {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onCreateMatch() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.AddBox, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Create Match", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPlayerManagement() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.People, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Add/View Players", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onMatchHistory() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Match History", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onStatsDashboard() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.BarChart, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("M3 Stats & Caps", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Caps Leaders Highlights (Orange / Purple caps with clean placeholders)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Orange Cap Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = GoldCap.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, GoldCap.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(GoldCap.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.WorkspacePremium,
                                contentDescription = "Orange Cap",
                                tint = GoldCap,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Orange Leader", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = GoldCap)
                            Text(
                                text = orangeCapLeader?.playerName ?: "No Runs Yet",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val runsVal = orangeCapLeader?.battingRuns ?: 0
                            Text(
                                text = if (runsVal > 0) "$runsVal Runs" else "— Runs",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Purple Cap Card
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = PurpleCap.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, PurpleCap.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PurpleCap.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MilitaryTech,
                                contentDescription = "Purple Cap",
                                tint = PurpleCap,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Purple Leader", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PurpleCap)
                            Text(
                                text = purpleCapLeader?.playerName ?: "No Wickets Yet",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val wktsVal = purpleCapLeader?.bowlingWickets ?: 0
                            Text(
                                text = if (wktsVal > 0) "$wktsVal Wkts" else "— Wkts",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Recent Completed Matches list
            Text(
                text = "Recent Matches",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            if (completedMatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.SportsCricket, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(0.3f), modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No completed matches found", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground.copy(0.5f))
                        Text("Create a match to begin scoring", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(0.4f))
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(completedMatches) { match ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .clickable { onMatchHistory() }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(match.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(formatMatchDate(match.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(match.teamAName, fontWeight = FontWeight.Medium)
                                        Text("VS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        Text(match.teamBName, fontWeight = FontWeight.Medium)
                                    }

                                    // Match Outcome Ribbon
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(TealPrimary.copy(alpha = 0.12f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = match.resultText,
                                            color = TealPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .testTag("developer_footer"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tanish Orchid Phase 1 • Official Cricket Scoring App",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Developed by Abhijit Borase",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        ProfileSettingsDialog(
            show = showProfileDialog,
            onDismiss = { showProfileDialog = false },
            currentUser = currentUser,
            userRole = role,
            syncState = syncState,
            syncMessage = syncMsg,
            lastSyncTimestamp = lastSyncTimestamp,
            onSyncNow = {
                viewModel.triggerFirebaseSync()
            },
            onLogout = onLogout
        )
    }
}

@Composable
fun ProfileSettingsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    currentUser: com.google.firebase.auth.FirebaseUser?,
    userRole: String,
    syncState: String,
    syncMessage: String,
    lastSyncTimestamp: Long,
    onSyncNow: () -> Unit,
    onLogout: () -> Unit
) {
    if (!show) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Profile & Settings",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // User details block (Card-based layout)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(TealPrimary.copy(alpha = 0.08f))
                        .padding(16.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        // User Avatar circular badge with initials
                        val email = currentUser?.email ?: ""
                        val displayName = currentUser?.displayName ?: ""
                        val nameText = if (displayName.isNotBlank()) displayName else email.substringBefore("@")
                        val initials = if (nameText.isNotBlank()) nameText.take(2).uppercase() else "CS"
                        
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(TealPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color.White,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = if (displayName.isNotBlank()) displayName else nameText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = email,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Role badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TealPrimaryContainer,
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = "Role: $userRole",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Settings section header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = TealPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "System Settings",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // App version & details list
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // App Version Item
                    SettingsRow(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        value = "v2.1.0-Premium"
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Cloud Sync Status Item
                    SettingsRow(
                        icon = when (syncState) {
                            "Synced" -> Icons.Default.CloudDone
                            "Syncing" -> Icons.Default.Sync
                            else -> Icons.Default.CloudOff
                        },
                        iconColor = when (syncState) {
                            "Synced" -> Color(0xFF10B981)
                            "Syncing" -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        },
                        title = "Cloud Sync Status",
                        value = syncState
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // Last Sync Timestamp Item
                    val dateFormatted = remember(lastSyncTimestamp) {
                        try {
                            if (lastSyncTimestamp > 0) {
                                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                                sdf.format(Date(lastSyncTimestamp))
                            } else {
                                "Never synced"
                            }
                        } catch (e: Exception) {
                            "Unknown"
                        }
                    }
                    SettingsRow(
                        icon = Icons.Default.AccessTime,
                        title = "Last Sync Timestamp",
                        value = dateFormatted
                    )

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                    // About section
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "About Society Cricket Scorer",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "A local-first hybrid mobile app engineered with offline-capable SQLite Room, integrated with Firestore for shared, real-time cooperative scoring dashboards within Tanish Orchid Phase 1.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            lineHeight = 15.sp
                        )
                    }
                }



                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Active button row: Sync Now & Logout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Sync Now Button
                    OutlinedButton(
                        onClick = onSyncNow,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (syncState == "Syncing") "Syncing..." else "Sync Now",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Logout Button
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.weight(1f).testTag("dialog_logout_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Log Out",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    iconColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
        }
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// --- PLAYER SEED / MANAGEMENT PANEL ---
@Composable
fun PlayerManagementScreen(
    viewModel: ScorerViewModel,
    onBack: () -> Unit
) {
    val players by viewModel.allPlayers.collectAsState()
    val role by viewModel.userRole.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var showPlayerDialog by remember { mutableStateOf(false) }

    // Dialog state for add/edit player
    var editingPlayerId by remember { mutableStateOf<Long?>(null) }
    var playerName by remember { mutableStateOf("") }
    var playerNickname by remember { mutableStateOf("") }
    var playerMobile by remember { mutableStateOf("") }
    var playerAvatar by remember { mutableStateOf("0") }

    // Filtering dynamically
    val filteredPlayers = remember(searchQuery, players) {
        if (searchQuery.isBlank()) {
            players
        } else {
            players.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.nickname.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = "Player Master List",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (role == "ADMIN") {
                    IconButton(
                        onClick = {
                            editingPlayerId = null
                            playerName = ""
                            playerNickname = ""
                            playerMobile = ""
                            playerAvatar = "0"
                            showPlayerDialog = true
                        },
                        modifier = Modifier.testTag("add_player_fab")
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add New Player", tint = TealPrimary)
                    }
                }
            }

            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search player by name or nickname") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("player_search_bar"),
                colors = OutlinedTextFieldDefaults.colors(),
                shape = RoundedCornerShape(12.dp)
            )

            // Players List
            if (filteredPlayers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PersonOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(0.3f), modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("No players found", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground.copy(0.6f))
                        Text(
                            if (role == "ADMIN") "Click the icon on top to introduce new player" else "Admin can add custom players",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(0.4f)
                        )

                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredPlayers) { player ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PlayerAvatar(avatarIndex = player.profilePhotoUri)
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(player.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = player.nickname.ifBlank { "No Nik" },
                                                fontSize = 13.sp,
                                                color = TealPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            if (player.mobileNumber.isNotBlank()) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "•  ${player.mobileNumber}",
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (role == "ADMIN") {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                editingPlayerId = player.id
                                                playerName = player.name
                                                playerNickname = player.nickname
                                                playerMobile = player.mobileNumber
                                                playerAvatar = player.profilePhotoUri
                                                showPlayerDialog = true
                                            }
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Player", tint = TealPrimary)
                                        }

                                        IconButton(
                                            onClick = { viewModel.deletePlayer(player.id) },
                                            modifier = Modifier.testTag("delete_player_${player.id}")
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Player", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add/Edit Dialog Form
        if (showPlayerDialog) {
            Dialog(onDismissRequest = { showPlayerDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (editingPlayerId == null) "Add Master Player" else "Edit Player Records",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        // Avatar Picker
                        Text("Choose Icon Avatar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf("0", "1", "2", "3", "4", "5", "6").forEach { index ->
                                Box(
                                    modifier = Modifier
                                        .clickable { playerAvatar = index }
                                        .padding(2.dp)
                                ) {
                                    PlayerAvatar(
                                        avatarIndex = index,
                                        size = 32,
                                        borderActive = playerAvatar == index,
                                        borderColor = TealPrimary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        TextField(
                            value = playerName,
                            onValueChange = { playerName = it },
                            label = { Text("Player Name") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("player_name_input"),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TextField(
                            value = playerNickname,
                            onValueChange = { playerNickname = it },
                            label = { Text("Nickname (Optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        TextField(
                            value = playerMobile,
                            onValueChange = { playerMobile = it },
                            label = { Text("Mobile Number (Optional)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showPlayerDialog = false }) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    if (playerName.isNotBlank()) {
                                        val id = editingPlayerId
                                        if (id == null) {
                                            viewModel.addPlayer(playerName, playerNickname, playerMobile, playerAvatar)
                                        } else {
                                            viewModel.editPlayer(id, playerName, playerNickname, playerMobile, playerAvatar)
                                        }
                                        showPlayerDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("save_player_button")
                            ) {
                                Text("Save Player", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- MATCH CREATION SCREEN ---
@Composable
fun MatchCreationScreen(
    viewModel: ScorerViewModel,
    onBack: () -> Unit,
    onMatchScoringRedirect: () -> Unit
) {
    val players by viewModel.allPlayers.collectAsState()

    var matchName by remember { mutableStateOf("") }
    var teamAName by remember { mutableStateOf("") }
    var teamBName by remember { mutableStateOf("") }
    var numberOfOvers by remember { mutableStateOf("5") }
    var expandedOvers by remember { mutableStateOf(false) }

    // Toss winner
    var tossWinnerSelection by remember { mutableStateOf("Team A") } // "Team A" or "Team B"
    var tossDecisionSelection by remember { mutableStateOf("BAT") } // "BAT" or "BOWL"

    // Dynamic Team Selection Lists
    var selectedTeamAPlayerIds = remember { mutableStateListOf<Long>() }
    var selectedTeamBPlayerIds = remember { mutableStateListOf<Long>() }
    var selectedCommonPlayerIds = remember { mutableStateListOf<Long>() }

    // Error alert
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("CricHeroes Match Setup", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))

                    // Fields
                    TextField(
                        value = matchName,
                        onValueChange = { matchName = it },
                        label = { Text("Match Title (e.g. Tanish Orchid Cup Match 3)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("match_title"),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = teamAName,
                            onValueChange = { teamAName = it },
                            label = { Text("Team A Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("team_a_name"),
                            shape = RoundedCornerShape(8.dp)
                        )
                        TextField(
                            value = teamBName,
                            onValueChange = { teamBName = it },
                            label = { Text("Team B Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("team_b_name"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Overs choice
                    Text("Match Overs Count:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("5", "6", "8", "10", "12").forEach { ovNum ->
                            Button(
                                onClick = { numberOfOvers = ovNum },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (numberOfOvers == ovNum) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (numberOfOvers == ovNum) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(ovNum)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Toss Winner
                    Text("Toss Setup:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Card(
                            onClick = { tossWinnerSelection = "Team A" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (tossWinnerSelection == "Team A") TealPrimary.copy(0.12f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.2.dp,
                                    if (tossWinnerSelection == "Team A") TealPrimary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Toss Winner", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Text(teamAName.ifBlank { "Team A" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        Card(
                            onClick = { tossWinnerSelection = "Team B" },
                            colors = CardDefaults.cardColors(
                                containerColor = if (tossWinnerSelection == "Team B") TealPrimary.copy(0.12f) else MaterialTheme.colorScheme.surface
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(
                                    1.2.dp,
                                    if (tossWinnerSelection == "Team B") TealPrimary else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Toss Winner", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                Text(teamBName.ifBlank { "Team B" }, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { tossDecisionSelection = "BAT" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tossDecisionSelection == "BAT") TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (tossDecisionSelection == "BAT") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Elect BAT")
                        }

                        Button(
                            onClick = { tossDecisionSelection = "BOWL" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (tossDecisionSelection == "BOWL") TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (tossDecisionSelection == "BOWL") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Elect BOWL")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // DYNAMIC REGISTRATION TEAM SECTIONS (Requirement 3: Players can change teams every match)
                    Text(
                        text = "Dynamic Lineup Selector",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Toggle players below to assign to Team A, Team B or Common Player slot.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )

                    if (teamAName.isNotBlank() || teamBName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "Lineup Assignment Directory:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "A: ${teamAName.ifBlank { "Team A" }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "B: ${teamBName.ifBlank { "Team B" }}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "CMN: Common",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                }

                // Players picker checklist inside stream
                if (players.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Add master players first of all in the Player Master section!", color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                            }
                        }
                    }
                } else {
                    items(players) { player ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1.3f)) {
                                PlayerAvatar(avatarIndex = player.profilePhotoUri, size = 32)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    player.name,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            
                            Row(
                                modifier = Modifier.weight(2f),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                val isA = selectedTeamAPlayerIds.contains(player.id)
                                val isB = selectedTeamBPlayerIds.contains(player.id)
                                val isC = selectedCommonPlayerIds.contains(player.id)

                                // Team A Button
                                OutlinedButton(
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    onClick = {
                                        selectedTeamBPlayerIds.remove(player.id)
                                        selectedCommonPlayerIds.remove(player.id)
                                        if (isA) selectedTeamAPlayerIds.remove(player.id) else selectedTeamAPlayerIds.add(player.id)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isA) TealPrimary else Color.Transparent,
                                        contentColor = if (isA) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    val finalLabelA = if (teamAName.isBlank()) "Team A" else if (teamAName.length <= 10) teamAName else "Team A"
                                    Text(finalLabelA, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Team B Button
                                OutlinedButton(
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    onClick = {
                                        selectedTeamAPlayerIds.remove(player.id)
                                        selectedCommonPlayerIds.remove(player.id)
                                        if (isB) selectedTeamBPlayerIds.remove(player.id) else selectedTeamBPlayerIds.add(player.id)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isB) TealPrimary else Color.Transparent,
                                        contentColor = if (isB) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    val finalLabelB = if (teamBName.isBlank()) "Team B" else if (teamBName.length <= 10) teamBName else "Team B"
                                    Text(finalLabelB, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }

                                // Common Player Button (Requirement 4: Played can play for both teams)
                                OutlinedButton(
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    onClick = {
                                        selectedTeamAPlayerIds.remove(player.id)
                                        selectedTeamBPlayerIds.remove(player.id)
                                        if (isC) selectedCommonPlayerIds.remove(player.id) else selectedCommonPlayerIds.add(player.id)
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (isC) MaterialTheme.colorScheme.secondary else Color.Transparent,
                                        contentColor = if (isC) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("CMN", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Validation errors and Launch Match button
                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    if (errorMsg != null) {
                        Text(errorMsg!!, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    Button(
                        onClick = {
                            if (matchName.isBlank() || teamAName.isBlank() || teamBName.isBlank()) {
                                errorMsg = "Match name and Team names cannot be empty"
                            } else if (numberOfOvers.toIntOrNull() == null) {
                                errorMsg = "Overs count must be a number"
                            } else if (selectedTeamAPlayerIds.size < 2 || selectedTeamBPlayerIds.size < 2) {
                                errorMsg = "Please select at least 2 players for both Team A and Team B from the player roster below."
                            } else {
                                viewModel.createMatch(
                                    name = matchName,
                                    teamAName = teamAName,
                                    teamBName = teamBName,
                                    numberOfOvers = numberOfOvers.toInt(),
                                    tossWinner = if (tossWinnerSelection == "Team A") teamAName else teamBName,
                                    tossDecision = tossDecisionSelection,
                                    teamAPlayerIds = selectedTeamAPlayerIds,
                                    teamBPlayerIds = selectedTeamBPlayerIds,
                                    commonPlayerIds = selectedCommonPlayerIds
                                )
                                onMatchScoringRedirect()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("launch_match_button")
                    ) {
                        Text("Save Match & Launch Scoreboard 🚀", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }

                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

// Helper functions to calculate top active performers for innings summary
fun getTopBatsman(players: List<Player>, balls: List<Ball>, inningsNum: Int): Pair<Player, Int>? {
    val innBalls = balls.filter { it.inningsNumber == inningsNum }
    if (innBalls.isEmpty()) return null
    val batsmanRuns = innBalls.groupBy { it.batsmanId }.mapValues { entry ->
        entry.value.sumOf { it.runs }
    }
    val topEntry = batsmanRuns.entries.maxByOrNull { it.value } ?: return null
    val player = players.find { it.id == topEntry.key } ?: return null
    return Pair(player, topEntry.value)
}

fun getTopBowler(players: List<Player>, balls: List<Ball>, inningsNum: Int): Pair<Player, String>? {
    val innBalls = balls.filter { it.inningsNumber == inningsNum }
    if (innBalls.isEmpty()) return null
    val bowlerStats = innBalls.groupBy { it.bowlerId }.mapValues { entry ->
        val wickets = entry.value.count { ball ->
            val wkAllowed = setOf("BOWLED", "CAUGHT", "LBW", "STUMPED", "HIT_WICKET")
            ball.isWicket && ball.wicketType in wkAllowed
        }
        val runsConceded = entry.value.sumOf { ball ->
            if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") 0 else ball.runs + ball.extraRuns
        }
        val validBalls = entry.value.count { it.extraType != "WIDE" && it.extraType != "NO_BALL" }
        Triple(wickets, runsConceded, validBalls)
    }
    val topEntry = bowlerStats.entries.maxWithOrNull(
        compareBy<Map.Entry<Long, Triple<Int, Int, Int>>> { it.value.first }
            .thenByDescending { it.value.second }
    ) ?: return null
    
    val player = players.find { it.id == topEntry.key } ?: return null
    val overs = topEntry.value.third / 6
    val ballsRem = topEntry.value.third % 6
    val statString = "${topEntry.value.first}W - ${topEntry.value.second}R ($overs.$ballsRem Overs)"
    return Pair(player, statString)
}

// --- LIVE SCORING SYSTEM BOARD ---
@Composable
fun LiveScoringScreen(
    viewModel: ScorerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activeMatch by viewModel.activeMatch.collectAsState()
    val inningsList by viewModel.activeInnings.collectAsState()
    val ballsList by viewModel.activeMatchBalls.collectAsState()
    val strikerId by viewModel.batsmanOnStrikeId.collectAsState()
    val nonStrikerId by viewModel.batsmanNonStrikeId.collectAsState()
    val currentBowlerId by viewModel.activeBowlerId.collectAsState()
    val allPlayers by viewModel.allPlayers.collectAsState()

    val currentInnings = inningsList.find { !it.isCompleted } ?: inningsList.lastOrNull()
    
    // Safety check redirect back
    if (activeMatch == null || currentInnings == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No active match loaded.", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Return to Dashboard") }
            }
        }
        return
    }

    val match = activeMatch!!
    val currentBallList = ballsList.filter { it.inningsNumber == currentInnings.inningsNumber }

    // Helpers to resolve Player objects
    val strikerPlayer = allPlayers.find { it.id == strikerId }
    val nonStrikerPlayer = allPlayers.find { it.id == nonStrikerId }
    val bowlerPlayer = allPlayers.find { it.id == currentBowlerId }

    // Aggregate batsman runs faced
    val strikerStats = remember(currentBallList, strikerId) {
        val runs = currentBallList.filter { it.batsmanId == strikerId }.sumOf { it.runs }
        val balls = currentBallList.filter { it.batsmanId == strikerId && it.extraType != "WIDE" }.size
        Pair(runs, balls)
    }
    val nonStrikerStats = remember(currentBallList, nonStrikerId) {
        val runs = currentBallList.filter { it.batsmanId == nonStrikerId }.sumOf { it.runs }
        val balls = currentBallList.filter { it.batsmanId == nonStrikerId && it.extraType != "WIDE" }.size
        Pair(runs, balls)
    }

    // Aggregate bowler stats conceded
    val bowlerStats = remember(currentBallList, currentBowlerId) {
        val bowlerDeliveries = currentBallList.filter { it.bowlerId == currentBowlerId }
        val runsConceded = bowlerDeliveries.sumOf { ball ->
            if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") 0 else ball.runs + ball.extraRuns
        }
        val validBalls = bowlerDeliveries.filter { it.extraType != "WIDE" && it.extraType != "NO_BALL" }.size
        val bowlerWickets = bowlerDeliveries.count { ball ->
            val wicketTypeAllowed = setOf("BOWLED", "CAUGHT", "LBW", "STUMPED", "HIT_WICKET")
            ball.isWicket && ball.wicketType in wicketTypeAllowed
        }
        Triple(runsConceded, validBalls, bowlerWickets)
    }

    // Modal dialogs states
    var showDismissalDialog by remember { mutableStateOf(false) }
    var selectedWicketType by remember { mutableStateOf("BOWLED") }
    var dismissedPlayerIdSelection by remember { mutableStateOf<Long?>(null) }
    var assistantFielderIdSelection by remember { mutableStateOf<Long?>(null) }

    // Late player adder state
    var showLatePlayerDialog by remember { mutableStateOf(false) }
    var latePlayerChoiceIndex by remember { mutableStateOf(0) } // 0: Select Existing, 1: Create New
    var latePlayerSelectedId by remember { mutableStateOf<Long?>(null) }
    var latePlayerNameInput by remember { mutableStateOf("") }
    var latePlayerNicknameInput by remember { mutableStateOf("") }
    var latePlayerMobileInput by remember { mutableStateOf("") }
    var latePlayerTeamAssign by remember { mutableStateOf("TEAM_A") } // "TEAM_A", "TEAM_B", "COMMON"

    // Next Bowler manual chooser State
    var showBowlerChooserDialog by remember { mutableStateOf(false) }
    var showStrikerChooserDialog by remember { mutableStateOf(false) }
    var showNonStrikerChooserDialog by remember { mutableStateOf(false) }
    var showNoBallRunsDialog by remember { mutableStateOf(false) }

    // Opening batsman and bowler selections
    var openingStrikerId by remember(currentInnings.id) { mutableStateOf<Long?>(null) }
    var openingNonStrikerId by remember(currentInnings.id) { mutableStateOf<Long?>(null) }
    var openingBowlerId by remember(currentInnings.id) { mutableStateOf<Long?>(null) }

    val battingPlayersPool = if (currentInnings.battingTeam == match.teamAName) match.teamAPlayerIds else match.teamBPlayerIds
    val bowlingPlayersPool = if (currentInnings.battingTeam == match.teamAName) match.teamBPlayerIds else match.teamAPlayerIds

    // Show Bowler / Bat chooser if null after over / wicket (only mid-innings, not a fresh innings!)
    val isFreshInnings = currentBallList.isEmpty()
    if (!isFreshInnings) {
        if (currentBowlerId == null && !currentInnings.isCompleted && !showBowlerChooserDialog) {
            LaunchedEffect(Unit) { showBowlerChooserDialog = true }
        }
        if (strikerId == null && !currentInnings.isCompleted && !showStrikerChooserDialog) {
            LaunchedEffect(Unit) { showStrikerChooserDialog = true }
        }
        
        // Auto-trigger Non-Striker chooser only if non-striker is null and there are eligible, non-out players remaining in the innings pool
        val currentBallListOutIds = currentBallList.filter { it.isWicket && it.dismissedPlayerId != null }.map { it.dismissedPlayerId!! }.toSet()
        val battingEligibleRemaining = (battingPlayersPool + match.commonPlayerIds).filter { it !in currentBallListOutIds && it != strikerId }
        if (nonStrikerId == null && battingEligibleRemaining.isNotEmpty() && !currentInnings.isCompleted && !showNonStrikerChooserDialog) {
            LaunchedEffect(Unit) { showNonStrikerChooserDialog = true }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Live Header Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(TealPrimary, TealDark)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .statusBarsPadding()
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                            Text(
                                text = "Tanish Live Scoring",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                        }

                        // Sync indicators
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Add Late Player Trigger
                            IconButton(
                                onClick = { showLatePlayerDialog = true },
                                modifier = Modifier.background(Color(0x22FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "Add Late Player", tint = Color.White)
                            }

                            // Share WhatsApp button
                            IconButton(
                                onClick = {
                                    val mvpName = allPlayers.find { it.id == match.mvpPlayerId }?.name
                                    shareMatchToWhatsApp(context, match, inningsList, mvpName)
                                },
                                modifier = Modifier.background(Color(0x22FFFFFF), CircleShape)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share WhatsApp", tint = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Core CricHeroes scoreboard summary card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = currentInnings.battingTeam,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "${currentInnings.totalRuns}/${currentInnings.totalWickets}",
                                    color = Color.White,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val overs = currentInnings.totalBallsBowled / 6
                                val balls = currentInnings.totalBallsBowled % 6
                                Text(
                                    text = "($overs.$balls Overs)",
                                    color = Color(0xFF2DD4BF),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                        }

                        // Target Box (Displays target if innings is 2)
                        if (currentInnings.inningsNumber == 2) {
                            Column(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0x26000000))
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("TARGET SCORE", fontSize = 10.sp, color = Color.White.copy(0.7f), fontWeight = FontWeight.Bold)
                                Text("${currentInnings.target}", fontSize = 20.sp, color = GoldCap, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
            
            if (currentInnings.inningsNumber == 2) {
                val target = currentInnings.target
                val runsRequired = maxOf(0, target - currentInnings.totalRuns)
                val totalBallsScheduled = match.numberOfOvers * 6
                val ballsRemaining = maxOf(0, totalBallsScheduled - currentInnings.totalBallsBowled)
                val crr = if (currentInnings.totalBallsBowled > 0) (currentInnings.totalRuns.toFloat() / (currentInnings.totalBallsBowled.toFloat() / 6f)) else 0f
                val rrr = if (ballsRemaining > 0) (runsRequired.toFloat() / (ballsRemaining.toFloat() / 6f)) else 0f
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = if (runsRequired > 0 && ballsRemaining > 0) "Need $runsRequired runs in $ballsRemaining balls" else if (runsRequired <= 0) "Target Met!" else "Innings Completed",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Target: $target | CRR: ${String.format("%.2f", crr)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Req R.R.",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            )
                            Text(
                                text = String.format("%.2f", rrr),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Striker / Bowler Table Row (M3 style)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // Batsmen
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Batsman (Tap to Change)", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TealPrimary)
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TealPrimary.copy(alpha = 0.12f))
                                .clickable { viewModel.swapStrike() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = "Swap Strike",
                                tint = TealPrimary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Swap Strike", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = TealPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showStrikerChooserDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "Striker", tint = GoldCap, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = strikerPlayer?.name ?: "Select Batsman...",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = if (strikerPlayer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text("${strikerStats.first} (${strikerStats.second})", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showNonStrikerChooserDialog = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(22.dp))
                            Text(
                                text = nonStrikerPlayer?.name ?: "Select Batsman...", 
                                fontSize = 14.sp,
                                color = if (nonStrikerPlayer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text("${nonStrikerStats.first} (${nonStrikerStats.second})", fontSize = 14.sp)
                    }

                    Divider(modifier = Modifier.padding(vertical = 10.dp))

                    // Bowlers info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Bowler", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TealPrimary)
                        Text("O  M  R  W", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = TealPrimary)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBowlerChooserDialog = true },
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = bowlerPlayer?.name ?: "Select Bowler...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = if (bowlerPlayer == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        )
                        val overs = bowlerStats.second / 6
                        val balls = bowlerStats.second % 6
                        Text("$overs.$balls - 0 - ${bowlerStats.first} - ${bowlerStats.third}", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Quick live timeline of the current over (displays ball-by-ball icons!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("This over: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                Spacer(modifier = Modifier.width(8.dp))

                val thisOverBalls = currentBallList.filter { 
                    it.overIndex == currentInnings.totalBallsBowled / 6 
                }

                if (thisOverBalls.isEmpty()) {
                    Text("First ball of over in progress", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        thisOverBalls.forEach { ball ->
                            val txt = when {
                                ball.isWicket -> "W"
                                ball.extraType == "WIDE" -> "${ball.totalRuns}Wd"
                                ball.extraType == "NO_BALL" -> "${ball.totalRuns}Nb"
                                ball.extraType == "BYE" -> "${ball.totalRuns}By"
                                ball.extraType == "LEG_BYE" -> "${ball.totalRuns}Lb"
                                else -> "${ball.runs}"
                            }
                            val isWkt = ball.isWicket
                            val isWdOrNb = ball.extraType == "WIDE" || ball.extraType == "NO_BALL"
                            val isBoundary = ball.runs == 4 || ball.runs == 6
                            
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isWkt -> Color(0xFFFEE2E2)
                                            isWdOrNb -> Color(0xFFFEF3C7)
                                            isBoundary -> TealPrimary
                                            else -> Color(0xFFE2E8F0)
                                        }
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = when {
                                            isWkt -> Color(0xFFFCA5A5)
                                            isWdOrNb -> Color(0xFFFCD34D)
                                            isBoundary -> Color.Transparent
                                            else -> Color(0xFFCBD5E1)
                                        },
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = txt,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        isWkt -> Color(0xFFB91C1C)
                                        isWdOrNb -> Color(0xFFB45309)
                                        isBoundary -> Color.White
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // RUNS INPUT PANEL (Requirement 7: Professional keyboard buttons!)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Main Runs buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(0, 1, 2, 3).forEach { runs ->
                        Button(
                            onClick = { viewModel.bowlBall(runs, null, false, null, null, null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .testTag("score_btn_$runs"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("$runs", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(4, 6).forEach { runs ->
                        Button(
                            onClick = { viewModel.bowlBall(runs, null, false, null, null, null) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = TealPrimaryContainer,
                                contentColor = TealPrimary
                            ),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                            modifier = Modifier
                                .weight(1.5f) // weight slightly larger for boundaries!
                                .height(56.dp)
                                .testTag("score_btn_$runs"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (runs == 4) "4" else "6", 
                                fontSize = 22.sp, 
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                    
                    // Single run of 5
                    Button(
                        onClick = { viewModel.bowlBall(5, null, false, null, null, null) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("5", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Extras panel (Wide, Nb, Bye, Lb) with precise Slate styling
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.bowlBall(0, "WIDE", false, null, null, null) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF1F5F9), 
                            contentColor = Color(0xFF475569)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Wide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { showNoBallRunsDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF1F5F9), 
                            contentColor = Color(0xFF475569)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("No Ball", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.bowlBall(1, "BYE", false, null, null, null) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF1F5F9), 
                            contentColor = Color(0xFF475569)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Bye", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }

                    OutlinedButton(
                        onClick = { viewModel.bowlBall(1, "LEG_BYE", false, null, null, null) },
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0xFFF1F5F9), 
                            contentColor = Color(0xFF475569)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.5.dp),
                        modifier = Modifier
                            .weight(1.1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Leg Bye", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }
                }

                // Dismissal and Undo row with customized Professional Polish themes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            selectedWicketType = "BOWLED"
                            dismissedPlayerIdSelection = strikerId
                            assistantFielderIdSelection = null
                            showDismissalDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFEF2F2), 
                            contentColor = Color(0xFFDC2626)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(52.dp)
                            .testTag("out_button_trigger"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Output, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OUT / WKT", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.undoLastBall() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF263238), 
                            contentColor = Color.White
                        ),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .weight(0.8f)
                            .height(52.dp)
                            .testTag("undo_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Undo", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // No Ball Bat Runs Selection Dialog
        if (showNoBallRunsDialog) {
            AlertDialog(
                onDismissRequest = { showNoBallRunsDialog = false },
                title = { Text("No Ball - Bat Runs", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Select runs scored off the bat, if any:", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val runsOptions = listOf(0, 1, 2, 3, 4, 6)
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            runsOptions.chunked(3).forEach { rowRuns ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowRuns.forEach { runValue ->
                                        Button(
                                            onClick = {
                                                viewModel.bowlBall(runValue, "NO_BALL", false, null, null, null)
                                                showNoBallRunsDialog = false
                                            },
                                            modifier = Modifier.weight(1f).height(48.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                                        ) {
                                            Text(
                                                text = "NB+$runValue", 
                                                fontSize = 12.sp, 
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = { showNoBallRunsDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 1. CHOOSE BOWLER MODAL DIALOG (Triggered at end of overs)
        if (showBowlerChooserDialog) {
            val eligibleBowlers = bowlingPlayersPool + match.commonPlayerIds
            Dialog(onDismissRequest = { /* Force selection */ }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Select Bowler for Next Over", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(eligibleBowlers) { id ->
                                val play = allPlayers.find { it.id == id }
                                if (play != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.setScoringLineup(strikerId, nonStrikerId, id)
                                                showBowlerChooserDialog = false
                                            }
                                            .padding(vertical = 10.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PlayerAvatar(avatarIndex = play.profilePhotoUri, size = 28)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(play.name, fontWeight = FontWeight.SemiBold)
                                    }
                                    Divider()
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. CHOOSE STRIKER BATSMAN MODAL DIALOG
        if (showStrikerChooserDialog) {
            val eligibleBatsmen = battingPlayersPool + match.commonPlayerIds
            Dialog(onDismissRequest = { /* Force selection */ }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Select New Batsman On Strike", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Filter out already dismissed batsmen for this match/innings
                        val outIds = currentBallList.filter { it.isWicket && it.dismissedPlayerId != null }.map { it.dismissedPlayerId!! }.toSet()
                        val activeAvailableBatsmen = eligibleBatsmen.filter { it !in outIds && it != nonStrikerId }

                        if (activeAvailableBatsmen.isEmpty()) {
                            val isInnings1 = currentInnings.inningsNumber == 1
                            Text(
                                text = if (isInnings1) {
                                    "No further batsmen available! This innings is complete. Transition to the next innings?"
                                } else {
                                    "No further batsmen available! Match is completed. Transition to results?"
                                },
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.forceEndActiveInnings()
                                    showStrikerChooserDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (isInnings1) "Transition to Innings 2 🏏" else "Complete & End Match 🏆",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(activeAvailableBatsmen) { id ->
                                    val play = allPlayers.find { it.id == id }
                                    if (play != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setScoringLineup(id, nonStrikerId, currentBowlerId)
                                                    showStrikerChooserDialog = false
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlayerAvatar(avatarIndex = play.profilePhotoUri, size = 28)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(play.name, fontWeight = FontWeight.SemiBold)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2B. CHOOSE NON-STRIKER BATSMAN MODAL DIALOG
        if (showNonStrikerChooserDialog) {
            val eligibleBatsmen = battingPlayersPool + match.commonPlayerIds
            Dialog(onDismissRequest = { showNonStrikerChooserDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Select Non-Striker Batsman", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Filter out already dismissed batsmen for this match/innings
                        val outIds = currentBallList.filter { it.isWicket && it.dismissedPlayerId != null }.map { it.dismissedPlayerId!! }.toSet()
                        val activeAvailableBatsmen = eligibleBatsmen.filter { it !in outIds && it != strikerId }

                        if (activeAvailableBatsmen.isEmpty()) {
                            Text(
                                text = "No further batsmen available for non-striker end.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    showNonStrikerChooserDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Close", fontWeight = FontWeight.Bold)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                                items(activeAvailableBatsmen) { id ->
                                    val play = allPlayers.find { it.id == id }
                                    if (play != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.setScoringLineup(strikerId, id, currentBowlerId)
                                                    showNonStrikerChooserDialog = false
                                                }
                                                .padding(vertical = 10.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlayerAvatar(avatarIndex = play.profilePhotoUri, size = 28)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(play.name, fontWeight = FontWeight.SemiBold)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- NEW DIALOGS FOR DETAILED SCORING FLOW AND TRANSITIONS ---
        // 1. SELECT OPENING LINEUP DIALOG
        if (isFreshInnings && !currentInnings.isCompleted) {
            var showSelectSubListByRole by remember { mutableStateOf<String?>(null) } // "STRIKER", "NON_STRIKER", "BOWLER" or null
            
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "innings ${currentInnings.inningsNumber} lineUp".uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = TealPrimary,
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentInnings.battingTeam,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        if (currentInnings.inningsNumber == 2) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Target: ${currentInnings.target} runs",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = GoldCap
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (showSelectSubListByRole != null) {
                            // Display list of players to select for the specific role
                            val role = showSelectSubListByRole!!
                            val candidates = if (role == "BOWLER") {
                                bowlingPlayersPool + match.commonPlayerIds
                            } else {
                                val base = battingPlayersPool + match.commonPlayerIds
                                if (role == "NON_STRIKER") base.filter { it != openingStrikerId } else base
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Select $role",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                TextButton(onClick = { showSelectSubListByRole = null }) {
                                    Text("Cancel", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(candidates) { pId ->
                                    val p = allPlayers.find { it.id == pId }
                                    if (p != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    if (role == "STRIKER") {
                                                        openingStrikerId = p.id
                                                        if (openingNonStrikerId == p.id) {
                                                            openingNonStrikerId = null
                                                        }
                                                    } else if (role == "NON_STRIKER") {
                                                        openingNonStrikerId = p.id
                                                    } else {
                                                        openingBowlerId = p.id
                                                    }
                                                    showSelectSubListByRole = null
                                                }
                                                .padding(vertical = 12.dp, horizontal = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            PlayerAvatar(avatarIndex = p.profilePhotoUri, size = 26)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(p.name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        } else {
                            // Display selected or buttons
                            val strikerName = allPlayers.find { it.id == openingStrikerId }?.name ?: "Tap to select"
                            val nonStrikerName = allPlayers.find { it.id == openingNonStrikerId }?.name ?: "Tap to select"
                            val bowlerName = allPlayers.find { it.id == openingBowlerId }?.name ?: "Tap to select"
                            
                            // Striker Button
                            OutlinedButton(
                                onClick = { showSelectSubListByRole = "STRIKER" },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Opening Striker:", fontWeight = FontWeight.Bold, color = TealDark)
                                    Text(strikerName, fontWeight = FontWeight.Medium, color = if(openingStrikerId == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f) else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            
                            // Non Striker Button
                            OutlinedButton(
                                onClick = { showSelectSubListByRole = "NON_STRIKER" },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(),
                                enabled = openingStrikerId != null
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Opening Non-Striker:", fontWeight = FontWeight.Bold, color = TealDark)
                                    Text(nonStrikerName, fontWeight = FontWeight.Medium, color = if(openingNonStrikerId == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f) else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            
                            // Bowler Button
                            OutlinedButton(
                                onClick = { showSelectSubListByRole = "BOWLER" },
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors()
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Opening Bowler:", fontWeight = FontWeight.Bold, color = TealDark)
                                    Text(bowlerName, fontWeight = FontWeight.Medium, color = if(openingBowlerId == null) MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f) else MaterialTheme.colorScheme.onSurface)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = {
                                    if (openingStrikerId != null && openingNonStrikerId != null && openingBowlerId != null) {
                                        viewModel.setScoringLineup(openingStrikerId, openingNonStrikerId, openingBowlerId)
                                    }
                                },
                                enabled = openingStrikerId != null && openingNonStrikerId != null && openingBowlerId != null,
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Confirm & Start Innings 🚀", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // 2. FIRST INNINGS ENDED SUMMARY DIALOG
        if (inningsList.size == 1 && inningsList[0].isCompleted) {
            val inn1 = inningsList[0]
            val topBat = getTopBatsman(allPlayers, ballsList, 1)
            val topBowl = getTopBowler(allPlayers, ballsList, 1)
            
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(16.dp).fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "First Innings Ended 📣",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = TealPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SUMMARY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = inn1.battingTeam,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Score", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${inn1.totalRuns}/${inn1.totalWickets}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = TealDark)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Overs", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${inn1.totalBallsBowled / 6}.${inn1.totalBallsBowled % 6}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Run Rate", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val rr = if (inn1.totalBallsBowled > 0) String.format("%.2f", (inn1.totalRuns.toFloat() / (inn1.totalBallsBowled.toFloat() / 6f))) else "0.00"
                                Text(rr, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 12.dp))
                        
                        if (topBat != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Top Batsman:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${topBat.first.name} (${topBat.second} Run${if(topBat.second == 1) "" else "s"})", fontSize = 13.sp)
                            }
                        }
                        
                        if (topBowl != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Top Bowler:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${topBowl.first.name} (${topBowl.second})", fontSize = 13.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Button(
                            onClick = { viewModel.startSecondInnings() },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Start Second Innings 🪐", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Allow Undo from here if they made a mistake on the final ball of innings 1
                        TextButton(
                            onClick = { viewModel.undoLastBall() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Undo Last Delivery ↩️", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. MATCH COMPLETED SUMMARY DIALOG (SECOND INNINGS ENDED / TARGET MET / ALL OUT)
        if (inningsList.find { it.inningsNumber == 2 }?.isCompleted == true) {
            val inn1 = inningsList.find { it.inningsNumber == 1 }
            val inn2 = inningsList.find { it.inningsNumber == 2 }
            
            if (inn1 != null && inn2 != null) {
                val inn1Score = inn1.totalRuns
                val inn2Score = inn2.totalRuns
                val battingTeamSize = if (inn2.battingTeam == match.teamAName) match.teamAPlayerIds.size else match.teamBPlayerIds.size
                val wicketsLeft = maxOf(0, battingTeamSize - inn2.totalWickets - 1)
                
                val resultMessage = if (inn1Score > inn2Score) {
                    "${inn1.battingTeam} won by ${inn1Score - inn2Score} runs"
                } else if (inn2Score > inn1Score) {
                    "${inn2.battingTeam} won by $wicketsLeft wickets"
                } else {
                    "Match Tied!"
                }
                
                Dialog(onDismissRequest = {}) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.padding(16.dp).fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Match Completed! 🏆",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 20.sp,
                                color = GoldCap
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = resultMessage,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = TealPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Show comparative scores
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${inn1.battingTeam} (1st Inn):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${inn1.totalRuns}/${inn1.totalWickets}", fontSize = 13.sp)
                            }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${inn2.battingTeam} (2nd Inn):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${inn2.totalRuns}/${inn2.totalWickets}", fontSize = 13.sp)
                            }
                            
                            Spacer(modifier = Modifier.height(24.dp))
                            
                            Button(
                                onClick = { viewModel.completeActiveMatchManual() },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Finish Match & Save Results 📁", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Undo button to correct last ball score!
                            TextButton(
                                onClick = { viewModel.undoLastBall() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Undo Last Delivery ↩️", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 3. WICKET DISMISSAL BOTTOM DIALOG
        if (showDismissalDialog) {
            Dialog(onDismissRequest = { showDismissalDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Record Dismissal/Wicket", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Wicket Type", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Column {
                            listOf("BOWLED", "CAUGHT", "LBW", "RUN_OUT", "STUMPED", "HIT_WICKET").forEach { mType ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedWicketType = mType }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedWicketType == mType, onClick = { selectedWicketType = mType })
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(mType)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Which player got out
                        Text("Batsman Dismissed", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            strikerPlayer?.let {
                                Button(
                                    onClick = { dismissedPlayerIdSelection = it.id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dismissedPlayerIdSelection == it.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (dismissedPlayerIdSelection == it.id) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(it.name.take(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            nonStrikerPlayer?.let {
                                Button(
                                    onClick = { dismissedPlayerIdSelection = it.id },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (dismissedPlayerIdSelection == it.id) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (dismissedPlayerIdSelection == it.id) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(it.name.take(12), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }

                        // Assistant Fielder if CAUGHT or RUN OUT
                        if (selectedWicketType == "CAUGHT" || selectedWicketType == "RUN_OUT" || selectedWicketType == "STUMPED") {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Fielder Involved (Optional)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 120.dp)) {
                                val fielders = bowlingPlayersPool + match.commonPlayerIds
                                items(fielders) { fId ->
                                    val fl = allPlayers.find { it.id == fId }
                                    if (fl != null) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { assistantFielderIdSelection = fId }
                                                .padding(vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(selected = assistantFielderIdSelection == fId, onClick = { assistantFielderIdSelection = fId })
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(fl.name)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showDismissalDialog = false }) { Text("Cancel") }
                            Button(
                                onClick = {
                                    val dismissed = dismissedPlayerIdSelection
                                    if (dismissed != null) {
                                        viewModel.bowlBall(
                                            runs = 0,
                                            extraType = null,
                                            isWicket = true,
                                            wicketType = selectedWicketType,
                                            dismissedPlayerId = dismissed,
                                            fielderId = assistantFielderIdSelection
                                        )
                                        showDismissalDialog = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Submit Out", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // 4. LATE PLAYER ASSIGNER BACKDOOR DIALOG (Requirement 5!)
        if (showLatePlayerDialog) {
            Dialog(onDismissRequest = { showLatePlayerDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text("Add Late Joined Player", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { latePlayerChoiceIndex = 0 },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (latePlayerChoiceIndex == 0) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (latePlayerChoiceIndex == 0) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("Choose Existing")
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Button(
                                onClick = { latePlayerChoiceIndex = 1 },
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (latePlayerChoiceIndex == 1) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (latePlayerChoiceIndex == 1) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text("Create New")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (latePlayerChoiceIndex == 0) {
                            Text("Select Master Player", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyColumn(modifier = Modifier.heightIn(max = 140.dp)) {
                                val currentIds = (match.teamAPlayerIds + match.teamBPlayerIds + match.commonPlayerIds).toSet()
                                val nonSelectedMaster = allPlayers.filter { it.id !in currentIds }
                                items(nonSelectedMaster) { opt ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { latePlayerSelectedId = opt.id }
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = latePlayerSelectedId == opt.id, onClick = { latePlayerSelectedId = opt.id })
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(opt.name)
                                    }
                                }
                            }
                        } else {
                            TextField(
                                value = latePlayerNameInput,
                                onValueChange = { latePlayerNameInput = it },
                                label = { Text("Player Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            TextField(
                                value = latePlayerNicknameInput,
                                onValueChange = { latePlayerNicknameInput = it },
                                label = { Text("Nickname (Optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Assign to Team:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("TEAM_A", "TEAM_B", "COMMON").forEach { teamT ->
                                Button(
                                    onClick = { latePlayerTeamAssign = teamT },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (latePlayerTeamAssign == teamT) TealPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (latePlayerTeamAssign == teamT) Color.White else MaterialTheme.colorScheme.onSurface
                                    ),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(if (teamT == "TEAM_A") match.teamAName.take(4) else if (teamT == "TEAM_B") match.teamBName.take(4) else "Common", fontSize = 10.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showLatePlayerDialog = false }) { Text("Cancel") }
                            Button(
                                onClick = {
                                    if (latePlayerChoiceIndex == 0) {
                                        val pId = latePlayerSelectedId
                                        val sourcePlayer = allPlayers.find { it.id == pId }
                                        if (sourcePlayer != null) {
                                            viewModel.addLatePlayerToActiveMatch(sourcePlayer, latePlayerTeamAssign)
                                            showLatePlayerDialog = false
                                        }
                                    } else {
                                        if (latePlayerNameInput.isNotBlank()) {
                                            val sourcePlayer = Player(name = latePlayerNameInput, nickname = latePlayerNicknameInput, mobileNumber = latePlayerMobileInput)
                                            viewModel.addLatePlayerToActiveMatch(sourcePlayer, latePlayerTeamAssign)
                                            showLatePlayerDialog = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Add Player", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- MATCH COMPLETED SCORECARDS & HISTORY SCREEN ---
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun MatchHistoryScreen(
    viewModel: ScorerViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val matches by viewModel.allMatches.collectAsState()
    val players by viewModel.allPlayers.collectAsState()

    var selectedMatchIdDetail by remember { mutableStateOf<Long?>(null) }
    var inningsOfSelectedMatch = remember { mutableStateListOf<Innings>() }
    var ballsOfSelectedMatch = remember { mutableStateListOf<Ball>() }

    val completedMatches = matches.filter { it.isCompleted }

    // Dialog and Menu States for Deletion and Editing
    var matchToDelete by remember { mutableStateOf<Match?>(null) }
    var matchToEdit by remember { mutableStateOf<Match?>(null) }
    var matchToShowOptionDialog by remember { mutableStateOf<Match?>(null) }

    // Text fields state for editing match
    var editMatchName by remember { mutableStateOf("") }
    var editMatchTeamA by remember { mutableStateOf("") }
    var editMatchTeamB by remember { mutableStateOf("") }

    // Coroutine scope to pull inner score details
    val scope = rememberCoroutineScope()
    val repo = remember { CricketsRepository(context) }

    LaunchedEffect(selectedMatchIdDetail) {
        val mId = selectedMatchIdDetail
        if (mId != null) {
            inningsOfSelectedMatch.clear()
            inningsOfSelectedMatch.addAll(repo.getInningsForMatch(mId))
            ballsOfSelectedMatch.clear()
            ballsOfSelectedMatch.addAll(repo.getBallsForMatch(mId))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (selectedMatchIdDetail != null) {
                        selectedMatchIdDetail = null
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = if (selectedMatchIdDetail == null) "Matches Archive" else "Full Match Scorecard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (selectedMatchIdDetail == null) {
                // List of Matches
                if (completedMatches.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SportsCricket, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(0.3f), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("No completed matches recorded yet.", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(completedMatches) { match ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .combinedClickable(
                                        onClick = { selectedMatchIdDetail = match.id },
                                        onLongClick = { matchToShowOptionDialog = match }
                                    )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(match.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                            Text(formatMatchDate(match.date), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            // Direct Trash can icon to delete match
                                            IconButton(
                                                onClick = { matchToDelete = match },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Match",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            // 3-dots choices menu
                                            IconButton(
                                                onClick = { matchToShowOptionDialog = match },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MoreVert,
                                                    contentDescription = "Match Options",
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(0.6f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(match.teamAName, fontWeight = FontWeight.Medium)
                                            Text("vs", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                            Text(match.teamBName, fontWeight = FontWeight.Medium)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(TealPrimary.copy(0.12f))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(match.resultText, color = TealPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            
                                            // Golden Trophy MVP label
                                            val mvpPlayer = players.find { it.id == match.mvpPlayerId }
                                            if (mvpPlayer != null) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldCap, modifier = Modifier.size(14.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text("MVP: ${mvpPlayer.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GoldCap)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Scorecard details of selected Completed Match
                val match = matches.find { it.id == selectedMatchIdDetail }
                if (match != null) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        item {
                            // Title Card
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(match.name, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(match.resultText, fontWeight = FontWeight.Black, fontSize = 14.sp, color = TealPrimary, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(formatMatchDate(match.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(0.7f))
                                }
                            }

                            // Exporters (PDF and Excel requirement!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        Toast.makeText(context, "PDF Report generated: /storage/emulated/0/Download/${match.name.replace(" ", "_")}_Scorecard.pdf", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export PDF", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        Toast.makeText(context, "Excel Document compiled: /storage/emulated/0/Download/${match.name.replace(" ", "_")}_KPI.xlsx", Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Icon(Icons.Default.GridOn, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Excel", fontSize = 12.sp)
                                }
                            }

                            // MVP Section
                            val mvpPlayer = players.find { it.id == match.mvpPlayerId }
                            if (mvpPlayer != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = GoldCap.copy(0.1f)),
                                    border = BorderStroke(1.dp, GoldCap),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = GoldCap, modifier = Modifier.size(48.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column {
                                            Text("MAN OF THE MATCH MVP", fontWeight = FontWeight.Bold, color = GoldCap, fontSize = 12.sp)
                                            Text(mvpPlayer.name, fontWeight = FontWeight.Black, fontSize = 18.sp)
                                            Text("Calculated automatically on boundaries, economy, strikerates and dismissals points", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                        }
                                    }
                                }
                            }
                        }

                        // Innings blocks
                        items(inningsOfSelectedMatch) { inn ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Scorecard: ${inn.battingTeam}", fontWeight = FontWeight.Bold, color = TealPrimary)
                                        val ov = inn.totalBallsBowled / 6
                                        val bl = inn.totalBallsBowled % 6
                                        Text("${inn.totalRuns}/${inn.totalWickets} ($ov.$bl Ov)", fontWeight = FontWeight.Black, fontSize = 16.sp)
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Batsman Details Table
                                    Text("Batting Table", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val innBalls = ballsOfSelectedMatch.filter { it.inningsNumber == inn.inningsNumber }
                                    val matchPlayerIds = (match.teamAPlayerIds + match.teamBPlayerIds + match.commonPlayerIds).toSet()
                                    val batters = players.filter { it.id in matchPlayerIds }

                                    batters.forEach { b ->
                                        val bBalls = innBalls.filter { it.batsmanId == b.id }
                                        if (bBalls.isNotEmpty()) {
                                            val runs = bBalls.sumOf { it.runs }
                                            val bf = bBalls.filter { it.extraType != "WIDE" }.size
                                            val out = innBalls.find { it.isWicket && it.dismissedPlayerId == b.id }
                                            val status = if (out != null) "out ${out.wicketType}" else "not out"
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Column {
                                                    Text(b.name, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                    Text(status, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                                }
                                                Text("$runs ($bf)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Bowling Details Table
                                    Text("Bowling Table", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                    Spacer(modifier = Modifier.height(4.dp))

                                    batters.forEach { bowler ->
                                        val bowBalls = innBalls.filter { it.bowlerId == bowler.id }
                                        if (bowBalls.isNotEmpty()) {
                                            val runs = bowBalls.sumOf { ball ->
                                                if (ball.extraType == "BYE" || ball.extraType == "LEG_BYE") 0 else ball.runs + ball.extraRuns
                                            }
                                            val validBalls = bowBalls.filter { it.extraType != "WIDE" && it.extraType != "NO_BALL" }.size
                                            val wickets = bowBalls.count { ball ->
                                                val wkAllowed = setOf("BOWLED", "CAUGHT", "LBW", "STUMPED", "HIT_WICKET")
                                                ball.isWicket && ball.wicketType in wkAllowed
                                            }
                                            
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(bowler.name, fontSize = 13.sp)
                                                val ovStr = "${validBalls / 6}.${validBalls % 6}"
                                                Text("Overs: $ovStr, Wkts: $wickets, Runs: $runs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

        // --- Dialogs ---

        // 1. Matches Options Choice Dialog
        if (matchToShowOptionDialog != null) {
            val match = matchToShowOptionDialog!!
            AlertDialog(
                onDismissRequest = { matchToShowOptionDialog = null },
                title = { Text(text = "Match Options", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(text = match.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose an action below for this match.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        ListItem(
                            headlineContent = { Text("View Scorecard", fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Default.SportsCricket, contentDescription = null, tint = TealPrimary) },
                            modifier = Modifier.clickable {
                                selectedMatchIdDetail = match.id
                                matchToShowOptionDialog = null
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ListItem(
                            headlineContent = { Text("Edit Match Details", fontWeight = FontWeight.SemiBold) },
                            leadingContent = { Icon(Icons.Default.Edit, contentDescription = null, tint = TealPrimary) },
                            modifier = Modifier.clickable {
                                matchToEdit = match
                                editMatchName = match.name
                                editMatchTeamA = match.teamAName
                                editMatchTeamB = match.teamBName
                                matchToShowOptionDialog = null
                            }
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        ListItem(
                            headlineContent = { Text("Delete Match", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.error) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            modifier = Modifier.clickable {
                                matchToDelete = match
                                matchToShowOptionDialog = null
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { matchToShowOptionDialog = null }) {
                        Text("Close")
                    }
                }
            )
        }

        // 2. Edit Match Fields Dialog
        if (matchToEdit != null) {
            val match = matchToEdit!!
            AlertDialog(
                onDismissRequest = { matchToEdit = null },
                title = { Text(text = "Edit Match Records", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editMatchName,
                            onValueChange = { editMatchName = it },
                            label = { Text("Match Title") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editMatchTeamA,
                            onValueChange = { editMatchTeamA = it },
                            label = { Text("Team A Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editMatchTeamB,
                            onValueChange = { editMatchTeamB = it },
                            label = { Text("Team B Name") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        onClick = {
                            if (editMatchName.isNotBlank() && editMatchTeamA.isNotBlank() && editMatchTeamB.isNotBlank()) {
                                viewModel.updateMatchFields(match.id, editMatchName, editMatchTeamA, editMatchTeamB)
                                matchToEdit = null
                                Toast.makeText(context, "Match updated successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Text("Save Changes", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { matchToEdit = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // 3. Delete Match Confirmation Dialog
        if (matchToDelete != null) {
            val match = matchToDelete!!
            AlertDialog(
                onDismissRequest = { matchToDelete = null },
                title = { Text("Delete Match?", fontWeight = FontWeight.Bold) },
                text = {
                    Text("Are you sure you want to delete this match?\n\nThis will permanently delete the match, its completed scorecards, associated innings, and ball-by-ball records from both local database and Cloud Firestore. This action cannot be undone.")
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        onClick = {
                            viewModel.deleteMatch(match.id)
                            matchToDelete = null
                            Toast.makeText(context, "Match deleted successfully", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { matchToDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

// --- PLAYER STATISTICS & LEaderboard TABS ---
@Composable
fun StatisticsScreen(
    viewModel: ScorerViewModel,
    onBack: () -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Batting Stats, 1: Bowling Stats, 2: Leaderboards, 3: Profile Search / QR
    val carrierStats by viewModel.careerStats.collectAsState()
    val playersAll by viewModel.allPlayers.collectAsState()

    var profileSearchQuery by remember { mutableStateOf("") }
    var selectedPlayerProfileId by remember { mutableStateOf<Long?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Carrier Stats & Caps", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            // Tab Rows
            TabRow(
                selectedTabIndex = activeTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = TealPrimary
            ) {
                Tab(selected = activeTab == 0, onClick = { activeTab = 0 }) { Text("Batting", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Tab(selected = activeTab == 1, onClick = { activeTab = 1 }) { Text("Bowling", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Tab(selected = activeTab == 2, onClick = { activeTab = 2 }) { Text("Caps", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                Tab(selected = activeTab == 3, onClick = { activeTab = 3 }) { Text("Profile QR", modifier = Modifier.padding(12.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when (activeTab) {
                0 -> {
                    // Batting Leaderboards
                    val sortedBatting = carrierStats.sortedByDescending { it.battingRuns }
                    if (sortedBatting.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No stats available. Complete matches first.") }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sortedBatting) { stat ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PlayerAvatar(avatarIndex = stat.profilePhotoUri, size = 32)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stat.playerName, fontWeight = FontWeight.Bold)
                                                Text("Matches: ${stat.battingMatches}  |  Avg: ${String.format("%.1f", stat.battingAverage)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${stat.battingRuns} Runs", fontWeight = FontWeight.Black, color = TealPrimary, fontSize = 16.sp)
                                            Text("S.R: ${String.format("%.1f", stat.battingStrikeRate)}", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Bowling Leaderboards
                    val sortedBowling = carrierStats.sortedByDescending { it.bowlingWickets }
                    if (sortedBowling.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No statistics available.") }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(sortedBowling) { stat ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PlayerAvatar(avatarIndex = stat.profilePhotoUri, size = 32)
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(stat.playerName, fontWeight = FontWeight.Bold)
                                                Text("Overs: ${stat.bowlingOversString}  |  Econ: ${String.format("%.2f", stat.bowlingEconomy)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
                                            }
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("${stat.bowlingWickets} Wickets", fontWeight = FontWeight.Black, color = PurpleCap, fontSize = 15.sp)
                                            Text("BB: ${stat.bestBowlingString}", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Leaderboards: Orange and Purple Cap cards + Fielding cap Card
                    val orangeLeader = carrierStats.maxByOrNull { it.battingRuns }
                    val purpleLeader = carrierStats.maxByOrNull { it.bowlingWickets }
                    val fieldingLeader = carrierStats.maxByOrNull { it.catches + it.runOuts + it.stumpings }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Orange Cap
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = GoldCap.copy(0.12f)),
                                border = BorderStroke(1.5.dp, GoldCap),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.WorkspacePremium, contentDescription = null, tint = GoldCap, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("ORANGE CAP HONOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GoldCap)
                                        Text(orangeLeader?.playerName ?: "No records found", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        Text("Most Runs: ${orangeLeader?.battingRuns ?: 0} in active matches", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Purple Cap
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PurpleCap.copy(0.12f)),
                                border = BorderStroke(1.5.dp, PurpleCap),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.MilitaryTech, contentDescription = null, tint = PurpleCap, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("PURPLE CAP HONOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PurpleCap)
                                        Text(purpleLeader?.playerName ?: "No records found", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        Text("Most Wickets: ${purpleLeader?.bowlingWickets ?: 0} wickets", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Fielding cap
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(0.12f)),
                                border = BorderStroke(1.5.dp, TealPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.CatchingPokemon, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(56.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("FIELDING MVP HONOR", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                        Text(fieldingLeader?.playerName ?: "No records", fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        val cts = fieldingLeader?.catches ?: 0
                                        val ro = fieldingLeader?.runOuts ?: 0
                                        val st = fieldingLeader?.stumpings ?: 0
                                        Text("Total Dismissals: ${cts + ro + st} ($cts catches, $ro runouts)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }
                3 -> {
                    // Profile search & QR sharing cards
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        TextField(
                            value = profileSearchQuery,
                            onValueChange = { profileSearchQuery = it },
                            placeholder = { Text("Search player to compile profile QR") },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val selectProfName = remember(profileSearchQuery, playersAll) {
                            playersAll.filter { it.name.contains(profileSearchQuery, ignoreCase = true) }
                        }

                        if (selectedPlayerProfileId == null) {
                            LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                                items(selectProfName) { pl ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedPlayerProfileId = pl.id }
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PlayerAvatar(avatarIndex = pl.profilePhotoUri, size = 28)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(pl.name, fontWeight = FontWeight.Bold)
                                    }
                                    Divider()
                                }
                            }
                        } else {
                            val prof = playersAll.find { it.id == selectedPlayerProfileId }
                            if (prof != null) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.widthIn(max = 340.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        PlayerAvatar(avatarIndex = prof.profilePhotoUri, size = 64)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(prof.name, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        Text("Nickname: ${prof.nickname.ifBlank { "N/A" }}", fontSize = 12.sp, color = TealPrimary, fontWeight = FontWeight.Bold)
                                        Text("Mobile: ${prof.mobileNumber.ifBlank { "N/A" }}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))

                                        Spacer(modifier = Modifier.height(20.dp))

                                        // QR Drawing (SimulatedQRCode)
                                        SimulatedQRCode(modifier = Modifier.size(160.dp), payload = prof.name + prof.id)

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Text(
                                            "Scan using another Tanish Scorer App to Import or Share Profile statistics easily!", 
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f),
                                            textAlign = TextAlign.Center
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        OutlinedButton(onClick = { selectedPlayerProfileId = null }) {
                                            Text("Change Player")
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
