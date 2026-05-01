from db_helper import get_db
conn = get_db()
c = conn.cursor(dictionary=True)
try:
    c.execute("DESCRIBE emergencies")
    for r in c.fetchall():
        print(f"{r['Field']}: Nullable={r['Null']}, Default={r['Default']}")
except Exception as e:
    print("Error:", e)
conn.close()
