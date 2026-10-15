---
title: Troubleshooting
description: The switch does nothing, the service stopped, camera switches, keyboard and PC connection problems, and where to get help.
---

## Nothing happens when I press the switch

- Check the home screen says **Switchify is on**. If it is off, turn the service on in Android's accessibility settings.
- A USB switch must be plugged in and turned on. A Bluetooth switch must be paired with the device in Android's Bluetooth settings.
- Make sure the switch is added under **Settings → Switches → External Switches** in the **active profile**, and has an action. Auto scanning needs Select; manual scanning needs Select, Next and Previous. The home screen shows a warning and lists any missing actions.
- If Switchify is paused, nothing responds until the pause times out or you hold a switch for the hold-to-unpause time. See [Settings](/using/settings/#pause).
- If a switch has long press actions, the action runs when you let go, not when you press.

## Switchify stopped after an hour

That is the free session limit. Turn the service off and on again in Android's accessibility settings for a fresh hour, or see [Pro and your account](/pro-and-account/). A new session cannot start while the device is locked.

## Camera switches do not trigger

- Grant camera permission: the home screen shows **Enable camera** if a camera switch is configured but permission is missing.
- Use good lighting and face the front camera. **Settings → Camera Settings → Test Gestures** shows whether a face is detected and which gestures register.
- If a gesture triggers too easily or not at all, change its hold time on the **Timing Settings** tab.
- If another app is using the camera, Switchify shows **Camera taken by external app** and resumes when the camera is free.

## Selecting a key opens the menu instead of typing

Turn on **Directly select keyboard keys** under **Settings → Selection**. See [Keyboard](/using/keyboard/).

## I cannot leave the keyboard

Wait for the end of the pass: the highlight lands on **Leave the keyboard? Press your switch now to escape.** Select it. **Scan Keyboard** in the menu takes you back.

## The scan is too fast or too slow

Change the speeds under **Settings → Scanning → Scan Speeds**, and the number of **Scan Cycles** under Access Technique Settings → Item Scan. See [Scanning](/using/scanning/).

## Cannot find or connect to a PC

- Switchify PC must be open on the computer, with Bluetooth on, and Bluetooth must be on on the phone.
- Allow **Nearby devices** (Android 12 and later) or **Location** (earlier versions) when asked. On older Android versions, Location must be turned on as well; Switchify does not use your location.
- If the pairing request was declined or expired, choose **Request access** again. If saved access is no longer valid, unpair and pair again.
- If the Remote tab says the controls are unavailable, update Switchify PC and reconnect.
- **PC control → Settings → Connection diagnostics** lists each connection stage and what failed. You can copy or export it to include in a support email.

## Switch Forwarding stopped

Forwarding stops on its own after 60 seconds without switch activity, when you leave the Forwarding surface, when the connection drops, when your switch configuration changes, or when scanning on the PC stops. Start it again. See [Switch Forwarding](/pc-control/switch-forwarding/).

## Get help

Email [info@theswitchify.app](mailto:info@theswitchify.app), or use **Send Feedback** on the home screen (this needs diagnostic data sharing to be on; see [Privacy](/privacy/)). Bugs and ideas are welcome on [GitHub](https://github.com/switchifyapp/switchify-android/issues).
