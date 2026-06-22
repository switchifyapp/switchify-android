# Spike: Multi-Display Overlay Routing

## Summary

Switchify currently treats service overlay UI as one full-screen accessibility overlay. That is not enough for multi-screen and multi-window environments. The service needs explicit overlay routing so HUDs, menus, scanners, and highlights know whether they belong to a display or to a specific accessibility window.

This spike documents the current architecture, the Android APIs involved, the data gaps, and a staged implementation path. It does not change production behavior.

## Current Architecture

The current overlay root is `SwitchifyAccessibilityWindow`.

- It creates one `WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY`.
- The overlay is `MATCH_PARENT` width and height.
- It owns one `RelativeLayout` base layout.
- Service UI components add child views into that base layout.
- It is not display-targeted.
- It assumes current/default window metrics through the service context.

Important current callers:

- `ServiceMessageHUD`
  - Adds a Compose HUD to the bottom of `SwitchifyAccessibilityWindow`.
  - Currently device/global, not display-scoped.

- `MenuHighlightHud`
  - Adds a scan-driven HUD to the top of `SwitchifyAccessibilityWindow`.
  - Calculates reserved space from current screen metrics.

- `MenuViewHandler`
  - Adds service menu views into the global overlay root.

- `NodeScannerUI`
  - Creates one scanner base layout sized with `ScreenUtils.getWidth(context)` and `ScreenUtils.getHeight(context)`.
  - Draws item, row, and escape highlights using coordinates from `Node`.
  - Uses the global overlay root.

- `Node`
  - Builds bounds from `AccessibilityNodeInfo.getBoundsInScreen(...)`.
  - Does not retain display id, window id, window type, or bounds in window coordinates.

- `ScreenUtils`
  - Reads metrics from the current `WindowManager`.
  - Uses `defaultDisplay` on older Android versions.
  - Is not display-scoped.

Keyboard-specific current implementation:

- `KeyboardBridge`
  - Detects keyboard visibility from `AccessibilityWindowInfo.TYPE_INPUT_METHOD`.
  - Stores keyboard bounds from `AccessibilityWindowInfo.getBoundsInScreen(...)`.

- `KeyboardNodeExtractor`
  - Finds the IME accessibility window with:
    ```kotlin
    windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
    ```
  - Uses that window's root node for keyboard scanning.

Switchify already treats the keyboard as a distinct accessibility window for node extraction. Overlay rendering does not yet preserve that distinction.

## Key Correction: Keyboard Is A Window

Keyboard scanning must not be modeled as merely display-scoped.

The soft keyboard is represented as an `AccessibilityWindowInfo.TYPE_INPUT_METHOD` window. Keyboard key highlights should therefore be candidates for window-scoped overlays when the IME window is available.

Display-scoped keyboard UI still exists:

- keyboard escape prompt
- HUD messages shown while using the keyboard
- menus opened while the keyboard is visible

But keyboard key highlights are content inside the IME window and should ideally carry:

- display id
- IME window id
- window type
- bounds in screen
- bounds in window

## Overlay Target Model

A future implementation should introduce explicit targets:

```kotlin
sealed class OverlayTarget {
    data class Display(val displayId: Int) : OverlayTarget()

    data class Window(
        val displayId: Int,
        val accessibilityWindowId: Int,
        val windowType: Int
    ) : OverlayTarget()
}
```

`windowType` matters because `TYPE_INPUT_METHOD` may require different fallback and lifecycle handling than application windows.

## Android API Summary

### Display-Attached Overlay

Android 14 / API 34 added:

```kotlin
AccessibilityService.attachAccessibilityOverlayToDisplay(
    displayId: Int,
    sc: SurfaceControl
)
```

This attaches an accessibility overlay `SurfaceControl` to a display. The overlay is independent of app windows on that display.

Use this for Switchify UI that belongs to a whole display:

- HUD
- service menu
- menu scan highlight HUD
- startup splash
- point scan
- radar scan
- keyboard escape prompt
- global gesture overlays
- trial overlay
- head-control overlays

### Window-Attached Overlay

Android 14 / API 34 also added:

```kotlin
AccessibilityService.attachAccessibilityOverlayToWindow(
    accessibilityWindowId: Int,
    sc: SurfaceControl
)
```

This attaches an accessibility overlay `SurfaceControl` to a specific accessibility window. Android keeps the overlay in the same relative position as the parent window moves or resizes.

Use this for Switchify UI that belongs to content inside a particular accessibility window:

- app node highlights
- app row/group highlights
- app escape highlights
- keyboard key highlights when scanning `TYPE_INPUT_METHOD`

### Surface Creation

The official API expects a `SurfaceControl`. The documented path is:

```kotlin
val viewHost = SurfaceControlViewHost(context, display, hostToken)
viewHost.setView(view, layoutParams)
val surfaceControl = viewHost.surfacePackage.surfaceControl
```

Before implementation, verify exact constructor requirements against the Android SDK available in the project, especially host token availability from the service context or overlay window.

### Removal

Remove and release an attached overlay with:

```kotlin
SurfaceControl.Transaction()
    .reparent(surfaceControl, null)
    .apply()
```

### Layering

Order multiple overlays with:

```kotlin
SurfaceControl.Transaction()
    .setLayer(surfaceControl, layer)
    .apply()
```

## Overlay Inventory

| Surface | Current Owner | Current Routing | Proposed Target |
|---|---|---|---|
| General overlay root | `SwitchifyAccessibilityWindow` | Single global overlay | Display-scoped overlay host |
| HUD | `ServiceMessageHUD` | Bottom of global overlay | `OverlayTarget.Display` |
| Menu highlight HUD | `MenuHighlightHud` | Top of global overlay | `OverlayTarget.Display` |
| Service menu | `MenuViewHandler` / `MenuView` | Global overlay child | `OverlayTarget.Display` |
| Item highlights | `NodeScannerUI` | Global overlay child | `OverlayTarget.Window` on API 34+, display fallback |
| Keyboard key highlights | `KeyboardScanner` + `NodeScannerUI` | Global overlay child | `OverlayTarget.Window(displayId, imeWindowId, TYPE_INPUT_METHOD)` on API 34+, display fallback |
| Keyboard escape prompt | `KeyboardEscapePrompt` | Global/menu overlay | `OverlayTarget.Display` |
| Point scan | point scan overlay classes | Global overlay child | `OverlayTarget.Display` |
| Radar scan | scanner classes | Global overlay child | `OverlayTarget.Display` |
| Gesture visuals | gesture visual classes | Global overlay child | `OverlayTarget.Display` |
| Trial overlay | `ServiceTrialOverlay` | Global overlay child | `OverlayTarget.Display` |
| Head-control overlays | head-control overlay classes | Global overlay child | `OverlayTarget.Display` |

## Data Gaps

### Node Metadata

Current `Node` stores:

- x
- y
- width
- height
- center
- `AccessibilityNodeInfo`

Current source:

```kotlin
nodeInfo.getBoundsInScreen(rect)
```

Future node metadata should include:

```kotlin
data class OverlayNodeBounds(
    val displayId: Int,
    val windowId: Int?,
    val windowType: Int?,
    val boundsInScreen: Rect,
    val boundsInWindow: Rect?
)
```

### Keyboard Window Metadata

Current keyboard state stores:

- visible state
- keyboard bounds in screen coordinates

Future keyboard state should also track:

```kotlin
data class KeyboardWindowTarget(
    val displayId: Int,
    val windowId: Int,
    val boundsInScreen: Rect
)
```

This should be derived from the `AccessibilityWindowInfo.TYPE_INPUT_METHOD` window found by `KeyboardBridge` and `KeyboardNodeExtractor`.

### Active Display Routing

Potential active-display sources:

- scanned node's owning window/display
- keyboard IME window display when keyboard scanner is active
- current accessibility event display, where available
- focused window from `getWindows()`
- input source display, if switch, camera, and head-control routing become display-aware
- fallback to `Display.DEFAULT_DISPLAY`

The first implementation should default to `Display.DEFAULT_DISPLAY` until callers can supply better routing data.

## Proposed Architecture

### Stage 1: Overlay Targets And Display Metrics

Add overlay target types and display-aware metrics helpers.

No behavior change.

Initial default:

```kotlin
OverlayTarget.Display(Display.DEFAULT_DISPLAY)
```

### Stage 2: Display-Scoped Overlay Host

Introduce an overlay host abstraction:

```kotlin
interface SwitchifyOverlayHost {
    fun addView(
        target: OverlayTarget.Display,
        view: ViewGroup,
        placement: OverlayPlacement
    )

    fun removeView(
        target: OverlayTarget.Display,
        view: ViewGroup
    )
}
```

Keep the existing `WindowManager.TYPE_ACCESSIBILITY_OVERLAY` backend underneath first. The goal is to route current overlays by display before changing rendering technology.

### Stage 3: Target-Aware Overlay Callers

Add optional display targets to display-scoped UI:

```kotlin
ServiceMessageHUD.showMessage(..., target: OverlayTarget.Display)
MenuViewHandler.setup(target: OverlayTarget.Display)
KeyboardEscapePrompt.show(..., target: OverlayTarget.Display)
```

Add explicit targets to scanner UI:

```kotlin
NodeScannerUI.showItemBounds(
    bounds: OverlayNodeBounds,
    target: OverlayTarget
)
```

Single-display behavior should remain unchanged by defaulting to `Display.DEFAULT_DISPLAY`.

### Stage 4: App And Keyboard Window Metadata

Update node creation to include source window context.

Current:

```kotlin
Node.fromAccessibilityNodeInfo(nodeInfo)
```

Future:

```kotlin
Node.fromAccessibilityNodeInfo(
    nodeInfo = nodeInfo,
    sourceWindow = accessibilityWindowInfo
)
```

For keyboard scanning, the source window is the `TYPE_INPUT_METHOD` window from `KeyboardNodeExtractor`.

For normal app scanning, the source window is the active or focused application window being examined.

### Stage 5: API 34 SurfaceControl Backends

Add backend selection:

```kotlin
interface OverlayBackend {
    fun attachDisplayOverlay(displayId: Int, view: View): OverlayHandle
    fun attachWindowOverlay(windowId: Int, view: View): OverlayHandle?
}
```

Backends:

- `WindowManagerAccessibilityOverlayBackend`
  - current fallback path
  - all supported Android versions

- `SurfaceControlAccessibilityOverlayBackend`
  - API 34+
  - uses `attachAccessibilityOverlayToDisplay`
  - optionally uses `attachAccessibilityOverlayToWindow`

Validate display-attached overlays before window-attached highlights.

### Stage 6: Window-Attached App And Keyboard Highlights

Move highlights after display routing is stable.

Rules:

- API 34+ and valid window id:
  - use `OverlayTarget.Window`
  - use `getBoundsInWindow`

- Missing or stale window metadata:
  - use display fallback
  - use current screen bounds path

Keyboard-specific rule:

- If keyboard scanner is active and an IME window is available:
  - keyboard key highlights should target the IME window

- If the IME window metadata is missing or stale:
  - fallback to display target using existing screen bounds

## Validation Plan

### Static Validation

Before implementation, verify:

- SDK API availability against current compile SDK.
- `SurfaceControlViewHost` constructor requirements.
- Whether a suitable host token is available from service or overlay context.
- Whether `AccessibilityWindowInfo` exposes enough id, display, and type data.
- `AccessibilityNodeInfo.getBoundsInWindow(...)` availability and behavior.
- Whether `TYPE_INPUT_METHOD` window ids are stable enough across keyboard layout changes.
- Whether keyboard root nodes can be reliably linked back to the IME window id.

### Device Validation Targets

Prioritize Android multi-display:

- Default phone/tablet display
- External display, if available
- Split screen
- Freeform window, if available
- Desktop mode, if available
- Soft keyboard / IME window
- System dialog / accessibility overlay interaction

### Manual Experiments For Follow-Up

1. Show HUD on default display.
2. Show HUD on external display.
3. Open menu on the display being scanned.
4. Highlight node in split-screen app.
5. Resize or move app window and observe whether highlight tracks.
6. Open soft keyboard and scan keyboard keys.
7. Compare keyboard key `getBoundsInScreen` vs `getBoundsInWindow`.
8. Compare app node `getBoundsInScreen` vs `getBoundsInWindow`.
9. Verify fallback below API 34.

## Risks

- API 34+ only; fallback is mandatory.
- Keyboard windows are transient; IME window ids may change when keyboard mode or layout changes.
- `SurfaceControlViewHost` may require careful lifecycle and resource cleanup.
- SurfaceControl overlays are lower-level than `WindowManager.addView`.
- Compose lifecycle owners must still be attached correctly.
- Multiple displays imply multiple overlay roots and multiple cleanup paths.
- HUD and menu singleton APIs are currently not target-aware.
- Existing `ScreenUtils` is not display-targeted.
- Node/window mapping may become stale during rapid window transitions.
- Window-attached overlays may become invalid if the window id changes or disappears.
- Keyboard scanner needs a fallback path because IME windows can disappear during selection or escape.

## Recommended Follow-Up PRs

### PR 1: Overlay Target Types And Display Metrics

- Add `OverlayTarget`.
- Add display-aware metrics helper.
- Keep behavior unchanged.
- Add unit tests for target/default selection.

### PR 2: Display-Scoped Overlay Host

- Refactor `SwitchifyAccessibilityWindow` into a display-aware host.
- Preserve default-display behavior.
- Move HUD, menu, and scanner callers through compatibility methods.
- Add host bookkeeping tests where practical.

### PR 3: Target-Aware HUD, Menu, And Keyboard Prompt APIs

- Add optional `OverlayTarget.Display` parameters.
- Default to current display fallback.
- Include keyboard escape prompt as display-scoped UI.
- Keep single-display visual behavior unchanged.

### PR 4: Node And Keyboard Window Metadata

- Capture app node display/window metadata.
- Capture keyboard `TYPE_INPUT_METHOD` window target metadata.
- Add bounds-in-window support.
- Keep screen-bound fallback.

### PR 5: API 34 SurfaceControl Prototype

- Add guarded `SurfaceControlAccessibilityOverlayBackend`.
- Gate internally.
- Validate attach-to-display first.

### PR 6: Window-Attached App And Keyboard Highlights

- Use `attachAccessibilityOverlayToWindow` for app item highlights on API 34+.
- Use `attachAccessibilityOverlayToWindow` for keyboard key highlights on API 34+ when IME window target is valid.
- Fallback to display overlay otherwise.
- Validate split-screen, freeform, and keyboard behavior.

## Acceptance Criteria For This Spike

- A GitHub issue exists with no milestone.
- A branch exists from `main`.
- A draft PR exists.
- This report is committed as the spike artifact.
- The report documents current architecture, API findings, risks, validation plan, and follow-up PR sequence.
- The report treats HUD, menu, point scan, radar, and keyboard escape prompt as display-scoped.
- The report treats app node highlights as possible window-scoped overlays.
- The report treats keyboard key highlights as possible window-scoped overlays because the keyboard is a `TYPE_INPUT_METHOD` window.
- API 34 compatibility and fallback requirements are explicit.
- `.\gradlew compileDebugKotlin` passes.

