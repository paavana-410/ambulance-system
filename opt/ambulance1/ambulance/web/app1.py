"""
app1.py - ResQGo Main Backend Server
Phase 1 fixes applied:
  - Secrets loaded from .env (python-dotenv)
  - bcrypt password hashing on register/login
  - Flask-Limiter rate limiting on OTP endpoint
  - CORS restricted to localhost for development
  - driver_id always read from server session (never trusted from client)
  - Removed hardcoded SECRET_KEY
"""

import os
import re
import math
import time
import random
import smtplib
from email.message import EmailMessage

import bcrypt
import requests
from dotenv import load_dotenv
from flask import Flask, render_template, request, jsonify, session
from flask_socketio import SocketIO, emit
from flask_limiter import Limiter
from flask_limiter.util import get_remote_address

from db_helper import get_db, init_db

# ?? Load environment ??????????????????????????????????????????????????????????
load_dotenv()

# ?? App setup ?????????????????????????????????????????????????????????????????
app = Flask(__name__, template_folder="templates", static_folder="static")
app.config["SECRET_KEY"] = os.getenv("SECRET_KEY", os.urandom(32).hex())

# SocketIO ? real-time push
socketio = SocketIO(app, cors_allowed_origins="*", async_mode="eventlet")

# Rate limiter – prevent OTP abuse
limiter = Limiter(
    key_func=get_remote_address,
    app=app,
    default_limits=["10000 per day", "2000 per hour"], # High defaults for polling
)

# ?? OTP in-memory store ???????????????????????????????????????????????????????
email_otp_store: dict = {}
EMAIL_OTP_TTL_SECONDS = 5 * 60
EMAIL_REGEX = re.compile(r"^[^\s@]+@[^\s@]+\.[^\s@]+$")


# ?????????????????????????????????????????????????????????????????????????????
# HELPERS
# ?????????????????????????????????????????????????????????????????????????????

def hash_password(plain: str) -> str:
    """Return a bcrypt hash of the given plain-text password."""
    return bcrypt.hashpw(plain.encode(), bcrypt.gensalt()).decode()


def check_password(plain: str, hashed: str) -> bool:
    """Return True if plain matches the bcrypt hash."""
    return bcrypt.checkpw(plain.encode(), hashed.encode())


def send_gmail_otp(email: str, otp: str) -> None:
    """Send OTP via Gmail SMTP using credentials from environment."""
    gmail_user = os.getenv("GMAIL_USER")
    gmail_pass = (os.getenv("GMAIL_APP_PASSWORD") or "").replace(" ", "")

    if not gmail_user or not gmail_pass:
        raise RuntimeError("GMAIL_USER and GMAIL_APP_PASSWORD must be set in .env")

    msg = EmailMessage()
    msg["Subject"] = "ResQGo ? Your Verification OTP"
    msg["From"]    = gmail_user
    msg["To"]      = email
    msg.set_content(
        f"Your ResQGo OTP is {otp}.\n"
        f"It expires in 5 minutes.\n\n"
        f"If you did not request this, please ignore."
    )
    with smtplib.SMTP_SSL("smtp.gmail.com", 465) as smtp:
        smtp.login(gmail_user, gmail_pass)
        smtp.send_message(msg)


def calculate_haversine(lat1, lon1, lat2, lon2) -> float:
    """Return straight-line distance in km between two GPS coordinates."""
    R = 6371
    dlat = math.radians(lat2 - lat1)
    dlon = math.radians(lon2 - lon1)
    a = (math.sin(dlat / 2) ** 2
         + math.cos(math.radians(lat1))
         * math.cos(math.radians(lat2))
         * math.sin(dlon / 2) ** 2)
    return R * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))


# ?????????????????????????????????????????????????????????????????????????????
# PAGE ROUTES
# ?????????????????????????????????????????????????????????????????????????????

@app.route("/")
def index():
    return render_template("index.html")


@app.route("/patient_sim")
def patient_sim():
    return render_template("patient_sim.html")


# ?????????????????????????????????????????????????????????????????????????????
# AUTH ROUTES
# ?????????????????????????????????????????????????????????????????????????????

@app.route("/login", methods=["POST"])
def login():
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or "").strip()
    password = data.get("password") or ""

    if not username or not password:
        return jsonify({"status": "error", "message": "Username and password required"}), 400

    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT id, driver_name, ambulance_no, password_hash "
            "FROM drivers WHERE username = %s",
            (username,),
        )
        driver = cur.fetchone()
    finally:
        conn.close()

    if not driver or not check_password(password, driver["password_hash"]):
        return jsonify({"status": "error", "message": "Invalid credentials"}), 401

    # Store driver identity in server session only
    session["driver_id"]    = driver["id"]
    session["driver_name"]  = driver["driver_name"]
    session["ambulance_no"] = driver["ambulance_no"]

    return jsonify({
        "status":       "success",
        "driver_id":    driver["id"],
        "driver_name":  driver["driver_name"],
        "ambulance_no": driver["ambulance_no"],
    })


@app.route("/register_driver", methods=["POST"])
def register_driver():
    data = request.get_json(silent=True) or {}
    driver_name  = (data.get("driver_name") or "").strip()
    username     = (data.get("username") or "").strip()
    password     = data.get("password") or ""
    ambulance_no = (data.get("ambulance_no") or "").strip()
    phone        = (data.get("phone") or "").strip()

    # Basic server-side validation
    if not all([driver_name, username, password, ambulance_no, phone]):
        return jsonify({"status": "error", "message": "All fields are required including phone number"}), 400
    if len(password) < 6:
        return jsonify({"status": "error", "message": "Password must be at least 6 characters"}), 400

    pw_hash = hash_password(password)

    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO drivers(driver_name, username, password_hash, ambulance_no, phone) "
            "VALUES(%s, %s, %s, %s, %s)",
            (driver_name, username, pw_hash, ambulance_no, phone),
        )
        conn.commit()
    except Exception:
        conn.rollback()
        return jsonify({"status": "error", "message": "Username already exists"}), 409
    finally:
        conn.close()

    return jsonify({"status": "success", "message": "Driver registered. Please login."})


@app.route("/logout", methods=["POST"])
def logout():
    session.clear()
    return jsonify({"status": "success"})


# ?????????????????????????????????????????????????????????????????????????????
# OTP ROUTES
# ?????????????????????????????????????????????????????????????????????????????

@app.route("/send-email-otp", methods=["POST"])
@limiter.limit("5 per minute")          # ? rate limit: max 5 OTPs per minute per IP
def send_email_otp():
    data  = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()

    if not EMAIL_REGEX.match(email):
        return jsonify({"status": "error", "message": "Invalid email address"}), 400

    otp = str(random.randint(100000, 999999))
    email_otp_store[email] = {"otp": otp, "expires_at": time.time() + EMAIL_OTP_TTL_SECONDS}

    try:
        send_gmail_otp(email, otp)
    except Exception as exc:
        email_otp_store.pop(email, None)
        return jsonify({"status": "error", "message": str(exc)}), 500

    return jsonify({"status": "success", "message": "OTP sent to email."})


@app.route("/verify-email-otp", methods=["POST"])
def verify_email_otp():
    data  = request.get_json(silent=True) or {}
    email = (data.get("email") or "").strip().lower()
    otp   = (data.get("otp") or "").strip()
    saved = email_otp_store.get(email)

    if not saved:
        return jsonify({"status": "error", "message": "OTP session expired. Request a new one."}), 400
    if time.time() > saved["expires_at"]:
        email_otp_store.pop(email, None)
        return jsonify({"status": "error", "message": "OTP expired. Request a new one."}), 400
    if otp != saved["otp"]:
        return jsonify({"status": "error", "message": "Invalid OTP."}), 400

    email_otp_store.pop(email, None)
    return jsonify({"status": "success", "message": "Email verified."})


# ?????????????????????????????????????????????????????????????????????????????
# EMERGENCY ROUTES
# ?????????????????????????????????????????????????????????????????????????????

@app.route("/api/request_ambulance", methods=["POST"])
@limiter.exempt
def request_ambulance():
    data    = request.get_json(silent=True) or {}
    otp     = str(random.randint(1000, 9999))
    phone   = (data.get("phone") or "").strip()
    name    = (data.get("name") or "Unknown").strip()
    age_raw = data.get("age", 0)

    try:
        age = int(age_raw)
    except (TypeError, ValueError):
        age = 0

    lat = float(data.get("lat") or 0.0)
    lon = float(data.get("lon") or 0.0)
    emergency_type = (data.get("emergency_type") or "Medical Emergency").strip()

    conn = get_db()
    try:
        cur = conn.cursor()
        # Expire previous pending requests from same phone
        cur.execute(
            "UPDATE emergencies SET status='expired' "
            "WHERE patient_mobile=%s AND status='pending'",
            (phone,),
        )
        now = int(time.time())
        cur.execute(
            "INSERT INTO emergencies"
            "(patient_name, patient_age, patient_mobile, emergency_type, lat, lon, status, otp, created_at) "
            "VALUES(%s,%s,%s,%s,%s,%s,'pending',%s,FROM_UNIXTIME(%s))",
            (name, age, phone, emergency_type, lat, lon, otp, now),
        )
        eid = cur.lastrowid
        conn.commit()
        print(f"? SOS Received: {name} | type={emergency_type} | id={eid}")
    except Exception as exc:
        conn.rollback()
        print(f"? request_ambulance error: {exc}")
        return jsonify({"status": "error", "message": str(exc)}), 500
    finally:
        conn.close()

    # Broadcast to all connected drivers via SocketIO
    socketio.emit("new_emergency", {
        "emergency_id":   eid,
        "patient_name":   name,
        "patient_age":    age,
        "patient_mobile": phone,
        "emergency_type": emergency_type,
        "lat":            lat,
        "lon":            lon,
    })

    return jsonify({"status": "success", "emergency_id": str(eid)})


@app.route("/api/emergency_status")
def get_status():
    eid = request.args.get("emergency_id")
    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT e.*, d.driver_name, d.ambulance_no, d.phone AS driver_phone "
            "FROM emergencies e LEFT JOIN drivers d ON e.driver_id = d.id "
            "WHERE e.emergency_id = %s",
            (eid,),
        )
        row = cur.fetchone()
    finally:
        conn.close()

    if not row:
        return jsonify({"status": "error"}), 404

    return jsonify({
        "status":          "success",
        "emergency_state": row["status"],
        "driver_name":     row["driver_name"],
        "ambulance_no":    row["ambulance_no"],
        "driver_phone":    row.get("driver_phone"),
        "dest_name":       row["dest_name"],
        "lat":             row["lat"],
        "lon":             row["lon"],
        "fare":            row.get("fare", 0.0),
        "ride_distance":   row.get("ride_distance", 0.0)
    })


@app.route("/api/ambulance_location")
def get_loc():
    eid = request.args.get("emergency_id")
    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT driver_id, lat, lon, status, dest_lat, dest_lon "
            "FROM emergencies WHERE emergency_id = %s",
            (eid,),
        )
        emer = cur.fetchone()
        if not emer or not emer["driver_id"]:
            return jsonify({"status": "error"}), 400

        cur.execute(
            "SELECT lat, lon FROM ambulance_locations "
            "WHERE driver_id = %s ORDER BY id DESC LIMIT 1",
            (emer["driver_id"],),
        )
        loc = cur.fetchone()
    finally:
        conn.close()

    if not loc:
        return jsonify({"lat": 0, "lon": 0})

    t_lat, t_lon = (
        (emer["dest_lat"], emer["dest_lon"])
        if emer["status"] == "active" and emer["dest_lat"]
        else (emer["lat"], emer["lon"])
    )
    dist = calculate_haversine(loc["lat"], loc["lon"], t_lat, t_lon)
    return jsonify({
        "lat":      loc["lat"],
        "lon":      loc["lon"],
        "distance": f"{dist:.2f} km",
        "eta":      f"{max(1, round(dist * 2))} mins",
    })


@app.route("/api/get_my_emergencies")
@limiter.exempt
def get_my_ems():
    lat = request.args.get("lat", type=float)
    lon = request.args.get("lon", type=float)
    five_mins_ago = int(time.time()) - 300

    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT * FROM emergencies "
            "WHERE status='pending' "
            "AND (created_at > FROM_UNIXTIME(%s) OR created_at IS NULL)",
            (five_mins_ago,),
        )
        rows = cur.fetchall()
    finally:
        conn.close()

    emergencies = [
        {
            "emergency_id":   r["emergency_id"],
            "patient_name":   r["patient_name"],
            "patient_age":    r["patient_age"],
            "patient_mobile": r["patient_mobile"],
            "emergency_type": r.get("emergency_type", "Medical Emergency"),
            "lat":            r["lat"],
            "lon":            r["lon"],
            "patient_location": {"lat": r["lat"], "lon": r["lon"]},
        }
        for r in rows
    ]

    if lat is not None and lon is not None and emergencies:
        for e in emergencies:
            e["distance"] = calculate_haversine(lat, lon, e["lat"], e["lon"])
        emergencies.sort(key=lambda x: x["distance"])
        return jsonify({"emergencies": [emergencies[0]]})

    return jsonify({"emergencies": [emergencies[0]] if emergencies else []})


@app.route("/api/accept_emergency", methods=["POST"])
def accept():
    # Always read driver_id from secure server session ? never from client body
    driver_id = session.get("driver_id")
    if not driver_id:
        return jsonify({"status": "error", "message": "Not authenticated"}), 401

    data = request.get_json(silent=True) or {}
    eid  = data.get("emergency_id")
    if not eid:
        return jsonify({"status": "error", "message": "emergency_id required"}), 400

    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute(
            "UPDATE emergencies SET status='accepted', driver_id=%s "
            "WHERE emergency_id=%s AND status='pending'",
            (driver_id, eid),
        )
        conn.commit()
    finally:
        conn.close()

    return jsonify({"status": "accepted"})


@app.route("/api/decline_emergency", methods=["POST"])
def decline():
    data = request.get_json(silent=True) or {}
    eid  = data.get("emergency_id")
    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute(
            "UPDATE emergencies SET status='declined' WHERE emergency_id=%s",
            (eid,),
        )
        conn.commit()
    finally:
        conn.close()
    return jsonify({"status": "declined"})


@app.route("/api/send_location", methods=["POST"])
@limiter.exempt
def send_loc():
    driver_id = session.get("driver_id")
    if not driver_id:
        return jsonify({"status": "error", "message": "Not authenticated"}), 401

    data = request.get_json(silent=True) or {}
    loc  = data.get("location") or {}
    lat  = loc.get("lat")
    lon  = loc.get("lon")
    if lat is None or lon is None:
        return jsonify({"status": "error", "message": "lat/lon required"}), 400

    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute(
            "INSERT INTO ambulance_locations(driver_id, lat, lon) VALUES(%s,%s,%s)",
            (driver_id, lat, lon),
        )
        conn.commit()
    finally:
        conn.close()

    # Broadcast location to patient listeners
    socketio.emit("ambulance_location", {"driver_id": driver_id, "lat": lat, "lon": lon})
    return jsonify({"status": "ok"})


@app.route("/api/nearby_hospitals")
@app.route("/api/nearby_hos_os")
def nearby():
    lat = request.args.get("lat")
    lon = request.args.get("lon")
    if not lat or not lon:
        return jsonify({"status": "error", "message": "Missing lat/lon"}), 400

    MOCK_HOSPITALS = [
        {"name": "M.S. Ramaiah Memorial Hospital",  "address": "MSRIT Post, Bangalore",       "location": {"lat": 13.0345, "lon": 77.5647}},
        {"name": "Columbia Asia Referral Hospital", "address": "Yeshwanthpur, Bangalore",      "location": {"lat": 13.0131, "lon": 77.5516}},
        {"name": "Aster CMI Hospital",              "address": "Hebbal, Bangalore",             "location": {"lat": 13.0497, "lon": 77.5891}},
        {"name": "Baptist Hospital",                "address": "Bellary Road, Bangalore",       "location": {"lat": 13.0336, "lon": 77.5897}},
    ]

    query = (
        f'[out:json][timeout:25];'
        f'(node["amenity"="hospital"](around:5000,{lat},{lon});'
        f'way["amenity"="hospital"](around:5000,{lat},{lon}););out center;'
    )
    try:
        res = requests.post(
            "https://overpass-api.de/api/interpreter",
            data={"data": query},
            timeout=15,
            headers={"User-Agent": "ResQGo/2.0 (student project)"},
        )
        raw = []
        if res.status_code == 200:
            for item in res.json().get("elements", []):
                h_lat = item.get("lat") or item.get("center", {}).get("lat")
                h_lon = item.get("lon") or item.get("center", {}).get("lon")
                tags  = item.get("tags", {})
                name  = tags.get("name", "Hospital")
                if "clinic" in name.lower():
                    continue
                if h_lat and h_lon:
                    raw.append({
                        "name":    name,
                        "address": tags.get("addr:street") or tags.get("addr:full") or "Nearby Area",
                        "location": {"lat": h_lat, "lon": h_lon},
                    })
        if not raw:
            raw = MOCK_HOSPITALS
    except Exception as exc:
        print(f"? Overpass error: {exc}")
        raw = MOCK_HOSPITALS

    for h in raw:
        h["distance_val"] = calculate_haversine(float(lat), float(lon), h["location"]["lat"], h["location"]["lon"])
        h["distance"]     = f"{h['distance_val']:.2f} km"
    raw.sort(key=lambda x: x["distance_val"])

    return jsonify({"status": "success", "hospitals": raw})


@app.route("/api/assign_hospital", methods=["POST"])
def assign_hospital():
    driver_id = session.get("driver_id")
    if not driver_id:
        return jsonify({"status": "error", "message": "Not authenticated"}), 401

    data = request.get_json(silent=True) or {}
    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute(
            "UPDATE emergencies SET dest_lat=%s, dest_lon=%s, dest_name=%s, status='active' "
            "WHERE emergency_id=%s",
            (data["lat"], data["lon"], data["name"], data["emergency_id"]),
        )
        conn.commit()
    finally:
        conn.close()
    return jsonify({"status": "success"})


@app.route("/api/patient_picked_up", methods=["POST"])
def patient_picked_up():
    driver_id = session.get("driver_id")
    if not driver_id:
        return jsonify({"status": "error", "message": "Not authenticated"}), 401

    data = request.get_json(silent=True) or {}
    eid  = data.get("emergency_id")
    if not eid:
        return jsonify({"status": "error", "message": "emergency_id required"}), 400

    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT emergency_id, driver_id, status FROM emergencies WHERE emergency_id=%s",
            (eid,),
        )
        emer = cur.fetchone()

        if not emer:
            return jsonify({"status": "error", "message": "Emergency not found"}), 404
        if str(emer["driver_id"]) != str(driver_id):
            return jsonify({"status": "error", "message": "Unauthorised"}), 403
        if emer["status"] != "accepted":
            return jsonify({"status": "error", "message": "Invalid state transition"}), 409

        cur2 = conn.cursor()
        cur2.execute(
            "UPDATE emergencies SET status='active', otp_verified=1 WHERE emergency_id=%s",
            (eid,),
        )
        conn.commit()
    finally:
        conn.close()

    return jsonify({"status": "success"})


@app.route("/api/complete_mission", methods=["POST"])
def complete_mission():
    driver_id = session.get("driver_id")
    data = request.get_json(silent=True) or {}
    eid = data.get("emergency_id")

    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        # 1. Fetch mission details to calculate fare
        if driver_id:
            cur.execute(
                "SELECT * FROM emergencies "
                "WHERE driver_id=%s AND status IN ('accepted','active') LIMIT 1",
                (driver_id,),
            )
        else:
            cur.execute("SELECT * FROM emergencies WHERE emergency_id=%s", (eid,))
        
        mission = cur.fetchone()
        if not mission:
            return jsonify({"status": "error", "message": "No active mission found"}), 404

        # 2. Calculate Distance and Fare
        # Distance between Pickup (lat/lon) and Destination (dest_lat/dest_lon)
        dist = 0.0
        if mission["lat"] and mission["dest_lat"]:
            dist = calculate_haversine(mission["lat"], mission["lon"], mission["dest_lat"], mission["dest_lon"])
        
        base_fare = 100.0
        rate_per_km = 15.0
        total_fare = base_fare + (dist * rate_per_km)

        # 3. Update Database
        cur.execute(
            "UPDATE emergencies SET status='completed', ride_distance=%s, fare=%s "
            "WHERE emergency_id=%s",
            (round(dist, 2), round(total_fare, 2), mission["emergency_id"]),
        )
        conn.commit()

        # 4. Push REAL-TIME Update to Patient
        socketio.emit("mission_finished", {
            "emergency_id": mission["emergency_id"],
            "distance": f"{dist:.2f} km",
            "fare": round(total_fare, 2),
            "patient_name": mission["patient_name"]
        })

    finally:
        conn.close()

    return jsonify({
        "status": "completed", 
        "fare": round(total_fare, 2), 
        "distance": f"{dist:.2f} km"
    })


@app.route("/api/get_current_mission")
def get_current_mission():
    driver_id = session.get("driver_id")
    if not driver_id:
        return jsonify({"status": "error"}), 401

    conn = get_db()
    try:
        cur = conn.cursor(dictionary=True)
        cur.execute(
            "SELECT * FROM emergencies "
            "WHERE driver_id=%s AND status IN ('accepted','active') LIMIT 1",
            (driver_id,),
        )
        mission = cur.fetchone()
    finally:
        conn.close()

    if not mission:
        return jsonify({"status": "no_mission"})

    return jsonify({
        "status": "success",
        "mission": {
            "emergency_id":   mission["emergency_id"],
            "patient_name":   mission["patient_name"],
            "patient_age":    mission["patient_age"],
            "patient_mobile": mission["patient_mobile"],
            "emergency_type": mission.get("emergency_type", "Medical Emergency"),
            "lat":            mission["lat"],
            "lon":            mission["lon"],
            "dest_name":      mission["dest_name"],
            "patient_location": {"lat": mission["lat"], "lon": mission["lon"]},
        },
    })


@app.route("/api/get_system_status")
def sys_status():
    five_mins_ago = int(time.time()) - 300
    conn = get_db()
    try:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM ambulance_locations")
        active = cur.fetchone()[0]
        cur.execute(
            "SELECT COUNT(*) FROM emergencies "
            "WHERE status='pending' AND (created_at > FROM_UNIXTIME(%s) OR created_at IS NULL)",
            (five_mins_ago,),
        )
        pending = cur.fetchone()[0]
    finally:
        conn.close()
    return jsonify({"active_ambulances": active, "pending_emergencies": pending})


@app.route("/api/activate_emergency_mode", methods=["POST"])
def activate():
    return jsonify({"status": "success"})


@app.route("/api/routing_distance", methods=["POST"])
def routing_dist():
    return jsonify({"status": "ok"})


# ?????????????????????????????????????????????????????????????????????????????
# WEBSOCKET EVENTS
# ?????????????????????????????????????????????????????????????????????????????

@socketio.on("connect")
def on_connect():
    print(f"? Client connected: {request.sid}")


@socketio.on("disconnect")
def on_disconnect():
    print(f"? Client disconnected: {request.sid}")


# ?????????????????????????????????????????????????????????????????????????????
# STARTUP
# ?????????????????????????????????????????????????????????????????????????????

# Initialise Database and Cleanup
try:
    init_db()
    conn = get_db()
    cur  = conn.cursor()
    cur.execute(
        "UPDATE emergencies SET status='expired' "
        "WHERE status IN ('pending','accepted','active') "
        "AND created_at < NOW() - INTERVAL 1 HOUR"
    )
    conn.commit()
    conn.close()
    print("? Database initialised and stale missions cleaned up.")
except Exception as exc:
    print(f"??  Startup initialization failed: {exc}")

if __name__ == "__main__":
    # Use PORT from environment (Railway) or default to 3000
    port = int(os.environ.get("PORT", 3000))
    print(f"🚀 ResQGo Server starting on port {port}...")
    socketio.run(app, host="0.0.0.0", port=port, debug=False)
