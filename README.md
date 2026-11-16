# Gecko Browser (Android 9+)

A native Kotlin browser starter using Mozilla GeckoView, with no Google Play Services dependency. `minSdk=28`, ABI filters/splits for `arm64-v8a` and `armeabi-v7a`, AppCompat theme switching, HTTPS-first navigation, AppAuth OAuth scaffolding, Room + SQLCipher storage, native URL-filter matching, and segmented downloads.

## Build

- Android Studio with JDK 17
- Android SDK Platform 36 and NDK/CMake installed
- Open this folder as a Gradle project, allow dependency sync, then build `app`.
- `gradlew` launcher scripts are intentionally not bundled; generate them with Android Studio or run `gradle wrapper` using Gradle 8.13.

GeckoView uses a dynamic stable-major selector (`153.+`) in `gradle.properties` so Maven can resolve the current stable 153 line. For reproducible releases, replace it with an exact Mozilla Maven version after checking the official GeckoView Maven metadata. Mozilla publishes GeckoView as a bundled engine, independent of Android System WebView and GMS.

## Important implementation notes / release blockers

This is a compilable-oriented core starter, not a certified production browser. Browser-grade release requires device testing and completion of the following:

1. **OAuth configuration:** register native/public clients with Google and Microsoft, configure redirect URI `com.example.geckobrowser://oauth2redirect`, client IDs, consent screens and provider verification. No client secret belongs in the APK. Google AppData requires `drive.appdata`; Microsoft uses `Files.ReadWrite.AppFolder`. AppAuth must be completed with Activity Result handling and refresh-token lifecycle before shipping.
2. **Sync correctness:** the included REST methods upload a new Google Drive file and overwrite OneDrive AppFolder content. Add Google file lookup/update, conflict resolution, encryption/signing of sync payloads, retries, deletion semantics and refresh-token refresh before enabling sync in UI.
3. **Ad blocking:** included C++ matcher is a fast substring matcher and sample host list, not a complete EasyList parser, cosmetic-filter engine, or Gecko network interception layer. Production integration should use a GeckoView WebExtension implementing `webRequest`/declarative blocking with regularly updated filter lists; do not claim complete EasyList coverage until verified.
4. **Tabs/PiP:** hibernation manager is a lifecycle primitive; connect it to a real tab model and persist tab metadata. Browser PiP entry only enters the activity's PiP mode; HTML5 media PiP requires GeckoView media-session/PiP integration and device testing.
5. **Downloads:** segmented range requests require server support for `Accept-Ranges: bytes`; the sample downloader writes to app-specific external storage. Add persistent job metadata, integrity verification, redirect/auth/cookie support, retry policy, notification controls, and a user-facing SAF destination flow. Android 9 public shared storage needs runtime `WRITE_EXTERNAL_STORAGE`; app-specific external storage does not.
6. **Security:** HTTPS is the default and cleartext is disabled globally. HTTP bypass is one-navigation and warned. Add certificate error UX, safe-browsing reputation, permission prompts, cookie controls, password manager threat model, encrypted Room key backup/rotation, and WebExtension permission review.
7. **Gecko API compatibility:** GeckoView updates on Mozilla's release cadence. Compile against the exact pinned stable artifact and review API changes before release. Validate WebGL/WebAssembly/PWA/video support on target chipsets; no embedded engine can guarantee identical behavior on every OEM build.

## Project map

- `MainActivity.kt`: GeckoView host, Google search, HTTP warning, theme and PiP entry.
- `BrowserEngine.kt`: search URL and desktop/mobile user-agent settings.
- `data/BrowserDatabase.kt`: Room entities/DAO with SQLCipher-backed database.
- `download/ParallelDownloadManager.kt`: range-based parallel downloader with pause/resume flags.
- `tabs/TabHibernationManager.kt`: background session hibernation primitive.
- `sync/OAuthSyncManager.kt`: AppAuth provider authorization scaffolding and Drive/Graph upload endpoints.
- `privacy/`: JNI matcher + sample block list.
- `res/xml/network_security_config.xml`: no cleartext by default.

## GMS-free statement

No Google Play Services artifacts or APIs are used. Google Search, Drive REST and Google OAuth are ordinary HTTPS services and still require network access and valid OAuth app registration. GMS-free does not mean Google endpoints are guaranteed reachable in every region/network.

## Automated APK build on Windows

1. Install Android Studio and, from **Tools > SDK Manager**, install:
   - Android SDK Platform 36
   - Android SDK Build-Tools 36.0.0
   - Android SDK Command-line Tools (latest)
   - CMake 3.22.1
   - Android NDK 27.2.12479018
2. Ensure Android Studio's bundled Java (JBR) or JDK 17 is available on `PATH` as `java`.
3. Extract the project ZIP, then double-click `BUILD_APK_WINDOWS.bat`.
4. The script downloads Gradle 8.13 into `.build-tools` if needed, installs the required SDK packages (unless `-SkipSdkInstall` is passed), runs `clean assembleDebug`, and copies generated APKs to `dist-apk`.
5. The debug APK is signed automatically with the standard Android debug key and is suitable for initial installation/testing. Do not distribute it as a production release.

Optional: run `powershell -ExecutionPolicy Bypass -File .\BUILD_APK_WINDOWS.ps1 -BuildRelease` to also attempt a release build. A release APK must be signed with your own protected release keystore before distribution. The script does not create or store a release key.

If the build fails, copy the complete PowerShell/Gradle output and share it for diagnosis. This script automates the build process; it does not certify runtime compatibility or production readiness.
