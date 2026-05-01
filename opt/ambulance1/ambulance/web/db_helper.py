"""
db_helper.py - Database connection helper for ResQGo.
Loads credentials securely from .env file using python-dotenv.
"""

import os
import mysql.connector
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

def _get_mysql_config():
    """Build MySQL config dict from environment variables."""
    return {
        "host":     os.getenv("DB_HOST", "localhost"),
        "port":     int(os.getenv("DB_PORT", 3306)),
        "user":     os.getenv("DB_USER", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME", "resqgo"),
        "ssl_disabled": False,
    }


def get_db():
    """
    Create and return a MySQL connection.
    Raises mysql.connector.Error on failure.
    """
    try:
        conn = mysql.connector.connect(**_get_mysql_config())
        print("[OK] Connected to MySQL database")
        return conn
    except mysql.connector.Error as exc:
        print(f"[WARN] MySQL connection failed: {exc}")
        raise


def init_db():
    """Create required tables if they do not already exist."""
    try:
        conn = get_db()
        cur = conn.cursor()

        # ── Drivers ──────────────────────────────────────────────────────────
        cur.execute("""
            CREATE TABLE IF NOT EXISTS drivers (
                id            INT AUTO_INCREMENT PRIMARY KEY,
                driver_name   VARCHAR(255) NOT NULL,
                username      VARCHAR(255) NOT NULL UNIQUE,
                password_hash VARCHAR(255) NOT NULL,
                ambulance_no  VARCHAR(255),
                phone         VARCHAR(50),
                availability  BOOLEAN DEFAULT TRUE,
                created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
        """)

        # ── Emergencies ───────────────────────────────────────────────────────
        cur.execute("""
            CREATE TABLE IF NOT EXISTS emergencies (
                emergency_id    INT AUTO_INCREMENT PRIMARY KEY,
                patient_name    VARCHAR(255),
                patient_age     INT,
                patient_mobile  VARCHAR(50),
                emergency_type  VARCHAR(100) DEFAULT 'Medical Emergency',
                lat             FLOAT,
                lon             FLOAT,
                status          VARCHAR(50) DEFAULT 'pending',
                otp             VARCHAR(10),
                otp_verified    BOOLEAN DEFAULT FALSE,
                created_at      TIMESTAMP NULL DEFAULT NULL,
                dest_lat        FLOAT,
                dest_lon        FLOAT,
                dest_name       VARCHAR(255),
                driver_id       INT,
                FOREIGN KEY (driver_id) REFERENCES drivers(id)
            )
        """)

        # ── Ambulance Locations ───────────────────────────────────────────────
        cur.execute("""
            CREATE TABLE IF NOT EXISTS ambulance_locations (
                id         INT AUTO_INCREMENT PRIMARY KEY,
                driver_id  INT NOT NULL,
                lat        FLOAT NOT NULL,
                lon        FLOAT NOT NULL,
                recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (driver_id) REFERENCES drivers(id)
            )
        """)

        conn.commit()
        print("[OK] Database tables initialised successfully.")
        cur.close()
        conn.close()
    except Exception as exc:
        print(f"[WARN] Failed to initialise tables: {exc}")
