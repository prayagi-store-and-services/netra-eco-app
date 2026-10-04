# Security notes - Netra Eco

Netra Eco is a small catalog app for the Netra apps by Prayagi Team. It shows each Netra app, whether it is installed, the newest version, and lets you download and install or update it directly.

## What the app talks to

- `https://prayagi-store-and-services.github.io/netra-eco/projects.json` - the list of Netra apps (public file on the Netra Eco website).
- `https://github.com/prayagi-store-and-services/<repo>/releases/latest/download/latest.json` - the newest version of each app (Battery Sentinel uses the `latest.json` on its website). Public files, no login.
- `https://github.com/prayagi-store-and-services/<repo>/releases/download/<tag>/app-release.apk` - the APK itself.

Nothing else. No analytics, no accounts, no keys or API tokens in the app, no personal data sent. Requests carry only the app name `netra-eco-app` as the user agent.

## What is protected

- Only repositories of the `prayagi-store-and-services` GitHub organization are accepted from the catalog. Any other owner is ignored.
- The APK address is built from the repository and the release tag in `latest.json`; it is never taken from the file as a free URL. The tag must look like `v1.2.3`.
- The downloaded file is deleted and not installed if its size or SHA-256 does not match `latest.json`.
- The system installer asks the user to confirm. Android installs an update only if it is signed with the same key as the app already installed, so a fake APK cannot replace a Netra app. (A first install of an app is checked by Android's own installer prompt.)
- The app asks for the permission to install apps only when you tap Download, and sends you to the Android settings screen for it.
- Installed apps are found through the Android package list, limited to the Netra package names declared in the manifest (`<queries>`). The app cannot see your other apps.
- Missing data shows as "Unavailable". No made-up ratings, download counts or version numbers.

## Roadmap strip speed (v1.0.8)
- Patch: the scrolling strip at the top now moves at a calm reading pace (22 dp per second instead of 60). Touch and hold the strip to pause it. This is display only: no new permission, no new network call, no new library.

## Installer file cleanup
- Downloaded APKs live only in the app cache folder (`cache/updates/`). They are deleted when you come back to the app after the system installer closes (installed or cancelled), and never while a download is running. A new download also removes older files first.

## Manual update check
- The header has a "Check for update" button for Netra Eco itself. It reads Eco's own latest.json from this repo's latest release, says whether you are on the latest version, and offers Update only when a newer one exists. The download is checked for size and SHA-256 before the Android installer opens, and the user confirms the install.

## Permissions

- `INTERNET` - to read the lists and download APKs.
- `REQUEST_INSTALL_PACKAGES` - to hand a downloaded Netra APK to the system installer.

- `netra_active` counter (v1.0.6): once a day the app adds +1 to the public Firestore counter `netra_active/netra-eco_<yyyyMMdd>` (and one per month) so the Netra site can show how many people use Eco. No user ID, no install ID, no location, no device data. On by default; switch it off with the toggle at the bottom of the app list.

## Limits

- A new Netra app appears in the list as soon as it is added to `projects.json`. To also detect whether it is installed, its package name must be added to the manifest `<queries>` in a new version of this app.
- Minimum Android version is 8.0 (API 26).

- Download progress (v1.0.3): while an APK downloads, the app shows the percentage left and an estimated time left, calculated on the phone from the bytes received. Nothing extra is sent anywhere and no new library or permission is used.

- Roadmap (v1.0.4): the app downloads one public file, roadmap.json, from the Netra Eco website to show upcoming features and a countdown to the next estimated release. It sends nothing about you. No new library or permission.

- Fresher update check (v1.0.5): the app now asks the GitHub release API (public, no sign-in) for the newest release, with a no-cache request, then reads that release's latest.json. Nothing about you is sent. No new library or permission.
