package com.enaboapps.switchify.screens.pc

import android.annotation.SuppressLint
import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.widget.EditText
import com.enaboapps.switchify.pc.PcKeyboardKey

@SuppressLint("AppCompatCustomView")
internal class PcLiveTypingEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : EditText(context, attrs) {
    var onTextCommitted: (String) -> Boolean = { false }
    var onKeyCommitted: (PcKeyboardKey) -> Unit = {}

    init {
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
        maxLines = 4
        isSingleLine = false
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : InputConnectionWrapper(target, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                val result = super.commitText(text, newCursorPosition)
                submitPendingText()
                return result
            }

            override fun finishComposingText(): Boolean {
                val result = super.finishComposingText()
                commitPendingText()
                return result
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val localBefore = selectionStart.coerceAtLeast(0)
                val localAfter = (length() - selectionEnd.coerceAtLeast(0)).coerceAtLeast(0)
                val result = super.deleteSurroundingText(beforeLength, afterLength)
                sendRepeatedKey(PcKeyboardKey.Backspace, beforeLength - localBefore)
                sendRepeatedKey(PcKeyboardKey.Delete, afterLength - localAfter)
                return result
            }

            override fun deleteSurroundingTextInCodePoints(
                beforeLength: Int,
                afterLength: Int
            ): Boolean {
                val safeStart = selectionStart.coerceIn(0, length())
                val safeEnd = selectionEnd.coerceIn(safeStart, length())
                val localBefore = text?.substring(0, safeStart)?.codePointCount(0, safeStart) ?: 0
                val remaining = text?.substring(safeEnd).orEmpty()
                val localAfter = remaining.codePointCount(0, remaining.length)
                val result = super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
                sendRepeatedKey(PcKeyboardKey.Backspace, beforeLength - localBefore)
                sendRepeatedKey(PcKeyboardKey.Delete, afterLength - localAfter)
                return result
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return when (event.keyCode) {
                        KeyEvent.KEYCODE_DEL,
                        KeyEvent.KEYCODE_FORWARD_DEL,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER,
                        KeyEvent.KEYCODE_TAB -> true
                        else -> super.sendKeyEvent(event)
                    }
                }
                return when (event.keyCode) {
                    KeyEvent.KEYCODE_DEL -> deleteSurroundingText(1, 0)
                    KeyEvent.KEYCODE_FORWARD_DEL -> deleteSurroundingText(0, 1)
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        commitPendingText()
                        onKeyCommitted(PcKeyboardKey.Enter)
                        true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        commitPendingText()
                        onKeyCommitted(PcKeyboardKey.Tab)
                        true
                    }
                    else -> {
                        val codePoint = event.unicodeChar
                        if (codePoint > 0 && !event.isCtrlPressed && !event.isAltPressed) {
                            onTextCommitted(String(Character.toChars(codePoint)))
                            true
                        } else {
                            super.sendKeyEvent(event)
                        }
                    }
                }
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                commitPendingText()
                onKeyCommitted(PcKeyboardKey.Enter)
                return true
            }
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val result = super.onTextContextMenuItem(id)
            if (result) {
                submitPendingText()
            }
            return result
        }
        return super.onTextContextMenuItem(id)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return handleKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return if (isHandledHardwareKey(keyCode, event)) true else super.onKeyUp(keyCode, event)
    }

    fun finishAndTakePendingText(): String {
        clearComposingText()
        submitPendingText()
        return text?.toString().orEmpty()
    }

    fun restorePendingText(value: String) {
        if (text.isNullOrEmpty() && value.isNotEmpty()) {
            setText(value)
            setSelection(length())
        }
    }

    private fun commitPendingText() {
        submitPendingText()
    }

    fun retryPendingText(): Boolean {
        return submitPendingText()
    }

    private fun submitPendingText(): Boolean {
        val pendingText = text?.toString().orEmpty()
        if (pendingText.isNotEmpty() && onTextCommitted(pendingText)) {
            clearLocalText()
            return true
        }
        return false
    }

    private fun clearLocalText() {
        text?.clear()
    }

    private fun sendRepeatedKey(key: PcKeyboardKey, count: Int) {
        repeat(count.coerceIn(0, MAX_KEYS_PER_EVENT)) {
            onKeyCommitted(key)
        }
    }

    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed) return false
        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> {
                if (length() > 0) {
                    super.onKeyDown(keyCode, event)
                } else {
                    onKeyCommitted(PcKeyboardKey.Backspace)
                }
                true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (length() > 0) {
                    super.onKeyDown(keyCode, event)
                } else {
                    onKeyCommitted(PcKeyboardKey.Delete)
                }
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                commitPendingText()
                onKeyCommitted(PcKeyboardKey.Enter)
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                commitPendingText()
                onKeyCommitted(PcKeyboardKey.Tab)
                true
            }
            else -> {
                val codePoint = event.unicodeChar
                if (codePoint > 0) {
                    if (length() > 0 && !submitPendingText()) return false
                    onTextCommitted(String(Character.toChars(codePoint)))
                } else {
                    false
                }
            }
        }
    }

    private fun isHandledHardwareKey(keyCode: Int, event: KeyEvent): Boolean {
        if (event.isCtrlPressed || event.isAltPressed) return false
        return keyCode == KeyEvent.KEYCODE_DEL ||
            keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_TAB ||
            event.unicodeChar > 0
    }

    companion object {
        private const val MAX_KEYS_PER_EVENT = 120
    }
}
