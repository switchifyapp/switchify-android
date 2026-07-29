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
    var onTextCommitted: (String) -> Unit = {}
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
                text?.toString()?.takeIf { it.isNotEmpty() }?.let(onTextCommitted)
                clearLocalText()
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
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                as? android.content.ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.description.hasMimeType("text/*")) {
                val pastedText = clip.getItemAt(0).coerceToText(context).toString()
                if (pastedText.isNotEmpty()) {
                    onTextCommitted(pastedText)
                    clearLocalText()
                    return true
                }
            }
        }
        return super.onTextContextMenuItem(id)
    }

    private fun commitPendingText() {
        val pendingText = text?.toString().orEmpty()
        if (pendingText.isNotEmpty()) {
            onTextCommitted(pendingText)
            clearLocalText()
        }
    }

    private fun clearLocalText() {
        text?.clear()
    }

    private fun sendRepeatedKey(key: PcKeyboardKey, count: Int) {
        repeat(count.coerceIn(0, MAX_KEYS_PER_EVENT)) {
            onKeyCommitted(key)
        }
    }

    companion object {
        private const val MAX_KEYS_PER_EVENT = 120
    }
}
