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

<div class="screenshots">
<figure><img src="/screenshots/pc-meet.webp" alt="Step 1 of 3, Meet PC control: Control your computer from Switchify, listing what you can do, with a Get Switchify PC link and a Continue button" loading="lazy"><figcaption>Step 1: Meet PC control.</figcaption></figure>
<figure><img src="/screenshots/pc-surfaces.webp" alt="Step 2 of 3: choose the surface shown first, with Mouse, Typing, Window and Forwarding options" loading="lazy"><figcaption>Step 2: Choose what opens first.</figcaption></figure>
<figure><img src="/screenshots/pc-bluetooth.webp" alt="Step 3 of 3, Connect directly to a nearby PC, explaining the six-digit pairing code, with Allow Bluetooth and Not now buttons" loading="lazy"><figcaption>Step 3: Connect with Bluetooth.</figcaption></figure>
</div>

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

<div class="screenshots">
<figure><img src="/screenshots/pc-list.webp" alt="The PCs tab searching for computers, with Looking for PCs, a Connection diagnostics row, and tabs for PCs, Remote and Settings at the bottom" loading="lazy"><figcaption>The PCs tab while searching.</figcaption></figure>
<figure><img src="/screenshots/pc-settings.webp" alt="The PC control Settings tab with Opening surface options and a Device name field" loading="lazy"><figcaption>PC control's Settings tab.</figcaption></figure>
</div>

## Opening PC control quickly

- Add the **PC control** widget to your Android home screen. It opens PC control for your default or a chosen paired PC.
- Give a switch the **Open PC Mouse** action, or **Open PC Switch Forwarding** or **Start PC Switch Forwarding**. See [Connect a switch](/get-started/connect-a-switch/).

## If you used Switchify Remote

Switchify Remote was a separate app. It is no longer on Google Play; its features return as PC control inside Switchify 3.1. When you update, pair your computer again from PC control; switches that used to launch Remote now open PC control's Mouse surface.
