package com.enaboapps.switchify.screens.pc

import android.annotation.SuppressLint
import android.content.Context
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
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
    var onSessionTextChanged: (String) -> Unit = {}
    var onEnterCommitted: () -> Boolean = { false }
    var onKeyCommitted: (PcKeyboardKey) -> Unit = {}
    private var restoringText = false

    init {
        inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE
        minLines = 2
        maxLines = 4
        isSingleLine = false
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(
                text: CharSequence?,
                start: Int,
                count: Int,
                after: Int
            ) = Unit

            override fun onTextChanged(
                text: CharSequence?,
                start: Int,
                before: Int,
                count: Int
            ) = Unit

            override fun afterTextChanged(text: Editable?) {
                if (!restoringText) {
                    onSessionTextChanged(text?.toString().orEmpty())
                }
            }
        })
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val target = super.onCreateInputConnection(outAttrs) ?: return null
        outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : InputConnectionWrapper(target, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text.isEnterCommit()) {
                    commitEnter()
                    return true
                }
                val result = super.commitText(text, newCursorPosition)
                submitCommittedTextIfReady()
                return result
            }

            override fun finishComposingText(): Boolean {
                val result = super.finishComposingText()
                submitCommittedText()
                return result
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                val localBefore = selectionStart.coerceAtLeast(0)
                val localAfter = (length() - selectionEnd.coerceAtLeast(0)).coerceAtLeast(0)
                val result = super.deleteSurroundingText(beforeLength, afterLength)
                submitCommittedTextIfReady()
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
                submitCommittedTextIfReady()
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
                        commitEnter()
                        true
                    }
                    KeyEvent.KEYCODE_TAB -> {
                        insertCommittedText("\t")
                        true
                    }
                    else -> {
                        val codePoint = event.unicodeChar
                        if (codePoint > 0 && !event.isCtrlPressed && !event.isAltPressed) {
                            insertCommittedText(String(Character.toChars(codePoint)))
                            true
                        } else {
                            super.sendKeyEvent(event)
                        }
                    }
                }
            }

            override fun performEditorAction(editorAction: Int): Boolean {
                commitEnter()
                return true
            }
        }
    }

    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            val result = super.onTextContextMenuItem(id)
            if (result) {
                submitCommittedTextIfReady()
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

    fun finishCompositionAndTakeSessionText(): String {
        clearComposingText()
        submitCommittedText()
        return text?.toString().orEmpty()
    }

    fun restoreSessionText(value: String) {
        if (text?.toString() != value) {
            restoringText = true
            setText(value)
            setSelection(length())
            restoringText = false
        }
    }

    fun retryCommittedText(): Boolean {
        return submitCommittedText()
    }

    private fun submitCommittedTextIfReady(): Boolean {
        return if (hasActiveComposition()) true else submitCommittedText()
    }

    private fun submitCommittedText(): Boolean {
        return onTextCommitted(text?.toString().orEmpty())
    }

    private fun commitEnter(): Boolean {
        clearComposingText()
        return submitCommittedText() && onEnterCommitted()
    }

    private fun hasActiveComposition(): Boolean {
        val editable = text ?: return false
        return BaseInputConnection.getComposingSpanStart(editable) >= 0 ||
            BaseInputConnection.getComposingSpanEnd(editable) >= 0
    }

    private fun insertCommittedText(value: String) {
        val editable = text ?: return
        clearComposingText()
        val start = selectionStart.coerceIn(0, editable.length)
        val end = selectionEnd.coerceIn(start, editable.length)
        editable.replace(start, end, value)
        setSelection(start + value.length)
        submitCommittedText()
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
                if (selectionStart != selectionEnd || selectionStart > 0) {
                    super.onKeyDown(keyCode, event)
                    submitCommittedTextIfReady()
                } else {
                    onKeyCommitted(PcKeyboardKey.Backspace)
                }
                true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (selectionStart != selectionEnd || selectionEnd < length()) {
                    super.onKeyDown(keyCode, event)
                    submitCommittedTextIfReady()
                } else {
                    onKeyCommitted(PcKeyboardKey.Delete)
                }
                true
            }
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                commitEnter()
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                insertCommittedText("\t")
                true
            }
            else -> {
                val codePoint = event.unicodeChar
                if (codePoint > 0) {
                    insertCommittedText(String(Character.toChars(codePoint)))
                    true
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

        private fun CharSequence?.isEnterCommit(): Boolean {
            return this == "\n" || this == "\r" || this == "\r\n"
        }
    }
}
