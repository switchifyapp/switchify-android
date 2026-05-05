package com.enaboapps.switchify.service.scanning

import android.graphics.Color
import android.view.animation.PathInterpolator

/**
 * Single source of truth for scan visual design tokens.
 * Item scan, point scan, and radar all reference these values so the
 * surfaces stay visually consistent.
 */
object ScanVisualConstants {

    // ---- Stroke widths (dp) ----

    /** Active item/group highlight stroke. */
    const val ACTIVE_STROKE_DP = 3

    /** Stroke around the tinted fill in fill-mode highlight. */
    const val FILL_STROKE_DP = 2

    /** Stroke for non-highlight structural overlays (point-scan grid, screen outline). */
    const val STRUCTURAL_STROKE_DP = 2

    /** Halo outline that contrasts with the active highlight color. */
    const val HALO_STROKE_DP = 1

    /** Cursor crosshair line, active block outline, and radar swept-line thickness. */
    const val CURSOR_LINE_DP = 4

    // ---- Corner radius (dp) ----

    const val CORNER_RADIUS_DP = 8f

    // ---- Spacing (dp) ----

    /** Outward offset of the contrast halo from the main highlight stroke. */
    const val HALO_OFFSET_DP = 2

    /** Padding between adjacent point-scan grid blocks so strokes overlap cleanly. */
    const val GRID_OVERLAP_PADDING_DP = 2

    // ---- Alphas (0-255) ----

    /** Tint alpha for fill-mode highlight (~12%). */
    const val FILL_ALPHA = 31

    /** Alpha for the contrast halo (~55%). */
    const val HALO_ALPHA = 140

    /** Alpha for structural overlays — grid, screen outline (~63%). */
    const val STRUCTURAL_ALPHA = 160

    // ---- Alphas (0-1) ----

    /** Alpha for radar swept-line and indicator circle (70%). */
    const val RADAR_ALPHA = 0.7f

    // ---- Structural colour ----

    /** Tone for non-highlight overlays. Hardcoded so it's always
     *  distinguishable from the user's selected scan colours. */
    val STRUCTURAL_COLOR: Int = Color.argb(STRUCTURAL_ALPHA, 0, 0, 0)

    // ---- Animation ----

    const val SHOW_DURATION_MS = 120L
    const val HIDE_DURATION_MS = 80L
    const val INITIAL_SCALE = 0.96f

    /** Material 3 standard easing curve (fast out, slow in). */
    val SHOW_INTERPOLATOR = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
}
