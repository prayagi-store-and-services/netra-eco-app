# 1.3.1 - Remove embedded Trikaal home card

- Removed the embedded NETRA TRIKAAL chart-calculation card from Eco home.
- Trikaal stays available as a normal store entry with its app detail page. Its standalone app and saved data are unchanged.
- No new permission, telemetry, remote install, or silent-update feature.

# 1.3.0 - Store browse, app details and explicit updates

- Original-colour social logos in swipeable header and footer rows. Today-only verified public post counts; no badge when data is unavailable, invalid, zero or from another IST day. No user tracking.

- Compact app browsing and dedicated app details. Install for a known not-installed app; Installed + Update for a newer release; Open/Uninstall for current installed apps.
- Unknown device state is not treated as not installed. Known Netra package IDs only; no new permission or all-app scan.
- Updates list with real release size/date, per-item retry and Update all download queue. Android still confirms each install. Downloaded never means installed.
- No invented ratings, reviews, download counts or data-safety claims. Privacy links come from the trusted catalog.
## 1.2.51 - Catalog and roadmap recovery

- Future apps and the roadmap load directly from the Netra GitHub repository, with direct GitHub Pages as a backup. Custom-domain DNS is not required.
- A failed Future-app request says it could not load, rather than incorrectly saying no apps are planned.
- No new permission or personal-data collection. Download/install verification remains unchanged.

## 1.2.50 - Device compatibility check

- Each release now states the minimum Android version it needs (minSdk). Before a download or update, Eco checks it against your device like the Play Store: an incompatible release is not offered, and the card says which Android version it needs.
- If you already have an app installed and its latest version no longer supports your Android version, the card shows a short static line: no security updates or any further updates possible due to Android limit.
- Releases that do not state a minimum version behave as before (nothing is hidden without data).

## 1.2.49 - Human Safety v1.5.8

- Human Safety v1.5.8 is the checked release: update notices there are timely (checks about every 6 hours and at launch) and its update card shows only when a newer version exists.

## 1.2.48 - Human Safety available again, no more scrolling strip

- Human Safety v1.5.7 (adaptive phone/tablet interface) is the checked release; the store lock now matches the website.
- A weak network or a busy GitHub no longer flips Human Safety to Unavailable: the last verified release is kept on the phone and used until a fresh check succeeds.
- Apps whose release cannot be verified are now hidden from the list instead of showing an Unavailable card; they return as soon as verification passes.

## Unreleased - community links

- Official YouTube, Instagram, Facebook, X and Telegram links in Current.
- Per-app website, real ratings/reviews and private feedback browser handoffs.
- General-browser-only routing avoids reopening Eco through its own app links; no-browser errors stay visible.
- Public review form disclosure names phone model, optional name/area and FormSubmit email copy; private contact stays separate.
- No automated follows, incentives, fake scores or new telemetry. Installer gates unchanged.

# v1.2.47 candidate (not released)

- Current apps use the latest actual release time, newest first; unknown times stay last.
- Updates available lists names, installed/published versions and an Update button. An explicit tap downloads checked bytes and asks Android to install.
- Future app cards show their published release target date/time in IST and sort by that target, nearest first. Dates are not changed by sorting.
- Observed Future-to-Current releases notify through the existing six-hour check or foreground catalog refresh. Notification taps open Current; denied/disabled alerts wait until allowed. Android can delay background work, so alerts are not guaranteed instant.
- Removed manual self-update checks. Install permission remains only for Eco's explicit installer flow. No new dependency or polling loop.

Awaiting CI, visual checks and independent review. No measured battery or thermal guarantee.

Review hardening (candidate): every installer entry now checks expected size, SHA256, package/version and independently pinned signing certificate immediately before Android handoff. Bad cached files are deleted; legacy files without bound metadata require a fresh download. Unknown signing identities fail closed. Existing Human Safety pin stays unchanged. The Future countdown stops when the hosting activity is not STARTED. This is not a measured battery improvement or Play approval; the existing APK self-update architecture is not suitable for Play distribution without a separate policy change.

## Unreleased Games section candidate
Dedicated Games entry separates live catalog games (including KBC when supplied by the current catalog) from clearly labelled plans. Ludo includes planned Snakes & Ladders in the same app; Chess, Sudoku and original match-3 are separate planned apps. Tic Tac Toe is awaiting confirmation. Planned entries have no download/install, release date, package or release link. No new permission, endpoint, telemetry or dependency. Owner preview and green checks required before public release.
