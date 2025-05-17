package com.enaboapps.switchify.service.window

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.activities.ui.theme.SwitchifyTheme
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.utils.Resources

/**
 * ServiceMessageHUD is responsible for displaying overlay messages at the bottom of the screen
 * using Jetpack Compose. It supports two types of messages: disappearing and permanent,
 * with configurable display durations.
 *
 * Usage:
 * 1. Initialize in your AccessibilityService:
 *    ```
 *    class YourAccessibilityService : AccessibilityService() {
 *        override fun onCreate() {
 *            ServiceMessageHUD.instance.setup(applicationContext)
 *        }
 *    }
 *    ```
 *
 * 2. Show messages:
 *    ```
 *    ServiceMessageHUD.instance.showMessage(
 *        R.string.your_message,
 *        ServiceMessageHUD.MessageType.DISAPPEARING,
 *        ServiceMessageHUD.Time.MEDIUM
 *    )
 *    ```
 *
 * 3. Clean up:
 *    ```
 *    override fun onDestroy() {
 *        ServiceMessageHUD.instance.dispose()
 *        super.onDestroy()
 *    }
 *    ```
 */
class ServiceMessageHUD private constructor() {
    companion object {
        /**
         * Singleton instance of ServiceMessageHUD.
         */
        val instance: ServiceMessageHUD by lazy { ServiceMessageHUD() }
        private const val TAG = "ServiceMessageHUDCompose"
    }

    private var applicationCtx: Context? = null
    private var messageComposeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    // State holders for Compose
    private var currentMessageString = mutableStateOf<String?>(null)
    private var isMessageVisible = mutableStateOf(false)

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
     */
    enum class Time(val milliseconds: Long) {
        /** Short duration (1.5 seconds) */
        SHORT(1500),

        /** Medium duration (5 seconds) */
        MEDIUM(5000),

        /** Long duration (10 seconds) */
        LONG(10000)
    }

    /**
     * Sets up the ServiceMessageHUD with the necessary Context.
     * This must be called before showing any messages, typically in your AccessibilityService's onCreate.
     *
     * @param appCtx The application context used for creating views and accessing resources
     */
    fun setup(appCtx: Context) {
        this.applicationCtx = appCtx.applicationContext
        Log.d(TAG, "ServiceMessageHUD setup with AppContext: $applicationCtx")
    }

    /**
     * Ensures the ComposeView is created and properly configured.
     * This is called internally when needed and handles the creation of the Compose UI.
     */
    private fun ensureMessageComposeViewIsCreated() {
        val ctxForView = applicationCtx ?: run {
            Log.e(TAG, "ApplicationContext is null. Cannot create ComposeView. Call setup() first.")
            return
        }

        if (messageComposeView == null) {
            Log.d(TAG, "Creating MessageComposeView")
            messageComposeView = AccessibilityComposeView(ctxForView) {
                SwitchifyTheme {
                    ServiceMessageUi(
                        message = currentMessageString.value,
                        isVisible = isMessageVisible.value
                    )
                }
            }
        }
    }

    /**
     * The main Composable UI for the message overlay.
     * This implements a card-based design with animations for appearance and disappearance.
     *
     * @param message The text message to display
     * @param isVisible Whether the message should be visible
     */
    @Composable
    private fun ServiceMessageUi(message: String?, isVisible: Boolean) {
        val savedMessage = rememberSaveable { message.toString() }
        val savedIsVisible = rememberSaveable { isVisible }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = savedIsVisible,
                enter = slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeIn(animationSpec = tween(durationMillis = 300)),
                exit = slideOutVertically(
                    targetOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300)
                ) + fadeOut(animationSpec = tween(durationMillis = 300))
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                        .padding(16.dp)
                        .shadow(8.dp, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Text(
                        text = savedMessage,
                        modifier = Modifier
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .fillMaxWidth(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            }
        }
    }

    /**
     * Internal implementation for showing messages. Handles both simple messages and those with
     * format arguments.
     *
     * @param messageResId The resource ID of the message to display
     * @param args Optional arguments for formatted strings
     * @param messageType The type of message (DISAPPEARING or PERMANENT)
     * @param time The duration to show the message (for DISAPPEARING messages)
     */
    private fun showMessageInternal(
        messageResId: Int,
        args: Array<out Any>?,
        messageType: MessageType,
        time: Time
    ) {
        applicationCtx ?: run {
            Log.e(TAG, "ApplicationContext is null, cannot show message. Call setup() first.")
            return
        }

        val messageText = if (args != null && args.isNotEmpty()) {
            Resources.getString(messageResId, *args)
        } else {
            Resources.getString(messageResId)
        }

        handler.post {
            Log.d(TAG, "Showing message: \"$messageText\", type: $messageType, time: $time")

            // Cancel any pending hide operations
            handler.removeCallbacksAndMessages(null)

            // Update message and visibility
            currentMessageString.value = messageText
            isMessageVisible.value = true

            // Ensure view exists and is added to window
            ensureMessageComposeViewIsCreated()
            messageComposeView?.let { view ->
                try {
                    // Remove existing view if it's already in the window
                    if (view.parent != null) {
                        SwitchifyAccessibilityWindow.instance.removeView(view)
                    }
                    // Add the view back with new content
                    SwitchifyAccessibilityWindow.instance.addViewToBottom(view)
                    Log.d(TAG, "Message ComposeView added to window.")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add ComposeView to window", e)
                    e.printStackTrace()
                    currentMessageString.value = null
                    return@post
                }
            }

            if (messageType == MessageType.DISAPPEARING) {
                handler.postDelayed({ hideMessage() }, time.milliseconds)
            }
        }
    }

    /**
     * Shows a message using a string resource ID.
     *
     * @param messageResId The resource ID of the message to display
     * @param messageType The type of message (DISAPPEARING or PERMANENT)
     * @param time The duration to show the message (for DISAPPEARING messages)
     */
    fun showMessage(
        messageResId: Int,
        messageType: MessageType,
        time: Time = Time.MEDIUM
    ) {
        showMessageInternal(messageResId, null, messageType, time)
    }

    /**
     * Shows a message using a string resource ID with format arguments.
     *
     * @param messageResId The resource ID of the message to display
     * @param messageArgs The arguments to format into the message string
     * @param messageType The type of message (DISAPPEARING or PERMANENT)
     * @param time The duration to show the message (for DISAPPEARING messages)
     */
    fun showMessage(
        messageResId: Int,
        messageArgs: Array<out Any>,
        messageType: MessageType,
        time: Time = Time.MEDIUM
    ) {
        showMessageInternal(messageResId, messageArgs, messageType, time)
    }

    /**
     * Hides the currently displayed message with an animation.
     */
    fun hideMessage() {
        if (!isMessageVisible.value && currentMessageString.value == null) {
            messageComposeView?.let { if (it.parent != null) removeViewFromWindow() }
            return
        }
        isMessageVisible.value = false
        val animationDuration = 350L
        handler.postDelayed({
            if (!isMessageVisible.value) {
                removeViewFromWindow()
                currentMessageString.value = null
            }
        }, animationDuration)
    }

    /**
     * Removes the message view from the window manager.
     */
    private fun removeViewFromWindow() {
        messageComposeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(view)
                Log.d(TAG, "ComposeView removed from window successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove ComposeView from window", e)
            }
        }
    }
}