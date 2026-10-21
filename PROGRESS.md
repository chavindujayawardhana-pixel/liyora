# Liyora keyboard rebuild – progress

Work happens on the `keyboard` branch. `master` / tag `v1.0.0-accessibility` hold the old accessibility version.

- [x] STEP 0 – Safety first
  - [x] Backup copied to `D:\My project\Liyora-accessibility-backup` (no build/.gradle)
  - [x] git init, .gitignore, commit 1.0.0, tag `v1.0.0-accessibility`
  - [x] Branch `keyboard` created
- [x] STEP 1 – Remove Accessibility (service, overlay, bubble, Works in, config, queries, manifest entries)
- [x] STEP 2 – Build the keyboard (InputMethodService, method.xml, keys, λ translate panel, password check, messages)
- [x] STEP 3 – Setup screens (4 steps with green ticks, re-check on resume, try-typing field)
- [ ] STEP 4 – Privacy screen + PRIVACY.md, versionName 1.1.0 / versionCode 2
- [ ] STEP 5 – Build debug + signed release APK, install with adb

## Log
- STEP 0 done. Next: STEP 1 (remove Accessibility).
- STEP 1 done: accessibility package, Works in, Permission screen, config and <queries> removed; debug build passes. Next: STEP 2 (keyboard service).
- STEP 2 done: LiyoraKeyboardService + KeyboardView (QWERTY, shift/caps, repeat backspace, ?123, globe, λ) + TranslatePanel (Apply/Dismiss), password check, messages; debug build passes. Next: STEP 3 (setup screens).
- STEP 3 done: Home shows 4 setup steps with green ticks (key, keyboard on, keyboard chosen, try typing); re-checked on resume and window focus. Next: STEP 4 (privacy text + version 1.1.0/2).
