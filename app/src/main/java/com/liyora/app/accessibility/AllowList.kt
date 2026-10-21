package com.liyora.app.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.core.content.edit

/**
 * The apps the user turned on in "Works in". The bubble appears only in these apps; every other
 * app is ignored completely. Banking apps, password managers and Liyora itself can never be allowed.
 */
class AllowedAppsStore(context: Context) {

    private val appContext = context.applicationContext
    val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun allowed(): Set<String> {
        seedIfNeeded()
        return prefs.getStringSet(KEY_ALLOWED, null).orEmpty().filterNot(::isBlocked).toSet()
    }

    fun isAllowed(packageName: String?): Boolean =
        packageName != null && !isBlocked(packageName) && packageName in allowed()

    fun setAllowed(packageName: String, on: Boolean) {
        val updated = allowed().toMutableSet()
        if (on && !isBlocked(packageName)) updated += packageName else updated -= packageName
        prefs.edit { putStringSet(KEY_ALLOWED, updated) }
    }

    fun isBlocked(packageName: String): Boolean =
        packageName == appContext.packageName ||
            packageName.contains("bank", ignoreCase = true) ||
            packageName in BLOCKED

    /** First run: turn on the default chat and notes apps that are installed. */
    private fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        val pm = appContext.packageManager
        val installed = DEFAULTS.filter { pm.getLaunchIntentForPackage(it) != null }.toSet()
        prefs.edit {
            putStringSet(KEY_ALLOWED, installed)
            putBoolean(KEY_SEEDED, true)
        }
    }

    private companion object {
        const val PREFS_NAME = "liyora_settings"
        const val KEY_ALLOWED = "allowed_packages"
        const val KEY_SEEDED = "allowed_seeded"

        val DEFAULTS = listOf(
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.facebook.orca",
            "com.google.android.apps.messaging",
            "com.google.android.keep",
        )

        /** Password managers and authenticators. Banking apps are caught by the "bank" check. */
        val BLOCKED = setOf(
            "com.x8bit.bitwarden",
            "com.onepassword.android",
            "com.agilebits.onepassword",
            "com.lastpass.lpandroid",
            "com.dashlane",
            "com.callpod.android_apps.keeper",
            "io.enpass.app",
            "com.nordpass.android.app.password.manager",
            "proton.android.pass",
            "keepass2android.keepass2android",
            "keepass2android.keepass2android_nonet",
            "com.kunzisoft.keepass.free",
            "com.kunzisoft.keepass.libre",
            "com.google.android.apps.authenticator2",
            "com.azure.authenticator",
            "com.authy.authy",
        )
    }
}

fun isLiyoraAccessibilityEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val me = ComponentName(context, LiyoraAccessibilityService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
}
