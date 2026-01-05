package com.enaboapps.switchify.service.window

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

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

/**
 * Defines the visual severity/type of a message with associated colors and icons.
 */
sealed class MessageSeverity(
    val containerColor: @Composable () -> Color,
    val onContainerColor: @Composable () -> Color,
    val icon: ImageVector
) {
    data object Info : MessageSeverity(
        containerColor = { MaterialTheme.colorScheme.surfaceVariant },
        onContainerColor = { MaterialTheme.colorScheme.onSurfaceVariant },
        icon = Icons.Filled.Info
    )

    data object Success : MessageSeverity(
        containerColor = {
            if (isSystemInDarkTheme()) Color(0xFF1B5E20) // Dark green
            else Color(0xFFC8E6C9) // Light green
        },
        onContainerColor = {
            if (isSystemInDarkTheme()) Color(0xFFA5D6A7) // Light text
            else Color(0xFF1B5E20) // Dark text
        },
        icon = Icons.Filled.CheckCircle
    )

    data object Warning : MessageSeverity(
        containerColor = {
            if (isSystemInDarkTheme()) Color(0xFFE65100) // Dark orange
            else Color(0xFFFFE0B2) // Light orange
        },
        onContainerColor = {
            if (isSystemInDarkTheme()) Color(0xFFFFCC80) // Light text
            else Color(0xFFE65100) // Dark text
        },
        icon = Icons.Filled.Warning
    )

    data object Error : MessageSeverity(
        containerColor = { MaterialTheme.colorScheme.errorContainer },
        onContainerColor = { MaterialTheme.colorScheme.onErrorContainer },
        icon = Icons.Filled.Error
    )
}

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
    private var currentMessageSeverity = mutableStateOf<MessageSeverity>(MessageSeverity.Info)
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
                val message = currentMessageString.value
                val visible = isMessageVisible.value
                val severity = currentMessageSeverity.value

                ServiceMessageUi(
                    message = message,
                    isVisible = visible,
                    severity = severity,
                    onDismiss = { hideMessage() }
                )
            }
        }
    }

    /**
     * The main Composable UI for the message overlay.
     * This implements a modern card-based design with spring animations, icons, and swipe gestures.
     *
     * @param message The text message to display
     * @param isVisible Whether the message should be visible
     * @param severity The visual severity/type of the message
     * @param onDismiss Callback when message is dismissed
     */
    @Composable
    private fun ServiceMessageUi(
        message: String?,
        isVisible: Boolean,
        severity: MessageSeverity,
        onDismiss: () -> Unit
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 0.7f,  // More bouncy
                        stiffness = 300f       // Slower for visibility
                    )
                ) + fadeIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = 400f
                    )
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 600f      // Quick exit
                    )
                ) + fadeOut(
                    animationSpec = spring(
                        dampingRatio = 1.0f,
                        stiffness = 600f
                    )
                )
            ) {
                SwipeableMessageCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    onDismiss = onDismiss
                ) {
                    MessageCard(
                        message = message ?: "",
                        severity = severity
                    )
                }
            }
        }
    }

    /**
     * A swipeable wrapper for the message card that handles horizontal swipe gestures.
     *
     * @param modifier Modifier for the container
     * @param onDismiss Callback when the card is swiped away
     * @param content The content to display (typically MessageCard)
     */
    @Composable
    private fun SwipeableMessageCard(
        modifier: Modifier = Modifier,
        onDismiss: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val offsetX = remember { Animatable(0f) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(Unit) {
            offsetX.snapTo(0f)
        }

        Box(
            modifier = modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val threshold = size.width * 0.4f
                            if (abs(offsetX.value) > threshold) {
                                // Dismiss - animate out fully
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = if (offsetX.value > 0) size.width.toFloat() else -size.width.toFloat(),
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                    onDismiss()
                                }
                            } else {
                                // Snap back
                                scope.launch {
                                    offsetX.animateTo(
                                        targetValue = 0f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount)
                            }
                        }
                    )
                }
        ) {
            content()
        }
    }

    /**
     * The visual message card with icon and text.
     *
     * @param message The text to display
     * @param severity The visual severity/type determining colors and icon
     */
    @Composable
    private fun MessageCard(
        message: String,
        severity: MessageSeverity
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 600.dp),
            shape = MaterialTheme.shapes.extraSmall,
            colors = CardDefaults.cardColors(
                containerColor = severity.containerColor().copy(alpha = 0.95f),
                contentColor = severity.onContainerColor()
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    imageVector = severity.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = severity.onContainerColor()
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = message,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 26.sp,
                    overflow = TextOverflow.Ellipsis,
                    color = severity.onContainerColor()
                )
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
     * @param severity The visual severity/type of the message
     */
    private fun showMessageInternal(
        messageResId: Int,
        args: Array<out Any>?,
        messageType: MessageType,
        time: Time,
        severity: MessageSeverity
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

            // Ensure view exists and is added to window first
            ensureMessageComposeViewIsCreated()
            messageComposeView?.let { view ->
                try {
                    // Add view to window if not already there
                    if (view.parent == null) {
                        SwitchifyAccessibilityWindow.instance.addViewToBottom(view)
                        Log.d(TAG, "Message ComposeView added to window.")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add ComposeView to window", e)
                    e.printStackTrace()
                    currentMessageString.value = null
                    return@post
                }
            }

            // Update message, severity, and visibility (triggers animation)
            currentMessageString.value = messageText
            currentMessageSeverity.value = severity
            isMessageVisible.value = true

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
     * @param severity The visual severity/type of the message (default: Info)
     */
    fun showMessage(
        messageResId: Int,
        messageType: MessageType,
        time: Time = Time.MEDIUM,
        severity: MessageSeverity = MessageSeverity.Info
    ) {
        showMessageInternal(messageResId, null, messageType, time, severity)
    }

    /**
     * Shows a message using a string resource ID with format arguments.
     *
     * @param messageResId The resource ID of the message to display
     * @param messageArgs The arguments to format into the message string
     * @param messageType The type of message (DISAPPEARING or PERMANENT)
     * @param time The duration to show the message (for DISAPPEARING messages)
     * @param severity The visual severity/type of the message (default: Info)
     */
    fun showMessage(
        messageResId: Int,
        messageArgs: Array<out Any>,
        messageType: MessageType,
        time: Time = Time.MEDIUM,
        severity: MessageSeverity = MessageSeverity.Info
    ) {
        showMessageInternal(messageResId, messageArgs, messageType, time, severity)
    }

    /**
     * Hides the currently displayed message with an animation.
     */
    private fun hideMessage() {
        if (!isMessageVisible.value && currentMessageString.value == null) {
            return
        }
        isMessageVisible.value = false
        // Don't remove view - keep it in window for next message
        handler.postDelayed({
            if (!isMessageVisible.value) {
                currentMessageString.value = null
            }
        }, 800L) // Increased delay for spring animation to complete
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

    /**
     * Cleans up the ServiceMessageHUD, removing any views and resetting state.
     */
    fun dispose() {
        Log.d(TAG, "Disposing ServiceMessageHUD")
        handler.removeCallbacksAndMessages(null)
        messageComposeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(view)
                messageComposeView = null
                currentMessageString.value = null
                isMessageVisible.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dispose ServiceMessageHUD", e)
            }
        }
        applicationCtx = null
        Log.d(TAG, "ServiceMessageHUD disposed successfully.")
    }
}