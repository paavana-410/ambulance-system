import mysql.connector
try:
    conn = mysql.connector.connect(host="localhost", user="root", password="root", database="resqgo")
    print("Successfully connected to local MySQL with root/root!")
    conn.close()
except Exception as e:
    print(f"Failed: {e}")
