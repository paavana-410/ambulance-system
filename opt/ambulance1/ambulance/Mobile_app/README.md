# Smart Ambulance Patient App

This is the Android Patient Application for the Smart Ambulance Dispatch System. It has been built using **Kotlin** and **Jetpack Compose** (Modern Android UI toolkit).

## Folder Structure

```text
PatientApp/
├── build.gradle.kts           # Project-level Gradle build file
├── settings.gradle.kts        # Project settings showing module inclusions
└── app/
    ├── build.gradle.kts       # App-level build configuration (Compose, Retrofit added)
    └── src/
        └── main/
            ├── AndroidManifest.xml  # Permissions (Internet, Location)
            ├── res/values/          # UI Themes and Strings
            └── java/com/smartambulance/patient/
                ├── network/
                │   └── ApiService.kt   # Retrofit integration (POST & GET APIs)
                └── MainActivity.kt     # App entry point and All UI Screens
```

## How to Build the APK

1. **Install Android Studio**
   Download from [developer.android.com/studio](https://developer.android.com/studio)

2. **Open the Project**
   - Open Android Studio
   - Click **Open** and select the `PatientApp` folder on your Desktop.
   - Wait for Android Studio to index the files and download the necessary Gradle tools automatically.

3. **Check the API Base URL**
   - If testing on the Android Emulator, the base URL `http://10.0.2.2:3000` inside `ApiService.kt` will correctly point to your laptop's Flask server running on `localhost:3000`.
   - If testing on a real physical Android device over Wi-Fi, change `BASE_URL` in `ApiService.kt` to the IPv4 address of your computer running the Flask server (e.g., `http://192.168.x.x:3000`).

4. **Build APK**
   - Go to Top Menu -> **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**
   - Android Studio will compile the app and provide a pop-up linking to the built `app-debug.apk` output file.

## Features Implemented
- [x] Welcome Screen
- [x] Request Ambulance Screen (Captures patient form and fetches live phone GPS coordinates)
- [x] Secure OTP Display Screen (polls backend for when the ambulance driver accepts and mission starts)
- [x] Waiting Screen (Displays Driver Name & Ambulance Number)
- [x] Live Tracking Screen (Actively polls ambulance location and ETA to patient)
