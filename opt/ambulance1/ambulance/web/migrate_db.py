import mysql.connector

def check_db():
    try:
        conn = mysql.connector.connect(
            host="localhost",
            user="root",
            password="",
            database="ambulance_system"
        )
        cursor = conn.cursor()
        
        # Check if table exists
        cursor.execute("SHOW TABLES LIKE 'emergencies'")
        if not cursor.fetchone():
            print("Table 'emergencies' does not exist. Creating it...")
            cursor.execute("""
                CREATE TABLE emergencies (
                    emergency_id INT AUTO_INCREMENT PRIMARY KEY,
                    patient_name VARCHAR(255),
                    patient_age INT,
                    patient_mobile VARCHAR(20),
                    lat DOUBLE,
                    lon DOUBLE,
                    status ENUM('pending', 'accepted', 'declined', 'completed', 'active') DEFAULT 'pending',
                    driver_id INT DEFAULT NULL,
                    otp VARCHAR(10),
                    otp_verified TINYINT(1) DEFAULT 0,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """)
            print("Table 'emergencies' created successfully.")
        else:
            print("Table 'emergencies' exists. Checking columns...")
            cursor.execute("DESCRIBE emergencies")
            columns = {row[0]: row[1] for row in cursor.fetchall()}
            
            required_columns = {
                "emergency_id": "int",
                "patient_name": "varchar",
                "patient_age": "int",
                "patient_mobile": "varchar",
                "lat": "double",
                "lon": "double",
                "status": "enum",
                "driver_id": "int",
                "otp": "varchar",
                "otp_verified": "tinyint",
                "created_at": "timestamp",
                "dest_lat": "double",
                "dest_lon": "double",
                "dest_name": "varchar",
                "address": "text"
            }
            
            for col in required_columns:
                if col not in columns:
                    print(f"Adding missing column: {col}")
                    if col == "otp":
                        cursor.execute("ALTER TABLE emergencies ADD COLUMN otp VARCHAR(10)")
                    elif col == "otp_verified":
                        cursor.execute("ALTER TABLE emergencies ADD COLUMN otp_verified TINYINT(1) DEFAULT 0")
                    elif col == "status":
                         cursor.execute("ALTER TABLE emergencies MODIFY COLUMN status ENUM('pending', 'accepted', 'declined', 'completed', 'active') DEFAULT 'pending'")
                    elif col in ["dest_lat", "dest_lon"]:
                        cursor.execute(f"ALTER TABLE emergencies ADD COLUMN {col} DOUBLE")
                    elif col == "dest_name":
                        cursor.execute("ALTER TABLE emergencies ADD COLUMN dest_name VARCHAR(255)")
                    elif col == "address":
                        cursor.execute("ALTER TABLE emergencies ADD COLUMN address TEXT")
            
            print("Columns checked.")
            
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Error: {e}")

if __name__ == "__main__":
    check_db()
