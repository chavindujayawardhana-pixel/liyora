# Liyora (ලියෝරා) - Android app

## Goal
Translate Sinhala (Sinhala script or Singlish) text into natural, correct English.

## Rules
- Kotlin, Jetpack Compose, minSdk 26.
- No backend server. The app calls the Gemini API directly using the USER'S OWN API key.
- Never hardcode any API key in the code.
- Store the user's key with EncryptedSharedPreferences. Never log it.
- App UI text in simple, everyday Sinhala.

## Gemini call
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
Header: x-goog-api-key: <user key>
Prompt: "The following text is Sinhala, either in Sinhala script or in Singlish (Sinhala written with English letters). Translate it into natural, grammatically correct English. Keep the tone the same. Reply with only the English translation.\n\nText: <text>"
Model name must be a single constant, easy to change.

## Phase 1 - Setup screen
- First launch: button "Key එක ගන්න" opens https://aistudio.google.com/apikey
- Paste field + "Paste" button. Test the key with a small request. Show a clear Sinhala message if it is valid or invalid.
- Handle HTTP 429 (daily limit reached): show "අද free limit එක ඉවරයි, හෙට ආයෙත් try කරන්න".

## Phase 2 - Translate screen
- Text box, Translate button, English result, Copy button.
- Loading state, no-internet message, error messages.

## Later phases (do NOT build yet)
- AccessibilityService reading text in other apps, floating icon, popup with Apply button that replaces the text.

## Working style
- Build one phase at a time. Make sure ./gradlew assembleDebug succeeds before saying done.
- After each phase, tell me how to test it on my phone.

## Final UX (Phases 3-5)
- The user keeps using their own keyboard (e.g. Gboard). We do NOT build a keyboard.
- A small floating Liyora icon appears near the text field while the user types.
- Tapping it reads the field text (AccessibilityService) and translates it.
- A Grammarly-style panel appears directly above the keyboard showing the English translation with an "Apply" button and a close button.
- Apply replaces the text in the field with the English text. The user taps Send themselves. Never auto-send.
- Position the panel using the input-method window bounds from the accessibility service.


## Privacy and visibility rules (allow-list only)
- The accessibility service works ONLY in apps the user has ticked in Settings. Default: every app is OFF.
- Settings screen lists the user's launchable apps with a checkbox each. Do not use QUERY_ALL_PACKAGES; list apps through a <queries> launcher-intent declaration.
- In any app that is not ticked, ignore all accessibility events: do not read text, do not show the icon.
- The floating icon is visible ONLY while the soft keyboard is open and an editable text field is focused in a ticked app. Remove it immediately when the keyboard closes.
- Never read text in password fields, even in ticked apps (check AccessibilityNodeInfo.isPassword).
- Text is read only when the user taps the icon. Never read or send text in the background. Never store typed text.
- Before asking for the accessibility permission, show a clear Sinhala notice explaining what it does and that only ticked apps are used.

## App icon
- Use liyora_logo.svg / liyora_logo.png in this folder as the app launcher icon (red background, white lambda).

## Icon and panel design (final)
- The Liyora icon is a floating round red bubble (like Grammarly's), white lambda inside, white ring and soft shadow, with a small dark badge showing "1". It floats at the bottom-right above the text field area, not inside the text field.
- Tapping the bubble opens a panel directly above the keyboard: a "Liyora" label, a "Singlish to English" chip, a close button, the English text, and two buttons: "Apply" (primary) and "Dismiss".
- All UI text in this panel is English.

## Permissions (corrected)
- Only ONE permission: Accessibility Service. Do NOT request "Display over other apps" (SYSTEM_ALERT_WINDOW).
- Show the bubble and panel using AccessibilityService windows (TYPE_ACCESSIBILITY_OVERLAY).
- Permission screen: button "Open settings" opens Settings.ACTION_ACCESSIBILITY_SETTINGS.
- Sideloaded APK on newer Android: if the switch is greyed out, show steps: Settings > Apps > Liyora > three-dot menu > "Allow restricted settings", then turn Liyora on and tap Allow.
- Check enabled status via Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES; Home screen shows "Accessibility permission: On/Off".
- Home screen Setup list has only two rows: Gemini key, Accessibility permission.
- UI text is English (Grammarly-style); screens: Home, Permission steps, panel above keyboard with Apply/Dismiss.

## Permissions (corrected)
- Only ONE permission: Accessibility Service. Do NOT request "Display over other apps" (SYSTEM_ALERT_WINDOW).
- Show the bubble and panel using AccessibilityService windows (TYPE_ACCESSIBILITY_OVERLAY).
- Permission screen: button "Open settings" opens Settings.ACTION_ACCESSIBILITY_SETTINGS.
- Sideloaded APK on newer Android: if the switch is greyed out, show steps: Settings > Apps > Liyora > three-dot menu > "Allow restricted settings", then turn Liyora on and tap Allow.
- Check enabled status via Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES; Home screen shows "Accessibility permission: On/Off".
- Home screen Setup list has only two rows: Gemini key, Accessibility permission. Tapping "Gemini key" opens the Gemini key screen.

## Gemini key screen
- Steps: tap "Get my key" (opens https://aistudio.google.com/apikey), copy the key, come back, paste, tap "Save key".
- Key box shows dots (masked). Buttons: Show, Paste. "Save key" and "Remove key" at the bottom.
- After saving, test the key with one small Gemini call. Show "Connected. The key works." or the error.
- Save the key encrypted on the phone only (EncryptedSharedPreferences). Never show it again on Home, only "Connected".
- Notice on screen: free keys may be used by Google to improve its products, so avoid private text.
- UI text is English. Screens: Home, Permission steps, Gemini key, panel above keyboard with Apply/Dismiss.
