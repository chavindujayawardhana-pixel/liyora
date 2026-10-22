# Liyora – progress

Work happens on the `keyboard` branch. Version 1.0.0 is kept on `master` and tag `v1.0.0-accessibility`.

## Keyboard version (1.1.0)
- [x] Keyboard (InputMethodService) with QWERTY keys, shift/caps, ?123, globe and the λ key
- [x] Translate panel above the keys (Apply/Dismiss), password fields skipped, clear messages
- [x] Setup screen: 4 steps with green ticks and a try-typing field
- [x] Privacy screen + PRIVACY.md; versionName 1.1.0, versionCode 2
- [x] Debug and signed release APKs built (apksigner verifies, v2)

## Follow-up: wording + multiple Gemini keys
- [x] "Singlish to English" wording changed to "To English" (panel chip, Home subtitle, Privacy intro, PRIVACY.md)
- [x] Multiple keys: KeyStore holds a list (encrypted, old single key migrated), add/remove/test with masked keys
- [x] Quota error (HTTP 429 / RESOURCE_EXHAUSTED) marks the key until next midnight Pacific time and retries once with the next free key; all used up -> "All keys have reached their limit. Try again later."
- [x] Key screen note about keys from the same Google project sharing one limit; Privacy updated
- [x] Signed release built and installed with adb (phone 25028RN03A, "Success")
- [x] Remaining Sinhala/Singlish UI text removed: empty-field message (keyboard), Home step 4 + "How to use" step 1, Translate screen input label. Gemini prompt left as it is. Signed release installed with adb (Success).

## Going public on GitHub (v1.1.0)
- [x] STEP A – Rename screenshots in docs/screenshots (no "applied" image; the second translate panel is translate2.jpg)
- [x] STEP B – Safety scan: files clean; dist/ added to .gitignore; author email rewritten to the GitHub noreply address on all commits (backup mirror in D:/My project/Liyora-git-backup), tag v1.0.0-accessibility re-created, repo-local user.name/user.email set
- [x] STEP C – README.md, docs/logo.png (rendered from the launcher icon), translate2.jpg, keystore.properties.example with fake values
- [x] STEP D – LICENSE comes from origin/main (MIT, Copyright (c) 2026 Chavindu Jayawardana); PRIVACY.md matches the in-app Privacy screen; SPEC.md (1.0.0 spec) removed, PROGRESS.md cleaned
- [ ] STEP E – Merge keyboard into main, merge origin/main, push main + tags
- [ ] STEP F – Signed APK in dist/, RELEASE_NOTES.md, tag v1.1.0, GitHub Release

Next: STEP E (merge keyboard into main, merge origin/main, push).
