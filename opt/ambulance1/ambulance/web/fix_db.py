import sqlite3

def fix_sqlite():
    print("Checking SQLite...")
    conn = sqlite3.connect('ambulance_system.db')
    c = conn.cursor()
    try:
        c.execute("ALTER TABLE emergencies ADD COLUMN created_at INTEGER")
        print("Added created_at to SQLite")
    except Exception as e:
        print("SQLite column already exists or error:", e)
    conn.commit()
    conn.close()

try:
    import mysql.connector
    try:
        conn = mysql.connector.connect(host="localhost", user="root", password="", database="ambulance_system")
        c = conn.cursor()
        c.execute("ALTER TABLE emergencies ADD COLUMN created_at INTEGER")
        conn.commit()
        print("Added created_at to MySQL")
        conn.close()
    except Exception as e:
        print("MySQL column already exists or error:", e)
except ImportError:
    pass

fix_sqlite()
print("Done!")
