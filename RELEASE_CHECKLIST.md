# Release validation checklist

- [ ] Pin exact GeckoView version and verify Mozilla security advisories.
- [ ] Run Gradle dependency verification and generate SBOM.
- [ ] Build/test arm64-v8a and armeabi-v7a on Android 9, 10, 12, 14 and 15+.
- [ ] Test on Huawei/EMUI/HarmonyOS without GMS installed.
- [ ] Verify offline startup, process death, low-memory recovery and session restoration.
- [ ] Implement complete Gecko WebExtension ad/tracker blocking and EasyList updates.
- [ ] Complete OAuth redirect/result handling, PKCE, refresh tokens and revocation.
- [ ] Implement Google Drive file lookup/update and sync conflict resolution.
- [ ] Encrypt sync JSON end-to-end; never store access tokens in logs.
- [ ] Implement cookie/profile isolation using separate GeckoSession contexts or GeckoRuntime profile directories; test data separation.
- [ ] Implement full media PiP via GeckoView media callbacks and verify playback after app backgrounding.
- [ ] Add download persistence, retry/resume validation, checksums and user-selected destination.
- [ ] Add password manager using Android Keystore-backed per-record encryption; never store plaintext credentials.
- [ ] Perform penetration testing, privacy review, crash/ANR monitoring and accessibility testing.
