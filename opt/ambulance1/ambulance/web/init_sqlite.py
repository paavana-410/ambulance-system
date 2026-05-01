# init_sqlite.py - Initialize SQLite database for local development
"""Create the necessary tables in the local SQLite fallback database.
Run this once before starting the Flask app if you don't have a MySQL server.
"""

import os
from db_helper import get_sqlite_connection

conn = get_sqlite_connection()
cur = conn.cursor()

# Drivers table
cur.execute('''
CREATE TABLE IF NOT EXISTS drivers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_name TEXT,
    username TEXT UNIQUE,
    password TEXT,
    ambulance_no TEXT,
    phone TEXT
)''')

# Emergencies table (simplified version of the original schema)
cur.execute('''
CREATE TABLE IF NOT EXISTS emergencies (
    emergency_id INTEGER PRIMARY KEY AUTOINCREMENT,
    patient_name TEXT,
    patient_age INTEGER,
    patient_mobile TEXT,
    lat REAL,
    lon REAL,
    status TEXT,
    otp TEXT,
    driver_id INTEGER,
    dest_lat REAL,
    dest_lon REAL,
    dest_name TEXT,
    otp_verified INTEGER DEFAULT 0,
    created_at INTEGER,
    FOREIGN KEY(driver_id) REFERENCES drivers(id)
)''')

# Ambulance locations table
cur.execute('''
CREATE TABLE IF NOT EXISTS ambulance_locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_id INTEGER,
    lat REAL,
    lon REAL,
    timestamp TEXT,
    FOREIGN KEY(driver_id) REFERENCES drivers(id)
)''')

conn.commit()
print("✅ SQLite database initialized with required tables.")
conn.close()
