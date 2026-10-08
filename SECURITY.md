# Security notes - Netra Eco

Netra Eco is a small catalog app for the Netra apps by Prayagi Team. It shows each Netra app, whether it is installed, the newest version, and lets you download and install or update it directly.

## Trikaal kundli (v1.2.13)

Netra Eco now has a Trikaal card: enter a name, birth date, time and place to see a kundli chart (North or South style), planet positions, Vimshottari dasha, current transits and a daily card. All calculation runs on the phone with the Swiss Ephemeris (Moshier mode, AGPL, source in astro-core). Saved profiles stay in the app's private storage on the phone. No new permission, no network call, no account, no data leaves the device. Values with no vetted rule show "Unavailable"; predictions are not claimed. Results are not checked against professional software for every case, and the card says so.

## Coming soon list (v1.2.12)

The app now also reads an "upcoming" list from the same public projects.json and shows each planned app as a card with an approximate countdown. These cards have no download or update button. No new permission, library or network address.

## App card layout (1.2.11)

The latest version and size no longer wrap onto the package line. Layout only: no new permission, library or network call.

## Downloads fix and download list (v1.2.10)
- Download progress now counts up (percent downloaded) and the time left no longer rises. Several apps can download at the same time. Finished files stay in a Downloads list with Install and Delete, and are deleted automatically once that version is installed.
- Files stay in the app's private cache folder only. No new permission, library or network call.

## Update alert (v1.2.9)
- Every 6 hours (network needed) the app checks its own public GitHub release and, when a newer version exists, shows one notification. Tapping it downloads the build, checks size and SHA-256, and opens the Android installer. The notification plays the normal notification sound.
- New permission: POST_NOTIFICATIONS (asked once on Android 13+; if refused, no notification is shown). New library: AndroidX WorkManager work-runtime-ktx 2.10.0. No new server, no personal data.

## Plain failure messages (1.2.8)
When a download, update, open or uninstall fails, the app now shows one plain sentence (for example "No internet, or the server did not answer") instead of the raw system text such as "Unable to resolve host". The app's own messages (checksum, allow installs) are unchanged. No new permission, library or network call.

## Next planned release per app (1.2.2)
Each app card shows the next planned item for that app and a live countdown to its ETA, both read from the same public roadmap file as the strip. The ETA is approximate and says so; it can be earlier or later. If no ETA is set, or the roadmap cannot be read, the card says Unavailable. No new network call or permission.

## Future plans strip (1.2.1)
The scrolling strip lists only plans that are not released yet. An item that names app versions is removed by itself once the latest published release of each named app (read from GitHub, the same data as the app cards) has reached that version. If a latest version cannot be read, the item stays, because it cannot be proven done. Nothing is hidden or marked done by hand, and no extra network call or permission was added.

## What the app talks to

- `https://prayagi-store-and-services.github.io/netra-eco/projects.json` - the list of Netra apps (public file on the Netra Eco website).
- `https://github.com/prayagi-store-and-services/<repo>/releases/latest/download/latest.json` - the newest version of each app (Battery Sentinel uses the `latest.json` on its website). Public files, no login.
- `https://github.com/prayagi-store-and-services/<repo>/releases/download/<tag>/app-release.apk` - the APK itself.
- `https://raw.githubusercontent.com/prayagi-store-and-services/<repo>/main/app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` (new in 1.2.0) - the launcher image of an app that is not installed, so its real icon can be shown. Only this one image path, only for repositories of this organization. An installed app shows its own icon from the phone. If neither can be read, a plain letter badge is shown instead, which is not the app icon.

Nothing else. No analytics, no accounts, no keys or API tokens in the app, no personal data sent. Requests carry only the app name `netra-eco-app` as the user agent.

## What is protected

- Only repositories of the `prayagi-store-and-services` GitHub organization are accepted from the catalog. Any other owner is ignored.
- The APK address is built from the repository and the release tag in `latest.json`; it is never taken from the file as a free URL. The tag must look like `v1.2.3`.
- The downloaded file is deleted and not installed if its size or SHA-256 does not match `latest.json`.
- The system installer asks the user to confirm. Android installs an update only if it is signed with the same key as the app already installed, so a fake APK cannot replace a Netra app. (A first install of an app is checked by Android's own installer prompt.)
- The app asks for the permission to install apps only when you tap Download, and sends you to the Android settings screen for it.
- Installed apps are found through the Android package list, limited to the Netra package names declared in the manifest (`<queries>`). The app cannot see your other apps.
- Missing data shows as "Unavailable". No made-up ratings, download counts or version numbers.

## Open and Uninstall buttons (v1.1.0)
- Every installed Netra app card now has an Open button (starts the app's own launcher screen) and an Uninstall button (asks Android to remove the app). Uninstall needs one new normal permission, `REQUEST_DELETE_PACKAGES`, granted at install with no popup. Android always shows its own confirm dialog; Eco cannot uninstall silently and cannot skip it. Open and Uninstall work only for the Netra package names already in the manifest `<queries>`; Eco still cannot see any other app. No new library, no new network call, nothing is sent anywhere.

## Roadmap strip speed (v1.0.8)
- Patch: the scrolling strip at the top now moves at a calm reading pace (22 dp per second instead of 60). Touch and hold the strip to pause it. This is display only: no new permission, no new network call, no new library.

## Installed-app detection, clearer (v1.0.10)
- Each card now shows which package name Eco looked for and, when found, the installed version code, so a wrong "Not installed" can be diagnosed. The list also refreshes the moment any app is installed, updated or removed (a receiver for the system package-added, replaced and removed events, registered only while Eco is open; it carries only a package name and Eco ignores everything except refreshing its own list). Still only the Netra package names in the manifest queries are visible to Eco. No new permission, no new library.

## Ticker direction (v1.0.9)
- Patch: the top strip now scrolls from right to left, like a normal news ticker, and every item names its app (several apps are joined with commas). Display only; nothing else changed.

## Netra Player listing (v1.0.9)
- Patch: Netra Player is now in the list. The manifest `<queries>` gets one more package name (`com.prayagi.netraplayer`) so the app can tell whether Netra Player is installed. Nothing else is visible to Eco. No new permission, no new library.

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

## Standard header (v1.1.1)
The header is now the Netra standard: 56 dp, only the app name, the installed version (from Android package info, "Unavailable" if missing) and the device date/time. The intro line and the Check for update button moved into the scrolling list; the future-plans strip stays pinned below the header. No new permission, network call or library. Every datum shown must have a real source, otherwise "Unavailable".

## Crash report (version 1.2.3)

- If the app crashes, a short report is saved on the device. Nothing is sent by itself. The "Crash report" card has a "Send crash report" button: it first shows the exact text (app, app version, phone model, Android version, the crash trace with class names and code locations only, no exception messages) and sends only if you tap Send; "Share instead" lets you pick any app. If no crash is saved it says Unavailable. A send counts as done only when the forwarding service (formsubmit.co) confirms it. No name, email, location, files or device ID is included.

## Home screen widget (version 1.2.4)

- New widget "Netra Eco - app updates": shows, for each Netra app, the installed version, the latest published version and the status, exactly as the last update check made inside the app found them, with the time of that check. Before the first check it shows "Unavailable".
- Stored on this phone only (local preferences, not backed up because backup is off): one text line per app and the check time. Nothing is sent anywhere. The widget makes no network call and has no timer: the app redraws it after each check it already makes.
- No new permission, network call or library. The widget receiver is exported because Android's launcher must send it update events; it handles only that action. Tapping the widget opens the app.

## Dated display change (version 1.2.5)

- The app now makes one extra small request per launch to a file on our own site and reads the server's date from the answer (the phone clock is never used for this). It sends no data about you. If the site has no such file yet, or there is no network, nothing changes. No new permission or library.
- Stored on this phone only: a short display text, if one is ever published. A server date can be faked by someone who controls your network connection; the worst case is that the display text appears earlier or not at all. It cannot change anything else in the app.

## Permissions list (version 1.2.6)

- New "Permissions" card in the app: lists each permission the app uses (Internet, Install apps, Uninstall apps), the plain reason, and the live status read from Android when you open the screen (no timer). Tapping "Install apps" opens the Android page where you can allow or stop it. Internet and Uninstall apps are normal permissions, shown as always allowed.
- No new permission, network call or library.

## Festival banner (version 1.2.7)

- A card at the top of the app shows today's festival (India calendar, bundled in the app, from timeanddate.com India 2026-2027) or "coming soon" for a festival within 3 days, with the live date and time. India's Independence Day (15 August) is shown too. It has no death anniversaries and no other country's days. After 2027 there is no data, so no banner is shown and nothing is invented. A date marked "may differ by a day" says so.
- It works offline. No new permission, network call or library.
