---
title: Privacy
description: Diagnostic data and tip notifications are off until you turn them on, what they involve, what stays on the device, and where the full privacy policy is.
---

## Diagnostic data is opt-in

Switchify does not send usage events or crash reports unless you turn on **Share diagnostic data**, either in the setup step or under **Settings → About → Privacy**. You can turn it off at any time.

<div class="screenshots">
<figure><img src="/screenshots/onboarding-diagnostics.webp" alt="The Help improve Switchify setup step explaining diagnostic data, stating that Switchify never sends the content of your screen, with Share diagnostic data and No thanks buttons" loading="lazy"><figcaption>The setup step. No thanks is the default.</figcaption></figure>
</div>

When it is on, Switchify sends usage events (which features you use) and crash reports so problems can be found and fixed. If you are signed in, the reports are linked to your account email. **Switchify never sends the content of your screen.**

Sending feedback from the app also uses this channel, so **Send Feedback** only works while diagnostic data sharing is on. Feedback can include an email address if you want a reply.

## Tip notifications are opt-in

Switchify can show a notification now and then with a short tip for using the app. This is off until you turn on **Tips** under **Settings → About → Notifications**. On Android 13 and later, Android asks you to allow notifications the first time.

Tips are the same for everyone and are not linked to your account, so you do not need to be signed in. To receive them, your device registers with Google's Firebase Cloud Messaging service, which gives the device a messaging ID. Turning **Tips** off removes that registration. A tip can include a link, which opens when you select the notification.

## What the accessibility service sees

As an accessibility service, Switchify reads the structure of what is on screen so it can scan it and act on it, and it can perform gestures and take screenshots for the features that need them (Take Screenshot in the menu, and the AI features). It only observes screen content to help you navigate.

## What stays on the device

- **Usage statistics** in Settings are stored on the device and can be cleared there.
- **PC control's connection diagnostics** stay on the device unless you copy or export them. Typed text and credentials are never recorded, and PC control settings are stored only on this device.
- **Commands to a paired PC** travel only over Bluetooth, never through a Switchify account or cloud service.
- **The on-device AI model** runs entirely on your device; nothing is sent to the cloud. The model itself is downloaded once.

## Your account

Signing in is optional. When you sign in, your preferences sync to your account. You can delete the account from the Account screen or at [www.theswitchify.app/account/delete](https://www.theswitchify.app/account/delete). See [Pro and your account](/pro-and-account/).

## The full policy

The privacy policy is at [www.theswitchify.app/privacy](https://www.theswitchify.app/privacy). It is also linked from **Settings → About**. Questions: [info@theswitchify.app](mailto:info@theswitchify.app).
