import requests
try:
    res = requests.get("http://127.0.0.1:3000/api/get_my_emergencies")
    print("Pending:", res.json())
except Exception as e:
    print("Error 1", e)

import mysql.connector
try:
    conn = mysql.connector.connect(host="localhost", user="root", password="", database="ambulance_system")
    c = conn.cursor(dictionary=True)
    c.execute("SELECT * FROM emergencies ORDER BY emergency_id DESC LIMIT 1")
    e = c.fetchone()
    if e:
        print("Last Emergency:", e)
        res = requests.get(f"http://127.0.0.1:3000/api/emergency_status?emergency_id={e['emergency_id']}")
        print("Status API:", res.status_code, res.text)
        res2 = requests.get(f"http://127.0.0.1:3000/api/ambulance_location?emergency_id={e['emergency_id']}")
        print("Location API:", res2.status_code, res2.text)
    else:
        print("No emergencies found in DB!")
    conn.close()
except Exception as e:
    print("Error 2", e)
