---
title: Connect a switch
description: Add an external USB or Bluetooth switch or a camera gesture, choose what a press does, and set up long press actions, stability and switch profiles.
---

Switchify sees an **external switch** as a key press. Anything that sends a key when you press it will work: a USB or Bluetooth switch interface, a Bluetooth button, or a key on an external keyboard. A **camera switch** is a facial gesture recognised by the front camera, and needs no hardware at all. You can mix the two.

Switches live under **Settings → General → Switches**.

## Add an external switch

1. Connect the switch. A USB switch needs to be plugged in and turned on; a Bluetooth switch needs to be paired with the device in Android's Bluetooth settings.
2. In Switchify, open **Settings → Switches → External Switches** and add a new switch.
3. Press the switch you want to use. Switchify learns the key it sends.
4. Choose a **press action** (see below). Switchify names the switch for you; rename it if you like.
5. Save. Long press actions can be set once the switch is saved.

Each key can belong to only one switch.

## Add a camera switch

1. Open **Settings → Switches → Camera Switches** and add a new switch. Switchify asks for camera permission the first time.
2. Choose a facial gesture: **Smile**, **Left Wink**, **Right Wink**, **Blink**, **Pucker**, **Head Turn Left**, **Head Turn Right**, **Head Turn Up** or **Head Turn Down**. Each gesture can be used by one switch.
3. Choose a press action and save.

Camera switches need good lighting. Under **Settings → Camera Settings** you can **test gestures** live and set how long each expression must be held before it counts. Head turns trigger as soon as they are seen and have no hold time or sensitivity setting:

| Gesture | Hold time |
| --- | --- |
| Smile | 0.1 to 5 seconds |
| Left wink, right wink | 0.1 to 3 seconds |
| Blink | 0.1 to 2 seconds |
| Pucker | 0.1 to 3 seconds |

If another app takes the camera, Switchify shows a message and the gestures resume when the camera is free again.

## Choose what a press does

| Action | What it does |
| --- | --- |
| **Select** | Select the highlighted item, or start auto scanning if it is not running. Auto scanning needs one switch set to Select. |
| **Move to Next Item**, **Move to Previous Item** | Step the highlight. Manual scanning needs Select, Next and Previous. |
| **Stop Scanning** | Stop the scan. |
| **Change Scanning Direction** | Reverse the scan. Auto scanning only. |
| **Home**, **Back**, **Recent Apps**, **Quick Settings**, **Notifications**, **Lock Screen** | The matching Android action. |
| **Play/Pause Media or Answer/End Call** | Acts like a headset button. |
| **Pause Switchify** | Pause scanning so you can use the switches with another app. See [Settings](/using/settings/#pause). |
| **Toggle Gesture Lock**, **Toggle Gesture Lock Re-arm**, **Toggle Gesture Repeat** | See [Gestures and actions](/using/gestures-and-actions/). |
| **Launch app** | Open an app you choose. |
| **Open PC Mouse**, **Open PC Switch Forwarding**, **Start PC Switch Forwarding** | Open PC control on that surface. See [PC control](/pc-control/pairing/). |
| **None** | Do nothing. |

Which actions are offered depends on the scan mode. The action picker shows **Recommended** chips for any required action you have not assigned yet, and the home screen warns if the active profile does not have the actions the scan mode needs.

## Long press actions

A switch can also run different actions depending on how long you hold it. Turn on **Enable Switch Hold** under **Settings → Switch Hold** and set the **Switch Hold Time** (0.1 to 10 seconds). Then edit a saved switch and add **Long Press Actions**. Actions execute in sequence based on hold duration: while you hold, Switchify shows on screen which action will run when you let go.

## Switch stability

Under **Settings → Switch Stability**, **Ignore Repeat** ignores repeated presses of the same switch for a delay you set (0.1 to 10 seconds). This helps when movement is involuntary.

## Switch profiles

**Settings → Switches → Switch Profiles** saves different external-switch and camera-gesture mappings as named profiles, for example one for a tray-mounted switch and one for camera gestures. Only the active profile controls Switchify, and you can edit another profile without changing your current controls.

Before a profile becomes active, Switchify asks you to use its Select action within 60 seconds, so you are never left without a working switch. You can also change profile from the **Switch profile** item in [the menu](/using/the-menu/). Profiles can be created, duplicated, renamed and deleted.
