package com.enaboapps.switchify.service.gestures.visuals

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.placement.FingerPlacement
import com.enaboapps.switchify.service.gestures.placement.TwoFingerPlacement
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import java.lang.ref.WeakReference

/**
 * Unified visual feedback manager for all gesture-related visual indicators.
 * Consolidates and standardizes visual feedback across the gesture system.
 * Integrates with GestureStateManager for coordinated state management.
 */
class GestureVisualManager(
    context: Context,
    role: GestureVisualManagerRole
) : GestureStateManager.GestureStateListener {

    private val stateListenerId = role.listenerId

    /**
     * Ensures UI operations happen on the main thread.
     */
    private inline fun onMainThread(crossinline action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post { action() }
        }
    }

    private val contextRef: WeakReference<Context> = WeakReference(context)
    private val accessibilityWindow = SwitchifyAccessibilityWindow.instance
    private val animatedGestureArrow = AnimatedGestureArrow(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    // Active visual tracking - single finger
    private var currentCircle: WeakReference<RelativeLayout>? = null
    private var currentAnimation: ScaleAnimation? = null
    private var removeHandler: Handler? = null

    // Multi-finger visual tracking
    private var currentMultiFingerVisual: WeakReference<RelativeLayout>? = null
    private val activeFingerCircles = mutableListOf<WeakReference<RelativeLayout>>()
    private var multiFingerRemoveHandler: Handler? = null

    // Multi-finger arrow animation tracking
    private val activeMultiFingerArrows = mutableListOf<AnimatedGestureArrow>()
    private val pendingMultiFingerArrowRunnables = mutableListOf<Runnable>()

    private sealed interface LinearGestureVisualRequest {
        data class Single(
            val x1: Int,
            val y1: Int,
            val x2: Int,
            val y2: Int,
            val duration: Long
        ) : LinearGestureVisualRequest

        data class Multi(
            val startPositions: List<PointF>,
            val endPositions: List<PointF>,
            val duration: Long
        ) : LinearGestureVisualRequest
    }

    private val linearGestureVisualThrottle = LinearGestureVisualThrottle(
        intervalMs = LINEAR_GESTURE_VISUAL_THROTTLE_MS,
        currentTimeMs = SystemClock::uptimeMillis,
        postDelayed = mainHandler::postDelayed,
        removeCallbacks = mainHandler::removeCallbacks,
        display = ::displayLinearGestureVisual
    )

    // Tap-family visuals (ripples for tap/double-tap, ring for tap-and-hold).
    // These are kept here so [clearCurrentVisual] / [clearAllVisuals] can
    // cancel them in lockstep with the existing single-finger circle.
    private val activeRipples = mutableListOf<TapRippleVisual>()
    private var currentTapAndHoldRing: TapAndHoldRingVisual? = null
    private var pendingDoubleTapRunnable: Runnable? = null

    companion object {
        // Standardized circle size - compromise between existing 40px and 60px
        private const val STANDARD_CIRCLE_SIZE = 48
        private const val LINEAR_GESTURE_VISUAL_THROTTLE_MS = 500L
    }

    init {
        // Register as state listener for coordinated visual feedback
        GestureStateManager.addStateListener(stateListenerId, this)
    }

    /**
     * Shows a static circle at the specified coordinates.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param duration Duration in milliseconds, null for persistent circle
     */
    fun showStaticCircle(x: Int, y: Int, duration: Long? = null) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val circleLayout = createCircleLayout(context, STANDARD_CIRCLE_SIZE)

        onMainThread {
            accessibilityWindow.addView(
                circleLayout,
                x - STANDARD_CIRCLE_SIZE / 2,
                y - STANDARD_CIRCLE_SIZE / 2,
                STANDARD_CIRCLE_SIZE,
                STANDARD_CIRCLE_SIZE
            )
        }

        currentCircle = WeakReference(circleLayout)

        // Auto-remove after duration if specified
        duration?.let {
            removeHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({ clearCurrentVisual() }, it)
            }
        }
    }

    /**
     * Shows a countdown circle with shrinking animation.
     * @param x The x-coordinate of the circle's center
     * @param y The y-coordinate of the circle's center
     * @param duration Duration of the countdown animation in milliseconds
     */
    fun showCountdownCircle(x: Int, y: Int, duration: Long) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val circleLayout = createCircleLayout(context, STANDARD_CIRCLE_SIZE)

        onMainThread {
            accessibilityWindow.addView(
                circleLayout,
                x - STANDARD_CIRCLE_SIZE / 2,
                y - STANDARD_CIRCLE_SIZE / 2,
                STANDARD_CIRCLE_SIZE,
                STANDARD_CIRCLE_SIZE
            )
        }

        currentCircle = WeakReference(circleLayout)

        // Create shrinking animation
        val scaleAnimation = ScaleAnimation(
            1f, 0f, 1f, 0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            this.duration = duration
            fillAfter = true
        }

        currentAnimation = scaleAnimation
        // Apply animation to both shadow and main circle views for proper countdown effect
        onMainThread {
            circleLayout.getChildAt(0)?.startAnimation(scaleAnimation) // shadowView
            circleLayout.getChildAt(1)?.startAnimation(scaleAnimation) // mainView
        }

        // Auto-remove after animation
        removeHandler = Handler(Looper.getMainLooper()).apply {
            postDelayed({ clearCurrentVisual() }, duration)
        }
    }

    /**
     * Shows an expanding ripple at the specified coordinates — the visual
     * affordance for a single-finger tap. The ripple auto-removes when its
     * animation completes.
     */
    fun showTapRipple(x: Int, y: Int) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val ripple = TapRippleVisual(context)
        activeRipples.add(ripple)

        onMainThread {
            ripple.show(x, y)
        }
    }

    /**
     * Shows two ripples staggered by [intervalMs] for a single-finger
     * double-tap. Each ripple is independent so cleanup cancels both.
     *
     * The actual gesture timing is still driven by the timing coordinator;
     * this method just provides the visual feedback that mirrors it.
     */
    fun showDoubleTapRipple(
        x: Int,
        y: Int,
        intervalMs: Long = GestureData.DOUBLE_TAP_INTERVAL
    ) {
        showTapRipple(x, y)

        val context = contextRef.get() ?: return
        val secondRipple = TapRippleVisual(context)
        activeRipples.add(secondRipple)

        val runnable = Runnable {
            secondRipple.show(x, y)
        }
        pendingDoubleTapRunnable = runnable
        mainHandler.postDelayed(runnable, intervalMs)
    }

    /**
     * Shows the tap-and-hold ring visual centred on ([x], [y]). The ring
     * fills clockwise over [durationMs] and the duration value is rendered
     * as text inside the ring's dot.
     */
    fun showTapAndHoldRing(x: Int, y: Int, durationMs: Long) {
        clearCurrentVisual()

        val context = contextRef.get() ?: return
        val ring = TapAndHoldRingVisual(context)
        currentTapAndHoldRing = ring

        onMainThread {
            ring.show(x, y, durationMs)
        }
    }

    /**
     * Shows an animated arrow from start to end point.
     * @param x1 Start x-coordinate
     * @param y1 Start y-coordinate
     * @param x2 End x-coordinate
     * @param y2 End y-coordinate
     * @param duration Animation duration in milliseconds
     */
    fun showArrowAnimation(x1: Int, y1: Int, x2: Int, y2: Int, duration: Long) {
        onMainThread {
            linearGestureVisualThrottle.submit(
                LinearGestureVisualRequest.Single(x1, y1, x2, y2, duration)
            )
        }
    }

    /**
     * Shows coordinated multi-finger arrow animations for linear gestures.
     *
     * This method creates synchronized arrow animations for all fingers in a multi-finger
     * linear gesture, providing clear visual feedback about the coordinated movement pattern.
     * Each arrow maintains the same direction and timing while showing individual finger paths.
     *
     * @param startPositions List of start positions for all fingers
     * @param endPositions List of end positions for all fingers (must match startPositions length)
     * @param duration Animation duration in milliseconds for all arrows
     */
    fun showMultiFingerArrowAnimation(
        startPositions: List<PointF>,
        endPositions: List<PointF>,
        duration: Long
    ) {
        require(startPositions.size == endPositions.size) {
            "Start positions and end positions must have the same size: ${startPositions.size} vs ${endPositions.size}"
        }

        onMainThread {
            linearGestureVisualThrottle.submit(
                LinearGestureVisualRequest.Multi(
                    startPositions.toList(),
                    endPositions.toList(),
                    duration
                )
            )
        }
    }

    private fun displayLinearGestureVisual(request: LinearGestureVisualRequest) {
        clearActiveLinearGestureVisuals()
        when (request) {
            is LinearGestureVisualRequest.Single -> animatedGestureArrow.showArrowAnimation(
                request.x1,
                request.y1,
                request.x2,
                request.y2,
                request.duration
            )

            is LinearGestureVisualRequest.Multi -> displayMultiFingerArrowAnimation(request)
        }
    }

    private fun displayMultiFingerArrowAnimation(request: LinearGestureVisualRequest.Multi) {
        val context = contextRef.get() ?: return
        request.startPositions.zip(request.endPositions).forEachIndexed { index, (start, end) ->
            val arrow = AnimatedGestureArrow(context)
            activeMultiFingerArrows.add(arrow)
            lateinit var runnable: Runnable
            runnable = Runnable {
                pendingMultiFingerArrowRunnables.remove(runnable)
                arrow.showArrowAnimation(
                    start.x.toInt(),
                    start.y.toInt(),
                    end.x.toInt(),
                    end.y.toInt(),
                    request.duration
                ) {
                    activeMultiFingerArrows.remove(arrow)
                }
            }
            pendingMultiFingerArrowRunnables.add(runnable)
            mainHandler.postDelayed(runnable, index * 50L)
        }
    }

    private fun clearActiveLinearGestureVisuals() {
        animatedGestureArrow.cancel()
        pendingMultiFingerArrowRunnables.forEach(mainHandler::removeCallbacks)
        pendingMultiFingerArrowRunnables.clear()
        activeMultiFingerArrows.forEach(AnimatedGestureArrow::cancel)
        activeMultiFingerArrows.clear()
    }

    /**
     * Shows multi-finger visual feedback with enhanced indicators.
     *
     * This method creates a sophisticated visual representation of multi-finger
     * gestures including individual finger circles, connection lines, and
     * visual indicators of the finger placement strategy.
     *
     * @param fingerPlacement The finger placement result from the algorithm
     * @param duration Duration in milliseconds, null for persistent display
     */
    fun showMultiFingerVisual(fingerPlacement: FingerPlacement, duration: Long? = null) {
        clearAllVisuals()

        val context = contextRef.get() ?: return

        when (fingerPlacement.fingerCount) {
            1 -> {
                // Use existing single finger visual
                showStaticCircle(
                    fingerPlacement.primaryPoint.x.toInt(),
                    fingerPlacement.primaryPoint.y.toInt(),
                    duration
                )
            }

            2 -> {
                showTwoFingerVisual(fingerPlacement as TwoFingerPlacement, duration)
            }

            else -> {
                showMultipleFingerVisual(fingerPlacement, duration)
            }
        }
    }

    /**
     * Shows specialized two-finger visual with connection line and strategy indicator.
     */
    private fun showTwoFingerVisual(placement: TwoFingerPlacement, duration: Long?) {
        val context = contextRef.get() ?: return

        // Create container for the entire two-finger visual
        val containerLayout = createTwoFingerContainer(context, placement)

        // Calculate container bounds
        val bounds = calculateContainerBounds(placement.fingerPoints)

        onMainThread {
            accessibilityWindow.addView(
                containerLayout,
                bounds.left,
                bounds.top,
                bounds.right - bounds.left,
                bounds.bottom - bounds.top
            )
        }

        currentMultiFingerVisual = WeakReference(containerLayout)

        // Auto-remove after duration if specified
        duration?.let {
            multiFingerRemoveHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({ clearAllVisuals() }, it)
            }
        }
    }

    /**
     * Shows visual for 3+ finger gestures with individual circles.
     */
    private fun showMultipleFingerVisual(placement: FingerPlacement, duration: Long?) {
        val context = contextRef.get() ?: return

        // Clear any existing visuals
        activeFingerCircles.clear()

        // Create individual circles for each finger
        placement.fingerPoints.forEachIndexed { index, point ->
            val circleLayout = createFingerCircle(context, index, placement.fingerCount)

            onMainThread {
                accessibilityWindow.addView(
                    circleLayout,
                    point.x.toInt() - STANDARD_CIRCLE_SIZE / 2,
                    point.y.toInt() - STANDARD_CIRCLE_SIZE / 2,
                    STANDARD_CIRCLE_SIZE,
                    STANDARD_CIRCLE_SIZE
                )
            }

            activeFingerCircles.add(WeakReference(circleLayout))
        }

        // Auto-remove after duration if specified
        duration?.let {
            multiFingerRemoveHandler = Handler(Looper.getMainLooper()).apply {
                postDelayed({ clearAllVisuals() }, it)
            }
        }
    }

    /**
     * Creates a container with two finger circles and connection line.
     */
    private fun createTwoFingerContainer(
        context: Context,
        placement: TwoFingerPlacement
    ): RelativeLayout {
        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            // Add custom view that draws the connection line
            addView(TwoFingerConnectionView(context, placement).apply {
                isClickable = false
                isFocusable = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })

            // Add individual finger circles
            val bounds = calculateContainerBounds(placement.fingerPoints)

            placement.fingerPoints.forEachIndexed { index, point ->
                val circleLayout = createFingerCircle(context, index, 2)

                val layoutParams = RelativeLayout.LayoutParams(
                    STANDARD_CIRCLE_SIZE, STANDARD_CIRCLE_SIZE
                ).apply {
                    leftMargin = (point.x - bounds.left).toInt() - STANDARD_CIRCLE_SIZE / 2
                    topMargin = (point.y - bounds.top).toInt() - STANDARD_CIRCLE_SIZE / 2
                }

                addView(circleLayout, layoutParams)
            }
        }
    }

    /**
     * Creates a finger circle with index indicator for multi-finger gestures.
     */
    private fun createFingerCircle(
        context: Context,
        fingerIndex: Int,
        totalFingers: Int
    ): RelativeLayout {
        // Choose colors based on finger index with distinct colors for up to 5 fingers
        val colors = when (fingerIndex) {
            0 -> Pair(0xFFFFFFFF.toInt(), 0xFF4CAF50.toInt()) // White with green accent
            1 -> Pair(0xFFFFFFFF.toInt(), 0xFF2196F3.toInt()) // White with blue accent
            2 -> Pair(0xFFFFFFFF.toInt(), 0xFF9C27B0.toInt()) // White with purple accent
            3 -> Pair(0xFFFFFFFF.toInt(), 0xFFFF5722.toInt()) // White with deep orange accent
            4 -> Pair(0xFFFFFFFF.toInt(), 0xFFE91E63.toInt()) // White with pink accent
            else -> Pair(
                0xFFFFFFFF.toInt(),
                0xFF607D8B.toInt()
            ) // White with blue-gray accent (fallback)
        }

        // Create shadow circle
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x30000000) // Semi-transparent black shadow
            setSize(STANDARD_CIRCLE_SIZE, STANDARD_CIRCLE_SIZE)
        }

        // Create main circle with colored border
        val mainDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(colors.first) // White fill
            setStroke(3, colors.second) // Colored border
            setSize(STANDARD_CIRCLE_SIZE, STANDARD_CIRCLE_SIZE)
        }

        // Shadow layer
        val shadowView = ImageView(context).apply {
            setImageDrawable(shadowDrawable)
            layoutParams =
                RelativeLayout.LayoutParams(STANDARD_CIRCLE_SIZE, STANDARD_CIRCLE_SIZE).apply {
                    leftMargin = 2
                    topMargin = 2
                }
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Main circle layer
        val mainView = ImageView(context).apply {
            setImageDrawable(mainDrawable)
            layoutParams = RelativeLayout.LayoutParams(STANDARD_CIRCLE_SIZE, STANDARD_CIRCLE_SIZE)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(shadowView)
            addView(mainView)
        }
    }

    /**
     * Calculates bounding rectangle for a container holding multiple finger points.
     * Clamps to screen bounds to prevent off-screen rendering.
     */
    private fun calculateContainerBounds(points: List<PointF>): android.graphics.Rect {
        val margin = STANDARD_CIRCLE_SIZE
        val minX = (points.minOf { it.x } - margin).toInt().coerceAtLeast(0)
        val maxX = (points.maxOf { it.x } + margin).toInt()
        val minY = (points.minOf { it.y } - margin).toInt().coerceAtLeast(0)
        val maxY = (points.maxOf { it.y } + margin).toInt()

        // Get screen dimensions from accessibility window if available
        val context = contextRef.get()
        if (context != null) {
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            return android.graphics.Rect(
                minX,
                minY,
                maxX.coerceAtMost(screenWidth),
                maxY.coerceAtMost(screenHeight)
            )
        }

        return android.graphics.Rect(minX, minY, maxX, maxY)
    }

    /**
     * Custom view that draws connection lines between fingers.
     */
    private inner class TwoFingerConnectionView(
        context: Context,
        private val placement: TwoFingerPlacement
    ) : View(context) {

        private val linePaint = Paint().apply {
            // Brand primary tint for the connection line that joins the two
            // finger circles in a two-finger gesture. The per-finger stroke
            // colours stay distinct (they convey finger identity); the
            // connector picks up the brand tint so the assembly reads as a
            // single coloured object.
            color = ContextCompat.getColor(context, R.color.gesture_visual_primary)
            strokeWidth = 4f
            alpha = 180 // Semi-transparent
            isAntiAlias = true
            style = Paint.Style.STROKE
        }

        private val shadowPaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 6f
            alpha = 60 // Very subtle shadow
            isAntiAlias = true
            style = Paint.Style.STROKE
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val bounds = calculateContainerBounds(placement.fingerPoints)
            val point1 = placement.primaryPoint
            val point2 = placement.secondaryPoint

            // Convert to local coordinates
            val localX1 = point1.x - bounds.left
            val localY1 = point1.y - bounds.top
            val localX2 = point2.x - bounds.left
            val localY2 = point2.y - bounds.top

            // Draw shadow line
            canvas.drawLine(localX1 + 1, localY1 + 1, localX2 + 1, localY2 + 1, shadowPaint)

            // Draw main connection line
            canvas.drawLine(localX1, localY1, localX2, localY2, linePaint)
        }
    }

    /**
     * Hides any currently displayed circle visual.
     */
    fun hideCircle() {
        clearCurrentVisual()
    }

    /**
     * Hides all visual feedback (single and multi-finger).
     */
    fun hideAllVisuals() {
        clearAllVisuals()
    }

    /**
     * Creates a standardized circle layout filled with the brand primary
     * colour. Used by [showStaticCircle] / [showCountdownCircle] for
     * gesture-start indicators (e.g. before a swipe).
     */
    private fun createCircleLayout(context: Context, size: Int): RelativeLayout {
        val primary = ContextCompat.getColor(context, R.color.gesture_visual_primary)

        // Create shadow circle (slightly offset and darker)
        val shadowDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x20000000) // Semi-transparent black shadow
            setSize(size, size)
        }

        // Create main primary-coloured circle
        val mainDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(primary)
            setStroke(1, 0x20000000) // Subtle dark stroke for definition
            setSize(size, size)
        }

        // Shadow layer
        val shadowView = ImageView(context).apply {
            setImageDrawable(shadowDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size).apply {
                leftMargin = 2
                topMargin = 2
            }
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        // Main circle layer
        val mainView = ImageView(context).apply {
            setImageDrawable(mainDrawable)
            layoutParams = RelativeLayout.LayoutParams(size, size)
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        return RelativeLayout(context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(shadowView)
            addView(mainView)
        }
    }

    /**
     * Clears any current single-finger visual and associated handlers/animations.
     */
    private fun clearCurrentVisual() {
        currentAnimation?.cancel()
        removeHandler?.removeCallbacksAndMessages(null)

        currentCircle?.get()?.let { circle ->
            onMainThread {
                accessibilityWindow.removeView(circle)
            }
        }

        currentCircle = null
        currentAnimation = null
        removeHandler = null

        // Cancel any in-flight tap-family visuals (ripples + tap-and-hold ring).
        pendingDoubleTapRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingDoubleTapRunnable = null
        activeRipples.forEach { ripple ->
            onMainThread { ripple.cancel() }
        }
        activeRipples.clear()
        currentTapAndHoldRing?.let { ring ->
            onMainThread { ring.cancel() }
        }
        currentTapAndHoldRing = null
    }

    /**
     * Clears all visual feedback (single and multi-finger).
     */
    private fun clearAllVisuals(clearLinearGestureVisuals: Boolean = true) {
        if (clearLinearGestureVisuals) {
            linearGestureVisualThrottle.clear()
            clearActiveLinearGestureVisuals()
        }
        // Clear single finger visuals
        clearCurrentVisual()

        // Clear multi-finger visuals
        multiFingerRemoveHandler?.removeCallbacksAndMessages(null)

        currentMultiFingerVisual?.get()?.let { visual ->
            onMainThread {
                accessibilityWindow.removeView(visual)
            }
        }

        // Clear individual finger circles
        activeFingerCircles.forEach { circleRef ->
            circleRef.get()?.let { circle ->
                onMainThread {
                    accessibilityWindow.removeView(circle)
                }
            }
        }

        // Reset state
        currentMultiFingerVisual = null
        activeFingerCircles.clear()
        multiFingerRemoveHandler = null
    }

    /**
     * Implements GestureStateListener to coordinate visual feedback with state changes.
     */
    override fun onStateChanged(event: String, data: Map<String, Any>) {
        when (event) {
            GestureStateManager.EVENT_GESTURE_ENDED -> {
                clearAllVisuals(clearLinearGestureVisuals = false)
            }

            GestureStateManager.EVENT_AUTO_SELECT_CANCELLED -> {
                // Clear all visuals when auto-select is cancelled
                clearAllVisuals()
            }

            GestureStateManager.EVENT_STATE_RESET -> {
                // Clear all visuals on state reset
                clearAllVisuals()
            }
        }
    }

    /**
     * Releases all resources and clears references.
     */
    fun release() {
        GestureStateManager.removeStateListener(stateListenerId)
        clearAllVisuals()
        contextRef.clear()
    }
}

enum class GestureVisualManagerRole(internal val listenerId: String) {
    GESTURE_MANAGER("visual_manager:gesture_manager"),
    LINEAR_GESTURE_PERFORMER("visual_manager:linear_gesture_performer"),
    MENU_MANAGER("visual_manager:menu_manager"),
    SELECTION_HANDLER("visual_manager:selection_handler")
}
