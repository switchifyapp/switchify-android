package com.enaboapps.switchify.service.window.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.display.DisplayManager
import android.graphics.Region
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.enaboapps.switchify.service.core.SwitchifyLifecycleOwner

class SurfaceControlOverlayBackend(
    private val service: AccessibilityService,
    private val hostTokenProvider: () -> IBinder?
) : OverlayBackend {
    fun canAttach(target: OverlayTarget): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            displayFor(target) != null
    }

    override fun attach(
        target: OverlayTarget,
        view: ViewGroup,
        placement: OverlayPlacement
    ): OverlayHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null

        val display = displayFor(target) ?: return null
        val token = hostTokenProvider() ?: Binder()
        val metrics = metricsFor(display.displayId)
        val surfaceSize = surfaceSizeFor(placement, metrics)
        if (surfaceSize.width <= 0 || surfaceSize.height <= 0) return null

        var viewHost: SurfaceControlViewHost? = null
        return try {
            val displayContext = service.createDisplayContext(display)
            val windowContext = displayContext.createWindowContext(
                display,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                null
            )
            val container = FrameLayout(windowContext).apply {
                setViewTreeLifecycleOwner(SwitchifyLifecycleOwner.getInstance())
                setViewTreeSavedStateRegistryOwner(SwitchifyLifecycleOwner.getInstance())
                addView(view, frameLayoutParamsFor(placement, metrics))
            }
            viewHost = SurfaceControlViewHost(windowContext, display, token)
            viewHost.setView(container, surfaceSize.width, surfaceSize.height)
            container.post {
                container.rootSurfaceControl?.setTouchableRegion(Region())
            }
            val surfaceControl = viewHost.surfacePackage?.surfaceControl ?: run {
                viewHost.release()
                return null
            }

            when (target) {
                is OverlayTarget.Display -> service.attachAccessibilityOverlayToDisplay(
                    target.displayId,
                    surfaceControl
                )

                is OverlayTarget.Window -> service.attachAccessibilityOverlayToWindow(
                    target.accessibilityWindowId,
                    surfaceControl
                )
            }

            SurfaceControl.Transaction()
                .setVisibility(surfaceControl, true)
                .setLayer(surfaceControl, SURFACE_LAYER)
                .apply()
            Log.d(TAG, "Attached SurfaceControl overlay to $target")
            SurfaceControlOverlayHandle(
                view = view,
                container = container,
                surfaceControl = surfaceControl,
                viewHost = viewHost
            )
        } catch (e: Exception) {
            (view.parent as? ViewGroup)?.removeView(view)
            viewHost?.release()
            Log.e(TAG, "Failed to attach SurfaceControl overlay to $target", e)
            null
        }
    }

    private fun displayFor(target: OverlayTarget): android.view.Display? {
        val displayId = when (target) {
            is OverlayTarget.Display -> target.displayId
            is OverlayTarget.Window -> target.displayId
        }
        val displayManager = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return displayManager.getDisplay(displayId)
    }

    private fun metricsFor(displayId: Int): OverlayDisplayMetrics {
        val displayManager = service.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val display = displayManager.getDisplay(displayId)
        val context = display?.let { service.createDisplayContext(it) } ?: service
        return OverlayDisplayMetricsProvider.displayMetrics(context, displayId)
    }

    private fun surfaceSizeFor(
        placement: OverlayPlacement,
        metrics: OverlayDisplayMetrics
    ): OverlaySurfaceSize {
        return when (placement) {
            is OverlayPlacement.Bounds -> OverlaySurfaceSize(
                width = maxOf(metrics.width, placement.x + placement.width),
                height = maxOf(metrics.height, placement.y + placement.height)
            )

            is OverlayPlacement.WrapAt,
            is OverlayPlacement.BottomCentered,
            is OverlayPlacement.TopCentered,
            OverlayPlacement.Centered -> OverlaySurfaceSize(metrics.width, metrics.height)
        }
    }

    private fun frameLayoutParamsFor(
        placement: OverlayPlacement,
        metrics: OverlayDisplayMetrics
    ): FrameLayout.LayoutParams {
        return when (placement) {
            is OverlayPlacement.Bounds -> FrameLayout.LayoutParams(
                placement.width,
                placement.height
            ).apply {
                leftMargin = placement.x
                topMargin = placement.y
                gravity = Gravity.TOP or Gravity.START
            }

            is OverlayPlacement.WrapAt -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = placement.x
                topMargin = placement.y
                gravity = Gravity.TOP or Gravity.START
            }

            is OverlayPlacement.BottomCentered -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                setMargins(
                    placement.margins,
                    placement.margins,
                    placement.margins,
                    placement.margins
                )
            }

            is OverlayPlacement.TopCentered -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                setMargins(
                    placement.margins,
                    placement.margins,
                    placement.margins,
                    placement.margins
                )
            }

            OverlayPlacement.Centered -> FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
            }
        }
    }

    private data class OverlaySurfaceSize(
        val width: Int,
        val height: Int
    )

    private class SurfaceControlOverlayHandle(
        override val view: ViewGroup,
        private val container: ViewGroup,
        private val surfaceControl: SurfaceControl,
        private val viewHost: SurfaceControlViewHost
    ) : OverlayHandle {
        private var released = false

        override fun setVisible(visible: Boolean) {
            if (released) return
            SurfaceControl.Transaction()
                .setVisibility(surfaceControl, visible)
                .apply()
        }

        override fun release() {
            if (released) return
            released = true
            try {
                if (view.parent === container) {
                    container.removeView(view)
                }
                SurfaceControl.Transaction()
                    .reparent(surfaceControl, null)
                    .apply()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to release SurfaceControl overlay", e)
            } finally {
                viewHost.release()
            }
        }
    }

    companion object {
        private const val TAG = "SurfaceControlOverlay"
        private const val SURFACE_LAYER = 1
    }
}
