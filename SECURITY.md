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

## Permissions

- `INTERNET` - to read the lists and download APKs.
- `REQUEST_INSTALL_PACKAGES` - to hand a downloaded Netra APK to the system installer.

## Limits

- A new Netra app appears in the list as soon as it is added to `projects.json`. To also detect whether it is installed, its package name must be added to the manifest `<queries>` in a new version of this app.
- Minimum Android version is 8.0 (API 26).
