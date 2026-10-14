# Sports Hub (formerly F1nalyze)

One Android app to follow all your favourite teams across leagues: fixtures, live scores,
league tables and news for, say, Manchester United, Ferrari and an IPL team in a single feed.

```
backend/   FastAPI service. Normalises ESPN, Jolpica (F1) and TheSportsDB into one schema.
android/   Kotlin + Jetpack Compose app. Follows are stored on the device (no account).
ml/        The original F1 race-prediction work (notebook, training script, dataset).
legacy/    The old Streamlit app. Kept until the predictor is ported into the new app.
```

## Coverage

About 290 competitions: 219 soccer leagues worldwide, 7 cricket (IPL, Big Bash, ...),
38 US sports leagues, 25 rugby, and Formula 1. Cricket leagues are a hand-kept list in
`backend/app/providers/espn.py` (`CRICKET_LEAGUES`) because ESPN cannot enumerate them.

## Run it

### 1. Backend (needs Python 3.11+)

```bash
cd backend
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt   # Windows; use .venv/bin/python elsewhere
.venv/Scripts/python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
.venv/Scripts/python -m pytest                             # tests (offline)
```

The first `/leagues` call takes ~20 s while it discovers leagues; the result is cached in
`backend/.cache/` for a week. Interactive docs: http://127.0.0.1:8000/docs

### 2. Android app on a USB-connected phone

1. On the phone, enable Developer options and USB debugging, connect it, and accept the prompt.
2. Let the phone reach the backend on your PC through the cable:

   ```bash
   adb reverse tcp:8000 tcp:8000
   ```

   Repeat after every reconnect. The app's default `apiBaseUrl` is `http://127.0.0.1:8000/`
   (see `android/gradle.properties`).
3. Install and launch:

   ```bash
   cd android
   ./gradlew installDebug
   ```

   Or open `android/` in Android Studio and press Run.

`android/local.properties` (the SDK path) is machine-specific and git-ignored; Android Studio
creates it for you.

## Notes

- ESPN's API is public but unofficial and rate-limits by IP. The backend throttles itself, caches
  responses, serves stale data on failures and backs off for 5 minutes after a 403/429.
  Don't loop over hundreds of leagues in a script.
- Debug builds allow plain HTTP to the local backend. A release build needs the backend deployed
  behind HTTPS and `apiBaseUrl` pointed at it.
