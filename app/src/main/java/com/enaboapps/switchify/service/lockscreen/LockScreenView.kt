package com.enaboapps.switchify.service.lockscreen

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow

class LockScreenView(
    private val accessibilityService: AccessibilityService
) {
    private lateinit var baseLayout: LinearLayout
    private lateinit var preferenceManager: PreferenceManager
    private var showing = false
    private var timeoutHandler = Handler(Looper.getMainLooper())
    private val INACTIVITY_TIMEOUT = 40000L // 40 seconds

    private val inactivityRunnable = Runnable {
        if (showing) {
            accessibilityService.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
            hide()
        }
    }

    fun setup(context: Context) {
        baseLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(context.resources.getColor(R.color.navy, null))
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
        }
        preferenceManager = PreferenceManager(context)
    }

    fun show() {
        if (showing || !isLockScreenEnabled()) {
            return
        }

        disposeOfLockScreenLayout()
        buildLockScreenLayout()
        showing = true
        startInactivityTimeout()

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,  // Allow watching outside touches
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        SwitchifyAccessibilityWindow.instance.addViewUnderBase(baseLayout, params)
    }

    private fun startInactivityTimeout() {
        timeoutHandler.removeCallbacks(inactivityRunnable)
        timeoutHandler.postDelayed(inactivityRunnable, INACTIVITY_TIMEOUT)
    }

    private fun resetInactivityTimeout() {
        if (showing) {
            startInactivityTimeout()
        }
    }

    fun hide() {
        if (!showing) {
            return
        }

        timeoutHandler.removeCallbacks(inactivityRunnable)
        try {
            SwitchifyAccessibilityWindow.instance.removeViewFromWindow(baseLayout)
            showing = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDestroy() {
        timeoutHandler.removeCallbacks(inactivityRunnable)
    }

    private fun disposeOfLockScreenLayout() {
        baseLayout.removeAllViews()
    }

    private fun buildLockScreenLayout() {
        if (isLockScreenCodeSet()) {
            val textView = TextView(accessibilityService).apply {
                text = "Enter 4-digit code to unlock Switchify"
                setTextColor(accessibilityService.resources.getColor(R.color.white, null))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            }
            baseLayout.addView(textView)

            val codeInput = TextView(accessibilityService).apply {
                setTextColor(accessibilityService.resources.getColor(R.color.white, null))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f)
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                gravity = Gravity.CENTER
                text = ""
                letterSpacing = 0.5f
                layoutParams = LinearLayout.LayoutParams(
                    400,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setMargins(0, 0, 0, 48)
                }
            }
            baseLayout.addView(codeInput)

            val numberPad = LockScreenNumberPadView(accessibilityService).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                setOnNumberClickListener { number ->
                    resetInactivityTimeout()  // Reset timeout on number press
                    val currentText = codeInput.text.toString()
                    if (currentText.length < 4) {
                        val newText = "•".repeat(currentText.length + 1)
                        codeInput.text = newText

                        val actualCode = (codeInput.tag as? String ?: "") + number
                        codeInput.tag = actualCode

                        if (actualCode.length == 4) {
                            if (validateLockScreenCode(actualCode)) {
                                hide()
                            } else {
                                // Wrong code handling
                                codeInput.startAnimation(createShakeAnimation())
                                codeInput.postDelayed({
                                    // Clear the input after animation
                                    codeInput.text = ""
                                    codeInput.tag = ""
                                }, 500)
                            }
                        }
                    }
                }

                setOnDeleteClickListener {
                    resetInactivityTimeout()  // Reset timeout on delete press
                    val currentText = codeInput.text.toString()
                    if (currentText.isNotEmpty()) {
                        val newText = "•".repeat(currentText.length - 1)
                        codeInput.text = newText

                        // Update actual code in tag
                        val actualCode = (codeInput.tag as? String ?: "")
                        if (actualCode.isNotEmpty()) {
                            codeInput.tag = actualCode.substring(0, actualCode.length - 1)
                        }
                    }
                }
            }
            baseLayout.addView(numberPad)
        } else {
            val textView = TextView(accessibilityService).apply {
                text = "Tap the button to unlock Switchify"
                setTextColor(accessibilityService.resources.getColor(R.color.white, null))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            }
            baseLayout.addView(textView)

            val button = Button(accessibilityService).apply {
                background = null
                text = "Unlock"
                setTextColor(accessibilityService.resources.getColor(R.color.white, null))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setAutoSizeTextTypeWithDefaults(AUTO_SIZE_TEXT_TYPE_NONE)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    400,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                setOnClickListener {
                    hide()
                }
            }
            baseLayout.addView(button)
        }
    }

    private fun isLockScreenEnabled(): Boolean {
        return preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_LOCK_SCREEN)
    }

    private fun isLockScreenCodeSet(): Boolean {
        return preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_LOCK_SCREEN_CODE) != ""
    }

    private fun validateLockScreenCode(code: String): Boolean {
        val savedCode =
            preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_LOCK_SCREEN_CODE)
        return code.length == 4 && code == savedCode
    }

    private fun createShakeAnimation(): Animation {
        return TranslateAnimation(0f, 10f, 0f, 0f).apply {
            duration = 50
            repeatMode = Animation.REVERSE
            repeatCount = 5
        }
    }
}