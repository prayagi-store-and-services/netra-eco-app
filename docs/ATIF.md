# ATIF v3.0 - applicable parts for Netra Ecosystem (app)

ATIF v3.0 (Absolute Truth & Intelligence Framework) is the owner's permanent master rule, issued 9 Nov 2026. Canonical full text: private governance copy (`netra-intruder`, `docs/ATIF-v3.0.md`). This file lists only the parts that apply to THIS app.

## Applicable parts and why

- Part 01, 02: app listings, versions and update states must be accurate.
- Part 04, 06: installed/update status is shown only from verified package checks and per-app signer pins; failed or unknown checks never become "Not installed".
- Part 09: release notes and versions come from real published releases.
- Part 14: Android confirms every install; CI and rendered UI checks do not prove physical-device acceptance.
- Part 16: no all-app inventory scan; reviews are community submissions, labelled as such.
- Part 17, 20: descriptions across app/store/site stay in sync with verified behaviour.

## Data this app shows, and its status

- Current and Future app lists: separate; unavailable data is labelled.
- Updates: only known installed apps with verified newer releases.
- Previews: screenshots only; no video previews.
- Ratings/reviews: community submissions, not Google Play ratings - stated in the UI flow.

## Standing rule

A planned feature stays in Future/planned wording until its signed release is published and verified; no version, size or changelog is shown that does not come from the real release metadata.

## Source labels (owner-approved 9 Nov 2026)

Every user-visible data claim carries a short source label. UI strings stay short; the label for each claim lives in this file's "Data this app shows" list and in WHATS_NEW/release notes (e.g. "Source: Android BatteryManager"). In-app visible source labels are planned and ship only after the owner's UI preview.

## Stale data: OLD marking (owner-approved 9 Nov 2026)

Any dated or versioned claim that stops matching reality is marked "OLD (as of <date>)" at the next check and corrected in the same or a following PR. Stale claims are never silently edited and never left unmarked.

## Weekly audit (owner-approved 9 Nov 2026)

A weekly audit records each material claim as VERIFIED or NOT VERIFIED in `docs/ATIF-AUDIT.md`, newest first. Failures and NOT VERIFIED items are listed first in every audit and every report.
