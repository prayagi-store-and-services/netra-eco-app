## Unreleased - community links

- Official YouTube, Instagram, Facebook, X and Telegram links in Current.
- Per-app website, real ratings/reviews and private feedback browser handoffs.
- No automated follows, incentives, fake scores or new telemetry. Installer gates unchanged.

# v1.2.47 candidate (not released)

- Current apps use the latest actual release time, newest first; unknown times stay last.
- Updates available lists names, installed/published versions and an Update button. An explicit tap downloads checked bytes and asks Android to install.
- Future app cards show their published release target date/time in IST and sort by that target, nearest first. Dates are not changed by sorting.
- Observed Future-to-Current releases notify through the existing six-hour check or foreground catalog refresh. Notification taps open Current; denied/disabled alerts wait until allowed. Android can delay background work, so alerts are not guaranteed instant.
- Removed manual self-update checks. Install permission remains only for Eco's explicit installer flow. No new dependency or polling loop.

Awaiting CI, visual checks and independent review. No measured battery or thermal guarantee.

Review hardening (candidate): every installer entry now checks expected size, SHA256, package/version and independently pinned signing certificate immediately before Android handoff. Bad cached files are deleted; legacy files without bound metadata require a fresh download. Unknown signing identities fail closed. Existing Human Safety pin stays unchanged. The Future countdown stops when the hosting activity is not STARTED. This is not a measured battery improvement or Play approval; the existing APK self-update architecture is not suitable for Play distribution without a separate policy change.
