# Testing

## ADB Testing Bridge

Debug builds expose an ADB testing bridge while the Switchify accessibility service is running. The bridge performs Switchify actions through the same service action path used by configured switches, without relying on `adb shell input keyevent`.

Synthetic ADB key events do not reliably reach `AccessibilityService.onKeyEvent` on Android devices, so use the broadcast bridge for automation.

### Requirements

- A debug build is installed.
- The Switchify accessibility service is enabled and running.
- The Android device is authorized for ADB.

### Broadcast Action

```powershell
com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION
```

### Action Names

```text
none
select
stop_scanning
change_scanning_direction
next
previous
toggle_gesture_lock
home
back
recents
quick_settings
notifications
lock_screen
media_play_pause
pause
toggle_gesture_lock_rearm
toggle_gesture_repeat
```

### Examples

```powershell
adb shell am broadcast -a com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION -p com.enaboapps.switchify --es action next
adb shell am broadcast -a com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION -p com.enaboapps.switchify --es action previous
adb shell am broadcast -a com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION -p com.enaboapps.switchify --es action select
```

Action IDs are also accepted:

```powershell
adb shell am broadcast -a com.enaboapps.switchify.debug.PERFORM_SWITCH_ACTION -p com.enaboapps.switchify --ei action_id 4
```

## Manual Scan QA Loop

1. Put Switchify in the scan mode and access technique you want to test.
2. Capture the screen:

```powershell
adb shell screencap -p /sdcard/switchify-screen.png
adb pull /sdcard/switchify-screen.png .
```

3. Perform an action with the ADB bridge.
4. Wait briefly, capture another screen, and inspect logcat.
5. Repeat until the scenario is complete or the scan state stops changing unexpectedly.

Avoid `uiautomator dump` during service-overlay testing unless you explicitly need it. UiAutomation can temporarily disrupt accessibility service state.

## Safety

The bridge is debug-only. Release builds do not register the ADB receiver.

Some actions perform global system operations such as Back, Home, Recents, Notifications, Quick Settings, or Lock Screen. Use those only when the test explicitly requires them.

For exploratory QA, prefer:

```text
next
previous
select
stop_scanning
change_scanning_direction
```

## Local Harness

The local harness in `switchify-agents-testing` can prime scan settings, send bridge actions, capture evidence, and restore preference backups. If it changes preferences or switch configuration, restore from that run directory when finished.
