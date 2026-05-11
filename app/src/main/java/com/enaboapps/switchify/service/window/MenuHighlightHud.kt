package com.enaboapps.switchify.service.window

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
         * Worst-case vertical footprint reserved at the top of the screen for
         * the HUD, in dp: 16 dp outer top margin + 10 dp card top padding +
         * ~28 dp title line + 4 dp spacer + ~20 dp description line +
         * 10 dp card bottom padding + 16 dp safety gap above the menu surface.
         * Callers should scale this by the system font scale (see
         * [reservedTopPx]) so larger font sizes still don't overlap the menu.
         */
        private const val RESERVED_TOP_DP = 104

        /**
         * Vertical space the menu surface must keep clear at the top of the
         * screen so the HUD never overlaps it. Returned in screen pixels and
         * scaled by the user's font scale (>=1.0f) so the worst-case HUD
         * footprint at Largest font still fits.
         */
        fun reservedTopPx(context: Context): Int {
            val fontScale =
                context.resources.configuration.fontScale.coerceAtLeast(1f)
            return (RESERVED_TOP_DP * context.resources.displayMetrics.density *
                fontScale).toInt()
        }
    }

    private var applicationCtx: Context? = null
    private var composeView: AccessibilityComposeView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val nameState = mutableStateOf<String?>(null)
    private val descriptionState = mutableStateOf<String?>(null)
    private val visibleState = mutableStateOf(false)

    fun setup(appCtx: Context) {
        this.applicationCtx = appCtx.applicationContext
        Log.d(TAG, "MenuHighlightHud setup with AppContext: $applicationCtx")
    }

    /**
     * Show the HUD with [name] (primary) and [description] (secondary).
     * Posting null/blank [name] is treated as a hide. Re-uses the existing
     * ComposeView across scan ticks; the card content cross-fades.
     */
    fun show(name: String?, description: String?) {
        if (name.isNullOrBlank()) {
            hide()
            return
        }
        handler.post {
            ensureComposeViewIsCreated()
            attachIfNeeded()
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

    private fun attachIfNeeded() {
        val view = composeView ?: return
        if (view.parent == null) {
            try {
                SwitchifyAccessibilityWindow.instance.addViewToTop(view, margins = 16)
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
            modifier = Modifier.fillMaxWidth(),
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
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (currentDescription.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
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
