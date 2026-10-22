# Liyora keyboard rebuild – progress

Work happens on the `keyboard` branch. `master` / tag `v1.0.0-accessibility` hold the old accessibility version.

- [x] STEP 0 – Safety first
  - [x] Backup copied to `D:\My project\Liyora-accessibility-backup` (no build/.gradle)
  - [x] git init, .gitignore, commit 1.0.0, tag `v1.0.0-accessibility`
  - [x] Branch `keyboard` created
- [x] STEP 1 – Remove Accessibility (service, overlay, bubble, Works in, config, queries, manifest entries)
- [x] STEP 2 – Build the keyboard (InputMethodService, method.xml, keys, λ translate panel, password check, messages)
- [x] STEP 3 – Setup screens (4 steps with green ticks, re-check on resume, try-typing field)
- [x] STEP 4 – Privacy screen + PRIVACY.md, versionName 1.1.0 / versionCode 2
- [ ] STEP 5 – Build debug + signed release APK, install with adb
  - [x] Debug APK and signed release APK built (apksigner verifies, v2, CN=Liyora)
  - [ ] Install on phone with adb install -r (BLOCKED: adb devices showed no device)

## Log
- STEP 0 done. Next: STEP 1 (remove Accessibility).
- STEP 1 done: accessibility package, Works in, Permission screen, config and <queries> removed; debug build passes. Next: STEP 2 (keyboard service).
- STEP 2 done: LiyoraKeyboardService + KeyboardView (QWERTY, shift/caps, repeat backspace, ?123, globe, λ) + TranslatePanel (Apply/Dismiss), password check, messages; debug build passes. Next: STEP 3 (setup screens).
- STEP 3 done: Home shows 4 setup steps with green ticks (key, keyboard on, keyboard chosen, try typing); re-checked on resume and window focus. Next: STEP 4 (privacy text + version 1.1.0/2).
- STEP 4 done: Privacy screen + PRIVACY.md rewritten for the keyboard (no Accessibility wording, no email); versionName 1.1.0, versionCode 2; SPEC.md marked as 1.0.0. Next: STEP 5 (debug + signed release build, adb install).
- STEP 5 partly done: both APKs built, release signed and verified. adb found no device, so the install is not done. Next: connect the phone (USB debugging on) and run adb install -r app/build/outputs/apk/release/app-release.apk.

## Follow-up: wording + multiple Gemini keys
- [x] "Singlish to English" wording changed to "To English" (panel chip, Home subtitle, Privacy intro, PRIVACY.md)
- [x] Multiple keys: KeyStore holds a list (encrypted, old single key migrated), add/remove/test with masked keys
- [x] Quota error (HTTP 429 / RESOURCE_EXHAUSTED) marks the key until next midnight Pacific time and retries once with the next free key; all used up -> "All keys have reached their limit. Try again later."
- [x] Key screen note about keys from the same Google project sharing one limit; Privacy updated
- [x] Signed release built and installed with adb (phone 25028RN03A, "Success")
- [x] Remaining Sinhala/Singlish UI text removed: empty-field message (keyboard), Home step 4 + "How to use" step 1, Translate screen input label. Gemini prompt and SPEC.md (1.0.0 spec) left as they are. Signed release installed with adb (Success).

## Going public on GitHub (v1.1.0)
- [x] STEP A – Rename screenshots in docs/screenshots (no "applied" image: photo_6255882624593236297_y.jpg is a second translate panel, left as is, not committed)
- [x] STEP B – Safety scan: files clean; dist/ added to .gitignore; author email rewritten to the GitHub noreply address on all commits (backup mirror in D:My projectiyora-git-backup), tag v1.0.0-accessibility re-created, repo-local user.name/user.email set
- [ ] STEP C – README.md, docs/logo.png, keystore.properties.example
- [ ] STEP D – LICENSE (MIT, 2026 Chavindu Jayawardana), PRIVACY.md check, remove old Accessibility mentions
- [ ] STEP E – Merge keyboard into main, merge origin/main, push main + tags
- [ ] STEP F – Signed APK in dist/, RELEASE_NOTES.md, tag v1.1.0, GitHub Release

Next: STEP C (README).
