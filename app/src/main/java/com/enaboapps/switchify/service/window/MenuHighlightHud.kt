package com.enaboapps.switchify.service.window

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enaboapps.switchify.service.components.AccessibilityComposeView
import com.enaboapps.switchify.service.window.overlay.OverlayTarget
import com.enaboapps.switchify.service.window.overlay.OverlayTargets

/**
 * Singleton top-of-screen overlay that surfaces the name and one-line
 * description of the menu item the scanner is currently highlighting.
 *
 * Visual style mirrors [ServiceMessageHUD] (Material 3 Card pinned to a
 * screen edge), but this HUD is driven by scan ticks rather than transient
 * service messages: there is no severity model, no swipe-to-dismiss, and no
 * auto-disappear timer — callers explicitly drive `show` / `hide`.
 *
 * Lifecycle is owned by [SwitchifyAccessibilityWindow]:
 * - `setup(applicationContext)` is called from window setup and on screen-wake.
 * - `dispose()` is called from screen-sleep and on service destroy.
 *
 * Call sites: [com.enaboapps.switchify.service.menu.MenuPage.translateMenuItemsToNodes]
 * pushes `show(name, description)` from each Node's `onHighlight` callback and
 * `hide()` from `onUnhighlight`; [com.enaboapps.switchify.service.menu.MenuView.close]
 * also calls `hide()` so the HUD never lingers past the menu.
 */
class MenuHighlightHud private constructor() {
    companion object {
        val instance: MenuHighlightHud by lazy { MenuHighlightHud() }
        private const val TAG = "MenuHighlightHud"
        private const val CONTENT_CROSSFADE_MS = 150

        /**
         * Worst-case vertical footprint of the HUD's card and surrounding
         * padding, in dp: 16 dp card outer padding (top) + 10 dp card inner
         * top padding + ~28 dp title line + 4 dp spacer + ~60 dp description
         * (up to 3 wrapped lines at ~20 dp each — see the description Text's
         * maxLines in [MenuHighlightHudUi]) + 10 dp card inner bottom
         * padding + 16 dp safety gap below the card. The status bar and
         * display cutout are tracked separately via [getSafeTopInsetPx]
         * and added in [reservedTopPx].
         */
        private const val RESERVED_TOP_DP = 144

        /**
         * Screens shorter than this (phones in landscape, ~360–420 dp tall)
         * can't afford to reserve the HUD's ~130 dp footprint without
         * clamping the radial ring so tight items overlap. Below the
         * threshold [reservedTopPx] returns 0: the HUD floats over the top
         * of the menu surface, hiding the top one or two ring items
         * visually, but those items remain scannable and the HUD still
         * surfaces their name + description. Tablets in any orientation and
         * phones in portrait sit well above the threshold and reserve
         * normally.
         */
        private const val SHORT_SCREEN_THRESHOLD_DP = 480

        /**
         * Vertical space the menu surface must keep clear at the top of the
         * screen so the HUD never overlaps it. Combines the HUD card's
         * worst-case footprint (scaled by the system font scale) with the
         * device's status bar + display cutout inset, so devices with tall
         * notches reserve enough room. On short screens (see
         * [SHORT_SCREEN_THRESHOLD_DP]) returns 0 so the menu can claim the
         * full available height — the HUD then floats over the top of the
         * menu instead of pushing it down.
         */
        fun reservedTopPx(context: Context): Int {
            val screenHeightDp = context.resources.configuration.screenHeightDp
            if (screenHeightDp < SHORT_SCREEN_THRESHOLD_DP) return 0
            val fontScale =
                context.resources.configuration.fontScale.coerceAtLeast(1f)
            val cardReservePx =
                RESERVED_TOP_DP * context.resources.displayMetrics.density *
                    fontScale
            return cardReservePx.toInt() + getSafeTopInsetPx(context)
        }

        /**
         * Top edge inset of the current display in px: the larger of the
         * status bar inset and any display cutout (notch / hole-punch). Used
         * to push the HUD card below system decor in [reservedTopPx]; the
         * Compose [WindowInsets.safeDrawing] modifier inside the HUD itself
         * applies the same offset visually.
         */
        private fun getSafeTopInsetPx(context: Context): Int {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val wm = context.getSystemService(Context.WINDOW_SERVICE)
                    as? android.view.WindowManager ?: return 0
                return try {
                    val insets = wm.currentWindowMetrics.windowInsets
                    val statusBars = insets.getInsets(
                        android.view.WindowInsets.Type.statusBars()
                    )
                    val cutout = insets.getInsets(
                        android.view.WindowInsets.Type.displayCutout()
                    )
                    maxOf(statusBars.top, cutout.top)
                } catch (e: Exception) {
                    0
                }
            }
            // API 29 fallback: use the system status_bar_height dimen. This
            // misses any extra cutout overhang on the rare API 29 notched
            // device, but the worst case is a tiny ~8 dp shortfall.
            val resId = context.resources.getIdentifier(
                "status_bar_height", "dimen", "android"
            )
            return if (resId > 0) {
                context.resources.getDimensionPixelSize(resId)
            } else 0
        }
    }

    private var applicationCtx: Context? = null
    private var composeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val nameState = mutableStateOf<String?>(null)
    private val descriptionState = mutableStateOf<String?>(null)
    private val visibleState = mutableStateOf(false)
    private var attachedTarget: OverlayTarget.Display? = null

    fun setup(appCtx: Context) {
        this.applicationCtx = appCtx.applicationContext
        Log.d(TAG, "MenuHighlightHud setup with AppContext: $applicationCtx")
    }

    /**
     * Show the HUD with [name] (primary) and [description] (secondary).
     * Posting null/blank [name] is treated as a hide. Re-uses the existing
     * ComposeView across scan ticks; the card content cross-fades.
     */
    fun show(
        name: String?,
        description: String?,
        target: OverlayTarget.Display = OverlayTargets.defaultDisplay()
    ) {
        val hudTarget = target
        if (name.isNullOrBlank()) {
            hide()
            return
        }
        handler.post {
            ensureComposeViewIsCreated()
            attachIfNeeded(hudTarget)
            nameState.value = name
            descriptionState.value = description?.takeIf { it.isNotBlank() }
            visibleState.value = true
        }
    }

    fun hide() {
        handler.post {
            visibleState.value = false
        }
    }

    fun dispose() {
        Log.d(TAG, "Disposing MenuHighlightHud")
        handler.removeCallbacksAndMessages(null)
        composeView?.let { view ->
            try {
                SwitchifyAccessibilityWindow.instance.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove HUD view", e)
            }
        }
        composeView = null
        attachedTarget = null
        nameState.value = null
        descriptionState.value = null
        visibleState.value = false
        applicationCtx = null
    }

    private fun ensureComposeViewIsCreated() {
        val ctx = applicationCtx ?: run {
            Log.e(TAG, "ApplicationContext is null. Call setup() first.")
            return
        }
        if (composeView == null) {
            composeView = AccessibilityComposeView(ctx) {
                MenuHighlightHudUi(
                    name = nameState.value,
                    description = descriptionState.value,
                    isVisible = visibleState.value
                )
            }
        }
    }

    private fun attachIfNeeded(target: OverlayTarget.Display) {
        val view = composeView ?: return
        if (view.parent != null && attachedTarget != target) {
            SwitchifyAccessibilityWindow.instance.removeView(attachedTarget ?: OverlayTargets.defaultDisplay(), view)
            attachedTarget = null
        }
        if (view.parent == null) {
            try {
                // No outer window-level margin — WindowInsets.safeDrawing
                // inside the Composable pushes the card below the status bar
                // and any notch, and the Card already has its own 16 dp outer
                // padding for the horizontal gap.
                SwitchifyAccessibilityWindow.instance.addViewToTop(target, view)
                attachedTarget = target
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach HUD view", e)
            }
        }
    }

    @Composable
    private fun MenuHighlightHudUi(
        name: String?,
        description: String?,
        isVisible: Boolean
    ) {
        val shouldShow = isVisible && !name.isNullOrBlank()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            AnimatedVisibility(
                visible = shouldShow,
                enter = fadeIn(animationSpec = tween(CONTENT_CROSSFADE_MS)),
                exit = fadeOut(animationSpec = tween(CONTENT_CROSSFADE_MS))
            ) {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .padding(16.dp),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.96f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    // Cross-fade the textual content as the scanner moves
                    // between items, keyed on (name, description) so any
                    // change to either triggers the transition.
                    Crossfade(
                        targetState = (name ?: "") to (description ?: ""),
                        animationSpec = tween(CONTENT_CROSSFADE_MS),
                        label = "MenuHighlightHudContent"
                    ) { (currentName, currentDescription) ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = currentName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.inverseOnSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentDescription.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.inverseOnSurface,
                                    textAlign = TextAlign.Center,
                                    // Allow long descriptions to wrap rather
                                    // than ellipsize. Cap at 3 lines so the
                                    // HUD's worst-case height stays bounded
                                    // (see RESERVED_TOP_DP) and a malformed
                                    // long string can't push the menu surface
                                    // off-screen.
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
