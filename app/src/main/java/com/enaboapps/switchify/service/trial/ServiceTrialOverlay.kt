package com.enaboapps.switchify.service.trial

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
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
 * Compose-based overlay that displays real-time trial countdown for non-Pro users.
 * Shows at top-center of screen with time remaining and warning states.
 */
class ServiceTrialOverlay(
    private val context: Context,
    private val trialManager: ServiceTrialManager
) : AccessTechniqueUIBase(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L // Update every second
        private const val BANNER_WIDTH_DP = 350
        private const val MARGIN_TOP_DP = 16
    }

    private var composeView: ComposeView? = null

    // Lifecycle components for Compose
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    // State for real-time updates
    private var remainingTime = ""
    private var isWarningState = false
    private var isCriticalState = false

    // Handler for real-time countdown updates and main thread operations
    private val mainHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (trialManager.isTrialActive()) {
                updateTrialDisplay()
                mainHandler.postDelayed(this, UPDATE_INTERVAL_MS)
            } else {
                // Trial expired or stopped - hide overlay
                hideOverlay()
            }
        }
    }

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.INITIALIZED
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    /**
     * Shows the trial countdown overlay
     * Safe to call from any thread - will execute on main thread
     */
    fun showOverlay() {
        mainHandler.post {
            if (composeView == null) {
                createComposeView()
            }
            updateTrialDisplay()
            positionOverlay()
            lifecycleRegistry.currentState = Lifecycle.State.STARTED
        }
    }

    /**
     * Hides the trial countdown overlay
     * Safe to call from any thread - will execute on main thread
     */
    fun hideOverlay() {
        mainHandler.post {
            lifecycleRegistry.currentState = Lifecycle.State.CREATED
            composeView?.let { removeView(it) }
            composeView = null
        }
    }

    /**
     * Starts real-time countdown updates
     */
    fun startUpdates() {
        mainHandler.post(updateRunnable)
    }

    /**
     * Stops real-time countdown updates
     */
    fun stopUpdates() {
        mainHandler.removeCallbacks(updateRunnable)
    }

    /**
     * Updates the trial display with current time and warning state
     */
    private fun updateTrialDisplay() {
        remainingTime = trialManager.getRemainingTimeFormatted()
        isWarningState = trialManager.isInWarningPeriod()

        // Critical state is last 5 minutes (or 5 seconds in debug mode)
        val remainingMs = trialManager.getRemainingTime()
        val criticalThreshold = if (android.os.Build.VERSION.SDK_INT >= 0) {
            // Use BuildConfig to check debug mode
            try {
                val buildConfigClass = Class.forName("com.enaboapps.switchify.BuildConfig")
                val debugField = buildConfigClass.getField("DEBUG")
                val isDebug = debugField.getBoolean(null)
                if (isDebug) 5000L else 300000L // 5s debug, 5min release
            } catch (e: Exception) {
                300000L // Default to 5 minutes if we can't determine
            }
        } else {
            300000L
        }
        isCriticalState = remainingMs in 1..criticalThreshold

        // Update the compose view content if it exists
        composeView?.let { view ->
            // Trigger recomposition by recreating the content
            view.setContent {
                TrialCountdownBanner(
                    remainingTime = remainingTime,
                    isWarning = isWarningState,
                    isCritical = isCriticalState
                )
            }
        }
    }

    /**
     * Creates the ComposeView with trial countdown UI
     */
    private fun createComposeView() {
        composeView = ComposeView(context).apply {
            // Set up lifecycle owners
            setViewTreeLifecycleOwner(this@ServiceTrialOverlay)
            setViewTreeViewModelStoreOwner(this@ServiceTrialOverlay)
            setViewTreeSavedStateRegistryOwner(this@ServiceTrialOverlay)

            setContent {
                TrialCountdownBanner(
                    remainingTime = remainingTime,
                    isWarning = isWarningState,
                    isCritical = isCriticalState
                )
            }
        }

        composeView?.let { view ->
            // Add view with wrap content dimensions
            addView(
                view,
                0,
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    /**
     * Positions the overlay at top-center of screen
     */
    private fun positionOverlay() {
        composeView?.let { view ->
            val screenWidth = ScreenUtils.getWidth(context)
            val bannerWidthPx = ScreenUtils.dpToPx(context, BANNER_WIDTH_DP)
            val marginTopPx = ScreenUtils.dpToPx(context, MARGIN_TOP_DP)

            // Center horizontally
            val x = (screenWidth - bannerWidthPx) / 2
            val y = marginTopPx

            // Position after view is attached
            view.post {
                updateView(
                    view,
                    x,
                    y,
                    bannerWidthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
    }
}
