package com.smartambulance.patient

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
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
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
    val RedPrimary = Color(0xFFFF4D4D)
    val LightGray = Color(0xFFF0F2F5)
    
    val colorScheme = lightColorScheme(
        primary = RedPrimary,
        secondary = RedPrimary,
        background = LightGray,
        surface = Color.White,
        onPrimary = Color.White,
        onBackground = Color.Black
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

    // Patient
    var firstName: String
        get() = prefs.getString("firstName", "") ?: ""
        set(value) = prefs.edit().putString("firstName", value).apply()

    var lastName: String
        get() = prefs.getString("lastName", "") ?: ""
        set(value) = prefs.edit().putString("lastName", value).apply()

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
        get() = role == "patient" && email.isNotBlank() && username.isNotBlank() && isProfileComplete

    val isDriverLoggedIn: Boolean
        get() = role == "driver" && driverId != -1

    var serverIp: String
        get() = prefs.getString("serverIp", "web-production-67038.up.railway.app") ?: "web-production-67038.up.railway.app"
        set(value) = prefs.edit().putString("serverIp", value).apply()

    var isAutoPayEnabled: Boolean
        get() = prefs.getBoolean("isAutoPayEnabled", false)
        set(value) = prefs.edit().putBoolean("isAutoPayEnabled", value).apply()
}

fun t(en: String, hi: String, kn: String): String {
    return when(UserSession.language) {
        1 -> hi
        2 -> kn
        else -> en
    }
}

val DeepPurple = Color(0xFF30336B)
val LightBlue = Color(0xFFEBF0FE)
val ResQGRed = Color(0xFFD32F2F)

@Composable
fun AppNavigation(activity: MainActivity, linkFlow: SharedFlow<String>? = null) {
    UserSession.init(activity)
    RetrofitClient.CURRENT_IP = UserSession.serverIp
    val authViewModel: AuthViewModel = viewModel()
    val authStatus = authViewModel.authStatus
    
    val navController = rememberNavController()

    LaunchedEffect(Unit) {
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
        modifier = Modifier.fillMaxSize().background(DeepPurple),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ResQG", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
            Text("Patient App", color = Color.White, fontSize = 20.sp)
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

@Composable
fun RoleSelectionScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
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
            Text("AMBULANCE DRIVER", color = DeepPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ==========================
// PATIENT FLOW
// ==========================

@Composable
fun LanguageScreen(navController: NavController) {
    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Choose Language", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(40.dp))
        
        listOf(
            Triple(0, "English", "English"),
            Triple(1, "Hindi (हिंदी)", "हिंदी"),
            Triple(2, "Kannada (ಕನ್ನಡ)", "ಕನ್ನಡ")
        ).forEach { lang ->
            Button(
                onClick = { 
                    UserSession.language = lang.first
                    navController.navigate("email") 
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).height(55.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(lang.second, color = DeepPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        TextButton(onClick = { navController.navigate("login") }) {
            Text("Already an existing user? Login here", color = Color.White)
        }
        TextButton(onClick = { navController.navigate("role_selection") { popUpTo("role_selection") { inclusive = true } } }) {
            Text("Back to Role Selection", color = Color.Gray)
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
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Enter Email", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center)
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
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPurple),
                    enabled = status !is AuthStatus.Loading && email.isNotBlank()
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("SEND MAGIC LINK", color = Color.White, fontWeight = FontWeight.Bold)
                    }
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
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Check Your Email", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
                    CircularProgressIndicator(color = DeepPurple, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (timeLeft > 0) {
                    Text("Resend Magic Link in $timeLeft s", color = Color.Gray)
                } else {
                    TextButton(
                        onClick = { viewModel.sendEmailOtp(email) },
                        enabled = status !is AuthStatus.Loading
                    ) {
                        Text("Resend Magic Link", color = DeepPurple, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(navController: NavController, email: String) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(t("Complete Profile", "प्रोफाइल पूरी करें", "ಪ್ರೊಫೈಲ್ ಪೂರ್ಣಗೊಳಿಸಿ"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = firstName, onValueChange = { firstName = it }, label = { Text(t("First Name", "पहला नाम", "ಮೊದಲ ಹೆಸರು")) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = lastName, onValueChange = { lastName = it }, label = { Text(t("Last Name", "अंतिम नाम", "ಕೊನೆಯ ಹೆಸರು")) }, modifier = Modifier.fillMaxWidth())
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
                        if (firstName.isNotBlank() && username.isNotBlank() && password.isNotBlank() && phone.isNotBlank()) {
                            UserSession.firstName = firstName
                            UserSession.lastName = lastName
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
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPurple)
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
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
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
                OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email Address") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(t("Password", "पासवर्ड", "ಪಾಸ್ವರ್ಡ್")) }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (email.isNotBlank() && password.isNotBlank()) {
                            UserSession.firstName = "User"
                            UserSession.email = email
                            UserSession.username = email
                            UserSession.isProfileComplete = true
                            navController.navigate("home") { popUpTo("login") { inclusive = true } }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPurple)
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
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.cacheMode = android.webkit.WebSettings.LOAD_NO_CACHE
                    
                    val mapHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <style>
                                body, html, #map { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: #f0f0f0; }
                                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #666; }
                            </style>
                        </head>
                        <body>
                            <div id="loading">Loading Map...</div>
                            <div id="map"></div>
                            <script>
                                var map = null;
                                var marker = null;
                                function updateMap(lat, lon) {
                                    document.getElementById('loading').style.display = 'none';
                                    if (typeof L === 'undefined') { 
                                        setTimeout(function(){ updateMap(lat, lon); }, 100); 
                                        return; 
                                    }
                                    if (!map) {
                                        var initialLat = lat !== 0 ? lat : 20.5937;
                                        var initialLon = lon !== 0 ? lon : 78.9629;
                                        map = L.map('map', {zoomControl: false}).setView([initialLat, initialLon], 16);
                                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                        marker = L.marker([initialLat, initialLon], {
                                            icon: L.icon({
                                                iconUrl: 'https://cdn-icons-png.flaticon.com/512/3603/3603850.png', 
                                                iconSize:[40,40],
                                                iconAnchor: [20, 40]
                                            })
                                        }).addTo(map);
                                    } else if (map && lat !== 0) {
                                        map.panTo([lat, lon]);
                                        marker.setLatLng([lat, lon]);
                                    }
                                }
                                if (window.pendingLat && window.pendingLon) {
                                    updateMap(window.pendingLat, window.pendingLon);
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon);}else{window.pendingLat=$lat; window.pendingLon=$lon;}", null)
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(8.dp)) {
                Text(t("Welcome, ${UserSession.firstName}", "स्वागत है, ${UserSession.firstName}", "ಸ್ವಾಗತ, ${UserSession.firstName}"), modifier = Modifier.padding(vertical = 8.dp, horizontal = 16.dp), fontWeight = FontWeight.Bold, color = DeepPurple)
            }
            Button(
                onClick = {
                    UserSession.firstName = ""; UserSession.phone = ""; UserSession.email = ""; UserSession.username = ""; UserSession.isProfileComplete = false
                    navController.navigate("splash") { popUpTo("home") { inclusive = true } }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ResQGRed)
            ) {
                Text(t("Logout", "लॉग आउट", "ಲಾಗ್ ಔಟ್"), fontSize = 12.sp)
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

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(t("Confirm Emergency", "आपातकाल की पुष्टि करें", "ತುರ್ತು ಪರಿಸ್ಥಿತಿಯನ್ನು ದೃಢೀಕರಿಸಿ"), fontWeight = FontWeight.Bold, color = DeepPurple) },
                text = { Text(t("Are you sure you want an ambulance here immediately?", "क्या आप निश्चित रूप से तुरंत एम्बुलेंस बुलाना चाहते हैं?", "ನಿಮ್ಮ ಪ್ರಸ್ತುತ ಸ್ಥಳಕ್ಕೆ ತಕ್ಷಣವೇ ಆಂಬ್ಯುಲೆನ್ಸ್ ವಿನಂತಿಸಲು ನೀವು ಖಚಿತವಾಗಿ ಬಯಸುವಿರಾ?")) },
                containerColor = Color.White,
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DeepPurple),
                        onClick = {
                            showDialog = false
                            loading = true
                            activity.fetchLastLocation { location ->
                                scope.launch {
                                    try {
                                        val fullName = "${UserSession.firstName} ${UserSession.lastName}".trim()
                                        val phone = UserSession.phone
                                        if (phone.isBlank()) {
                                            Toast.makeText(activity, "Error: Phone number missing. Please register again.", Toast.LENGTH_LONG).show()
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
    var emergencyState by remember { mutableStateOf("pending") }
    var driverName by remember { mutableStateOf("-") }
    var ambulanceNo by remember { mutableStateOf("-") }
    var driverPhone by remember { mutableStateOf<String?>(null) }
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    var distance by remember { mutableStateOf("-") }
    var eta by remember { mutableStateOf(t("Wait...", "प्रतीक्षा करें...", "ಕಾಯಿರಿ...")) }
    var destName by remember { mutableStateOf<String?>(null) }
    var hospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }
    var fare by remember { mutableStateOf(0.0) }

    var lastStatus by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val status = RetrofitClient.instance.getEmergencyStatus(emergencyId)
                emergencyState = status.emergency_state
                driverName = status.driver_name ?: "-"
                ambulanceNo = status.ambulance_no ?: "-"
                destName = status.dest_name
                driverPhone = status.driver_phone
                fare = status.fare ?: 0.0

                if (emergencyState != lastStatus) {
                   Log.d("STATUS_CHANGE", "New status: $emergencyState")
                   lastStatus = emergencyState
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
                // Don't pop immediately, wait for user to see fare and pay
                // kotlinx.coroutines.delay(3000)
                // navController.popBackStack()
                // return@LaunchedEffect
            }
            kotlinx.coroutines.delay(3000)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFE3F2FD))) {
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
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <style>
                                body, html, #map { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: #f0f0f0; }
                                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #666; }
                            </style>
                        </head>
                        <body>
                            <div id="loading">Loading Map...</div>
                            <div id="map"></div>
                            <script>
                                var map = null;
                                var marker = null;
                                function updateMap(lat, lon) {
                                    document.getElementById('loading').style.display = 'none';
                                    if (typeof L === 'undefined') { setTimeout(function(){ updateMap(lat, lon); }, 100); return; }
                                    if (!map) {
                                        var initialLat = lat !== 0 ? lat : 20.5937;
                                        var initialLon = lon !== 0 ? lon : 78.9629;
                                        map = L.map('map', {zoomControl: false}).setView([initialLat, initialLon], 16);
                                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                        marker = L.marker([initialLat, initialLon], {
                                            icon: L.icon({
                                                iconUrl: 'https://cdn-icons-png.flaticon.com/512/2967/2967350.png', 
                                                iconSize:[35,35],
                                                iconAnchor: [17, 17]
                                            })
                                        }).addTo(map);
                                    } else if (map && lat !== 0) {
                                        map.panTo([lat, lon]);
                                        marker.setLatLng([lat, lon]);
                                    }
                                }
                                if (window.pendingLat && window.pendingLon) {
                                    updateMap(window.pendingLat, window.pendingLon);
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon);}else{window.pendingLat=$lat; window.pendingLon=$lon;}", null)
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
                    Text("✅ Ride Completed", fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = Color(0xFF155724))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Total Fare: ₹$fare", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = DeepPurple)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Please complete the payment to end session", fontSize = 14.sp, color = Color.Gray)
                    
                    if (UserSession.isAutoPayEnabled) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("AutoPay Active: Settlement scheduled in 3 days.", color = Color(0xFF2E7D32), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                val upiUri = "upi://pay?pa=resqgo@upi&pn=ResQGo&am=$fare&cu=INR"
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                intent.setPackage("com.phonepe.app")
                                try {
                                    activity.startActivity(intent)
                                } catch (e: Exception) {
                                    val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
                                    val chooser = Intent.createChooser(fallbackIntent, "Pay with UPI")
                                    activity.startActivity(chooser)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(60.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5f7cff)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("PAY NOW", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = { navController.popBackStack() }) {
                        Text("Close", color = Color.Gray)
                    }
                } else {
                    val msg = when {
                        emergencyState == "pending" -> t("Searching for ambulance...", "एम्बुलेंस खोज रहा है...", "ಆಂಬ್ಯುಲೆನ್ಸ್ ಹುಡುಕಲಾಗುತ್ತಿದೆ...")
                        emergencyState == "accepted" -> t("Driver accepted! Arriving in $eta", "ड्राइवर ने स्वीकार किया! $eta में आ रहा है", "ಚಾಲಕ ಒಪ್ಪಿದ್ದಾರೆ! $eta ನಿಮಿಷಗಳಲ್ಲಿ ಆಗಮಿಸುತ್ತಾರೆ")
                        emergencyState == "active" -> t("Patient picked up. Heading to hospital.", "मरीज को ले लिया। अस्पताल जा रहे हैं।", "ರೋಗಿಯನ್ನು ಹತ್ತಿಸಿಕೊಳ್ಳಲಾಗಿದೆ. ಆಸ್ಪತ್ರೆಯತ್ತ.")
                        emergencyState == "completed" -> t("Ride Completed", "यात्रा पूरी हुई", "ಪ್ರಯಾಣ ಪೂರ್ಣಗೊಂಡಿದೆ")
                        else -> t("Please wait...", "कृपया प्रतीक्षा करें...", "ದಯವಿಟ್ಟು ಕಾಯಿರಿ...")
                    }
                    Text(msg, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, color = DeepPurple)
                    
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
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
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
        TextButton(onClick = { navController.navigate("role_selection") { popUpTo("driver_login") { inclusive = true } } }) {
            Text("Back to Role Selection", color = Color.Gray)
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
    val scope = rememberCoroutineScope()
    var info by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
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
                
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                val res = RetrofitClient.instance.registerDriver(DriverRegisterPayload(name, username, password, ambulanceNo, phone))
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
                                var driverMarker = L.marker([0,0], {icon: L.icon({iconUrl: 'https://cdn-icons-png.flaticon.com/512/2967/2967350.png', iconSize:[35,35]})}).addTo(map);
                                
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
                Text(if(isAccepted) "ON MISSION" else "AVAILABLE", modifier = Modifier.padding(12.dp), color = DeepPurple, fontWeight = FontWeight.Bold)
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
                    Text("Navigating to Patient", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepPurple)
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
                    Text("Patient Secure. Proceed to Hospital.", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepPurple)
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
