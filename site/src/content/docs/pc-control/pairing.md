---
title: Pair a PC
description: Connect Switchify to Switchify PC on a Windows PC or Mac over Bluetooth by comparing a six-digit code, and manage saved PCs.
---

PC control is built into Switchify. Once paired with a computer running [Switchify PC](https://pc.theswitchify.app), you can drive its pointer, keyboard and windows from the phone, or forward your switches to it. It is free and needs neither Pro nor an account.

## What you need

- Switchify PC installed and open on the computer, with Bluetooth on. The setup in Switchify links to the [downloads](https://github.com/switchifyapp/switchify-pc/releases).
- Bluetooth Low Energy on the Android device.

## Set up PC control

Open **PC control** from Switchify's home screen. The first time, a three-step setup runs:

1. **Meet PC control**: what it does, and a link to get Switchify PC.
2. **Choose what opens first**: the surface shown on the Remote tab when a PC connects (Mouse, Typing, Window or Forwarding). You can change this later in PC control's Settings.
3. **Connect with Bluetooth**: Android asks to allow **Nearby devices**. On versions before Android 12 it asks for **Location** instead, and Location must be turned on; Switchify does not use your location.

## Pair

1. On the **PCs** tab, choose **Find nearby PCs** and pick your computer. New computers show **Request access**.
2. Both apps show a six-digit code. Compare them.
3. On the computer, approve the request if the codes match. If Switchify PC is signed in to the same Switchify account, it approves automatically.

Commands travel only over Bluetooth, not through a Switchify account or cloud service.

## Saved PCs

The PCs tab lists saved computers with their platform (Windows or macOS) and whether they are nearby. For each you can **Connect**, **Set as default** or **Unpair**. Unpairing removes the saved access from this device; you will need to pair again.

In PC control's **Settings** tab:

- **Default PC**: the computer used for automatic connection, or the most recently connected one.
- **Device name**: how this device appears on computers when pairing (up to 40 characters, or the device model name).
- **Opening surface** and **Typing mode** (live or draft).
- **Pointer controls**: the pointer speed, movement repeat and displays reported by the connected computer. These are set on the computer.
- **Switch Forwarding**: the hold-to-stop time. See [Switch Forwarding](/pc-control/switch-forwarding/).
- **Connection diagnostics**: a log of connection events that stays on the device. Typed text and credentials are never recorded. You can copy, export or clear it.

PC control settings are stored only on this device.

## Opening PC control quickly

- Add the **PC control** widget to your Android home screen. It opens PC control for your default or a chosen paired PC.
- Give a switch the **Open PC Mouse** action, or **Open PC Switch Forwarding** or **Start PC Switch Forwarding**. See [Connect a switch](/get-started/connect-a-switch/).

## If you used Switchify Remote

Switchify Remote was a separate app. Its features are now PC control inside Switchify. Pair your computer again from PC control; switches that used to launch Remote now open PC control's Mouse surface.
