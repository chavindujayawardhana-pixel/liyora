package com.liyora.app.accessibility

import android.content.Context
import androidx.core.content.edit

enum class Side { LEFT, RIGHT }

/** Where the user left the bubble: which edge, and how far its bottom sits above the keyboard. */
data class BubblePosition(val side: Side, val aboveKeyboardDp: Float)

/** Last bubble position per app. Not sensitive, so plain SharedPreferences. */
class BubblePositionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(packageName: String): BubblePosition? {
        val side = prefs.getString("$packageName.side", null)
            ?.let { runCatching { Side.valueOf(it) }.getOrNull() }
            ?: return null
        return BubblePosition(side, prefs.getFloat("$packageName.above", 0f))
    }

    fun save(packageName: String, position: BubblePosition) {
        prefs.edit {
            putString("$packageName.side", position.side.name)
            putFloat("$packageName.above", position.aboveKeyboardDp)
        }
    }

    private companion object {
        const val PREFS_NAME = "liyora_bubble_positions"
    }
}
