package com.smartambulance.patient

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.concurrent.TimeUnit
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.media.RingtoneManager
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
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.LocationServices
import com.smartambulance.patient.network.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

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

        setContent {
            AppTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppNavigation(this)
                }
            }
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

    var isProfileComplete: Boolean
        get() = prefs.getBoolean("isProfileComplete", false)
        set(value) = prefs.edit().putBoolean("isProfileComplete", value).apply()

    var serverIp: String
        get() = prefs.getString("serverIp", "web-production-67038.up.railway.app") ?: "web-production-67038.up.railway.app"
        set(value) = prefs.edit().putString("serverIp", value).apply()
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
fun AppNavigation(activity: MainActivity) {
    UserSession.init(activity)
    RetrofitClient.CURRENT_IP = UserSession.serverIp
    val authViewModel: AuthViewModel = viewModel()
    val authStatus = authViewModel.authStatus
    
    val navController = rememberNavController()
    
    // Global Navigation Handler for Auth Status
    LaunchedEffect(authStatus) {
        when (authStatus) {
            is AuthStatus.Authenticated -> {
                Log.d("AppNavigation", "Navigation: Authenticated as ${authStatus.role}")
                if (authStatus.role == "driver") {
                    navController.navigate("driver_home") { popUpTo(0) }
                } else {
                    // Role mismatch or invalid entry for Driver app
                    authViewModel.signOut()
                    navController.navigate("driver_login") { popUpTo(0) }
                }
            }
            is AuthStatus.Idle -> {
                // Initial or signout
            }
            else -> {}
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") { SplashScreen(navController, authViewModel) }
        composable("driver_login") { DriverLoginScreen(navController, authViewModel) }
        composable("driver_register") { DriverRegisterScreen(navController, authViewModel) }
        composable("driver_home") { DriverHomeScreen(navController, activity, authViewModel) }
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
            UserSession.role = "driver" 
            navController.navigate("driver_login") { popUpTo("splash") { inclusive = true } }
        }
    }
    
    Box(
        modifier = Modifier.fillMaxSize().background(DeepPurple),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ResQG", fontSize = 64.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
            Text("Driver Professional", color = Color.White, fontSize = 20.sp)
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
                    navController.navigate("driver_login") { popUpTo("splash") { inclusive = true } }
                }) { Text("Save") }
            }
        )
    }
}



// ==========================
// PATIENT FLOW
// ==========================















// ==========================
// DRIVER FLOW
// ==========================

@Composable
fun DriverLoginScreen(navController: NavController, viewModel: AuthViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val status = viewModel.authStatus

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("ResQG Driver", fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, color = ResQGRed, fontFamily = FontFamily.Serif)
        Text("Login to your dashboard", color = Color.White, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(32.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = status !is AuthStatus.Loading
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = status !is AuthStatus.Loading
                )
                
                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.loginDriver(username, password) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepPurple),
                    enabled = status !is AuthStatus.Loading && username.isNotBlank() && password.isNotBlank()
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("LOGIN", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                TextButton(
                    onClick = { navController.navigate("driver_register") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Don't have an account? Register here", color = DeepPurple)
                }
            }
        }
    }
}

@Composable
fun DriverRegisterScreen(navController: NavController, viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var ambulanceNo by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    val status = viewModel.authStatus

    Column(
        modifier = Modifier.fillMaxSize().background(DeepPurple).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text("Driver Registration", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                OutlinedTextField(value = ambulanceNo, onValueChange = { ambulanceNo = it }, label = { Text("Ambulance No.") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, modifier = Modifier.fillMaxWidth())
                
                if (status is AuthStatus.Error) {
                    Text(status.message, color = ResQGRed, modifier = Modifier.padding(top = 8.dp))
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { viewModel.registerDriver(name, username, password, ambulanceNo, phone) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResQGRed),
                    enabled = status !is AuthStatus.Loading
                ) {
                    if (status is AuthStatus.Loading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    } else {
                        Text("REGISTER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
                
                TextButton(
                    onClick = { navController.navigate("driver_login") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Already registered? Login here", color = DeepPurple)
                }
            }
        }
    }
}


@Composable
fun DriverHomeScreen(navController: NavController, activity: MainActivity, viewModel: AuthViewModel) {
    var lat by remember { mutableStateOf(0.0) }
    var lon by remember { mutableStateOf(0.0) }
    var currentEmergency by remember { mutableStateOf<DriverEmergency?>(null) }
    var isAccepted by remember { mutableStateOf(false) }
    var isPatientPickedUp by remember { mutableStateOf(false) }
    var hospitals by remember { mutableStateOf<List<Hospital>>(emptyList()) }
    var selectedHospital by remember { mutableStateOf<Hospital?>(null) }
    val scope = rememberCoroutineScope()
    
    // Play sound when new emergency arrives
    LaunchedEffect(currentEmergency) {
        if (currentEmergency != null && !isAccepted) {
            try {
                val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(activity, notification)
                r.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Observe logout
    val status = viewModel.authStatus
    LaunchedEffect(status) {
        if (status is AuthStatus.Idle) {
            navController.navigate("driver_login") { popUpTo(0) }
        }
    }

    LaunchedEffect(isPatientPickedUp) {
        if (isPatientPickedUp && lat != 0.0 && lon != 0.0) {
            try {
                hospitals = RetrofitClient.instance.getNearbyHospitals(lat, lon).hospitals
            } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            activity.fetchLastLocation { location ->
                if (location != null) {
                    lat = location.latitude
                    lon = location.longitude
                }
            }

            if (!isAccepted) {
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
                                body, html, #map { height: 100vh; width: 100vw; margin: 0; padding: 0; overflow: hidden; background: #e0e0e0; }
                                #loading { position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%); font-family: sans-serif; color: #666; z-index: 1000; }
                            </style>
                        </head>
                        <body>
                            <div id="loading">Initializing Map...</div>
                            <div id="map"></div>
                            <script>
                                var map = null;
                                var driverMarker = null;
                                var routingControl = null;

                                function updateMap(dLat, dLon, pLat, pLon, drawRoute) {
                                    try {
                                        if (typeof L === 'undefined' || typeof L.Routing === 'undefined') { 
                                            setTimeout(function(){ updateMap(dLat, dLon, pLat, pLon, drawRoute); }, 200); 
                                            return; 
                                        }
                                        document.getElementById('loading').style.display = 'none';
                                        
                                        if (!map) {
                                            var initialLat = (dLat && dLat !== 0) ? dLat : 13.0266;
                                            var initialLon = (dLon && dLon !== 0) ? dLon : 77.5714;
                                            map = L.map('map', {zoomControl: false, attributionControl: false}).setView([initialLat, initialLon], 16);
                                            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                            
                                            driverMarker = L.marker([initialLat, initialLon], {
                                                icon: L.icon({
                                                    iconUrl: 'https://cdn-icons-png.flaticon.com/512/2967/2967350.png', 
                                                    iconSize:[40,40],
                                                    iconAnchor: [20, 20]
                                                })
                                            }).addTo(map);
                                        }

                                        if (map && dLat && dLat !== 0) {
                                            driverMarker.setLatLng([dLat, dLon]);
                                            
                                            if (drawRoute && pLat && pLat !== 0) {
                                                if (!routingControl) {
                                                    routingControl = L.Routing.control({
                                                        waypoints: [L.latLng(dLat, dLon), L.latLng(pLat, pLon)],
                                                        show: false,
                                                        addWaypoints: false,
                                                        draggableWaypoints: false,
                                                        fitSelectedRoutes: true,
                                                        lineOptions: { styles: [{ color: '#f03', weight: 6 }] }
                                                    }).addTo(map);
                                                } else {
                                                    routingControl.setWaypoints([L.latLng(dLat, dLon), L.latLng(pLat, pLon)]);
                                                }
                                            } else {
                                                if (routingControl) {
                                                    map.removeControl(routingControl);
                                                    routingControl = null;
                                                }
                                                map.panTo([dLat, dLon]);
                                            }
                                        }
                                    } catch(e) {
                                        console.error("Map Error: " + e);
                                    }
                                }
                            </script>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://openstreetmap.org", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                val pLoc = currentEmergency?.patient_location
                val pLat = pLoc?.lat ?: 0.0
                val pLon = pLoc?.lon ?: 0.0
                val drawRoute = isAccepted && pLoc != null
                
                view.evaluateJavascript(
                    "if(typeof updateMap === 'function'){ updateMap($lat, $lon, $pLat, $pLon, $drawRoute); } else { window.pendingData = {dLat: $lat, dLon: $lon, pLat: $pLat, pLon: $pLon, drawRoute: $drawRoute}; }", 
                    null
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // Top Actions
        Column(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)) {
            if (currentEmergency != null && !isAccepted) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ResQGRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "🚨 NEW EMERGENCY REQUEST! 🚨",
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center,
                        fontSize = 20.sp
                    )
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Text(if(isAccepted) "ON MISSION" else "AVAILABLE", modifier = Modifier.padding(12.dp), color = DeepPurple, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { viewModel.signOut() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) {
                    Text("Logout")
                }
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
            // Hospital Selection
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.BottomCenter),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(15.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    if (selectedHospital == null) {
                        Text("Select Nearest Hospital", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DeepPurple)
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(hospitals) { hospital ->
                                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    onClick = {
                                        scope.launch {
                                            RetrofitClient.instance.assignHospital(
                                                HospitalAssignPayload(
                                                    emergency_id = currentEmergency!!.emergency_id,
                                                    lat = hospital.location.lat,
                                                    lon = hospital.location.lon,
                                                    name = hospital.name
                                                )
                                            )
                                            selectedHospital = hospital
                                        }
                                    },
                                    colors = CardDefaults.cardColors(containerColor = LightBlue)
                                ) {
                                    Text(hospital.name, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    } else {
                        Text("Heading to: ${selectedHospital?.name}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    RetrofitClient.instance.completeMission(DriverEmergencyActionPayload(currentEmergency!!.emergency_id, UserSession.driverId))
                                    // Reset
                                    currentEmergency = null
                                    isAccepted = false
                                    isPatientPickedUp = false
                                    selectedHospital = null
                                    hospitals = emptyList()
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
}
