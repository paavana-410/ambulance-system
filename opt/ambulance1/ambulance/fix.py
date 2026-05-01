import sys

path = 'Patient_app/app/src/main/java/com/smartambulance/patient/MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

out = []
insert_done = False
for i in range(len(lines)):
    line = lines[i]
    if not insert_done and 'Text("ResQG Driver", fontSize = 42.sp' in line:
        missing = """                            </script>
                        </body>
                        </html>
                    \"\"\".trimIndent()
                    loadDataWithBaseURL("about:blank", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                if (lat != 0.0 && lon != 0.0) {
                    view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon);}else{window.pendingLat=$lat; window.pendingLon=$lon;}", null)
                }
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
                                        val res = RetrofitClient.instance.requestAmbulance(
                                            RequestAmbulancePayload(fullName, "N/A", UserSession.phone, location?.latitude ?: 0.0, location?.longitude ?: 0.0)
                                        )
                                        loading = false
                                        navController.navigate("status/${res.emergency_id}")
                                    } catch (e: Exception) { loading = false }
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

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val status = RetrofitClient.instance.getEmergencyStatus(emergencyId)
                emergencyState = status.emergency_state
                driverName = status.driver_name ?: "-"
                ambulanceNo = status.ambulance_no ?: "-"
                destName = status.dest_name
                driverPhone = status.driver_phone

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
                kotlinx.coroutines.delay(3000)
                navController.popBackStack()
                return@LaunchedEffect
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
                    
                    val mapHtml = \"\"\"
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"/>
                            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
                            <style>
                                body, html, #map { height: 100%; width: 100%; margin: 0; padding: 0; overflow: hidden; background: #eee; }
                            </style>
                        </head>
                        <body>
                            <div id="map"></div>
                            <script>
                                var map = null;
                                var marker = null;
                                function updateMap(lat, lon) {
                                    if (typeof L === 'undefined') { setTimeout(function(){ updateMap(lat, lon); }, 100); return; }
                                    if (lat == 0 && lon == 0) return;
                                    if (!map) {
                                        map = L.map('map', {zoomControl: false}).setView([lat, lon], 16);
                                        L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png').addTo(map);
                                        marker = L.marker([lat, lon], {icon: L.icon({iconUrl: 'https://cdn-icons-png.flaticon.com/512/2967/2967350.png', iconSize:[35,35]})}).addTo(map);
                                    } else {
                                        map.setView([lat, lon]);
                                        marker.setLatLng([lat, lon]);
                                    }
                                }
                                if (window.pendingLat && window.pendingLon) {
                                    updateMap(window.pendingLat, window.pendingLon);
                                }
                            </script>
                        </body>
                        </html>
                    \"\"\".trimIndent()
                    loadDataWithBaseURL("about:blank", mapHtml, "text/html", "UTF-8", null)
                }
            },
            update = { view ->
                if (lat != 0.0 && lon != 0.0) {
                    view.evaluateJavascript("if(typeof updateMap==='function'){updateMap($lat, $lon);}else{window.pendingLat=$lat; window.pendingLon=$lon;}", null)
                }
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
                } else {
                    val msg = when {
                        emergencyState == "pending" -> t("Searching for ambulance...", "एम्बुलेंस खोज रहा है...", "ಆಂಬ್ಯುಲೆನ್ಸ್ ಹುಡುಕಲಾಗುತ್ತಿದೆ...")
                        emergencyState == "active" -> t("Heading to hospital", "अस्पताल की ओर", "ಆಸ್ಪತ್ರೆಯತ್ತ")
                        else -> t("Ambulance arriving in $eta", "$eta में एम्बुलेंस आ रही है", "$eta ನಿಮಿಷಗಳಲ್ಲಿ ಆಂಬ್ಯುಲೆನ್ಸ್ ಆಗಮಿಸುತ್ತದೆ")
                    }
                    Text(msg, fontWeight = FontWeight.Bold, fontSize = 20.sp, textAlign = TextAlign.Center, color = DeepPurple)
                    
                    if (emergencyState != "pending" && driverName != "-") {
                        Spacer(modifier = Modifier.height(12.dp))
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
"""
        out.append(missing)
        insert_done = True
        
    out.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(out)

print("Done")
