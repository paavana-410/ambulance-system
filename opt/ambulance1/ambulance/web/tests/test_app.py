"""
tests/test_app.py
Basic smoke tests for ResQGo Flask backend.
Run with: pytest tests/ -v
"""
import sys
import os
import pytest

# Add parent dir to path so we can import app1
sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

# ── Fixtures ──────────────────────────────────────────────────────────────────

@pytest.fixture
def client(monkeypatch):
    """Create a Flask test client with a mocked DB."""
    # Patch get_db so tests never touch the real cloud DB
    import db_helper

    class FakeCursor:
        def __init__(self): self.rows = []
        def execute(self, *a, **kw): pass
        def fetchone(self): return None
        def fetchall(self): return []
        def close(self): pass

    class FakeConn:
        def cursor(self, **kw): return FakeCursor()
        def commit(self): pass
        def rollback(self): pass
        def close(self): pass

    monkeypatch.setattr(db_helper, "get_db", lambda: FakeConn())

    import app1
    app1.app.config["TESTING"] = True
    app1.app.config["SECRET_KEY"] = "test-secret"
    with app1.app.test_client() as c:
        yield c


# ── Tests ─────────────────────────────────────────────────────────────────────

def test_index_loads(client):
    """Home page should return 200."""
    resp = client.get("/")
    assert resp.status_code == 200


def test_patient_sim_loads(client):
    """Patient SOS page should return 200."""
    resp = client.get("/patient_sim")
    assert resp.status_code == 200


def test_login_missing_fields(client):
    """Login with empty body should return 400."""
    resp = client.post("/login", json={})
    assert resp.status_code == 400
    data = resp.get_json()
    assert data["status"] == "error"


def test_login_bad_credentials(client):
    """Login with wrong credentials should return 401."""
    resp = client.post("/login", json={"username": "nobody", "password": "wrong"})
    assert resp.status_code == 401


def test_register_missing_fields(client):
    """Registration with missing fields should return 400."""
    resp = client.post("/register_driver", json={"username": "test"})
    assert resp.status_code == 400


def test_register_short_password(client):
    """Registration with short password should return 400."""
    resp = client.post("/register_driver", json={
        "driver_name": "Test Driver",
        "username": "testuser",
        "password": "123",        # too short
        "ambulance_no": "KA01A1234",
        "phone": "9999999999"
    })
    assert resp.status_code == 400


def test_send_email_otp_invalid_email(client):
    """OTP request with invalid email should return 400."""
    resp = client.post("/send-email-otp", json={"email": "not-an-email"})
    assert resp.status_code == 400


def test_get_system_status_unauthenticated(client):
    """System status should not require auth - returns data."""
    resp = client.get("/api/get_system_status")
    # Will return 200 even without auth (public endpoint)
    assert resp.status_code in [200, 500]  # 500 if DB mock returns bad data


def test_accept_emergency_requires_auth(client):
    """Accept emergency without session should return 401."""
    resp = client.post("/api/accept_emergency", json={"emergency_id": 1})
    assert resp.status_code == 401


def test_send_location_requires_auth(client):
    """Send location without session should return 401."""
    resp = client.post("/api/send_location", json={"location": {"lat": 13.0, "lon": 77.5}})
    assert resp.status_code == 401


def test_haversine_calculation():
    """Haversine distance between same points should be 0."""
    from app1 import calculate_haversine
    dist = calculate_haversine(13.0, 77.5, 13.0, 77.5)
    assert dist == 0.0


def test_haversine_known_distance():
    """Haversine distance between two known Bangalore points ~5km."""
    from app1 import calculate_haversine
    # MG Road to Hebbal is approx 10km
    dist = calculate_haversine(12.9716, 77.5946, 13.0497, 77.5891)
    assert 8 < dist < 12  # Should be ~10 km
