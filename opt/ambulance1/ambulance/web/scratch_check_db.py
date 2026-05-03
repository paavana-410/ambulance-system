from db_helper import get_db
try:
    conn = get_db()
    print("Successfully connected to database!")
    conn.close()
except Exception as e:
    print(f"Failed to connect: {e}")
