# XAU Pilot

XAU Pilot is a Kotlin/Jetpack Compose Android client with an optional FastAPI/PostgreSQL backend for XAUUSD EA management.

## Android app

Open the repository root in Android Studio or AI Studio and let Gradle sync. The app starts in Demo mode, which uses local seeded data and a simulated XAUUSD quote; it does not require a backend to explore the UI.

From a machine with Android SDK installed:

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew test
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For live API mode, authenticate through the app's login flow (or the host integration), configure the API base URL, and set the backend's `JWT_SECRET` and `DATABASE_URL` environment variables. Do not put access tokens, broker passwords, database passwords, or signing keys in source control.

## Backend

Install the pinned dependencies, configure the database and JWT secret, then start FastAPI:

```bash
cd backend
python3 -m pip install -r requirements.txt
export DATABASE_URL='postgresql://user:password@host:5432/xau_pilot_db'
export JWT_SECRET="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
uvicorn backend.app.main:app --host 0.0.0.0 --port 8000
```

Health check: `GET /health`.

## Safety notes

Demo mode is intentionally local-only. Live account controls and emergency-stop commands should be used only after the backend, EA bridge, authentication, and risk limits have been configured and tested in a broker demo account.
