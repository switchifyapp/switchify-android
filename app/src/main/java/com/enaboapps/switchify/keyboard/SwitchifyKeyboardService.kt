package com.enaboapps.switchify.keyboard

import android.graphics.drawable.Drawable
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import androidx.core.content.res.ResourcesCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.keyboard.prediction.PredictionListener
import com.enaboapps.switchify.keyboard.prediction.PredictionManager
import com.enaboapps.switchify.keyboard.prediction.PredictionView
import com.enaboapps.switchify.keyboard.utils.CapsModeHandler
import com.enaboapps.switchify.keyboard.utils.TextParser
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Logger

/**
 * This class is responsible for managing the keyboard service.
 * It extends InputMethodService and implements KeyboardLayoutListener.
 */
class SwitchifyKeyboardService : InputMethodService(), KeyboardLayoutListener, PredictionListener {

    // The main keyboard layout
    private lateinit var keyboardLayout: LinearLayout

    // The global layout listener
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    // The prediction manager
    private var predictionManager: PredictionManager? = null

    // The prediction view
    private lateinit var predictionView: PredictionView

    // The text parser
    private val textParser = TextParser.getInstance()

    /**
     * This method is called when the service is created.
     * It initializes the keyboard accessibility manager and the prediction manager.
     */
    override fun onCreate() {
        super.onCreate()

        Logger.init(this)
        Logger.logEvent("Switchify Keyboard Service Created")

        // Initialize IAPHandler
        IAPHandler.initialize(this)

        // Set the layout listener
        KeyboardLayoutManager.listener = this

        // Initialize the prediction manager
        predictionManager = PredictionManager(this, this)
        predictionManager?.initialize()
    }

    override fun onDestroy() {
        super.onDestroy()
        predictionManager?.destroy()
    }

    /**
     * This method is called when the input view is created.
     * It initializes the keyboard layout and the keyboard accessibility manager.
     */
    override fun onCreateInputView(): View {
        // Create the main keyboard layout programmatically
        keyboardLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            background =
                ResourcesCompat.getDrawable(resources, R.drawable.keyboard_background, null)
        }

        // If running on Android 15 or higher, we need to add an inset to the bottom of the keyboard
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            keyboardLayout.setPadding(0, 0, 0, 140)
        }

        // Initialize the prediction view
        predictionView = PredictionView(this) { prediction ->
            handleKeyPress(prediction)
        }

        // Initialize the keyboard layout
        initializeKeyboardLayout(keyboardLayout)

        return keyboardLayout
    }

    /**
     * This method is called when the input view is started.
     * It updates the keyboard layout and adds the global layout listener.
     */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // Add the global layout listener when the input view is started
        globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        }
        keyboardLayout.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)

        predictionManager?.reloadLanguage()

        info?.let {
            CapsModeHandler.updateCapsMode(it)
        }

        resetKeyboardLayout()

        updateTextState()
    }

    /**
     * This method is called when the input view is finished.
     * It removes the global layout listener.
     */
    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        // Remove the global layout listener when the input view is finished
        keyboardLayout.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
    }

    /**
     * This method resets the keyboard layout.
     */
    private fun resetKeyboardLayout() {
        KeyboardLayoutManager.setLayoutState(KeyboardLayoutState.Lower)

        // If the input field requires a number pad, switch to the number pad layout
        if (currentInputEditorInfo?.inputType == EditorInfo.TYPE_CLASS_NUMBER ||
            currentInputEditorInfo?.inputType == EditorInfo.TYPE_CLASS_PHONE
        ) {
            KeyboardLayoutManager.switchLayout(KeyboardLayoutType.NumPad)
        } else {
            KeyboardLayoutManager.switchLayout(KeyboardLayoutType.AlphabeticLower)
        }
    }

    /**
     * This method is called when the text selection changes.
     */
    override fun onUpdateSelection(
        oldSelStart: Int,
        oldSelEnd: Int,
        newSelStart: Int,
        newSelEnd: Int,
        candidatesStart: Int,
        candidatesEnd: Int
    ) {
        super.onUpdateSelection(
            oldSelStart,
            oldSelEnd,
            newSelStart,
            newSelEnd,
            candidatesStart,
            candidatesEnd
        )

        updateTextState()
    }

    /**
     * This method is called when the text changes.
     */
    private fun updateTextState() {
        currentInputConnection?.let {
            val text = it.getTextBeforeCursor(Int.MAX_VALUE, 0).toString()
            textParser.parseText(text)
            predictionManager?.predict(textParser)
            updateShiftState()
        }
    }

    /**
     * This method is called when the predictions are available.
     */
    override fun onPredictionsAvailable(predictions: List<String>) {
        predictionView.setPredictions(predictions)
        predictionView.updateCase()
        println("Predictions available: $predictions")
    }

    /**
     * This method initializes the keyboard layout.
     * It creates a new row layout for each row in the current layout,
     * and a new key button for each key type in the row.
     */
    private fun initializeKeyboardLayout(keyboardLayout: LinearLayout) {
        // Clear the keyboard layout
        keyboardLayout.removeAllViews()

        // Set up the predictions view if we need it for the current layout
        if (KeyboardLayoutManager.isAlphabeticLayout()) {
            keyboardLayout.addView(predictionView)
        }

        KeyboardLayoutManager.currentLayout.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    MATCH_PARENT,
                    WRAP_CONTENT
                )
            }

            for (type in row) {
                // Skip the tab key on a non-tablet device
                if (type is KeyType.Tab && !ScreenUtils.isTablet(this)) {
                    continue
                }

                // Create the key button
                val keyButton = KeyboardKey(this).apply {
                    // Set a higher weight for the space key
                    val weight = if (type is KeyType.Space) 3f else 1f
                    layoutParams =
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, weight)
                    tapAction = {
                        handleKeyPress(type)
                    }
                    if (type is KeyType.Backspace) {
                        holdAction = {
                            handleKeyPress(type)
                        }
                    }
                    if (getDrawableResource(type) != null) {
                        setKeyContent(
                            drawable = getDrawableResource(type),
                            contentDescription = getContentDescriptionOfKey(type)
                        )
                    } else {
                        setKeyContent(
                            text = type.toString(),
                            contentDescription = getContentDescriptionOfKey(type)
                        )
                    }
                    if (type is KeyType.ShiftCaps) {
                        setPinned(KeyboardLayoutManager.currentLayoutState != KeyboardLayoutState.Lower)
                    }
                }
                rowLayout.addView(keyButton)
            }

            keyboardLayout.addView(rowLayout)
        }
    }

    /**
     * This function returns the correct content description for the given key type.
     *
     * @param keyType the key type.
     * @return the content description.
     */
    private fun getContentDescriptionOfKey(keyType: KeyType): String {
        return when (keyType) {
            is KeyType.Character -> keyType.char.toString()
            is KeyType.Special -> keyType.symbol
            is KeyType.Prediction -> keyType.prediction
            is KeyType.Backspace -> "Backspace"
            is KeyType.DeleteWord -> "Delete Word"
            is KeyType.Clear -> "Clear"
            is KeyType.ImeAction -> "IME Action"
            is KeyType.Return -> "Return"
            is KeyType.Tab -> "Tab"
            is KeyType.LeftArrow -> "Left Arrow"
            is KeyType.RightArrow -> "Right Arrow"
            is KeyType.UpArrow -> "Up Arrow"
            is KeyType.DownArrow -> "Down Arrow"
            is KeyType.Cut -> "Cut"
            is KeyType.Copy -> "Copy"
            is KeyType.Paste -> "Paste"
            is KeyType.SelectAll -> "Select All"
            is KeyType.SwitchToNextInput -> "Switch to Next Input"
            is KeyType.ShiftCaps -> "Shift Caps"
            is KeyType.SwitchToMenu -> "Switch to Menu"
            is KeyType.CloseMenu -> "Close Menu"
            else -> "Unknown"
        }
    }

    /**
     * This function returns the correct drawable resource for the given key type.
     *
     * @param keyType the key type.
     * @return the drawable resource.
     */
    private fun getDrawableResource(keyType: KeyType): Drawable? {
        if (keyType is KeyType.Backspace) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_backspace, null)
        }
        if (keyType is KeyType.DeleteWord) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_deleteword, null)
        }
        if (keyType is KeyType.Clear) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_bin, null)
        }
        if (keyType is KeyType.ImeAction) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_ime_action, null)
        }
        if (keyType is KeyType.Return) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_return, null)
        }
        if (keyType is KeyType.Tab) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_tab, null)
        }
        if (keyType is KeyType.LeftArrow) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_cursor_left, null)
        }
        if (keyType is KeyType.RightArrow) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_cursor_right, null)
        }
        if (keyType is KeyType.UpArrow) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_cursor_up, null)
        }
        if (keyType is KeyType.DownArrow) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_cursor_down, null)
        }
        if (keyType is KeyType.HideKeyboard) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_hide, null)
        }
        if (keyType is KeyType.SwitchToNextInput) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_globe, null)
        }
        if (keyType is KeyType.ShiftCaps) {
            return if (KeyboardLayoutManager.currentLayoutState == KeyboardLayoutState.Lower ||
                KeyboardLayoutManager.currentLayoutState == KeyboardLayoutState.Shift
            ) {
                ResourcesCompat.getDrawable(resources, R.drawable.ic_shift, null)
            } else {
                ResourcesCompat.getDrawable(resources, R.drawable.ic_caps, null)
            }
        }
        if (keyType is KeyType.SwitchToMenu) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_menu, null)
        }
        if (keyType is KeyType.CloseMenu) {
            return ResourcesCompat.getDrawable(resources, R.drawable.ic_keyboard_menu_close, null)
        }
        return null
    }

    /**
     * This method updates the shift state of the keyboard based on the current text.
     */
    private fun updateShiftState() {
        if (KeyboardLayoutManager.currentLayoutState == KeyboardLayoutState.Caps && KeyboardLayoutManager.isAlphabeticLayout()) {
            return
        }

        val isNewSentence = textParser.isNewSentence()
        val isNewWord = textParser.isNewWord()
        val mode = CapsModeHandler.currentCapsMode
        if (isNewSentence && mode == CapsModeHandler.CapsMode.SENTENCES) {
            KeyboardLayoutManager.setLayoutState(KeyboardLayoutState.Shift)
        } else if (isNewWord && mode == CapsModeHandler.CapsMode.WORDS) {
            KeyboardLayoutManager.setLayoutState(KeyboardLayoutState.Shift)
        } else if (mode == CapsModeHandler.CapsMode.CHARACTERS) {
            KeyboardLayoutManager.setLayoutState(KeyboardLayoutState.Caps)
        } else {
            KeyboardLayoutManager.setLayoutState(KeyboardLayoutState.Lower)
        }
    }

    /**
     * This method handles key press events.
     * It performs different actions based on the type of the key.
     */
    private fun handleKeyPress(keyType: KeyType) {
        when (keyType) {
            is KeyType.Character -> {
                val text = keyType.char
                currentInputConnection.commitText(text, 1)
            }

            is KeyType.Special -> {
                val text = keyType.symbol
                if (textParser.shouldFormatSpecialCharacter(text[0])) {
                    val whitespace = textParser.getLengthOfWhitespacesAtEndOfLatestSentence()
                    currentInputConnection.deleteSurroundingText(whitespace, 0)
                    currentInputConnection.commitText(text, 1)
                    currentInputConnection.commitText(" ", 1)
                } else {
                    currentInputConnection.commitText(text, 1)
                }
            }

            is KeyType.Prediction -> {
                val text = CapsModeHandler.getCapitalizedText(keyType.prediction)
                val currentWordLength =
                    textParser.getWordFromLatestSentenceBySubtractingNumberFromLastIndex(0).length
                currentInputConnection.deleteSurroundingText(currentWordLength, 0)
                currentInputConnection.commitText(text, 1)
                currentInputConnection.commitText(" ", 1)
            }

            KeyType.ShiftCaps -> {
                KeyboardLayoutManager.toggleState()
                predictionView.updateCase()
            }

            KeyType.HideKeyboard -> {
                requestHideSelf(0)
            }

            KeyType.SwitchToNextInput -> {
                if (!switchToNextInputMethod(false)) {
                    switchToPreviousInputMethod()
                }
            }

            KeyType.SwitchToAlphabetic -> {
                if (KeyboardLayoutManager.currentLayoutState == KeyboardLayoutState.Shift ||
                    KeyboardLayoutManager.currentLayoutState == KeyboardLayoutState.Caps
                ) {
                    KeyboardLayoutManager.switchLayout(KeyboardLayoutType.AlphabeticUpper)
                } else {
                    KeyboardLayoutManager.switchLayout(KeyboardLayoutType.AlphabeticLower)
                }
            }

            KeyType.Space -> {
                currentInputConnection.commitText(" ", 1)
                if (!KeyboardLayoutManager.isAlphabeticLayout()) {
                    KeyboardLayoutManager.switchLayout(KeyboardLayoutType.AlphabeticLower)
                    updateTextState()
                }
            }

            KeyType.ImeAction -> {
                handleImeAction()
            }

            KeyType.Return -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_ENTER
                    )
                )
                if (!KeyboardLayoutManager.isAlphabeticLayout()) {
                    KeyboardLayoutManager.switchLayout(KeyboardLayoutType.AlphabeticLower)
                    updateTextState()
                }
            }

            KeyType.Tab -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_TAB
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_TAB
                    )
                )
            }

            KeyType.Backspace -> {
                currentInputConnection.deleteSurroundingText(1, 0)
            }

            KeyType.DeleteWord -> {
                val count = textParser.getLengthOfWordToDelete()
                currentInputConnection.deleteSurroundingText(count, 0)
            }

            KeyType.Clear -> {
                val allCount = textParser.getAllText().length
                currentInputConnection.deleteSurroundingText(allCount, 0)
            }

            KeyType.LeftArrow -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_LEFT
                    )
                )
            }

            KeyType.RightArrow -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_RIGHT
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_RIGHT
                    )
                )
            }

            KeyType.UpArrow -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_UP
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_UP
                    )
                )
            }

            KeyType.DownArrow -> {
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_DOWN,
                        KeyEvent.KEYCODE_DPAD_DOWN
                    )
                )
                currentInputConnection.sendKeyEvent(
                    KeyEvent(
                        KeyEvent.ACTION_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN
                    )
                )
            }

            KeyType.Cut -> {
                currentInputConnection.performContextMenuAction(android.R.id.cut)
            }

            KeyType.Copy -> {
                currentInputConnection.performContextMenuAction(android.R.id.copy)
            }

            KeyType.Paste -> {
                currentInputConnection.performContextMenuAction(android.R.id.paste)
            }

            KeyType.SelectAll -> {
                currentInputConnection.performContextMenuAction(android.R.id.selectAll)
            }

            KeyType.SwitchToSymbols -> {
                KeyboardLayoutManager.switchLayout(KeyboardLayoutType.SymbolsPageOne)
            }

            KeyType.SwitchToSymbolsOne -> {
                KeyboardLayoutManager.switchLayout(KeyboardLayoutType.SymbolsPageOne)
            }

            KeyType.SwitchToSymbolsTwo -> {
                KeyboardLayoutManager.switchLayout(KeyboardLayoutType.SymbolsPageTwo)
            }

            KeyType.SwitchToEdit -> {
                KeyboardLayoutManager.switchLayout(KeyboardLayoutType.Edit)
            }

            KeyType.SwitchToMenu -> {
                KeyboardLayoutManager.switchLayout(KeyboardLayoutType.Menu)
            }

            KeyType.CloseMenu -> {
                KeyboardLayoutManager.switchToPreviousLayout()
            }
        }
    }

    /**
     * This method handles the IME action key press event.
     */
    private fun handleImeAction() {
        val inputConnection = currentInputConnection
        val currentEditorInfo = currentInputEditorInfo

        inputConnection?.let { ic ->
            currentEditorInfo?.let { editorInfo ->
                when (val actionId = editorInfo.imeOptions and EditorInfo.IME_MASK_ACTION) {
                    EditorInfo.IME_ACTION_SEARCH,
                    EditorInfo.IME_ACTION_SEND,
                    EditorInfo.IME_ACTION_GO,
                    EditorInfo.IME_ACTION_DONE ->
                        // Trigger the action.
                        ic.performEditorAction(actionId)

                    else -> {
                        // If no specific action is specified, simulate ENTER key events.
                        // Or you can opt to perform a custom default action here.
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
                        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                    }
                }
            }
        }
    }

    /**
     * This method is called when the keyboard layout changes.
     * It updates the keyboard layout.
     */
    override fun onLayoutChanged(layoutType: KeyboardLayoutType) {
        // Update the keyboard layout when the layout changes
        keyboardLayout.removeAllViews()
        initializeKeyboardLayout(keyboardLayout)
    }
}
