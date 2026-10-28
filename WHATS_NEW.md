# v1.2.47 candidate (not released)

- Current apps use the latest actual release time, newest first; unknown times stay last.
- Updates available lists names, installed/published versions and an Update button. An explicit tap downloads checked bytes and asks Android to install.
- Future app cards show their published release target date/time in IST and sort by that target, nearest first. Dates are not changed by sorting.
- Observed Future-to-Current releases notify through the existing six-hour check or foreground catalog refresh. Notification taps open Current; denied/disabled alerts wait until allowed. Android can delay background work, so alerts are not guaranteed instant.
- Removed manual self-update checks. Install permission remains only for Eco's explicit installer flow. No new dependency or polling loop.

Awaiting CI, visual checks and independent review. No measured battery or thermal guarantee.
