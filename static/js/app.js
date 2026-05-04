// Global variables
// Global variables
const BASE_URL = window.location.origin;

let map;
let ambulanceMarker;
let patientMarker = null;
let hospitalMarker = null;
let hospitalSearchMarkers = [];



let currentEmergency = null;
let currentMission = null;
let routingControl = null;
let mapCentered = false;
let currentEmergencyIdForOTP = null;
let isMoving = false; // Flag to prevent real GPS from overriding simulation

let emergencyTimer;
let locationInterval;
let timeLeft = 15;

let routeCoordinates = [];
let movementIndex = 0;
let movementInterval = null;

// Fixed test location (Bangalore)
let currentLocation = { lat: 13.026632, lon: 77.571419 };







// Login function - REAL SERVER CONNECTION
function login() {
    const username = document.getElementById('username').value;
    const password = document.getElementById('password').value;
    
    if (!username || !password) {
        alert('Please enter both username and password');
        return;
    }
    
    // Show loading
    const loginBtn = document.querySelector('.login-btn');
    loginBtn.textContent = 'Connecting to Server...';
    loginBtn.disabled = true;
    
    fetch(`${BASE_URL}/login`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({username, password})
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Server error: ' + response.status);
        }
        return response.json();
    })
    .then(data => {
        if (data.status === 'success') {

    document.getElementById('login-screen').style.display = 'none';

    const dashboard = document.getElementById('main-app');
    dashboard.style.display = 'flex';
    dashboard.style.position = 'absolute';
    dashboard.style.top = '0';
    dashboard.style.left = '0';
    dashboard.style.width = '100%';
    dashboard.style.height = '100%';

    document.getElementById('driver-name').textContent = data.driver_name;
    document.getElementById('ambulance-no').textContent = data.ambulance_no;
    document.getElementById('your-name').textContent = data.driver_name;
    document.getElementById('your-ambulance').textContent = data.ambulance_no;

    initMap();
    startLocationTracking();
    startEmergencyPolling();
    updateSystemStatus();
    checkCurrentMission();

    console.log("Login successful with server");
}
         else {
            alert('Login failed: ' + data.message);
            loginBtn.textContent = 'Login to Dashboard';
            loginBtn.disabled = false;
        }
    })
    .catch(error => {
        console.error('Login error:', error);
        alert('Cannot connect to backend server. Please make sure the Flask server is running on the laptop.');
        // alert('Cannot connect to server. Please check if Raspberry Pi server is running.');
        loginBtn.textContent = 'Login to Dashboard';
        loginBtn.disabled = false;
    });
}

function logout() {
    if (locationInterval) {
        clearInterval(locationInterval);
    }
    // ADDED: Clear routing control on logout
    if (routingControl) {
        map.removeControl(routingControl);
        routingControl = null;
    }
    fetch(`${BASE_URL}/logout`, {method: 'POST'})
    .then(() => {
        location.reload();
    });
}

function initMap() {
    // Center map on currrent location initially
    map = L.map('map').setView([currentLocation.lat, currentLocation.lon], 15);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '© OpenStreetMap contributors'
    }).addTo(map);

    // 🚑 Ambulance marker - Initialize with current location to avoid undefined errors
    ambulanceMarker = L.marker([currentLocation.lat, currentLocation.lon],{
        icon:L.icon({
            iconUrl:'https://cdn-icons-png.flaticon.com/512/2967/2967350.png',
            iconSize:[35,35]
        })
    }).addTo(map);
}

function startLocationTracking() {
    if (!navigator.geolocation) {
        alert("Geolocation not supported by this browser.");
        return;
    }

    navigator.geolocation.watchPosition(function(position){
        if (isMoving) return; // Don't let real GPS override simulation or manual move

        currentLocation = {
            lat: position.coords.latitude,
            lon: position.coords.longitude
        };

        console.log("📍 Live GPS Update:", currentLocation);

        // Move marker safely
        if(ambulanceMarker) {
            ambulanceMarker.setLatLng([currentLocation.lat, currentLocation.lon]);
            if(!mapCentered) {
                map.setView([currentLocation.lat, currentLocation.lon]);
                mapCentered = true;
            }
        }

        sendLocationToServer(currentLocation);
    }, function(error){
        console.warn("GPS Tracking Warning (Expected on HTTP):", error.message);
    },{
        enableHighAccuracy: true,
        maximumAge: 1000
    });
}

// 📡 Send driver GPS location to server
function sendLocationToServer(location) {
    if (!location) return;
    fetch(`${BASE_URL}/api/send_location`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ location: location })
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === 'ok') {
            console.log('📍 Location sent:', location);
        }
    })
    .catch(error => console.error('Error sending location:', error));
}

function sendRoutingDistanceToServer(distance, time) {
    if(!currentMission) return;
    fetch(`${BASE_URL}/api/routing_distance`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ 
            routing_distance: distance,
            estimated_time: time,
            emergency_id: currentMission.emergency_id
        })
    }).catch(e => console.error(e));
}

function clearRoute() {
    if (routingControl) {
        map.removeControl(routingControl);
        routingControl = null;
    }
}



function startEmergencyPolling() {
    // Check for new emergencies every 5 seconds (Reduced frequency to prevent 429 errors)
    setInterval(() => {
        if (!currentEmergency) { 
            checkForEmergencies();
        }
    }, 5000);
}

function checkForEmergencies() {
    console.log("🔍 Checking for emergencies...");
    
    fetch(`${BASE_URL}/api/get_my_emergencies`)
    .then(response => response.json())
    .then(data => {
        console.log("📋 Emergency check response:", data);
        
        if (data.emergencies && data.emergencies.length > 0) {
            const emergency = data.emergencies[0];
            console.log("🚨 New emergency found:", emergency);
            
            // Only show popup if we don't have a current emergency
            if (!currentEmergency) {
                showEmergencyPopup(emergency);
            }
        } else {
            console.log("📭 No emergencies found");
        }
    })
    .catch(error => {
        console.error('Error checking emergencies:', error);
    });
}

function showEmergencyPopup(emergency) {
    console.log("🎯 Showing emergency popup for REAL data:", emergency);
    
    // Play alert sound using browser-generated beep (no external dependency)
    try {
        const ctx = new (window.AudioContext || window.webkitAudioContext)();
        const oscillator = ctx.createOscillator();
        const gainNode = ctx.createGain();
        oscillator.connect(gainNode);
        gainNode.connect(ctx.destination);
        oscillator.type = 'sine';
        oscillator.frequency.value = 880;
        gainNode.gain.setValueAtTime(0.5, ctx.currentTime);
        gainNode.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.8);
        oscillator.start(ctx.currentTime);
        oscillator.stop(ctx.currentTime + 0.8);
    } catch(e) { console.warn('Audio alert failed:', e); }

    currentEmergency = emergency;
    currentEmergencyIdForOTP = emergency.emergency_id;
    timeLeft = 15;
    
    // Update popup content - Use data from mobile app
    document.getElementById('popup-name').textContent = emergency.patient_name || 'Unknown';
    document.getElementById('popup-age').textContent = emergency.patient_age || 'Unknown';
    document.getElementById('popup-mobile').textContent = emergency.patient_mobile || 'Unknown';
    document.getElementById('popup-emergency').textContent = 'Medical Emergency'; // Default
    document.getElementById('popup-location').textContent = emergency.address || 'Current Location';
    
    // Show the popup as FLEX to respect centering
    document.getElementById('emergency-popup').style.display = 'flex';
    
    // Update status
    document.getElementById('status-indicator').textContent = 'Emergency Alert';
    document.getElementById('status-indicator').className = 'status-indicator status-busy';
    
    // Start timer
    startEmergencyTimer();
    
    // Set patient location on map
    if (emergency.patient_location) {
        setPatientLocation(emergency.patient_location, emergency.patient_name);
    } else {
        console.warn("⚠️ No patient location data in emergency");
    }
}

function startEmergencyTimer() {
    clearInterval(emergencyTimer);
    
    // Update timer display immediately
    document.getElementById('emergency-timer').textContent = timeLeft + 's';
    
    emergencyTimer = setInterval(() => {
        timeLeft--;
        document.getElementById('emergency-timer').textContent = timeLeft + 's';
        
        if (timeLeft <= 0) {
            clearInterval(emergencyTimer);
            console.log("⏰ Emergency timer expired - auto declining");
            // Auto-decline after timeout
            declineEmergency();
            hideEmergencyPopup();
        }
    }, 1000);
}

function hideEmergencyPopup() {
    console.log("🎭 Hiding emergency popup");
    document.getElementById('emergency-popup').style.display = 'none';
    clearInterval(emergencyTimer);
    currentEmergency = null;
    
    // Reset status if not on mission
    if (!document.getElementById('mission-info').style.display || 
        document.getElementById('mission-info').style.display === 'none') {
        document.getElementById('status-indicator').textContent = 'Available';
        document.getElementById('status-indicator').className = 'status-indicator status-available';
    }
}

function acceptEmergency() {
    if (!currentEmergency) {
        alert('No emergency to accept');
        return;
    }
    
    console.log("✅ Accepting emergency:", currentEmergency.emergency_id);
    
    // Disable buttons during processing
    const acceptBtn = document.querySelector('.accept-btn');
    const declineBtn = document.querySelector('.decline-btn');
    acceptBtn.disabled = true;
    declineBtn.disabled = true;
    acceptBtn.textContent = 'Processing...';
    
    fetch(`${BASE_URL}/api/accept_emergency`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ emergency_id: currentEmergency.emergency_id })
    })
    .then(response => response.json())
    .then(data => {
        console.log("📨 Accept emergency response:", data);
        
        if (data.status === 'accepted') {
            const emergencyData = { ...currentEmergency }; // Save copy
            hideEmergencyPopup(); 
            try {
                startMission(emergencyData); // Start with saved data
            } catch (e) {
                console.error("Error starting mission:", e);
            }
            alert('Emergency accepted! Moving toward patient...');
        } else {
            alert('Error accepting emergency: ' + (data.message || 'Unknown error'));
            acceptBtn.disabled = false;
            declineBtn.disabled = false;
            acceptBtn.textContent = '✅ ACCEPT';
        }
    })
    .catch(error => {
        console.error('Error accepting emergency:', error);
        alert('Error accepting emergency. Please try again.');
        acceptBtn.disabled = false;
        declineBtn.disabled = false;
        acceptBtn.textContent = '✅ ACCEPT';
    });
}

function declineEmergency() {
    if (!currentEmergency) return; // Silent return if no emergency
    
    console.log("❌ Declining emergency:", currentEmergency.emergency_id);
    
    // Disable buttons during processing
    const acceptBtn = document.querySelector('.accept-btn');
    const declineBtn = document.querySelector('.decline-btn');
    acceptBtn.disabled = true;
    declineBtn.disabled = true;
    declineBtn.textContent = 'Processing...';
    
    fetch(`${BASE_URL}/api/decline_emergency`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({ emergency_id: currentEmergency.emergency_id })
    })
    .then(response => response.json())
    .then(data => {
        console.log("📨 Decline emergency response:", data);
        
        if (data.status === 'declined') {
            hideEmergencyPopup();
            // Reset status to available
            document.getElementById('status-indicator').textContent = 'Available';
            document.getElementById('status-indicator').className = 'status-indicator status-available';
            alert('Emergency declined. Waiting for next assignment.');
        } else {
            alert('Error declining emergency: ' + (data.message || 'Unknown error'));
            acceptBtn.disabled = false;
            declineBtn.disabled = false;
            declineBtn.textContent = '❌ DECLINE';
        }
    })
    .catch(error => {
        console.error('Error declining emergency:', error);
        alert('Error declining emergency. Please try again.');
        acceptBtn.disabled = false;
        declineBtn.disabled = false;
        declineBtn.textContent = '❌ DECLINE';
    });
}

function startMission(emergency) {

    console.log("🎯 Starting mission:", emergency);

    currentMission = emergency;

    document.getElementById("pickup-section").style.display = "block";
    document.getElementById('mission-info').style.display = 'block';

    document.getElementById('status-indicator').textContent = 'On Mission - Pickup';
    document.getElementById('status-indicator').className = 'status-indicator status-busy';

    // Update mission details in Sidebar
    document.getElementById('mission-patient').textContent = emergency.patient_name || 'Rahul Sharma';
    document.getElementById('mission-age').textContent = emergency.patient_age || '45';
    document.getElementById('mission-mobile').textContent = emergency.patient_mobile || '9876543210';
    document.getElementById('mission-emergency').textContent = 'Medical Emergency';

    document.getElementById('your-status').textContent = 'On Mission - Pickup';

    // ⭐ Set patient marker
    const pLoc = emergency.patient_location || { lat: emergency.lat, lon: emergency.lon };
    setPatientLocation(pLoc, emergency.patient_name);

    // Center map to see both driver and patient
    if (ambulanceMarker && patientMarker) {
        const group = L.featureGroup([ambulanceMarker, patientMarker]);
        map.fitBounds(group.getBounds().pad(0.2));
    }

    // ⭐ ROUTE CALCULATION - Waypoints must be valid numbers
    if (currentLocation.lat && currentLocation.lon && pLoc.lat && pLoc.lon) {
        calculateRouteToPatient(pLoc);
    } else {
        console.error("❌ Cannot calculate route: Missing coordinates", currentLocation, pLoc);
    }
}

function calculateRouteToPatient(patientLocation) {
console.log("🚑 Driver location:", currentLocation);
console.log("📍 Patient location:", patientLocation);
    if (routingControl) {
        map.removeControl(routingControl);
        routingControl = null;
    }

    console.log("🛣️ Calculating route to patient...");

    routingControl = L.Routing.control({
        waypoints: [
            L.latLng(currentLocation.lat, currentLocation.lon),
            L.latLng(patientLocation.lat, patientLocation.lon)
        ],
        routeWhileDragging: false,
        show: false,
        addWaypoints: false,
        draggableWaypoints: false,
        itinerary: { containerClassName: 'hidden' }, // Hide the panel
        lineOptions: {
            styles: [{ color: '#e74c3c', opacity: 0.8, weight: 6 }]
        },
        createMarker: function(i, waypoint, n) {
            return null;
        }

    }).addTo(map);


    routingControl.on('routesfound', function(e) {

        const route = e.routes[0];

        const distance = (route.summary.totalDistance / 1000).toFixed(1);
        const time = Math.round(route.summary.totalTime / 60);

        console.log(`📍 Route calculated: ${distance} km, ${time} minutes`);
        document.getElementById('mission-distance').textContent =
            `${Math.round(route.summary.totalDistance)}m - ${time} min`;

        routeCoordinates = route.coordinates;
        movementIndex = 0;

        startAmbulanceMovement('patient');
    });

}

function startAmbulanceMovement(targetType){
    if(!routeCoordinates || routeCoordinates.length === 0) return;

    isMoving = true; // Block real GPS updates
    if(movementInterval) clearInterval(movementInterval);

    console.log("🚕 Simulation Started: Realistic speed (2-3 mins trip)...");

    movementInterval = setInterval(()=>{
        if(movementIndex >= routeCoordinates.length){
            clearInterval(movementInterval);
            movementInterval = null;
            if(targetType === 'patient') {
                document.getElementById('mission-distance').textContent = "Arrived at Patient!";
            } else if(targetType === 'hospital') {
                isMoving = false; // Mission done
                arrivedAtHospital();
            }
            return;
        }

        const point = routeCoordinates[movementIndex];
        currentLocation = { lat: point.lat, lon: point.lng };

        if(ambulanceMarker) {
            ambulanceMarker.setLatLng([point.lat, point.lng]);
            map.panTo([point.lat, point.lng]); // Follow the car
        }

        sendLocationToServer(currentLocation);

        // REALISTIC SPEED: Only 1 step every 2 seconds means a realistic 2-3 min journey
        movementIndex += 1; 

    }, 2000); 
}


function startPatientArrivalCheck() {
    const arrivalCheck = setInterval(() => {
        if (!currentMission || !currentLocation || !patientMarker) return;
        
        const patientLoc = patientMarker.getLatLng();
        const distance = calculateDistance(
            currentLocation.lat, currentLocation.lon,
            patientLoc.lat, patientLoc.lng
        );
        
        // Keep showing routing distance, but check straight-line for arrival
        if (distance <= 50) {
            clearInterval(arrivalCheck);
            // Notify driver they have arrived, let them click the button
        }
    }, 3000);
}

function patientPickedUp() {
    console.log("✅ Patient picked up");
    const pickupBtn = document.querySelector('#pickup-section button');
    if(pickupBtn) {
        pickupBtn.textContent = 'Processing...';
        pickupBtn.disabled = true;
    }

    fetch(`${BASE_URL}/api/patient_picked_up`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ emergency_id: currentMission.emergency_id })
    })
    .then(res => res.json())
    .then(data => {
        if(data.status === "success"){
            alert("✅ Patient Picked Up confirmed! Traffic signals are now turning GREEN for your route.");
            activateEmergencyMode();

            document.getElementById('status-indicator').textContent = 'On Mission - To Hospital';
            document.getElementById('your-status').textContent = 'On Mission - To Hospital';
            document.getElementById('mission-distance').textContent = 'Patient picked up - Select hospital';
            
            document.getElementById("pickup-section").style.display = "none";
            document.getElementById('hospital-search-section').style.display = 'block';

            clearRoute();
            searchHospitals();
        } else {
            alert("Server error confirming pickup");
        }
    })
    .catch(error => {
        console.error("Patient pickup error:", error);
        alert("Server error. Please try again.");
    })
    .finally(() => {
        if(pickupBtn) {
            pickupBtn.textContent = '✅ Patient Picked Up';
            pickupBtn.disabled = false;
        }
    });
}

// REAL OSM Hospital Search Functions
function searchHospitals() {
    const searchInput = document.getElementById('hospital-search');
    const query = searchInput.value.trim();
    
    // Automatically search for 'hospital' if query is empty
    const effectiveQuery = query || "hospital";
    
    console.log("🏥 Searching hospitals on OSM:", effectiveQuery);
    
    // Show loading
    const searchBtn = document.querySelector('.search-hospital-btn');
    const originalText = searchBtn ? searchBtn.textContent : "Search";
    if(searchBtn) {
        searchBtn.textContent = 'Searching OSM...';
        searchBtn.disabled = true;
    }
    
    // Clear previous results
    document.getElementById('hospital-list').innerHTML = '<div class="no-results">Searching OpenStreetMap...</div>';
    
    // Use Nominatim API for OSM search
    searchOSMHospitals(effectiveQuery)
        .then(hospitals => {
            displayHospitals(hospitals);
            searchBtn.textContent = originalText;
            searchBtn.disabled = false;
        })
        .catch(error => {
            console.error('OSM Search error:', error);
            document.getElementById('hospital-list').innerHTML = '<div class="no-results">Error searching hospitals. Please try again.</div>';
            searchBtn.textContent = originalText;
            searchBtn.disabled = false;
        });
}

function searchOSMHospitals(query) {
    // If query is empty, just search for any hospital
    const searchQuery = query || "hospital";
    
    // Support both flattened and nested location structure
    let lat = currentLocation.lat;
    let lon = currentLocation.lon;

    if (currentMission) {
        if (currentMission.lat) {
            lat = currentMission.lat;
            lon = currentMission.lon;
        } else if (currentMission.patient_location) {
            lat = currentMission.patient_location.lat;
            lon = currentMission.patient_location.lon;
        }
    }

    console.log(`🏥 Searching hospitals near ${lat}, ${lon} via Backend API...`);

    // Call Backend API instead of Overpass directly
    return fetch(`${BASE_URL}/api/nearby_hospitals?lat=${lat}&lon=${lon}`)
    .then(response => response.json())
    .then(data => {
        if (data.status === 'success') {
            console.log("🏥 Search results from backend:", data.hospitals);
            
            // Map backend data to frontend format
            const hospitals = data.hospitals.map(h => {
                const hLat = h.location.lat;
                const hLon = h.location.lon;
                
                // Calculate distance locally for UI
                let distStr = 'Unknown';
                if (lat && lon) {
                    const dist = calculateDistance(lat, lon, hLat, hLon);
                    distStr = (dist / 1000).toFixed(1) + ' km';
                }
                
                return {
                    name: h.name,
                    address: h.address,
                    distance: distStr,
                    location: { lat: hLat, lon: hLon }
                };
            });
            
            // If user entered a specific query, filter by name
            if (query) {
                return hospitals.filter(h => h.name.toLowerCase().includes(query.toLowerCase()));
            }

            return hospitals.sort((a,b) => parseFloat(a.distance) - parseFloat(b.distance));
        } else {
            throw new Error(data.message || 'Error fetching hospitals from backend');
        }
    });
}

function displayHospitals(hospitals) {
    const hospitalList = document.getElementById('hospital-list');
    hospitalList.innerHTML = '';
    
    // Clear existing search markers
    hospitalSearchMarkers.forEach(m => map.removeLayer(m));
    hospitalSearchMarkers = [];
    
    if (hospitals.length === 0) {
        hospitalList.innerHTML = '<div class="no-results">No hospitals found. Try a different search.</div>';
        return;
    }
    
    const bounds = [];
    if (currentLocation) bounds.push([currentLocation.lat, currentLocation.lon]);
    
    hospitals.forEach((hospital, index) => {
        // Add to Sidebar
        const hospitalItem = document.createElement('div');
        hospitalItem.className = 'hospital-item';
        hospitalItem.innerHTML = `
            <div class="hospital-info">
                <strong>🏥 ${hospital.name}</strong>
                <div class="hospital-address">📍 ${hospital.address}</div>
                <div class="hospital-distance">📏 ${hospital.distance} away</div>
            </div>
            <button class="select-hospital-btn" onclick="selectHospital(${index})">Select</button>
        `;
        hospitalItem.dataset.hospitalData = JSON.stringify(hospital);
        hospitalList.appendChild(hospitalItem);

        // Add to Map as temporary marker
        const hMarker = L.marker([hospital.location.lat, hospital.location.lon], {
            icon: L.icon({
                iconUrl: 'https://cdn-icons-png.flaticon.com/512/2311/2311545.png', // Hospital icon
                iconSize: [30, 30]
            })
        })
        .addTo(map)
        .bindPopup(`
            <strong>${hospital.name}</strong><br>
            ${hospital.distance} away<br>
            <button onclick="setHospitalDestination(${JSON.stringify(hospital).replace(/"/g, '&quot;')})" 
                    style="margin-top:5px; padding:5px; background:#2196F3; color:white; border:none; border-radius:4px; cursor:pointer;">
                Select this Hospital
            </button>
        `);
        
        hospitalSearchMarkers.push(hMarker);
        bounds.push([hospital.location.lat, hospital.location.lon]);
    });

    // Fit map to show all options
    if (bounds.length > 0) {
        map.fitBounds(bounds, { padding: [50, 50] });
    }
}

function selectHospital(index) {
    const hospitalItems = document.querySelectorAll('.hospital-item');
    if (hospitalItems[index]) {
        const hospitalData = JSON.parse(hospitalItems[index].dataset.hospitalData);
        setHospitalDestination(hospitalData);
    }
}

function setHospitalDestination(hospital) {
    console.log("🎯 Setting hospital destination:", hospital);
    
    // Clear all search markers
    hospitalSearchMarkers.forEach(m => map.removeLayer(m));
    hospitalSearchMarkers = [];
    
    // Remove existing hospital marker
    if (hospitalMarker) {
        map.removeLayer(hospitalMarker);
    }
    
    // Add hospital marker
    hospitalMarker = L.marker([hospital.location.lat, hospital.location.lon])
        .addTo(map)
        .bindPopup(`🏥 ${hospital.name}<br>${hospital.address}<br><strong>Hospital Destination</strong>`)
        .openPopup();
    
    // Add blue circle around hospital
    L.circle([hospital.location.lat, hospital.location.lon], {
        color: 'blue',
        fillColor: '#007bff',
        fillOpacity: 0.1,
        radius: 200
    }).addTo(map);
    
    // 📡 SYNC WITH SERVER so patient can see the destination
    fetch(`${BASE_URL}/api/assign_hospital`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            emergency_id: currentMission.emergency_id,
            lat: hospital.location.lat,
            lon: hospital.location.lon,
            name: hospital.name
        })
    });
    
    // Update mission status
    document.getElementById('status-indicator').textContent = 'On Mission - To Hospital';
    document.getElementById('your-status').textContent = 'On Mission - To Hospital';
    
    // Update mission info with hospital details
    document.getElementById('mission-distance').textContent = `Going to ${hospital.name}`;
    
    // Show hospital destination in mission info
    const missionDetails = document.querySelector('.mission-details');
    if (!document.getElementById('hospital-destination')) {
        const hospitalElement = document.createElement('p');
        hospitalElement.id = 'hospital-destination';
        hospitalElement.innerHTML = `<strong>Hospital:</strong> ${hospital.name}`;
        missionDetails.appendChild(hospitalElement);
    } else {
        document.getElementById('hospital-destination').innerHTML = `<strong>Hospital:</strong> ${hospital.name}`;
    }
    
    // ADDED: Calculate route to hospital
    calculateRouteToHospital(hospital.location);
    
    // Start hospital arrival check
    startHospitalArrivalCheck(hospital);
    
    alert(`Hospital set: ${hospital.name}\nRoute to hospital calculated.`);
}

// ADDED: Function to calculate route to hospital
function calculateRouteToHospital(hospitalLocation) {
    // Clear any existing route
    if (routingControl) {
        map.removeControl(routingControl);
        routingControl = null;
    }
    
    console.log("🛣️ Calculating route to hospital...");
    
    routingControl = L.Routing.control({
        waypoints: [
            L.latLng(currentLocation.lat, currentLocation.lon),
            L.latLng(hospitalLocation.lat, hospitalLocation.lon)
        ],
        routeWhileDragging: false,
        show: false,
        addWaypoints: false,
        draggableWaypoints: false,
        itinerary: { containerClassName: 'hidden' }, // Hide the panel
        lineOptions: {
            styles: [{ color: '#2196F3', opacity: 0.8, weight: 6 }]
        },
        createMarker: function(i, waypoint, n) {
            return null;
        }
    }).addTo(map);
    
    routingControl.on('routesfound', function(e) {
        const routes = e.routes;
        const route = routes[0];
        const distance = (route.summary.totalDistance / 1000).toFixed(1);
        const time = Math.round(route.summary.totalTime / 60);
        
        console.log(`🏥 Route to hospital: ${distance} km, ${time} minutes`);
        
        // Send hospital routing distance to server
        sendRoutingDistanceToServer(route.summary.totalDistance, time);
        
        document.getElementById('mission-distance').textContent = 
            `${Math.round(route.summary.totalDistance)}m to hospital - ${time} min`;

        // ⭐ START MOVEMENT TO HOSPITAL
        routeCoordinates = route.coordinates;
        movementIndex = 0;
        startAmbulanceMovement('hospital');
    });
}

function startHospitalArrivalCheck(hospital) {
    const arrivalCheck = setInterval(() => {
        if (!currentMission || !currentLocation || !hospitalMarker) return;
        
        const hospitalLoc = hospitalMarker.getLatLng();
        const distance = calculateDistance(
            currentLocation.lat, currentLocation.lon,
            hospitalLoc.lat, hospitalLoc.lng
        );
        
        // Update distance display
        if (document.getElementById('hospital-destination')) {
            document.getElementById('hospital-destination').innerHTML = 
                `<strong>Hospital:</strong> ${hospital.name} (${Math.round(distance)}m away)`;
        }
        
        // If within 20 meters, consider arrived at hospital
        if (distance <= 20) {
            clearInterval(arrivalCheck);
            arrivedAtHospital();
        }
    }, 3000);
}

function arrivedAtHospital() {
    console.log("🎉 Arrived at hospital");
    // ADDED: Clear route when mission completed
    clearRoute();
    completeMission();
}

function calculateDistance(lat1, lon1, lat2, lon2) {
    const R = 6371000; // Earth radius in meters
    const phi1 = lat1 * Math.PI / 180;
    const phi2 = lat2 * Math.PI / 180;
    const deltaPhi = (lat2 - lat1) * Math.PI / 180;
    const deltaLambda = (lon2 - lon1) * Math.PI / 180;
    
    const a = Math.sin(deltaPhi/2) * Math.sin(deltaPhi/2) +
              Math.cos(phi1) * Math.cos(phi2) *
              Math.sin(deltaLambda/2) * Math.sin(deltaLambda/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    
    return R * c;
}

function completeMission() {
    console.log("🎉 Completing mission");
    
    // ADDED: Clear route when mission completed
    clearRoute();
    
    // driver_id is read from server session – no need to send it in body
    fetch(`${BASE_URL}/api/complete_mission`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ emergency_id: currentMission?.emergency_id })
    })
    .then(response => response.json())
    .then(data => {
        console.log("📨 Complete mission response:", data);
        
        if (data.status === 'completed') {
            // Show completion modal
            document.getElementById('final-fare').textContent = `₹${data.fare}`;
            document.getElementById('mission-complete-modal').style.display = 'flex';

            // Reset everything
            document.getElementById('mission-info').style.display = 'none';
            document.getElementById('hospital-search-section').style.display = 'none';
            document.getElementById('status-indicator').textContent = 'Mission Completed';
            document.getElementById('status-indicator').className = 'status-indicator status-available';
            document.getElementById('your-status').textContent = 'Mission Completed';
            
            setTimeout(() => {
                document.getElementById('status-indicator').textContent = 'Available';
                document.getElementById('your-status').textContent = 'Available';
            }, 8000);
            
            // Clear markers
            if (patientMarker) {
                map.removeLayer(patientMarker);
                patientMarker = null;
            }
            if (hospitalMarker) {
                map.removeLayer(hospitalMarker);
                hospitalMarker = null;
            }
            
            // Clear search
            document.getElementById('hospital-search').value = '';
            document.getElementById('hospital-list').innerHTML = '';
            
            currentMission = null;
            currentEmergency = null;
            
            updateSystemStatus();
        } else {
            alert('Error completing mission: ' + (data.message || 'Unknown error'));
        }
    })
    .catch(error => {
        console.error('Error completing mission:', error);
        alert('Error completing mission. Please try again.');
    });
}

function setPatientLocation(location, patientName) {

    console.log("📍 Setting patient location:", location, patientName);

    // Safeguard keys (support both lat/lon and latitude/longitude)
    const lat = location.lat || location.latitude;
    const lon = location.lon || location.longitude;

    if (lat === undefined || lon === undefined || lat === null || lon === null) {
        console.error("❌ Invalid patient location:", location);
        return;
    }

    if (patientMarker) {
        map.removeLayer(patientMarker);
    }

    patientMarker = L.marker([lat, lon])
        .addTo(map)
        .bindPopup(`🚨 ${patientName || 'Patient'}<br>Emergency Location`)
        .openPopup();

    L.circle([lat, lon], {
        color: 'red',
        fillColor: '#f03',
        fillOpacity: 0.1,
        radius: 200
    }).addTo(map);

    if (ambulanceMarker) {
        const group = L.featureGroup([ambulanceMarker, patientMarker]);
        map.fitBounds(group.getBounds().pad(0.1));
    }

    console.log("✅ Patient location set on map");
}

function updateSystemStatus() {
    fetch(`${BASE_URL}/api/get_system_status`)
    .then(response => response.json())
    .then(data => {
        if (data.status !== 'error') {
            const activeElem = document.getElementById('active-count');
            const pendingElem = document.getElementById('pending-count');
            if (activeElem) activeElem.textContent = data.active_ambulances || 0;
            if (pendingElem) pendingElem.textContent = data.pending_emergencies || 0;
        }
    })
    .catch(error => {
        console.error('Error updating system status:', error);
    });
}

function closeMissionModal() {
    console.log("🚪 Closing mission completion modal...");
    document.getElementById('mission-complete-modal').style.display = 'none';
    // Ensure the dashboard is reset to available state
    document.getElementById('status-indicator').textContent = 'Available';
    document.getElementById('status-indicator').className = 'status-indicator status-available';
    document.getElementById('your-status').textContent = 'Available';
    
    // Clear any remaining markers just in case
    if (patientMarker) map.removeLayer(patientMarker);
    if (hospitalMarker) map.removeLayer(hospitalMarker);
    patientMarker = null;
    hospitalMarker = null;
    
    // Resume polling for new emergencies
    startEmergencyPolling();
}

function centerOnAmbulance() {
    if (ambulanceMarker) {
        map.setView(ambulanceMarker.getLatLng(), 16);
    } else if (currentLocation) {
        map.setView([currentLocation.lat, currentLocation.lon], 16);
    }
}

function checkCurrentMission() {
    console.log("🔍 Checking for current active mission...");
    fetch(`${BASE_URL}/api/get_current_mission`)
    .then(res => res.json())
    .then(data => {
        if(data.status === 'success' && data.mission) {
            console.log("♻️ Resuming active mission:", data.mission);
            startMission(data.mission);
        }
    })
    .catch(err => console.error("Error checking session:", err));
}

// Update system status every 10 seconds
setInterval(updateSystemStatus, 10000);

// Enter key support for login and hospital search
document.getElementById('password').addEventListener('keypress', function(e) {
    if (e.key === 'Enter') {
        login();
    }
});

const hospitalSearch = document.getElementById('hospital-search');

if (hospitalSearch) {
    hospitalSearch.addEventListener('keypress', function(e){
        if(e.key === "Enter"){
            searchHospitals();
        }
    });
}

// -----------------------------
// PATIENT PICKUP 
// -----------------------------

// verifyOTP has been replaced with patientPickedUp logic above

function activateEmergencyMode() {
    if (!currentMission) return;
    
    console.log("📡 Requesting Traffic Signal Preemption Mode...");
    
    fetch(`${BASE_URL}/api/activate_emergency_mode`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ emergency_id: currentMission.emergency_id })
    })
    .then(res => res.json())
    .then(data => {
        if (data.status === "success") {
            console.log("🚦 TRAFFIC SIGNAL: GREEN MODE ACTIVATED");
        }
    })
    .catch(err => console.error("Hardware activation error:", err));
}