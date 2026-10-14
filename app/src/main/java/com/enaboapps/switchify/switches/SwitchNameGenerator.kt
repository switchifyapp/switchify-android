package com.enaboapps.switchify.switches

import android.view.KeyEvent

object SwitchNameGenerator {

    private const val KEYCODE_PREFIX = "KEYCODE_"
    private val KEEP_AS_IS = Regex("[A-Z]?[0-9]+")

    private val friendlyKeyNames = mapOf(
        KeyEvent.KEYCODE_SPACE to "Space",
        KeyEvent.KEYCODE_ENTER to "Enter",
        KeyEvent.KEYCODE_TAB to "Tab",
        KeyEvent.KEYCODE_DPAD_UP to "Up Arrow",
        KeyEvent.KEYCODE_DPAD_DOWN to "Down Arrow",
        KeyEvent.KEYCODE_DPAD_LEFT to "Left Arrow",
        KeyEvent.KEYCODE_DPAD_RIGHT to "Right Arrow",
        KeyEvent.KEYCODE_DPAD_CENTER to "Center",
        KeyEvent.KEYCODE_ESCAPE to "Escape",
        KeyEvent.KEYCODE_DEL to "Backspace",
        KeyEvent.KEYCODE_VOLUME_UP to "Volume Up",
        KeyEvent.KEYCODE_VOLUME_DOWN to "Volume Down",
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "Play/Pause"
    )

    fun forKeyCode(keyCode: Int): String =
        friendlyKeyNames[keyCode] ?: fromKeyCodeString(KeyEvent.keyCodeToString(keyCode), keyCode)

    fun fromKeyCodeString(raw: String?, keyCode: Int): String {
        val stripped = raw?.removePrefix(KEYCODE_PREFIX)?.trim().orEmpty()
        if (stripped.isEmpty() || stripped.all { it.isDigit() }) return "Key $keyCode"
        return stripped.split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { part ->
                if (part.length == 1 || KEEP_AS_IS.matches(part)) part
                else part.lowercase().replaceFirstChar { it.uppercase() }
            }
    }

    fun forGesture(gesture: CameraSwitchFacialGesture?): String =
        gesture?.getName()?.takeIf { it.isNotBlank() } ?: "Camera Switch"
}
