import mysql.connector
try:
    conn = mysql.connector.connect(host="localhost", user="root", password="", database="ambulance_system")
    cursor = conn.cursor(dictionary=True)
    cursor.execute("SELECT * FROM drivers")
    rows = cursor.fetchall()
    print("Drivers in DB:")
    for r in rows:
        print(r)
    conn.close()
except Exception as e:
    print(e)
