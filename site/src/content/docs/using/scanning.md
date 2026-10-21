---
title: Scanning
description: The three access techniques (item scan, point scan and radar), auto and manual scanning, and the timing settings for each.
---

An **access technique** is how Switchify decides which part of the screen you mean. A **scanning mode** is who moves the highlight: Switchify (auto) or you (manual). Choose both under **Settings → Scanning**. You can change the technique at any time from the Settings item in [the menu](/using/the-menu/) without leaving what you are doing.

<div class="screenshots">
<figure><img src="/screenshots/access-technique.webp" alt="The Select Access Technique dialog offering Item Scan, Point Scan and Radar, each with a one-line description" loading="lazy"><figcaption>Choosing an access technique.</figcaption></figure>
<figure><img src="/screenshots/scanning-mode.webp" alt="The Select Scanning Mode dialog offering Auto, use a single switch to scan and select, and Manual, use multiple switches to manually move and select" loading="lazy"><figcaption>Choosing a scanning mode.</figcaption></figure>
</div>

## Access techniques

### Item scan

Item scan highlights the interactive things on screen, such as buttons, links and text fields, one at a time. Press Select when the one you want is highlighted. This is the default and the quickest for everyday tasks.

<div class="screenshots">
<figure><img src="/screenshots/item-scan.webp" alt="Android Settings with the Google row outlined in orange by the item scan highlight" loading="lazy"><figcaption>Item scan on the Android Settings screen.</figcaption></figure>
</div>

- **Row/Column Scan** moves through rows first, then the items in the chosen row.
- **Group Scan** splits long rows into smaller groups to shorten the scan. Applies when Row/Column Scan is on. You can also toggle it from the menu.
- **Item Scan Speech** speaks each item's description as it is highlighted.
- **Scan Cycles**: how many times to go round before stopping, from 1 to 10.
- **Item Scan Speed**: 0.2 to 10 seconds per item, in auto scan.

The highlight can be a **Border**, a **Fill**, or a **Spotlight** that dims everything except the current item or row. You can animate the highlight as it moves and show a countdown of the time left before it advances.

### Point scan

Point scan picks an exact spot with moving lines. Two modes:

- **Line only.** A line moves across the screen; press to stop it. A second line then moves the other way; press again. Where they cross is your point.
- **Grid + line.** The screen is first divided into a grid from 2 × 2 to 10 × 10. The blocks highlight in turn; pick one, then the lines run inside it.

**Line speed** has five presets, from very slow to very fast. **Grid scan speed** (0.1 to 5 seconds) applies while you are choosing a block. Both are auto scan settings.

<div class="screenshots">
<figure><img src="/screenshots/point-scan.webp" alt="Android Settings with an orange vertical line and an orange horizontal line crossing over the Sound and vibration row, drawn by point scan" loading="lazy"><figcaption>Point scan: where the two lines cross is your point.</figcaption></figure>
</div>

### Radar

A line rotates round the screen like a windscreen wiper. Press to lock the angle. A circle then travels out along that line; press again to drop the point there. It reaches awkward corners and overlaid menus that item scan cannot.

<div class="screenshots">
<figure><img src="/screenshots/radar.webp" alt="Android Settings with a blue radar line angled across the top-left corner and an orange circle travelling along it" loading="lazy"><figcaption>Radar: the circle travels along the locked line.</figcaption></figure>
</div>

- **Radar only** sweeps the whole screen. **Grid + radar** picks a grid block first, then sweeps inside it.
- **Radar speed** has five presets, from very slow to very fast, with a separate grid scan speed.
- **Slow Down Then Select**: press once to slow the sweep, press again to select.
- **Radar Starting Position**: Top, Bottom or Center.

### App-specific scanning

**Settings → Scanning → App-specific scanning** gives an app its own technique. Apps without a rule use your default.

## Scanning modes

### Auto

The highlight moves by itself at the speed you set. Press Select when it reaches what you want. One switch is enough.

Under **Auto Scan Settings**:

- **Auto-start scanning** starts the next scan by itself after a selection.
- **Pause on first item** holds the highlight on the first item of each cycle for 0.5 to 3 seconds.

### Manual

The highlight moves only when you press **Next** or **Previous**, and **Select** picks the item. Manual needs all three actions.

Under **Manual Scan Settings**, **Move Repeat** keeps moving while you hold a Next or Previous switch, with a delay of 0.1 to 2 seconds between moves.

## Speeds

**Settings → Scanning → Scan Speeds** gathers the auto scan speeds in one place: point scan speed (shown as line speed and grid scan speed when a grid is used), item scan speed, and radar speed with its grid scan speed.

## What happens after you select

With item scan, selecting an item opens [the menu](/using/the-menu/) at that spot by default. **Auto select** under **Settings → Selection** taps the item instead after a delay of your choosing (0.1 to 100 seconds); press again during the delay to open the menu. **Assisted selection** chooses the nearest interactive item to where you selected.

## Highlight colours

**Settings → Scanning → Scan Color** offers five colour sets: **Default** (blue and amber), **High Contrast** (black and white), **Deuteranopia-safe** (blue and yellow), **Tritanopia-safe** (red and teal) and **Monochrome** (greyscale).
