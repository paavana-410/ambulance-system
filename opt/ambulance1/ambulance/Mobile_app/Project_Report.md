# Mini Project Report – Smart Ambulance System

---

## 1. INTRODUCTION

### 1.1 General Introduction
The **Smart Ambulance System** is an end‑to‑end emergency response platform that connects patients, drivers, and hospitals in real time. It leverages a web‑based dashboard for drivers, an Android patient app, and a backend powered by Flask‑MySQL. The system tracks ambulance locations, handles OTP‑based patient verification, and suggests nearby hospitals.

### 1.2 Problem Statement
In many urban areas, ambulance dispatch suffers from delayed response, lack of visibility, and inefficient routing. The project aims to provide a **real‑time, location‑aware** solution that reduces response time and improves coordination between emergency services and hospitals.

### 1.3 Objectives
- Build a responsive driver dashboard (web) with live map tracking.
- Develop a patient Android app for emergency requests and OTP verification.
- Implement a cloud‑ready Flask backend with MySQL for persistence.
- Enable automatic hospital search via OpenStreetMap Nominatim.
- Provide a secure login/registration flow for both drivers and patients.

### 1.4 Project Deliverables
- Web dashboard (HTML/JS/CSS) hosted on Vercel/Render.
- Android patient app (Kotlin Compose) with OTP flow.
- Flask API server with Docker support.
- Database schema and migration scripts.
- Complete documentation (this report, PPT template, user guide).

### 1.5 Current Scope
The MVP covers driver login, emergency request, OTP generation, live tracking, and hospital selection. Advanced features like traffic‑aware routing, analytics, and multi‑language support are planned for future releases.

### 1.6 Future Scope
- Integration with public health APIs for incident reporting.
- AI‑based prediction of ambulance demand hotspots.
- Multi‑language UI (English, Hindi, Kannada).
- Deployment on Kubernetes for high availability.

---

## 2. PROJECT ORGANIZATION

| Component | Description |
|-----------|-------------|
| **Backend** | Flask server (`app.py`) exposing REST endpoints for login, emergency handling, location updates, and hospital search. |
| **Database** | MySQL (`ambulance_system`) with tables `drivers`, `emergencies`, `otp_verifications`, `hospitals`. |
| **Driver Dashboard** | HTML/CSS/JS web app (`index.html`, `app.js`) showing live map, status cards, and emergency popup. |
| **Patient App** | Android Kotlin‑Compose app (`MainActivity.kt`) handling registration, OTP verification, and emergency request. |
| **Documentation** | README, PPT template, this report. |

---

## 3. LITERATURE SURVEY

### 3.1 Introduction
Emergency medical services (EMS) have been studied extensively, focusing on response time reduction, GPS tracking, and automated dispatch.

### 3.2 Related Works
- **Ola EMS** – Uses a ride‑hailing model for ambulance allocation; lacks OTP verification.
- **SmartEMS (IEEE 2022)** – Proposes a cloud‑based dispatch system with real‑time traffic data but requires proprietary hardware.
- **OpenStreetMap‑Based Dispatch** – Demonstrates low‑cost hospital search using OSM Nominatim, similar to our approach.

### 3.3 Conclusion of Survey
Existing solutions either focus on commercial ride‑hailing or require expensive infrastructure. Our system combines **open‑source mapping**, **lightweight OTP verification**, and **cross‑platform UI**, offering a cost‑effective alternative.

---

## 4. PROJECT MANAGEMENT PLAN

### 4.1 Schedule (Gantt Chart)
```
| Phase                | Start   | End     |
|----------------------|---------|---------|
| Requirements         | 01‑Apr  | 07‑Apr  |
| Design               | 08‑Apr  | 14‑Apr  |
| Backend Development  | 15‑Apr  | 25‑Apr  |
| Frontend Dashboard   | 16‑Apr  | 28‑Apr  |
| Android App          | 20‑Apr  | 02‑May  |
| Integration Testing  | 03‑May  | 10‑May  |
| Documentation        | 11‑May  | 15‑May  |
| Final Review         | 16‑May  | 18‑May  |
```
*(The visual Gantt chart can be generated in Excel or any project‑management tool.)*

### 4.2 Risk Identification
| Risk | Impact | Mitigation |
|------|--------|------------|
| Network latency causing delayed location updates | Medium | Use WebSocket (SocketIO) for push updates. |
| OTP delivery failure | High | Provide fallback SMS via Twilio or manual verification. |
| Database schema drift | Medium | Maintain migration script (`migrate_db.py`). |
| Device permission denial (GPS) | Low | Prompt user with clear rationale and fallback to manual location entry. |

---

## 5. SOFTWARE REQUIREMENT SPECIFICATIONS (SRS)

### 5.1 Purpose
To define functional and non‑functional requirements for the Smart Ambulance System, ensuring reliable emergency dispatch and tracking.

### 5.2 Project Scope
The system shall support driver login, emergency creation, OTP verification, live map tracking, hospital search, and status updates.

### 5.3 Overall Description
#### 5.3.1 Product Perspective
The system is a **stand‑alone** solution that integrates with existing hospital databases via a simple REST API.
#### 5.3.2 Product Functions
- **User Authentication** (driver & patient).
- **Emergency Creation** with patient details and GPS coordinates.
- **OTP Generation & Verification** stored in `otp_verifications`.
- **Live Location Streaming** from driver to backend.
- **Hospital Search** using OSM Nominatim.
- **Status Dashboard** showing active, pending, and completed emergencies.
#### 5.3.3 Operating Environment
- Backend: Python 3.11, Flask, MySQL 8.x, Docker (optional).
- Frontend: Modern browsers (Chrome/Firefox) with JavaScript ES6.
- Mobile: Android 8+ (API 26) using Kotlin Compose.
#### 5.3.4 External Interfaces
- **User Interfaces**: Web dashboard UI, Android UI.
- **Hardware Interfaces**: GPS sensor on Android device, network interface for HTTP.
- **Software Interfaces**: REST endpoints (`/login`, `/register_driver`, `/api/request_ambulance`, etc.).

### 5.5 System Features
#### 5.5.1 Functional Requirements
1. **FR‑1**: Driver must authenticate with username/password.
2. **FR‑2**: Patient app must capture name, age, phone, and location.
3. **FR‑3**: System generates a 4‑digit OTP and stores it with a timestamp.
4. **FR‑4**: Driver can accept/decline an emergency; acceptance triggers OTP verification.
5. **FR‑5**: Live location updates are sent every 2 seconds.
6. **FR‑6**: Hospital search returns top 5 nearest hospitals within 10 km.
#### 5.5.2 Non‑functional Requirements
- **Performance**: API response < 200 ms for location queries.
- **Reliability**: 99.5 % uptime for backend services.
- **Security**: HTTPS for all communications; passwords hashed with bcrypt.
- **Usability**: UI follows Material Design guidelines; accessible color contrast.

### 5.6 Use Case Description
#### 5.6.1 Use Case: Request Ambulance
**Actor**: Patient
**Precondition**: Patient app installed and location permission granted.
**Main Flow**:
1. Patient fills form (name, age, phone).
2. App sends POST `/api/request_ambulance`.
3. Backend creates `emergencies` record, generates OTP, returns `emergency_id` and OTP.
4. Patient sees OTP on screen.
5. Driver receives alert, accepts, and verifies OTP.
**Postcondition**: Emergency state changes to `active`.

#### 5.6.2 Use Case: Hospital Assignment
**Actor**: Driver
**Main Flow**:
1. After patient pickup, driver searches hospitals.
2. selects a hospital; app sends `/api/assign_hospital`.
3. Backend updates emergency with destination coordinates.
4. Patient view updates with hospital name.

---

## 6. DESIGN

### 6.1 Architecture Design
```
+-------------------+      +-------------------+      +-------------------+
|   Patient App    | <--->|   Flask Backend   | <--->|   MySQL Database |
+-------------------+      +-------------------+      +-------------------+
        ^  ^                     ^   ^                     ^   ^
        |  |                     |   |                     |   |
   GPS  |  |  HTTP(S)           |   |  REST API           |   |
        |  |                     |   |                     |   |
        |  +---------------------+   +---------------------+   |
        |                                                   |
        +------------------- Web Dashboard -------------------+
```
- **Backend**: Handles authentication, OTP, location persistence, and hospital lookup.
- **Web Dashboard**: JavaScript UI with Leaflet map, SocketIO for real‑time updates.
- **Patient App**: Kotlin‑Compose UI, Retrofit for API calls, fused location provider.

### 6.2 User Interface Design
- **Driver Dashboard**: Dark header, status cards, map panel, emergency popup with Accept/Decline.
- **Patient App**: Clean card UI, OTP display, live map preview, hospital list modal.
- **Consistency**: Primary color `#5f7cff`, accent `#ff4d4d`, rounded corners, subtle shadows.

### 6.3 Low‑Level Design
- **Database Schema** (excerpt):
```sql
CREATE TABLE drivers (
  driver_id INT AUTO_INCREMENT PRIMARY KEY,
  driver_name VARCHAR(255),
  username VARCHAR(100) UNIQUE,
  password_hash VARCHAR(255),
  ambulance_no VARCHAR(20)
);

CREATE TABLE emergencies (
  emergency_id INT AUTO_INCREMENT PRIMARY KEY,
  patient_name VARCHAR(255),
  patient_age INT,
  patient_mobile VARCHAR(20),
  lat DOUBLE,
  lon DOUBLE,
  status ENUM('pending','accepted','active','completed') DEFAULT 'pending',
  driver_id INT,
  otp VARCHAR(10),
  otp_verified TINYINT(1) DEFAULT 0,
  dest_lat DOUBLE,
  dest_lon DOUBLE,
  dest_name VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (driver_id) REFERENCES drivers(driver_id)
);
```
- **Routing Logic**: Leaflet‑Routing‑Machine calculates shortest path; movement simulated on the driver side for demo.
- **OTP Flow**: Generated server‑side, stored with expiry (5 min), validated on driver acceptance.

---

## 7. REFERENCES
1. A.V. Oppenheim and R.W. Schafer, *Digital Signal Processing*, Prentice Hall, 3rd ed., 1975.
2. D. Smith, “Real‑time ambulance dispatch using open‑source GIS,” *Proc. IEEE Conf. on Smart Cities*, vol 71, Aug 2021, pp 1901‑1907.
3. OpenStreetMap Nominatim API Documentation, https://nominatim.org/release-docs/latest/api/Overview/.
4. Flask‑SocketIO Documentation, https://flask-socketio.readthedocs.io/en/latest/.
5. Android Jetpack Compose Guide, https://developer.android.com/jetpack/compose.

---

*Prepared by the Mini Project Team – Dr. Geetha J, Dept. of CSE, MSRIT, Bangalore*
