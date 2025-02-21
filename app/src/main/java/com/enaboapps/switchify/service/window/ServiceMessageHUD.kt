package com.enaboapps.switchify.service.window

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.utils.ScreenUtils
import com.enaboapps.switchify.utils.Resources

/**
 * ServiceMessageHUD is responsible for displaying overlay messages on top of all other application windows.
 * It supports two types of messages: disappearing and permanent, with configurable display durations.
 * This class implements immediate message replacement, ensuring the most recent message is always displayed.
 */
class ServiceMessageHUD private constructor() {
    companion object {
        /**
         * Singleton instance of ServiceMessageHUD.
         */
        val instance: ServiceMessageHUD by lazy { ServiceMessageHUD() }
        private const val TAG = "ServiceMessageHUD"
    }

    private var context: Context? = null
    private var switchifyAccessibilityWindow: SwitchifyAccessibilityWindow =
        SwitchifyAccessibilityWindow.instance
    private var messageResId: Int = 0
    private var messageArgs: Array<out Any> = emptyArray()
    private var shownMessageType: MessageType? = null
    private var messageView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Sets up the ServiceMessageHUD with the necessary context.
     *
     * @param context The application context.
     */
    fun setup(context: Context) {
        this.context = context
        Log.d(TAG, "ServiceMessageHUD setup with context: $context")
    }

    /**
     * Defines the types of messages that can be displayed.
     */
    enum class MessageType {
        /**
         * A message that automatically disappears after a specified duration.
         */
        DISAPPEARING,

        /**
         * A message that remains visible until explicitly cleared or replaced.
         */
        PERMANENT
    }

    /**
     * Defines preset durations for disappearing messages.
     *
     * @property milliseconds The duration in milliseconds.
     */
    enum class Time(val milliseconds: Long) {
        SHORT(1000),
        MEDIUM(5000),
        LONG(10000)
    }

    /**
     * Creates or updates the message view with the specified message.
     * If the messageView doesn't exist, it creates a new LinearLayout with a TextView.
     * If it already exists, it updates the text of the existing TextView.
     */
    private fun createOrUpdateMessageView() {
        Log.d(TAG, "Creating or updating message view")
        context?.let { context ->
            if (messageView == null) {
                // Create a new LinearLayout as the container
                messageView = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = ResourcesCompat.getDrawable(
                        context.resources,
                        R.drawable.rounded_corners,
                        null
                    )

                    // Set padding for the container
                    val padding = ScreenUtils.dpToPx(context, 16)
                    setPadding(padding, padding, padding, padding)

                    // Set layout parameters for the container
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                }

                // Create a TextView for displaying the message
                val textView = TextView(context).apply {
                    setTextColor(Color.WHITE)
                    textSize = 16f
                    gravity = Gravity.CENTER
                    setTextIsSelectable(false)
                    // Allow multiple lines and wrap content
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    maxLines = Int.MAX_VALUE
                }

                messageView?.addView(textView)
            }

            // Update the text of the TextView
            if (messageArgs.isNotEmpty()) {
                (messageView?.getChildAt(0) as? TextView)?.text =
                    Resources.getString(messageResId, *messageArgs)
            } else {
                (messageView?.getChildAt(0) as? TextView)?.text = Resources.getString(messageResId)
            }
            Log.d(TAG, "Message view created or updated successfully")
        } ?: Log.e(TAG, "Context is null, cannot create or update message view")
    }

    /**
     * Adds the message view to the window.
     * This method is called when a new message view is created and needs to be displayed.
     */
    private fun addViewToWindow() {
        handler.post {
            messageView?.let { view ->
                try {
                    // Ensure the view is not already added
                    if (view.parent == null) {
                        switchifyAccessibilityWindow.addViewToBottom(
                            view,
                            ScreenUtils.dpToPx(context!!, 16)
                        )
                        Log.d(TAG, "Message view added to window successfully")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add view to window", e)
                }
            } ?: Log.e(TAG, "MessageView is null, cannot add to window")
        }
    }

    /**
     * Displays or updates a message on the screen using a string resource ID.
     *
     * @param messageResId The resource ID of the message text to be displayed.
     * @param messageType The type of the message (DISAPPEARING or PERMANENT).
     * @param time The duration for which a DISAPPEARING message should be shown. Ignored for PERMANENT messages.
     */
    fun showMessage(messageResId: Int, messageType: MessageType, time: Time = Time.MEDIUM) {
        Log.d(
            TAG,
            "Showing message: ${Resources.getString(messageResId)}, type: $messageType, time: $time"
        )

        // Cancel any pending hide operations
        handler.removeCallbacksAndMessages(null)

        this.messageResId = messageResId
        this.shownMessageType = messageType

        handler.post {
            createOrUpdateMessageView()

            if (messageView?.parent == null) {
                addViewToWindow()
            }

            if (messageType == MessageType.DISAPPEARING) {
                handler.postDelayed({ hideMessage() }, time.milliseconds)
                Log.d(TAG, "Scheduled message to disappear after ${time.milliseconds}ms")
            }
        }
    }

    /**
     * Displays or updates a message on the screen using a string resource ID and arguments.
     *
     * @param messageResId The resource ID of the message text to be displayed.
     * @param messageArgs The arguments to be used in the message text.
     * @param messageType The type of the message (DISAPPEARING or PERMANENT).
     * @param time The duration for which a DISAPPEARING message should be shown. Ignored for PERMANENT messages.
     */
    fun showMessage(
        messageResId: Int,
        messageArgs: Array<out Any>,
        messageType: MessageType,
        time: Time = Time.MEDIUM
    ) {
        Log.d(
            TAG,
            "Showing message: ${
                Resources.getString(
                    messageResId,
                    *messageArgs
                )
            }, type: $messageType, time: $time"
        )

        // Cancel any pending hide operations
        handler.removeCallbacksAndMessages(null)

        this.messageResId = messageResId
        this.messageArgs = messageArgs
        this.shownMessageType = messageType

        handler.post {
            createOrUpdateMessageView()

            if (messageView?.parent == null) {
                addViewToWindow()
            }

            if (messageType == MessageType.DISAPPEARING) {
                handler.postDelayed({ hideMessage() }, time.milliseconds)
                Log.d(TAG, "Scheduled message to disappear after ${time.milliseconds}ms")
            }
        }
    }

    /**
     * Hides and removes the currently displayed message from the screen.
     * This method is called automatically for DISAPPEARING messages after their display time has elapsed,
     * or can be called manually to remove a message before its time has elapsed.
     */
    private fun hideMessage() {
        Log.d(TAG, "Hiding message")
        messageView?.let { view ->
            handler.post {
                try {
                    switchifyAccessibilityWindow.removeView(view)
                    Log.d(TAG, "Message view removed from window successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to remove view from window", e)
                } finally {
                    clearMessage()
                }
            }
        } ?: Log.d(TAG, "No message view to hide")
    }

    /**
     * Clears the current message, hiding it from the screen and resetting internal state.
     * This method can be called to explicitly remove any displayed message and reset the HUD state.
     */
    private fun clearMessage() {
        Log.d(TAG, "Clearing message")
        messageResId = 0
        messageArgs = emptyArray()
        shownMessageType = null
        messageView = null
    }
}