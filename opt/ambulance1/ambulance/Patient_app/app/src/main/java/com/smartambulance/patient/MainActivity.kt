package com.smartambulance.patient

import androidx.compose.ui.graphics.Brush

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.smartambulance.patient.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

val CoralRed = Color(0xFFFF4D6D)
val LightBg = Color(0xFFF8F9FA)
val DarkGrey = Color(0xFF333333)
val ResQGRed = CoralRed

class MainActivity : ComponentActivity() {

    private val linkFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        intent?.data?.toString()?.let { link ->
            linkFlow.tryEmit(link)
        }

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation(this, linkFlow)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent?.data?.toString()?.let { link ->
             linkFlow.tryEmit(link)
        }
    }
    
    @SuppressLint("MissingPermission")
    fun fetchLastLocation(onResult: (Location?) -> Unit) {
        val client = LocationServices.getFusedLocationProviderClient(this)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            client.lastLocation.addOnSuccessListener { loc -> onResult(loc) }
        } else {
            onResult(null)
        }
    }
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val RedPrimary = CoralRed
    val LightGray = LightBg
    
    val colorScheme = lightColorScheme(
        primary = RedPrimary,
        secondary = RedPrimary,
        background = LightGray,
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = DarkGrey
    )
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

object UserSession {
    lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("PatientAppPrefs", Context.MODE_PRIVATE)
    }

    var role: String // "patient" or "driver"
        get() = prefs.getString("role", "") ?: ""
        set(value) = prefs.edit().putString("role", value).apply()

    // Shared
    var phone: String
        get() = prefs.getString("phone", "") ?: ""
        set(value) = prefs.edit().putString("phone", value).apply()

    var email: String
        get() = prefs.getString("email", "") ?: ""
        set(value) = prefs.edit().putString("email", value).apply()

    var isProfileComplete: Boolean
        get() = prefs.getBoolean("isProfileComplete", false)
        set(value) = prefs.edit().putBoolean("isProfileComplete", value).apply()
        
    var username: String
        get() = prefs.getString("username", "") ?: ""
        set(value) = prefs.edit().putString("username", value).apply()

    var language: Int
        get() = prefs.getInt("language", 0) // 0: English, 1: Hindi, 2: Kannada
        set(value) = prefs.edit().putInt("language", value).apply()

    // Driver
    var driverId: Int
        get() = prefs.getInt("driverId", -1)
        set(value) = prefs.edit().putInt("driverId", value).apply()

    val isPatientLoggedIn: Boolean
        get() = role == "patient" && email.isNotBlank() && username.isNotBlank() && phone.isNotBlank() && isProfileComplete

    val isDriverLoggedIn: Boolean
        get() = role == "driver" && driverId != -1

    var serverIp: String
        get() = prefs.getString("serverIp", "web-production-67038.up.railway.app") ?: "web-production-67038.up.railway.app"
        set(value) = prefs.edit().putString("serverIp", value).apply()

    var isAutoPayEnabled: Boolean
        get() = prefs.getBoolean("isAutoPayEnabled", false)
        set(value) = prefs.edit().putBoolean("isAutoPayEnabled", value).apply()

    var isPendingPayment: Boolean
        get() = prefs.getBoolean("isPendingPayment", false)
        set(value) = prefs.edit().putBoolean("isPendingPayment", value).apply()

    var lastFare: String
        get() = prefs.getString("lastFare", "0.0") ?: "0.0"
        set(value) = prefs.edit().putString("lastFare", value).apply()

    var paymentStatus: String // "Pending", "Paid", "AutoPay Processed"
        get() = prefs.getString("paymentStatus", "") ?: ""
        set(value) = prefs.edit().putString("paymentStatus", value).apply()

    var paymentMethod: String // "QR" or "App"
        get() = prefs.getString("paymentMethod", "") ?: ""
        set(value) = prefs.edit().putString("paymentMethod", value).apply()

    var rideCompletionTime: Long
        get() = prefs.getLong("rideCompletionTime", 0L)
        set(value) = prefs.edit().putLong("rideCompletionTime", value).apply()

    var rideExpiryTime: Long
        get() = prefs.getLong("rideExpiryTime", 0L)
        set(value) = prefs.edit().putLong("rideExpiryTime", value).apply()
}

fun t(en: String, hi: String, kn: String): String {
    return when(UserSession.language) {
        1 -> hi
        2 -> kn
        else -> en
    }
}

fun checkAutoPay() {
    val now = System.currentTimeMillis()
    // 3 days = 3 * 24 * 60 * 60 * 1000 ms
    if (UserSession.paymentStatus == "Pending" && UserSession.rideExpiryTime > 0 && now > UserSession.rideExpiryTime) {
        UserSession.paymentStatus = "AutoPay Processed"
        UserSession.isPendingPayment = false
        Log.d("AutoPay", "Simulated AutoPay Processed for ride completed at ${UserSession.rideCompletionTime}")
    }
}



@Composable
fun AppNavigation(activity: MainActivity, linkFlow: SharedFlow<String>? = null) {
    UserSession.init(activity)
    RetrofitClient.CURRENT_IP = UserSession.serverIp
    val authViewModel: AuthViewModel = viewModel()
    val authStatus = authViewModel.authStatus
    
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
        checkAutoPay()
        linkFlow?.collect { link ->
            if (FirebaseAuth.getInstance().isSignInWithEmailLink(link)) {
                authViewModel.verifyEmailLink(link)
            }
        }
    }

    LaunchedEffect(authStatus) {
        when (authStatus) {
            is AuthStatus.Authenticated -> {
                if (authStatus.role == "patient") {
                    if (authStatus.isProfileComplete) navController.navigate("home") { popUpTo(0) }
                    else navController.navigate("register/${Uri.encode(UserSession.email)}") { popUpTo(0) }
                } else {
                    authViewModel.signOut()
                }
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController, authViewModel) }
        composable("role_selection") { RoleSelectionScreen(navController) }
        composable("language") { LanguageScreen(navController) }
        composable("email") { EmailScreen(navController, authViewModel) }
        composable("otp/{email}") { backStackEntry ->
            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: "")
            OtpScreen(navController, email, authViewModel)
        }
        composable("register/{email}") { backStackEntry ->
            val email = Uri.decode(backStackEntry.arguments?.getString("email") ?: "")
            RegisterScreen(navController, email)
        }
        composable("login") { LoginScreen(navController) }
        composable("home") { HomeScreen(navController, activity) }
        composable("status/{emergencyId}") { backStackEntry ->
            val emergencyId = backStackEntry.arguments?.getString("emergencyId") ?: ""
            LiveStatusScreen(navController, activity, emergencyId)
        }
    }
}

@Composable
fun SplashScreen(navController: NavController, viewModel: AuthViewModel) {
    var showDialog by remember { mutableStateOf(false) }
    var tempIp by remember { mutableStateOf(UserSession.serverIp) }
    val authStatus = viewModel.authStatus

    LaunchedEffect(authStatus, showDialog) {
        if (authStatus is AuthStatus.Authenticated) return@LaunchedEffect
        delay(2000)
        if (!showDialog) {
            UserSession.role = "patient"
            navController.navigate("language") { popUpTo("splash") { inclusive = true } }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(CoralRed),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ResQG", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, fontFamily = FontFamily.Serif)
            Text("Patient App", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = Color.White)
        }

        Button(
            onClick = { showDialog = true },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
        ) {
            Text("⚙ Server IP")
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Set Backend IP") },
            text = {
                OutlinedTextField(
                    value = tempIp,
                    onValueChange = { tempIp = it },
                    label = { Text("IP Address") }
                )
            },
            confirmButton = {
                Button(onClick = {
                    UserSession.serverIp = tempIp
                    RetrofitClient.CURRENT_IP = tempIp
                    showDialog = false
                    navController.navigate("language") { popUpTo("splash") { inclusive = true } }
                }) { Text("Save") }
            }
        )
    }
}

class WebAppInterface(private val mContext: Context) {
    @JavascriptInterface
    fun showToast(toast: String) {
        Toast.makeText(mContext, toast, Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun RoleSelectionScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("I am a...", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        
        Button(
            onClick = { 
                UserSession.role = "patient"
                navController.navigate("language") 
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("PATIENT", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = { 
                UserSession.role = "driver"
                navController.navigate("driver_login") 
            },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("AMBULANCE DRIVER", color = CoralRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================
// PATIENT FLOW
// ==========================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageScreen(navController: NavController) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFFFFFFFF), Color(0xFFF0F2F5))
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Choose Language", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
            Spacer(modifier = Modifier.height(40.dp))
            
            listOf(
                Triple(0, "English", Color(0xFF5B3EB6)),
                Triple(1, "Hindi (हिंदी)", Color(0xFFE65100)),
                Triple(2, "Kannada (ಕನ್ನಡ)", Color(0xFFC62828))
            ).forEach { (id, label, color) ->
                Card(
                    onClick = { 
                        UserSession.language = id
                        navController.navigate("email") 
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).height(70.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = color),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = label, 
                            color = Color.White, 
                            fontSize = 22.sp, 
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            TextButton(onClick = { navController.navigate("login") }) {
                Text(
                    "Already an existing user? Login here", 
                    color = Color.Gray,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun EmailScreen(navController: NavController, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val status = viewModel.authStatus

    LaunchedEffect(status) {
        if (status is AuthStatus.CodeSent) {
            navController.navigate("otp/${Uri.encode(email.trim().lowercase())}")
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Email", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = CoralRed, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { input ->
                        email = input.trim()
                        error = false
                    },
                    label = { Text("Email Address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                    isError = error,
                    enabled = status !is AuthStatus.Loading
                )

                if (error) {
                    Text("Please enter a valid email address.", color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }
                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        val cleanEmail = email.trim().lowercase()
                        if (android.util.Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
                            UserSession.email = cleanEmail
                            UserSession.role = "patient"
                            viewModel.sendEmailOtp(cleanEmail)
                        } else {
                            error = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                    enabled = status !is AuthStatus.Loading && email.isNotBlank()
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("SEND MAGIC LINK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }

                // DEMO LOGIN BUTTON FOR PRESENTATION
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = {
                        UserSession.email = "demo@resqgo.com"
                        UserSession.username = "Guest User"
                        UserSession.phone = "9999999999"
                        UserSession.role = "patient"
                        UserSession.isProfileComplete = true
                        navController.navigate("home") { popUpTo(0) }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CoralRed),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("SKIP FOR DEMO (GUEST)", color = CoralRed, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun OtpScreen(navController: NavController, email: String, viewModel: AuthViewModel) {
    val status = viewModel.authStatus
    val timeLeft = viewModel.timeLeft

    LaunchedEffect(status) {
        if (status is AuthStatus.Authenticated) {
            navController.navigate("register/${Uri.encode(email)}") {
                popUpTo("email") { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Check Your Email", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = CoralRed)
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(email, color = Color.Gray, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text("We've sent a magic link to your email. Click it to securely sign in.", textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))

                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (status is AuthStatus.Loading) {
                    CircularProgressIndicator(color = CoralRed, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (timeLeft > 0) {
                    Text("Resend Magic Link in $timeLeft s", color = Color.Gray)
                } else {
                    TextButton(
                        onClick = { viewModel.sendEmailOtp(email) },
                        enabled = status !is AuthStatus.Loading
                    ) {
                        Text("Resend Magic Link", color = CoralRed, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController, email: String) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(t("ResQGo", "ResQGo", "ResQGo"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(t("Username", "उपयोगकर्ता नाम", "ಬಳಕೆದಾರರ ಹೆಸರು")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(t("Password", "पासवर्ड", "ಪಾಸ್ವರ್ಡ್")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(t("Phone Number", "फ़ोन नंबर", "ದೂರವಾಣಿ ಸಂಖ್ಯೆ")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                
                var autoPayChecked by remember { mutableStateOf(true) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                    Checkbox(checked = autoPayChecked, onCheckedChange = { autoPayChecked = it })
                    Text(t("Enable UPI AutoPay (Recommended)", "यूपीआई ऑटोपे सक्षम करें", "ಯುಪಿಐ ಆಟೋಪೇ ಸಕ್ರಿಯಗೊಳಿಸಿ"), fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(24.dp))
                val scope = rememberCoroutineScope()
                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank() && phone.isNotBlank()) {
                            UserSession.email = email
                            UserSession.phone = phone
                            UserSession.username = username
                            UserSession.isProfileComplete = true
                            UserSession.isAutoPayEnabled = autoPayChecked

                            if (autoPayChecked) {
                                scope.launch {
                                    try {
                                        RetrofitClient.instance.registerMandate(MandatePayload(phone))
                                    } catch (e: Exception) { Log.e("AutoPay", "Registration failed", e) }
                                }
                            }

                            navController.navigate("home") { popUpTo("register") { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text(t("SAVE & CONTINUE", "सहेजें और आगे बढ़ें", "ಉಳಿಸಿ ಮತ್ತು ಮುಂದುವರಿಯಿರಿ"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LoginScreen(navController: NavController) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ResQGo", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                var phone by remember { mutableStateOf("") }
                
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(t("Password", "पासवर्ड", "ಪಾಸ್ವರ್ಡ್")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text(t("Phone Number", "फ़ोन नंबर", "ದೂರವಾಣಿ ಸಂಖ್ಯೆ")) }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank() && phone.isNotBlank()) {
                            UserSession.email = email
                            UserSession.username = email
                            UserSession.phone = phone
                            UserSession.isProfileComplete = true
                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CoralRed)
                ) {
                    Text(t("SECURE LOGIN", "सुरक्षित लॉगिन", "ಸುರಕ್ಷಿತ ಲಾಗಿನ್"), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun HomeScreen(navController: NavController, activity: MainActivity) {
    var showDialog by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        activity.fetchLastLocation { location ->
            if (location != null) {
                lat = location.latitude
                lon = location.longitude
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "ResQGo/1.0 (Android; Student Project; contact@resqgo.app)"
                    addJavascriptInterface(WebAppInterface(activity), "Android")
                    
                    setPadding(0,0,0,0)
                    
                    val mapHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <link rel="stylesheet" href="https://unpkg.com/leaflet-routing-machine@3.2.12/dist/leaflet-routing-machine.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <script src="https://unpkg.com/leaflet-routing-machine@3.2.12/dist/leaflet-routing-machine.js"></script>
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                body, html, #map { height: 100vh; width: 100vw; overflow: hidden; background: #e8eaf6; }
                                .leaflet-routing-container { display: none !important; }
                                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%,-50%);
                                    font-family: sans-serif; font-size: 16px; color: #555; z-index: 1000;
                                    background: rgba(255,255,255,0.9); padding: 16px 24px; border-radius: 10px; }
                                .status-pill { position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
                                    background: rgba(40,40,60,0.85); color: white; padding: 8px 20px;
                                    border-radius: 20px; font-family: sans-serif; font-size: 13px;
                                    z-index: 500; white-space: nowrap; }
                            </style>
                        </head>
                        <body>
                            <div id="loading">Loading Map...</div>
                            <div id="map"></div>
                            <div class="status-pill" id="status-pill">Searching for driver...</div>
                            <script>
                                var map = null, ambulanceMarker = null, patientMarker = null, hospitalMarker = null;
                                var routingControl = null, lastState = '', animFrame = null;
                                var lastAmbLat = 0, lastAmbLon = 0;

                                function animateMarker(marker, toLat, toLon) {
                                    if (!marker) return;
                                    var from = marker.getLatLng();
                                    var steps = 40, step = 0;
                                    if (animFrame) cancelAnimationFrame(animFrame);
                                    function tick() {
                                        step++;
                                        var t = step / steps;
                                        marker.setLatLng([from.lat + (toLat - from.lat) * t, from.lng + (toLon - from.lng) * t]);
                                        if (step < steps) animFrame = requestAnimationFrame(tick);
                                    }
                                    tick();
                                }

                                function mkIcon(url, sz) {
                                    return L.icon({ iconUrl: url, iconSize: [sz, sz], iconAnchor: [sz/2, sz/2], popupAnchor: [0, -sz/2] });
                                }

                                function updateRoute(fLat, fLon, tLat, tLon) {
                                    if (routingControl) { map.removeControl(routingControl); routingControl = null; }
                                    if (!fLat || !tLat || fLat === 0 || tLat === 0) return;
                                    routingControl = L.Routing.control({
                                        waypoints: [L.latLng(fLat, fLon), L.latLng(tLat, tLon)],
                                        routeWhileDragging: false, show: false, addWaypoints: false, draggableWaypoints: false,
                                        lineOptions: { styles: [{ color: '#E53935', opacity: 0.85, weight: 5 }] },
                                        createMarker: function() { return null; },
                                        router: L.Routing.osrmv1({ serviceUrl: 'https://router.project-osrm.org/route/v1', profile: 'driving' })
                                    }).addTo(map);
                                }

                                function fitMarkers(markers) {
                                    var pts = markers.filter(function(m){ return m && map.hasLayer(m); }).map(function(m){ return m.getLatLng(); });
                                    if (pts.length >= 2) map.fitBounds(L.latLngBounds(pts).pad(0.3), { animate: true });
                                    else if (pts.length === 1) map.panTo(pts[0], { animate: true });
                                }

                                function updateMap(ambLat, ambLon, patLat, patLon, hospLat, hospLon, state) {
                                    try {
                                        if (typeof L === 'undefined') { setTimeout(function(){ updateMap(ambLat,ambLon,patLat,patLon,hospLat,hospLon,state); }, 400); return; }
                                        document.getElementById('loading').style.display = 'none';
                                        var cLat = (patLat && patLat !== 0) ? patLat : 13.0266;
                                        var cLon = (patLon && patLon !== 0) ? patLon : 77.5714;

                                        if (!map) {
                                            map = L.map('map', { zoomControl: true, attributionControl: false }).setView([cLat, cLon], 14);
                                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', { maxZoom: 19 }).addTo(map);
                                        }

                                        // Patient marker - fixed at their SOS location
                                        if (patLat && patLat !== 0 && !patientMarker) {
                                            patientMarker = L.marker([patLat, patLon], {
                                                icon: mkIcon('https://cdn-icons-png.flaticon.com/512/2922/2922510.png', 42),
                                                zIndexOffset: 800
                                            }).addTo(map).bindTooltip('You', { permanent: true, direction: 'top', offset: [0,-24] });
                                        }

                                        // Ambulance marker - moves smoothly
                                        if (ambLat && ambLat !== 0) {
                                            if (!ambulanceMarker) {
                                                // Start ambulance offset so it doesn't overlap patient
                                                var initLat = (patLat && patLat !== 0) ? patLat + 0.005 : ambLat;
                                                var initLon = (patLon && patLon !== 0) ? patLon + 0.005 : ambLon;
                                                ambulanceMarker = L.marker([initLat, initLon], {
                                                    icon: mkIcon('https://cdn-icons-png.flaticon.com/512/2967/2967350.png', 46),
                                                    zIndexOffset: 1000
                                                }).addTo(map).bindTooltip('Ambulance', { permanent: false, direction: 'top', offset: [0,-30] });
                                            } else if (Math.abs(ambLat - lastAmbLat) > 0.00002 || Math.abs(ambLon - lastAmbLon) > 0.00002) {
                                                animateMarker(ambulanceMarker, ambLat, ambLon);
                                            }
                                            lastAmbLat = ambLat; lastAmbLon = ambLon;
                                        }

                                        // Hospital marker
                                        if (hospLat && hospLat !== 0 && !hospitalMarker) {
                                            hospitalMarker = L.marker([hospLat, hospLon], {
                                                icon: mkIcon('https://cdn-icons-png.flaticon.com/512/1032/1032989.png', 40),
                                                zIndexOffset: 900
                                            }).bindTooltip('Hospital', { permanent: true, direction: 'top', offset: [0,-26] });
                                        }

                                        // State-based logic
                                        var stateChanged = state !== lastState;
                                        if (state === 'pending') {
                                            document.getElementById('status-pill').textContent = 'Searching for ambulance...';
                                            if (routingControl) { map.removeControl(routingControl); routingControl = null; }
                                            if (hospitalMarker && map.hasLayer(hospitalMarker)) map.removeLayer(hospitalMarker);
                                            if (patientMarker && !map.hasLayer(patientMarker)) patientMarker.addTo(map);
                                        } else if (state === 'accepted') {
                                            document.getElementById('status-pill').textContent = 'Driver on the way to you!';
                                            if (hospitalMarker && map.hasLayer(hospitalMarker)) map.removeLayer(hospitalMarker);
                                            if (patientMarker && !map.hasLayer(patientMarker)) patientMarker.addTo(map);
                                            if (stateChanged && ambLat !== 0 && patLat !== 0) updateRoute(ambLat, ambLon, patLat, patLon);
                                            fitMarkers([ambulanceMarker, patientMarker]);
                                        } else if (state === 'active') {
                                            document.getElementById('status-pill').textContent = 'Heading to hospital!';
                                            if (patientMarker && map.hasLayer(patientMarker)) map.removeLayer(patientMarker);
                                            if (hospitalMarker && !map.hasLayer(hospitalMarker)) hospitalMarker.addTo(map);
                                            if (stateChanged && ambLat !== 0 && hospLat !== 0) updateRoute(ambLat, ambLon, hospLat, hospLon);
                                            fitMarkers([ambulanceMarker, hospitalMarker]);
                                        } else if (state === 'completed') {
                                            document.getElementById('status-pill').textContent = 'Ride Completed!';
                                            if (routingControl) { map.removeControl(routingControl); routingControl = null; }
                                            if (hospitalMarker && hospLat !== 0) {
                                                if (!map.hasLayer(hospitalMarker)) hospitalMarker.addTo(map);
                                                map.panTo([hospLat, hospLon]);
                                            }
                                        }
                                        lastState = state;
                                    } catch(e) { console.error('Map error: ' + e); }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://carto.com", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon);}else{window.pendingLat=$lat; window.pendingLon=$lon;}", null)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Emergency Call Button
        Card(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            IconButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:108")) // Default Emergency Number
                    activity.startActivity(intent)
                },
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Call Help", tint = ResQGRed, modifier = Modifier.size(32.dp))
            }
        }

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                val displayName = UserSession.username.let { 
                    if (it.contains("@")) it.split("@").first() 
                    else it.split(" ").firstOrNull() ?: it
                }
                Text(t("Welcome, $displayName", "स्वागत है, $displayName", "ಸ್ವಾಗತ, $displayName"), modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), fontWeight = FontWeight.ExtraBold, color = CoralRed)
            }
            Button(
                onClick = {
                    UserSession.phone = ""; UserSession.email = ""; UserSession.username = ""; UserSession.isProfileComplete = false
                    navController.navigate("splash") { popUpTo("home") { inclusive = true } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                modifier = Modifier.size(height = 45.dp, width = 85.dp),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(t("Logout", "लॉग आउट", "ಲಾಗ್ ಔಟ್"), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // AutoPay Status Badge
        if (UserSession.isAutoPayEnabled) {
            Card(
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 80.dp, end = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    Text("AutoPay Active", color = Color(0xFF2E7D32), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // SOS Button
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Persistent Pay Now if pending
                if (UserSession.isPendingPayment || UserSession.paymentStatus == "AutoPay Processed") {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (UserSession.paymentStatus == "AutoPay Processed") Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        elevation = CardDefaults.cardElevation(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (UserSession.paymentStatus == "AutoPay Processed") {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("✅", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("AutoPay Completed", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                }
                                Text("Payment for your last ride (₹${UserSession.lastFare}) was automatically processed.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                TextButton(onClick = { UserSession.paymentStatus = ""; UserSession.rideCompletionTime = 0L; UserSession.rideExpiryTime = 0L }) {
                                    Text("Dismiss", color = Color(0xFF2E7D32))
                                }
                            } else {
                                var remainingHomeText by remember { mutableStateOf("") }
                                LaunchedEffect(UserSession.paymentStatus, UserSession.rideExpiryTime) {
                                    while (UserSession.paymentStatus == "Pending") {
                                        val now = System.currentTimeMillis()
                                        val diff = UserSession.rideExpiryTime - now
                                        if (diff > 0) {
                                            val days = diff / (24 * 60 * 60 * 1000)
                                            val hours = (diff / (60 * 60 * 1000)) % 24
                                            val minutes = (diff / (60 * 1000)) % 60
                                            val seconds = (diff / 1000) % 60
                                            remainingHomeText = "${days}d ${hours}h ${minutes}m ${seconds}s"
                                        } else {
                                            checkAutoPay()
                                            remainingHomeText = "Processing..."
                                        }
                                        delay(1000)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("💳", fontSize = 20.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Pending Payment Found", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                }
                                Text("Amount: ₹${UserSession.lastFare}", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                                Text("AutoPay in: $remainingHomeText", fontSize = 13.sp, color = Color.Gray)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        val upiUri = "upi://pay?pa=resqgo@upi&pn=ResQGo&am=${UserSession.lastFare}&cu=INR&tn=PendingRide&tr=TXID${System.currentTimeMillis()}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                        try {
                                            activity.startActivity(Intent.createChooser(intent, "Pay Now"))
                                            UserSession.paymentStatus = "Paid"
                                            UserSession.isPendingPayment = false
                                        } catch (e: Exception) {}
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("PAY NOW", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = { showDialog = true },
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 15.dp, pressedElevation = 5.dp)
                ) {
                    Text(if (loading) t("...", "...", "...") else t("SOS", "SOS", "SOS"), fontSize = 32.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, color = Color.White)
                }
            }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(t("Confirm Emergency", "आपातकाल की पुष्टि करें", "ತುರ್ತು ಪರಿಸ್ಥಿತಿಯನ್ನು ದೃಢೀಕರಿಸಿ"), fontWeight = FontWeight.Bold, color = CoralRed) },
                text = { Text(t("Are you sure you want an ambulance here immediately?", "क्या आप निश्चित रूप से तुरंत एम्बुलेंस बुलाना चाहते हैं?", "ನಿಮ್ಮ ಪ್ರಸ್ತುತ ಸ್ಥಳಕ್ಕೆ ತಕ್ಷಣವೇ ಆಂಬ್ಯುಲೆನ್ಸ್ ವಿನಂತಿಸಲು ನೀವು ಖಚಿತವಾಗಿ ಬಯಸುವಿರಾ?")) },
                containerColor = Color.White,
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                        onClick = {
                            showDialog = false
                            loading = true
                            activity.fetchLastLocation { location ->
                                scope.launch {
                                    try {
                                        val fullName = UserSession.username
                                        val phone = UserSession.phone
                                        if (phone.isBlank()) {
                                            Toast.makeText(activity, "Error: Phone number missing. Please register again.", Toast.LENGTH_LONG).show()
                                            navController.navigate("register/${Uri.encode(UserSession.email)}")
                                            loading = false
                                            return@launch
                                        }
                                        val res = RetrofitClient.instance.requestAmbulance(
                                            RequestAmbulancePayload(fullName, "N/A", phone, location?.latitude ?: 0.0, location?.longitude ?: 0.0)
                                        )
                                        loading = false
                                        navController.navigate("status/${res.emergency_id}")
                                    } catch (e: Exception) { 
                                        loading = false
                                        Toast.makeText(activity, "SOS Failed: ${e.message}", Toast.LENGTH_LONG).show()
                                        Log.e("SOS", "Error", e)
                                    }
                                }
                            }
                        }) {
                        Text(t("YES, SEND HELP", "हां, मदद भेजें", "ಹೌದು, ಸಹಾಯ ಕಳುಹಿಸಿ"), color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text(t("CANCEL", "रद्द करें", "ರದ್ದುಮಾಡು"), color = Color.Gray) }
                }
            )
        }
    }
}

@Composable
fun LiveStatusScreen(navController: NavController, activity: MainActivity, emergencyId: String) {
    val scope = rememberCoroutineScope()
    var emergencyState by remember { mutableStateOf("pending") }
    var driverName by remember { mutableStateOf("-") }
    var ambulanceNo by remember { mutableStateOf("-") }
    var driverPhone by remember { mutableStateOf<String?>(null) }
    var driverUpi by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    var distance by remember { mutableStateOf("-") }
    var eta by remember { mutableStateOf(t("Wait...", "प्रतीक्षा करें...", "ಕಾಯಿರಿ...")) }
    var destName by remember { mutableStateOf<String?>(null) }
    var hospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }
    var fare by remember { mutableStateOf(0.0) }

    var patientLat by remember { mutableStateOf(0.0) }
    var patientLon by remember { mutableStateOf(0.0) }
    var hospLat by remember { mutableStateOf(0.0) }
    var hospLon by remember { mutableStateOf(0.0) }
    
    var lastStatus by remember { mutableStateOf("") }
    var isAutoPayPending by remember { mutableStateOf(false) }
    var payTimer by remember { mutableStateOf(30) }
    
    // Payment Pending Timer Logic
    LaunchedEffect(emergencyState, UserSession.paymentStatus) {
        if (emergencyState == "completed" && UserSession.paymentStatus == "Pending") {
            while (payTimer > 0 && UserSession.paymentStatus == "Pending") {
                kotlinx.coroutines.delay(1000)
                payTimer--
            }
        }
    }
    
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val status = RetrofitClient.instance.getEmergencyStatus(emergencyId)
                emergencyState = status.emergency_state
                driverName = status.driver_name ?: "-"
                ambulanceNo = status.ambulance_no ?: "-"
                destName = status.dest_name
                driverPhone = status.driver_phone
                driverUpi = status.driver_upi
                fare = status.fare ?: 0.0
                patientLat = status.lat ?: 0.0
                patientLon = status.lon ?: 0.0
                hospLat = status.dest_lat ?: 0.0
                hospLon = status.dest_lon ?: 0.0

                // REAL-TIME PAYMENT SYNC
                if (status.payment_status == "Paid") {
                    UserSession.paymentStatus = "Paid"
                    UserSession.paymentMethod = status.payment_method ?: "QR"
                    UserSession.isPendingPayment = false
                    payTimer = 0 // Stop the timer
                }

                if (emergencyState == "declined") {
                    navController.popBackStack()
                    return@LaunchedEffect
                }

                if (emergencyState == "active" || emergencyState == "accepted") {
                    val loc = RetrofitClient.instance.getAmbulanceLocation(emergencyId)
                    lat = loc.lat; lon = loc.lon; distance = loc.distance ?: "-"; eta = loc.eta ?: t("Wait...", "प्रतीक्षा करें...", "ಕಾಯಿರಿ...")
                    if (emergencyState == "active" && hospitals.isEmpty()) {
                        hospitals = RetrofitClient.instance.getNearbyHospitals(lat, lon).hospitals
                    }
                }
            } catch (e: Exception) {}
            if (emergencyState == "completed") {
                if (UserSession.paymentStatus != "Paid" && UserSession.paymentStatus != "AutoPay Processed") {
                    if (UserSession.rideCompletionTime == 0L) {
                        UserSession.rideCompletionTime = System.currentTimeMillis()
                        // FOR DEMO: If you want to see it happen faster, you can reduce this time.
                        // Here we use 3 days as requested.
                        UserSession.rideExpiryTime = System.currentTimeMillis() + (3 * 24 * 60 * 60 * 1000L) 
                        UserSession.paymentStatus = "Pending"
                        UserSession.isPendingPayment = true
                    }
                }
                UserSession.lastFare = String.format("%.2f", fare)
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    // Simulated AutoPay Countdown
    var remainingTimeText by remember { mutableStateOf("") }
    LaunchedEffect(UserSession.paymentStatus, UserSession.rideExpiryTime) {
        while (UserSession.paymentStatus == "Pending") {
            val now = System.currentTimeMillis()
            val diff = UserSession.rideExpiryTime - now
            if (diff > 0) {
                val days = diff / (24 * 60 * 60 * 1000)
                val hours = (diff / (60 * 60 * 1000)) % 24
                val minutes = (diff / (60 * 1000)) % 60
                val seconds = (diff / 1000) % 60
                remainingTimeText = "${days}d ${hours}h ${minutes}m ${seconds}s"
            } else {
                checkAutoPay()
                remainingTimeText = "Processing..."
            }
            delay(1000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE3F2FD))) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    webViewClient = WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient() // Added for better rendering
                    
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "ResQGo/1.0 (Android; Student Project; contact@resqgo.app)"
                    addJavascriptInterface(WebAppInterface(activity), "Android")
                    
                    // Force the map to fill the space
                    setPadding(0,0,0,0)
                    
                    val mapHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="utf-8" />
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <link rel="stylesheet" href="https://unpkg.com/leaflet-routing-machine/dist/leaflet-routing-machine.css" />
                            <script src="https://unpkg.com/leaflet-routing-machine/dist/leaflet-routing-machine.js"></script>
                            <style>
                                body, html, #map { height: 100vh; width: 100vw; margin: 0; padding: 0; overflow: hidden; background: #e0e0e0; }
                                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #666; z-index: 1000; }
                                .corridor-banner {
                                    position: absolute; top: 80px; left: 50%; transform: translateX(-50%);
                                    background: rgba(211, 47, 47, 0.9); color: white; padding: 10px 20px;
                                    border-radius: 30px; font-family: sans-serif; font-weight: bold;
                                    z-index: 2000; display: none; box-shadow: 0 4px 15px rgba(0,0,0,0.3);
                                    animation: slideDown 0.5s ease-out;
                                }
                                @keyframes slideDown { from { top: -50px; } to { top: 80px; } }
                                .signal-label { font-size: 10px; font-weight: bold; background: white; padding: 2px; border: 1px solid #ccc; white-space: nowrap; }
                            </style>
                        </head>
                        <body>
                            <div id="loading">Initializing Map...</div>
                            <div id="corridor-banner" class="corridor-banner">🚑 Emergency Corridor Activated</div>
                            <div id="map"></div>
                            <script>
                                var map = null;
                                var ambulanceMarker = null;
                                var patientMarker = null;
                                var hospitalMarker = null;
                                var routingControl = null;
                                
                                var signal1 = null;
                                var signal2 = null;
                                var signal2State = 'RED'; // RED, YELLOW, GREEN, NORMAL
                                var bannerShown = false;

                                function haversine(lat1, lon1, lat2, lon2) {
                                    var R = 6371e3;
                                    var phi1 = lat1 * Math.PI/180;
                                    var phi2 = lat2 * Math.PI/180;
                                    var dPhi = (lat2-lat1) * Math.PI/180;
                                    var dLambda = (lon2-lon1) * Math.PI/180;
                                    var a = Math.sin(dPhi/2) * Math.sin(dPhi/2) + Math.cos(phi1) * Math.cos(phi2) * Math.sin(dLambda/2) * Math.sin(dLambda/2);
                                    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
                                }

                                function updateMap(ambLat, ambLon, patLat, patLon, hospLat, hospLon, state) {
                                    try {
                                        if (typeof L === 'undefined' || typeof L.Routing === 'undefined') { 
                                            setTimeout(function(){ updateMap(ambLat, ambLon, patLat, patLon, hospLat, hospLon, state); }, 200); 
                                            return; 
                                        }
                                        document.getElementById('loading').style.display = 'none';
                                        
                                        var centerLat = (ambLat && ambLat !== 0) ? ambLat : ((patLat && patLat !== 0) ? patLat : 13.0266);
                                        var centerLon = (ambLon && ambLon !== 0) ? ambLon : ((patLon && patLon !== 0) ? patLon : 77.5714);

                                        if (!map) {
                                            map = L.map('map', {zoomControl: false, attributionControl: false}).setView([centerLat, centerLon], 15);
                                            L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', { maxZoom: 19 }).addTo(map);
                                            
                                            ambulanceMarker = L.marker([centerLat, centerLon], {
                                                icon: L.icon({
                                                    iconUrl: 'https://cdn-icons-png.flaticon.com/512/2967/2967350.png', 
                                                    iconSize:[40,40], iconAnchor: [20, 20]
                                                }),
                                                zIndexOffset: 1000
                                            }).addTo(map);
                                            
                                            patientMarker = L.marker([0, 0], {
                                                icon: L.icon({
                                                    iconUrl: 'https://cdn-icons-png.flaticon.com/512/2864/2864403.png',
                                                    iconSize:[45,45], iconAnchor: [22, 22]
                                                })
                                            });

                                            hospitalMarker = L.marker([0, 0], {
                                                icon: L.icon({
                                                    iconUrl: 'https://cdn-icons-png.flaticon.com/512/1032/1032989.png',
                                                    iconSize:[40,40], iconAnchor: [20, 20]
                                                })
                                            });
                                        } 

                                        if (ambLat && ambLat !== 0) {
                                            ambulanceMarker.setLatLng([ambLat, ambLon]);
                                        }

                                        // Update Signals Simulation
                                        if (state === 'active' && routeCoordinates.length > 0) {
                                            if (!signal1 || !signal2) {
                                                console.log("🚦 Placing Signals on Route Path (Patient side)");
                                                var idx1 = Math.floor(routeCoordinates.length * 0.3);
                                                var idx2 = Math.floor(routeCoordinates.length * 0.7);
                                                var pos1 = routeCoordinates[idx1];
                                                var pos2 = routeCoordinates[idx2];

                                                signal1 = L.marker([pos1.lat, pos1.lng], {
                                                    icon: L.divIcon({
                                                        html: `<div style="background:#2ecc71; width:22px; height:22px; border-radius:50%; border:3px solid #333; box-shadow:0 0 8px rgba(46,204,113,0.8); margin:auto;"></div><div class="signal-label" style="border-color:#2ecc71;">Signal 1: Priority Active</div>`,
                                                        className: '', iconSize: [110, 50], iconAnchor: [55, 25]
                                                    }), zIndexOffset: 2000
                                                }).addTo(map);

                                                signal2 = L.marker([pos2.lat, pos2.lng], {
                                                    icon: L.divIcon({
                                                        html: `<div id="s2-light" style="background:#e74c3c; width:22px; height:22px; border-radius:50%; border:3px solid #333; box-shadow:0 0 8px rgba(231,76,60,0.8); margin:auto;"></div><div id="s2-status" class="signal-label" style="border-color:#e74c3c;">Signal 2: RED</div>`,
                                                        className: '', iconSize: [110, 50], iconAnchor: [55, 25]
                                                    }), zIndexOffset: 2000
                                                }).addTo(map);
                                            }

                                            var banner = document.getElementById('corridor-banner');
                                            
                                            // Signal 1 Detection
                                            var d1 = haversine(ambLat, ambLon, signal1.getLatLng().lat, signal1.getLatLng().lng);
                                            if (d1 < 300 && d1 > 30) {
                                                if(banner) {
                                                    banner.style.display = 'block';
                                                    banner.innerText = "🚑 Signal 1: Priority Active";
                                                }
                                            } else if (d1 <= 30) {
                                                if(banner && banner.innerText.includes("Signal 1")) banner.style.display = 'none';
                                            }

                                            // Signal 2 Detection
                                            var d2 = haversine(ambLat, ambLon, signal2.getLatLng().lat, signal2.getLatLng().lng);
                                            var s2Light = document.getElementById('s2-light');
                                            var s2Status = document.getElementById('s2-status');

                                            if (d2 < 500 && signal2State === 'RED') {
                                                signal2State = 'YELLOW';
                                                if(banner) {
                                                    banner.style.display = 'block';
                                                    banner.innerText = "🚑 Signal 2: Approaching Zone";
                                                }
                                                if(s2Light) s2Light.style.background = '#f1c40f';
                                                if(s2Status) s2Status.innerText = 'Signal 2: Transitioning...';
                                                
                                                setTimeout(function() {
                                                    signal2State = 'GREEN';
                                                    if(s2Light) {
                                                        s2Light.style.background = '#2ecc71';
                                                        s2Light.style.boxShadow = '0 0 12px rgba(46,204,113,0.9)';
                                                    }
                                                    if(s2Status) {
                                                        s2Status.innerText = 'Signal 2: CLEARED';
                                                        s2Status.style.borderColor = '#2ecc71';
                                                    }
                                                    if(banner) banner.innerText = "🚑 Signal 2: Priority Active";
                                                }, 2500);
                                            }
                                            
                                            if (d2 < 40 && signal2State === 'GREEN') {
                                                if(banner && banner.innerText.includes("Signal 2")) banner.style.display = 'none';
                                            }
                                        } else {
                                            if (signal1) { map.removeLayer(signal1); signal1 = null; }
                                            if (signal2) { map.removeLayer(signal2); signal2 = null; }
                                            var banner = document.getElementById('corridor-banner');
                                            if(banner) banner.style.display = 'none';
                                            signal2State = 'RED';
                                        }

                                        // Update Patient
                                        if (patLat && patLat !== 0 && state !== 'active' && state !== 'completed') {
                                            patientMarker.setLatLng([patLat, patLon]);
                                            if (!map.hasLayer(patientMarker)) patientMarker.addTo(map);
                                        } else {
                                            if (map.hasLayer(patientMarker)) map.removeLayer(patientMarker);
                                        }

                                        // Update Hospital
                                        if (hospLat && hospLat !== 0) {
                                            hospitalMarker.setLatLng([hospLat, hospLon]);
                                            if (!map.hasLayer(hospitalMarker)) hospitalMarker.addTo(map);
                                        }

                                        // Update Routing Control
                                        var waypoints = [];
                                        if (state === 'accepted') {
                                            if (ambLat !== 0 && patLat !== 0) {
                                                waypoints = [L.latLng(ambLat, ambLon), L.latLng(patLat, patLon)];
                                            }
                                        } else if (state === 'active') {
                                            if (ambLat !== 0 && hospLat !== 0) {
                                                waypoints = [L.latLng(ambLat, ambLon), L.latLng(hospLat, hospLon)];
                                            }
                                        }

                                        if (waypoints.length >= 2) {
                                            if (!routingControl) {
                                                routingControl = L.Routing.control({
                                                    waypoints: waypoints,
                                                    router: L.Routing.osrmv1({ serviceUrl: 'https://router.project-osrm.org/route/v1', profile: 'driving' }),
                                                    routeWhileDragging: false,
                                                    show: false,
                                                    addWaypoints: false,
                                                    draggableWaypoints: false,
                                                    fitSelectedRoutes: true,
                                                    lineOptions: { styles: [{ color: '#FF4D6D', opacity: 0.8, weight: 6 }] }
                                                }).addTo(map);
                                                
                                                routingControl.on('routesfound', function(e) {
                                                    routeCoordinates = e.routes[0].coordinates;
                                                });
                                            } else {
                                                routingControl.setWaypoints(waypoints);
                                            }
                                        } else {
                                            if (routingControl) {
                                                map.removeControl(routingControl);
                                                routingControl = null;
                                                routeCoordinates = [];
                                            }
                                            if (ambLat !== 0) map.panTo([ambLat, ambLon]);
                                        }
                                        
                                        // Auto-fit bounds
                                        if (!routingControl) {
                                            var group = [];
                                            if (ambulanceMarker && map.hasLayer(ambulanceMarker)) group.push(ambulanceMarker.getLatLng());
                                            if (hospitalMarker && map.hasLayer(hospitalMarker)) group.push(hospitalMarker.getLatLng());
                                            if (group.length >= 2) map.fitBounds(L.latLngBounds(group), {padding: [50, 50]});
                                        }

                                    } catch(e) {
                                        console.error("Map Error: " + e);
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://carto.com", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon, $patientLat, $patientLon, $hospLat, $hospLon, '$emergencyState');}", null)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Status Card
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = if (emergencyState == "pending") Color(0xFFFFF3CD) else Color.White),
            elevation = CardDefaults.cardElevation(10.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if(emergencyState == "completed") {
                    // Main Header Logic
                    if (UserSession.paymentStatus == "Paid" || UserSession.paymentStatus == "AutoPay Processed") {
                        Text("✅ Ride Completed", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFF155724))
                    } else {
                        Text("Ride Finished", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = CoralRed)
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Total Fare: ₹$fare", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = CoralRed)
                    
                    if (UserSession.paymentStatus != "Paid" && UserSession.paymentStatus != "AutoPay Processed") {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Please complete the payment to end session", fontSize = 14.sp, color = Color.Gray)
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    if (UserSession.paymentStatus == "AutoPay Processed") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✅ AutoPay Completed", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                                Text("Payment was automatically processed after pending period.", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)
                            }
                        }
                    } else if (UserSession.paymentStatus == "Paid") {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                val successMethodMsg = if (UserSession.paymentMethod == "QR") "Payment Successful using QR Code" else "Payment Successful"
                                Text("✅ $successMethodMsg", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            }
                        }
                    } else if (UserSession.paymentStatus == "Pending") {
                        // Only show pending info if initial 30s timer expired
                        if (payTimer <= 0) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("⚠️ Payment Pending", fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    Text("AutoPay in: $remainingTimeText", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Since you did not pay now, you can pay anytime within 3 days or it will autopay on the third day.", fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                                }
                            }
                        } else {
                            // Initial 30s window - Just show waiting text
                            Text("Waiting for payment... $payTimer s", color = CoralRed, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Pay Now button is visible during BOTH initial 30s and subsequent 3-day pending state
                        Button(
                            onClick = {
                                try {
                                    val currentDriverUpi = driverUpi ?: "resqgo@upi"
                                    val formattedFare = String.format("%.2f", fare)
                                    val upiUri = "upi://pay?pa=$currentDriverUpi&pn=$driverName&am=$formattedFare&cu=INR&tn=Ambulance Fare - ResQGo"
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    val chooser = Intent.createChooser(intent, "Pay via PhonePe, GPay, or Paytm")
                                    
                                    activity.startActivity(chooser)
                                    
                                    // Notify backend of payment initiation (Simulated success for demo sync)
                                    scope.launch {
                                        try {
                                            RetrofitClient.instance.notifyPaymentSuccess(mapOf(
                                                "emergency_id" to emergencyId,
                                                "method" to "App"
                                            ))
                                        } catch (e: Exception) {
                                            Log.e("Payment", "Failed to notify server: ${e.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(activity, "Payment failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = CoralRed),
                            shape = RoundedCornerShape(14.dp),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📱", fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("PAY NOW VIA PHONEPE", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            navController.navigate("home") {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                    ) {
                        Text("Close Dashboard", color = Color.DarkGray)
                    }
                } else {
                    val msg = when {
                        emergencyState == "pending" -> t("Searching for ambulance...", "एम्बुलेंस खोज रहा है...", "ಆಂಬ್ಯುಲೆನ್ಸ್ ಹುಡುಕಲಾಗುತ್ತಿದೆ...")
                        emergencyState == "accepted" -> t("Driver accepted! Arriving in $eta", "ड्राइवर ने स्वीकार किया! $eta में आ रहा है", "ಚಾಲಕ ಒಪ್ಪಿದ್ದಾರೆ! $eta ನಿಮಿಷಗಳಲ್ಲಿ ಆಗಮಿಸುತ್ತಾರೆ")
                        emergencyState == "active" -> t("Patient picked up. Heading to hospital.", "मरीज को ले लिया। अस्पताल जा रहे हैं।", "ರೋಗಿಯನ್ನು ಹತ್ತಿಸಿಕೊಳ್ಳಲಾಗಿದೆ. ಆಸ್ಪತ್ರೆಯತ್ತ.")
                        emergencyState == "completed" -> t("Ride Completed", "यात्रा पूरी हुई", "ಪ್ರಯಾಣ ಪೂರ್ಣಗೊಂಡಿದೆ")
                        else -> t("Please wait...", "कृपया प्रतीक्षा करें...", "ದಯವಿಟ್ಟು ಕಾಯಿರಿ...")
                    }
                    Text(msg, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, color = CoralRed)
                    
                    if (emergencyState != "pending" && driverName != "-") {
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        if (destName != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("🏥 Destination Hospital", fontSize = 12.sp, color = Color.Gray)
                                    Text(destName!!, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = Color(0xFF2E7D32))
                                    if (distance != "-") {
                                        Text("📍 Distance: $distance", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Divider()
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(t("$driverName", "$driverName", "$driverName"), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(t("$ambulanceNo", "$ambulanceNo", "$ambulanceNo"), color = Color.Gray, fontSize = 14.sp)
                            }
                            if (driverPhone != null) {
                                Button(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$driverPhone"))
                                        activity.startActivity(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CALL", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }

        // QR PAYMENT SUCCESS OVERLAY
        if (UserSession.paymentStatus == "Paid") {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(15.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("✅", fontSize = 60.sp)
                        Spacer(modifier = Modifier.height(16.dp))
                        val header = if (UserSession.paymentMethod == "QR") "QR Code Payment Successful" else "Payment Successful"
                        Text(
                            header,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val successMsg = if (UserSession.paymentMethod == "QR") {
                            "Ride Completed"
                        } else {
                            "Ride Completed"
                        }
                        Text(
                            successMsg,
                            fontSize = 16.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        Button(
                            onClick = {
                                UserSession.paymentStatus = ""
                                UserSession.isPendingPayment = false
                                navController.navigate("home") { popUpTo(0) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.fillMaxWidth().height(55.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("RETURN TO DASHBOARD", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================
// DRIVER FLOW
// ==========================

@Composable
fun DriverLoginScreen(navController: NavController) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var errorMsg by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ResQG Driver", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
        Spacer(modifier = Modifier.height(32.dp))
        
        if (errorMsg.isNotBlank()) {
            Text(errorMsg, color = Color.Yellow, modifier = Modifier.padding(bottom = 16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (username.isNotBlank() && password.isNotBlank()) {
                            scope.launch {
                                try {
                                    val res = RetrofitClient.instance.loginDriver(DriverLoginPayload(username, password))
                                    if(res.status == "success" && res.driver_id != null) {
                                        UserSession.driverId = res.driver_id
                                        navController.navigate("driver_home") { popUpTo("driver_login") { inclusive = true } }
                                    } else {
                                        errorMsg = res.message ?: "Invalid login"
                                    }
                                } catch(e: Exception) { errorMsg = "Network error" }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResQGRed)
                ) {
                    Text("LOGIN", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = { navController.navigate("driver_register") }) {
            Text("Register New Driver", color = Color.White)
        }
    }
}

@Composable
fun DriverRegisterScreen(navController: NavController) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ambulanceNo by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var upiId by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(LightBg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Driver Registration", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (info.isNotBlank()) {
            Text(info, color = Color.Yellow, modifier = Modifier.padding(bottom = 16.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Driver Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = ambulanceNo, onValueChange = { ambulanceNo = it }, label = { Text("Ambulance No.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                OutlinedTextField(value = upiId, onValueChange = { upiId = it }, label = { Text("UPI ID (e.g. name@ybl)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (!upiId.contains("@")) {
                            info = "Please enter a valid UPI ID"
                            return@Button
                        }
                        scope.launch {
                            try {
                                val res = RetrofitClient.instance.registerDriver(DriverRegisterPayload(name, username, password, ambulanceNo, phone, upiId))
                                if(res.status == "success") {
                                    navController.navigate("driver_login") { popUpTo("driver_register") { inclusive = true } }
                                } else {
                                    info = res.message ?: "Error"
                                }
                            } catch(e: Exception) { info = "Network error" }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResQGRed)
                ) {
                    Text("REGISTER", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun DriverHomeScreen(navController: NavController, activity: MainActivity) {
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    var currentEmergency by remember { mutableStateOf<DriverEmergency?>(null) }
    var isAccepted by remember { mutableStateOf(false) }
    var isPatientPickedUp by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        while(true) {
            activity.fetchLastLocation { location ->
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                }
            }

            if (lat != 0.0 && lon != 0.0 && !isAccepted) {
                // Poll for emergencies
                try {
                    val res = RetrofitClient.instance.getMyEmergencies(lat, lon)
                    if (res.emergencies.isNotEmpty() && currentEmergency == null) {
                        currentEmergency = res.emergencies[0]
                    }
                } catch(e: Exception) {}
            }
            
            if (lat != 0.0 && lon != 0.0) {
                // Send current location
                try {
                    RetrofitClient.instance.sendLocation(DriverLocationPayload(UserSession.driverId, LocationData(lat, lon)))
                } catch (e: Exception) {}
            }
            delay(3000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.White)) {
        // Full map
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    
                    val mapHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <link rel="stylesheet" href="https://unpkg.com/leaflet-routing-machine@3.2.12/dist/leaflet-routing-machine.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <script src="https://unpkg.com/leaflet-routing-machine@3.2.12/dist/leaflet-routing-machine.js"></script>
                            <style>
                                body, html, #map { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: #eee; }
                            </style>
                        </head>
                        <body>
                            <div id="map"></div>
                            <script>
                                var map = L.map('map', {zoomControl: false}).setView([0,0], 16);
                                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                var driverMarker = L.marker([0,0], {icon: L.icon({iconUrl: 'https://cdn-icons-png.flaticon.com/512/1077/1077114.png', iconSize:[35,35]})}).addTo(map);
                                
                                var routingControl = null;

                                function updateMap(dLat, dLon, pLat, pLon, drawRoute) {
                                    if(dLat === 0) return;
                                    driverMarker.setLatLng([dLat, dLon]);
                                    
                                    if(drawRoute && pLat !== 0) {
                                        if(!routingControl) {
                                            routingControl = L.Routing.control({
                                                waypoints: [L.latLng(dLat, dLon), L.latLng(pLat, pLon)],
                                                show: false,
                                                addWaypoints: false
                                            }).addTo(map);
                                            map.setView([dLat, dLon], 14);
                                        } else {
                                            routingControl.setWaypoints([L.latLng(dLat, dLon), L.latLng(pLat, pLon)]);
                                        }
                                    } else {
                                        if(routingControl) {
                                            map.removeControl(routingControl);
                                            routingControl = null;
                                        }
                                        map.setView([dLat, dLon]);
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("about:blank", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                val pLoc = currentEmergency?.patient_location
                if (isAccepted && pLoc != null) {
                    view.evaluateJavascript("updateMap($lat, $lon, ${pLoc.lat}, ${pLoc.lon}, true)", null)
                } else {
                    view.evaluateJavascript("updateMap($lat, $lon, 0, 0, false)", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Actions
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween) {
            Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Text(if(isAccepted) "ON MISSION" else "AVAILABLE", modifier = Modifier.padding(12.dp), color = CoralRed, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    UserSession.driverId = -1
                    navController.navigate("splash") { popUpTo("driver_home") { inclusive = true } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
            ) {
                Text("Logout")
            }
        }

        // Action Cards at Bottom
        if (currentEmergency != null && !isAccepted) {
            // New Emergency Request
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(15.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("🚨 New Emergency Request!", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ResQGRed)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Patient: ${currentEmergency?.patient_name}")
                    Text("Phone: ${currentEmergency?.patient_mobile}")
                    val dist = currentEmergency?.distance?.let { String.format("%.2f km away", it) } ?: "Unknown distance"
                    Text("Distance: $dist", color = Color.Gray)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        Button(
                            onClick = {
                                scope.launch {
                                    RetrofitClient.instance.acceptEmergency(DriverEmergencyActionPayload(currentEmergency!!.emergency_id, UserSession.driverId))
                                    isAccepted = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            modifier = Modifier.weight(1f)
                        ) { Text("ACCEPT") }
                        Spacer(modifier = Modifier.width(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    RetrofitClient.instance.declineEmergency(DriverEmergencyActionPayload(currentEmergency!!.emergency_id, UserSession.driverId))
                                    currentEmergency = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                            modifier = Modifier.weight(1f)
                        ) { Text("DECLINE") }
                    }
                }
            }
        } else if (isAccepted && !isPatientPickedUp) {
            // Navigation to Patient
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(15.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Navigating to Patient", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Patient: ${currentEmergency?.patient_name} (${currentEmergency?.patient_mobile})")
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${currentEmergency?.patient_mobile}"))
                            activity.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)
                    ) { Text("Call Patient", color = Color.Black) }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                RetrofitClient.instance.patientPickedUp(DriverEmergencyActionPayload(currentEmergency!!.emergency_id, UserSession.driverId))
                                isPatientPickedUp = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("✅ PATIENT PICKED UP") }
                }
            }
        } else if (isPatientPickedUp) {
            // To Hospital
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(15.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Patient Secure. Proceed to Hospital.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CoralRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                RetrofitClient.instance.completeMission(DriverEmergencyActionPayload(currentEmergency!!.emergency_id, UserSession.driverId))
                                // Reset
                                currentEmergency = null
                                isAccepted = false
                                isPatientPickedUp = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) { Text("🏁 COMPLETE MISSION") }
                }
            }
        }
    }
}
