import os
import mysql.connector
import sqlite3
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()

class SQLiteWrapper:
    def __init__(self, conn):
        self.conn = conn
    def cursor(self, dictionary=False):
        c = self.conn.cursor()
        if dictionary:
            self.conn.row_factory = sqlite3.Row
        return CursorWrapper(c)
    def commit(self):
        self.conn.commit()
    def rollback(self):
        self.conn.rollback()
    def close(self):
        self.conn.close()

class CursorWrapper:
    def __init__(self, cursor):
        self.cursor = cursor
    def execute(self, query, params=None):
        if params:
            # Handle list of params or single param
            if not isinstance(params, (list, tuple)):
                params = (params,)
            query = query.replace('%s', '?')
            
        # MySQL to SQLite translations
        query = query.replace('FROM_UNIXTIME(%s)', 'datetime(%s, "unixepoch")')
        query = query.replace('FROM_UNIXTIME( %s )', 'datetime(%s, "unixepoch")')
        query = query.replace('CURDATE()', "date('now', 'localtime')")
        query = query.replace('DATE(created_at)', "date(created_at)")
        query = query.replace("NOW() - INTERVAL 1 HOUR", "datetime('now', '-1 hour')")
        
        # Log query for debugging
        # print(f"[SQL] {query} | Params: {params}")
        
        try:
            self.cursor.execute(query, params or ())
        except Exception as e:
            print(f"[SQL Error] {e} | Query: {query}")
            raise
    def fetchone(self):
        row = self.cursor.fetchone()
        if row and hasattr(row, 'keys'):
            return dict(row)
        return row
    def fetchall(self):
        rows = self.cursor.fetchall()
        return [dict(r) if hasattr(r, 'keys') else r for r in rows]
    @property
    def lastrowid(self):
        return self.cursor.lastrowid
    def close(self):
        self.cursor.close()

def _get_mysql_config():
    """Build MySQL config dict from environment variables (Supports Local, Aiven, and Railway)."""
    # Railway provides MYSQLHOST, MYSQLPORT, MYSQLUSER, MYSQLPASSWORD, MYSQLDATABASE
    return {
        "host":     os.getenv("MYSQLHOST") or os.getenv("DB_HOST", "localhost"),
        "port":     int(os.getenv("MYSQLPORT") or os.getenv("DB_PORT", 3306)),
        "user":     os.getenv("MYSQLUSER") or os.getenv("DB_USER", "root"),
        "password": os.getenv("MYSQLPASSWORD") or os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("MYSQLDATABASE") or os.getenv("DB_NAME", "resqgo"),
    }

def get_db():
    """
    Create and return a MySQL connection, or fallback to SQLite.
    """
    try:
        conn = mysql.connector.connect(**_get_mysql_config())
        print("[OK] Connected to MySQL database")
        return conn
    except Exception as exc:
        print(f"[WARN] MySQL failed, falling back to SQLite: {exc}")
        conn = sqlite3.connect("ambulance_system.db", check_same_thread=False)
        return SQLiteWrapper(conn)

def get_sqlite_connection():
    return sqlite3.connect("ambulance_system.db")

def init_db():
    """Create required tables if they do not already exist."""
    try:
        conn = get_db()
        cur = conn.cursor()

        # ── Drivers ──────────────────────────────────────────────────────────
        # Use simple AUTOINCREMENT for SQLite compatibility
        is_sqlite = isinstance(conn, SQLiteWrapper)
        auto_inc = "AUTOINCREMENT" if is_sqlite else "AUTO_INCREMENT"
        
        cur.execute(f"""
            CREATE TABLE IF NOT EXISTS drivers (
                id            INTEGER PRIMARY KEY {auto_inc},
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
        cur.execute(f"""
            CREATE TABLE IF NOT EXISTS emergencies (
                emergency_id    INTEGER PRIMARY KEY {auto_inc},
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
                ride_distance   FLOAT DEFAULT 0.0,
                fare            FLOAT DEFAULT 0.0,
                FOREIGN KEY (driver_id) REFERENCES drivers(id)
            )
        """)

        # ── Ambulance Locations ───────────────────────────────────────────────
        cur.execute(f"""
            CREATE TABLE IF NOT EXISTS ambulance_locations (
                id         INTEGER PRIMARY KEY {auto_inc},
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
