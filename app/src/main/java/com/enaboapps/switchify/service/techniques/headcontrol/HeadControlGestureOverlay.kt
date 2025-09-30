package com.enaboapps.switchify.service.techniques.headcontrol

import android.content.Context
import android.view.ViewGroup
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.enaboapps.switchify.service.techniques.AccessTechniqueUIBase
import com.enaboapps.switchify.service.utils.ScreenUtils

/**
 * Compose-based overlay that displays progress bars for head control facial expression gestures.
 * Shows in top right corner with real-time progress feedback for select and menu gestures.
 */
class HeadControlGestureOverlay(private val context: Context) : AccessTechniqueUIBase(),
    LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private var composeView: ComposeView? = null
    private val settings = HeadControlSettings(context)

    // Lifecycle components for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // Auto-sizing with margin
    private val margin = 16

    // Progress state
    private var selectProgress by mutableFloatStateOf(0f)
    private var menuProgress by mutableFloatStateOf(0f)
    private var selectActive by mutableStateOf(false)
    private var menuActive by mutableStateOf(false)
    private var selectGestureText by mutableStateOf("😊 Select")
    private var menuGestureText by mutableStateOf("😉 Menu")

    // Progress tracking
    private var selectStartTime: Long = 0
    private var menuStartTime: Long = 0
    private var selectHoldTime: Long = 0
    private var menuHoldTime: Long = 0

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun showOverlay() {
        if (composeView == null) {
            createComposeView()
        }
        updateGestureLabels()
        updateHoldTimes()
        positionOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
    }

    fun hideOverlay() {
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        composeView?.let { removeView(it) }
        composeView = null
        resetProgress()
    }

    fun updateSelectProgress(isActive: Boolean) {
        if (isActive) {
            if (selectStartTime == 0L) {
                selectStartTime = System.currentTimeMillis()
            }
            val elapsed = System.currentTimeMillis() - selectStartTime
            selectProgress = (elapsed.toFloat() / selectHoldTime).coerceIn(0f, 1f)
            selectActive = true
        } else {
            selectStartTime = 0L
            selectProgress = 0f
            selectActive = false
        }
    }

    fun updateMenuProgress(isActive: Boolean) {
        if (isActive) {
            if (menuStartTime == 0L) {
                menuStartTime = System.currentTimeMillis()
            }
            val elapsed = System.currentTimeMillis() - menuStartTime
            menuProgress = (elapsed.toFloat() / menuHoldTime).coerceIn(0f, 1f)
            menuActive = true
        } else {
            menuStartTime = 0L
            menuProgress = 0f
            menuActive = false
        }
    }

    fun resetProgress() {
        selectStartTime = 0L
        menuStartTime = 0L
        selectProgress = 0f
        menuProgress = 0f
        selectActive = false
        menuActive = false
    }

    fun updateGestureLabels() {
        val selectGesture = settings.selectGesture()
        val menuGesture = settings.menuGesture()

        selectGestureText = "${getGestureEmoji(selectGesture)} Select"
        menuGestureText = "${getGestureEmoji(menuGesture)} Menu"
    }

    private fun createComposeView() {
        composeView = ComposeView(context).apply {
            // Set up lifecycle owners
            setViewTreeLifecycleOwner(this@HeadControlGestureOverlay)
            setViewTreeViewModelStoreOwner(this@HeadControlGestureOverlay)
            setViewTreeSavedStateRegistryOwner(this@HeadControlGestureOverlay)

            setContent {
                GestureProgressOverlay(
                    selectText = selectGestureText,
                    menuText = menuGestureText,
                    selectProgress = selectProgress,
                    menuProgress = menuProgress,
                    selectActive = selectActive,
                    menuActive = menuActive
                )
            }
        }

        composeView?.let { view ->
            // Let the view wrap its content
            addView(
                view,
                0,
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun positionOverlay() {
        composeView?.let { view ->
            val screenWidth = ScreenUtils.getWidth(context)
            val maxWidth = 480 // Wider max width for better content fit

            // Safe positioning with proper view attachment validation
            if (view.isAttachedToWindow) {
                // View is already attached, proceed with measurement
                safeMeasureAndPosition(view, screenWidth, maxWidth)
            } else {
                // View not attached yet, wait for attachment
                view.post {
                    // Double-check attachment after post
                    if (view.isAttachedToWindow) {
                        safeMeasureAndPosition(view, screenWidth, maxWidth)
                    } else {
                        // Still not attached, use ViewTreeObserver to wait
                        view.viewTreeObserver.addOnGlobalLayoutListener(object :
                            android.view.ViewTreeObserver.OnGlobalLayoutListener {
                            override fun onGlobalLayout() {
                                if (view.isAttachedToWindow) {
                                    view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                                    safeMeasureAndPosition(view, screenWidth, maxWidth)
                                }
                            }
                        })

                        // Fallback: Position without measurement after delay
                        view.postDelayed({
                            if (!view.isAttachedToWindow) {
                                // Use fallback dimensions if still not attached
                                val fallbackWidth = 350
                                val fallbackHeight = 100
                                val x = screenWidth - fallbackWidth - margin
                                val y = margin
                                updateView(
                                    view as androidx.compose.ui.platform.ComposeView,
                                    x,
                                    y,
                                    fallbackWidth,
                                    fallbackHeight
                                )
                            }
                        }, 100) // 100ms fallback delay
                    }
                }
            }
        }
    }

    private fun safeMeasureAndPosition(view: android.view.View, screenWidth: Int, maxWidth: Int) {
        try {
            // Safe measurement with exception handling
            view.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(
                    maxWidth,
                    android.view.View.MeasureSpec.AT_MOST
                ),
                android.view.View.MeasureSpec.makeMeasureSpec(
                    0,
                    android.view.View.MeasureSpec.UNSPECIFIED
                )
            )

            val viewWidth = if (view.measuredWidth > 0) view.measuredWidth else 350
            val viewHeight = if (view.measuredHeight > 0) view.measuredHeight else 100

            val x = screenWidth - viewWidth - margin
            val y = margin

            updateView(
                view as androidx.compose.ui.platform.ComposeView,
                x,
                y,
                viewWidth,
                viewHeight
            )
        } catch (e: IllegalStateException) {
            // Handle ComposeView measurement errors
            android.util.Log.w(
                "HeadControlGestureOverlay",
                "ComposeView measurement failed, using fallback positioning",
                e
            )

            // Use fallback dimensions
            val fallbackWidth = 350
            val fallbackHeight = 100
            val x = screenWidth - fallbackWidth - margin
            val y = margin
            updateView(
                view as androidx.compose.ui.platform.ComposeView,
                x,
                y,
                fallbackWidth,
                fallbackHeight
            )
        } catch (e: Exception) {
            // Handle any other unexpected exceptions
            android.util.Log.e(
                "HeadControlGestureOverlay",
                "Unexpected error during view positioning",
                e
            )

            // Use fallback dimensions
            val fallbackWidth = 350
            val fallbackHeight = 100
            val x = screenWidth - fallbackWidth - margin
            val y = margin
            updateView(
                view as androidx.compose.ui.platform.ComposeView,
                x,
                y,
                fallbackWidth,
                fallbackHeight
            )
        }
    }

    private fun updateHoldTimes() {
        selectHoldTime = settings.getSelectGestureHoldTime()
        menuHoldTime = settings.getMenuGestureHoldTime()
    }

    private fun getGestureEmoji(gestureId: String): String {
        return when (gestureId) {
            "smile" -> "😊"
            "left_wink" -> "😉"
            "right_wink" -> "😉"
            "blink" -> "😑"
            "pucker" -> "😗"
            else -> "👤"
        }
    }
}

@Composable
private fun GestureProgressOverlay(
    selectText: String,
    menuText: String,
    selectProgress: Float,
    menuProgress: Float,
    selectActive: Boolean,
    menuActive: Boolean
) {
    Box(
        modifier = Modifier
            .background(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Select gesture row
            GestureProgressRow(
                text = selectText,
                progress = selectProgress,
                isActive = selectActive
            )

            // Menu gesture row
            GestureProgressRow(
                text = menuText,
                progress = menuProgress,
                isActive = menuActive
            )
        }
    }
}

@Composable
private fun GestureProgressRow(
    text: String,
    progress: Float,
    isActive: Boolean
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 100),
        label = "progress"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.7f,
        animationSpec = tween(durationMillis = 200),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.alpha(alpha)
        )

        LinearProgressIndicator(
            progress = { animatedProgress },
            modifier = Modifier
                .width(140.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = Color(0xFF4CAF50),
            trackColor = Color.White.copy(alpha = 0.25f),
        )
    }
}