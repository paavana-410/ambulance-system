"""
migrate_passwords.py
One-time migration: converts plaintext passwords -> bcrypt hashes.
Run ONCE after deploying Phase 1:
    python migrate_passwords.py
"""
import sys
import bcrypt
from db_helper import get_db


def migrate():
    conn = get_db()
    cur  = conn.cursor(dictionary=True)

    # Step 1: Add password_hash column if not present
    try:
        cur.execute("ALTER TABLE drivers ADD COLUMN password_hash VARCHAR(255)")
        conn.commit()
        print("[OK]  Added column: password_hash")
    except Exception:
        print("[INFO] Column password_hash already exists - skipping ALTER.")

    # Step 2: Read drivers with no hash yet
    cur.execute(
        "SELECT id, username, password FROM drivers "
        "WHERE (password_hash IS NULL OR password_hash = '') "
        "AND password IS NOT NULL AND password != ''"
    )
    drivers = cur.fetchall()
    print(f"[INFO] Found {len(drivers)} driver(s) needing migration.")

    if not drivers:
        print("[OK]  Nothing to migrate. All passwords are already hashed.")
        conn.close()
        return

    # Step 3: Hash and save
    update_cur = conn.cursor()
    for drv in drivers:
        pw_hash = bcrypt.hashpw(drv["password"].encode(), bcrypt.gensalt()).decode()
        update_cur.execute(
            "UPDATE drivers SET password_hash = %s WHERE id = %s",
            (pw_hash, drv["id"]),
        )
        print(f"   [HASHED] {drv['username']}")

    conn.commit()
    conn.close()
    print("\n[OK]  Password migration complete! All passwords are now bcrypt hashed.")
    print("[NOTE] You can now safely remove the old 'password' column from the DB.")


if __name__ == "__main__":
    migrate()
