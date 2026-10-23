# Symptom Journal

A local Android app for quickly logging symptoms and reviewing their history in a calendar. You can keep records for multiple people, such as yourself and family members.

## Features

- Quickly select a symptom from the home screen.
- View symptoms in a list or a grid of large icons.
- Record when a symptom started: now, 5, 15, 30, or 60 minutes ago, or choose an exact time.
- Review entries by day in the calendar.
- Use multiple local profiles; the most recently selected profile is remembered.
- Store data on the device using SQLite.
- Localized in English, German, and Russian.

The initial symptom catalog includes headache, fatigue, tingling, low mood, apathy, anxiety, and pain in different parts of the body.
The app follows the selected app or device language when available and uses English as the fallback.

## Requirements

- Android Studio with support for Kotlin and Jetpack Compose.
- Android SDK configured for the project.
- An Android 9 (API 28) or newer device or emulator.

## Build and run

Open the project in Android Studio and run the app configuration on a device or emulator. From the command line, build a debug APK with:

```bash
./gradlew :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Tests

Run the local unit tests:

```bash
./gradlew :app:testDebugUnitTest
```

Run the instrumented tests on a connected device or emulator:

```bash
./gradlew :app:connectedDebugAndroidTest
```

## Data storage

Symptom events and profiles are stored in an SQLCipher-encrypted SQLite database in the app's private storage. The database passphrase is randomly generated and wrapped with an AES key held by Android Keystore. Existing plaintext databases are encrypted in place on the first launch after this update; their profiles and symptom entries are preserved. Each entry's date and time are saved as a timestamp. The app does not request internet access or provide cloud synchronization.

Automatic Android cloud backups and device-to-device system transfers are disabled for app data. This prevents Android's backup service from copying the journal database and preferences. It does not delete any backup that may have been created before this setting was applied.

Database encryption protects the database file at rest, but does not protect data while the app is using it or against a rooted or compromised device. The database key is tied to this app installation and is not backed up; clearing app storage or uninstalling removes the journal and its key. Use a strong device screen lock and keep Android up to date.

## Medical use and managing your data

Symptom Journal is a personal tracking tool. It does not diagnose conditions, recommend treatment, or replace advice or care from a qualified healthcare professional. Do not use the journal to decide whether to seek care. For severe or urgent symptoms, contact local emergency services or a healthcare professional.

The current version does not support editing, deleting, or exporting individual entries or profiles. To remove all journal data, clear the app's storage from Android system settings or uninstall the app. This permanently removes all profiles and entries stored by the app; there is no in-app export or recovery.

## License and attribution

The first-party application code, translations, documentation, and original app artwork are released by Sergey Stepanov under the GNU Affero General Public License, version 3 only (AGPL-3.0-only). See `COPYRIGHT` for the scope of this notice and `LICENSE` for the unmodified license text.

Third-party dependencies, the Gradle wrapper, Android platform components, and device-provided emoji fonts are not relicensed by this project notice; their respective licenses and terms continue to apply. Direct dependencies are declared in `app/build.gradle.kts` and `gradle/libs.versions.toml`.

## Store listing assets

Fastlane-compatible listing text and screenshots are in `fastlane/metadata/android/`, localized for English (`en-US`), German (`de-DE`), and Russian (`ru-RU`). The English listing includes the app icon, phone screenshots, and the initial changelog.
