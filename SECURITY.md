# Security notes - Netra Ecosystem

## Fuller crash reports (v1.2.39)

Automatic crash reports used to be cut off before the root cause when an exception was wrapped in others (a crash in KBC showed only the wrappers). The report now keeps a few frames of each wrapper and more frames of the root cause, within a larger size limit (6000 characters). It still holds only class names and code locations, never exception messages. No new permission, library or server. Not a vulnerability fix.

## Download count for in-app downloads (v1.2.38)

When you have switched the anonymous usage count ON in Settings (it is OFF by default), a finished and checksum-verified download adds +1 to the app's public download counter on the Ecosystem site (abacus.jasoncameron.dev, key dl-<app code>). The request carries no device id, install id, account, location or app data, only the counter name. If it fails, nothing changes for the download. With the setting OFF nothing is sent. No new permission, library or server. Not a vulnerability fix.

## App icon and verified web links (v1.2.37)

The launcher icon now matches the Netra Ecosystem website icon (the green eye on a dark background), as an adaptive icon with a themed (monochrome) layer; it works on every supported Android version (minimum 8.0). The app also declares a verified web link for its own page, https://prayagi-store-and-services.github.io/netra-eco/app/eco, so that link can open the installed app. Android opens it in the app only when the website's assetlinks.json confirms this app's signing certificate; otherwise it stays in the browser. The link carries no data into the app and no new screen reads it, so nothing is stored or sent. No new permission, library or server.

## Time Speak Out crash fix (v1.2.36)

Bug fix: when Android refused audio focus while the app was about to speak the time, the focus handle was null and was stored in a ConcurrentHashMap, which rejects null values, so the alarm receiver crashed with a NullPointerException (automatic crash report, Android 16). A small registry now stores a safe marker for a refused focus, so the line is still spoken and released exactly once. On-device logic only: no new permission, no new library, no network address, nothing stored or sent.

## Smooth scrolling with preview media (v1.2.34)

Scrolling had gone heavy again after preview media landed in v1.2.27 (reported by the owner, 20 Oct 2026): on Wi-Fi every visible app card started its own inline video, and screenshots were decoded at full camera resolution into an unbounded in-memory map. Both are fixed without removing the feature. At most one card now plays a video - the live-app card that fills most of the screen; every other card shows the tap-to-play poster, and playback stops when its card leaves the screen. Screenshots are decoded at display size (never full resolution) into a small memory-bounded cache (24 MB), so the list stays light and old images are dropped automatically. The media trust rule is unchanged: only addresses under the Netra websites are accepted, nothing is stored or sent, and on mobile data a video still downloads nothing until a tap. No new permission, library or server.

## Time Speak Out (v1.2.33)

New on-device feature: the app can speak the time every 30 minutes, on the hour and half hour. It never speaks between 11 PM and 6 AM, stays quiet when the ringer is on silent or vibrate, and never speaks into a phone call. While it speaks it holds transient audio focus, so other media pauses and then resumes (ecosystem rule, 19 Oct 2026); a watchdog releases the focus even if the speech engine never calls back. A "Test announcement" button plays a clearly labelled test line through the same speaker path - it is only a test, never a real alert. The schedule uses Android alarms: exact when Android allows it, and the card honestly says when exact timing is Unavailable and announcements may be a little late. Status shows the real next and last announced times. Everything runs on the phone; nothing is recorded, stored beyond the on/off switch and the last-announced time, or sent anywhere. New permissions: SCHEDULE_EXACT_ALARM (timing only) and RECEIVE_BOOT_COMPLETED (re-schedule after a restart). No new library, server or network address.

## Preview media and Coming soon dates (v1.2.27)

Each app card can now show a Play-Store-style preview: the app's promo video and real screenshots. The list comes from one public media.json file on the Netra website, so a website update appears in the app on its own. Only addresses under the Netra websites (prayagi-store-and-services.github.io) are accepted; anything else is ignored, and an app with no video and no real screenshots simply shows no preview. Coming soon cards now also read their approximate date from the same public roadmap.json as the website, so a card that used to say "Unavailable" shows the planned date once one exists; cards with no planned date (idea stage) still honestly say "Unavailable". Media and dates are only read and shown on the phone; nothing is stored or sent. No new permission, library or server - the network calls use the same website the app already reads.


## Public brand rename (v1.2.26)

Visible catalog app name is now Netra Ecosystem, including the offline header, Android label and widget. Package IDs, repository URLs, installer paths, permissions, signing key and saved data are unchanged. The old date/network name gate no longer controls new builds; no name-fetch request is needed. Existing releases are not rewritten. This is a branding change, not a security patch.

Netra Ecosystem is a small catalog app for the Netra apps by Prayagi Team. It shows each Netra app, whether it is installed, the newest version, and lets you download and install or update it directly.

## Not-built items removed from the Trikaal card (v1.2.23)

The Trikaal card showed a list of planned features and a "PDF soon" button that did nothing yet. Both are hidden until they work. No calculation changed. No new permission, library or network address.

## Beta channel (workflow only, no app change)

A separate workflow, Beta Release, can publish a signed test build as a GitHub pre-release with a tag like v1.2.23-beta.1. It only runs from main and uses the same signing key and tests as the stable release. The in-app updater reads only the latest stable release, so a beta is never offered to anyone automatically. Testers install it by hand from the Releases page (see BETA.md) and give feedback through a public issue form that warns against personal data. The beta process sends nothing from the phone and adds no permission, library, server or billing.

## Usage count is opt-in (v1.2.22)

The anonymous daily usage counter (adds 1 to a public counter, no ID, no location, no app data) used to be on by default. It is now OFF by default: nothing is sent until the user turns it on in Settings. People who never touched the switch are therefore no longer counted. This follows the privacy-first and DPDP consent rule. No new permission, library or network address.

## App search (v1.2.21)

A search box above the app list filters the apps by name, type and description. It runs only on the phone over the list already loaded. What you type is not stored, logged or sent anywhere. No new permission, library or network address.

## Region guard (v1.2.18)

The app refuses to open when the phone's SIM or network country is on a built-in block list (PK, BD, AF, CN, KP). The check runs only on the phone, uses no permission, no network call and no IP lookup, and nothing is stored or sent. With no signal, or an Indian SIM, it never blocks. It is a deterrent, not foolproof: removing the SIM or changing the language region bypasses it.

## Report delivery fix (v1.2.16)

Crash and feedback reports from the app now send the website address as the origin header, so the report service accepts them. Reports that could not be delivered before (they stay saved on the phone and retry at the next start) will now go through. Report contents, the stored file and the opt-in text are unchanged. No new permission, library or server.

## Trikaal kundli (v1.2.13)

Netra Ecosystem now has a Trikaal card: enter a name, birth date, time and place to see a kundli chart (North or South style), planet positions, Vimshottari dasha, current transits and a daily card. All calculation runs on the phone with the Swiss Ephemeris (Moshier mode, AGPL, source in astro-core). Up to 5 saved profiles with a default one stay in the app's private storage on the phone. One optional permission: approximate location (ACCESS_COARSE_LOCATION), asked with a clear yes or no. It is used only on the phone to work out Rahu Kaal, muhurat and tithi for your place, is stored only in the app's private storage and is never sent anywhere. If you decline, Prayagraj is used. No network call, no account, no data leaves the device. Values with no vetted rule show "Unavailable"; predictions are not claimed. Results are not checked against professional software for every case, and the card says so.

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

- `https://prayagi-store-and-services.github.io/netra-eco/projects.json` - the list of Netra apps (public file on the Netra Ecosystem website).
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
- The header has a "Check for update" button for Netra Ecosystem itself. It reads Eco's own latest.json from this repo's latest release, says whether you are on the latest version, and offers Update only when a newer one exists. The download is checked for size and SHA-256 before the Android installer opens, and the user confirms the install.

## Permissions

- `INTERNET` - to read the lists and download APKs.
- `REQUEST_INSTALL_PACKAGES` - to hand a downloaded Netra APK to the system installer.

- `netra_active` counter (v1.0.6): once a day the app adds +1 to the public Firestore counter `netra_active/netra-eco_<yyyyMMdd>` (and one per month) so the Netra site can show how many people use Eco. No user ID, no install ID, no location, no device data. On by default; switch it off with the toggle at the bottom of the app list.

## Limits

- A new Netra app appears in the list as soon as it is added to `projects.json`. To also detect whether it is installed, its package name must be added to the manifest `<queries>` in a new version of this app.
- Minimum Android version is 8.0 (API 26).

- Download progress (v1.0.3): while an APK downloads, the app shows the percentage left and an estimated time left, calculated on the phone from the bytes received. Nothing extra is sent anywhere and no new library or permission is used.

- Roadmap (v1.0.4): the app downloads one public file, roadmap.json, from the Netra Ecosystem website to show upcoming features and a countdown to the next estimated release. It sends nothing about you. No new library or permission.

- Fresher update check (v1.0.5): the app now asks the GitHub release API (public, no sign-in) for the newest release, with a no-cache request, then reads that release's latest.json. Nothing about you is sent. No new library or permission.

## Standard header (v1.1.1)
The header is now the Netra standard: 56 dp, only the app name, the installed version (from Android package info, "Unavailable" if missing) and the device date/time. The intro line and the Check for update button moved into the scrolling list; the future-plans strip stays pinned below the header. No new permission, network call or library. Every datum shown must have a real source, otherwise "Unavailable".

## Crash report (version 1.2.3)

- If the app crashes, a short report is saved on the device. Nothing is sent by itself. The "Crash report" card has a "Send crash report" button: it first shows the exact text (app, app version, phone model, Android version, the crash trace with class names and code locations only, no exception messages) and sends only if you tap Send; "Share instead" lets you pick any app. If no crash is saved it says Unavailable. A send counts as done only when the forwarding service (formsubmit.co) confirms it. No name, email, location, files or device ID is included.

## Home screen widget (version 1.2.4)

- New widget "Netra Ecosystem - app updates": shows, for each Netra app, the installed version, the latest published version and the status, exactly as the last update check made inside the app found them, with the time of that check. Before the first check it shows "Unavailable".
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


Visible version row now ends with " | Netra Ecosystem" per owner instruction. No permissions, identifiers, signing or data changes from this attribution. Header rendering is reviewed before release.

2026-10-18 crash-report facts (1.2.32): crash reports now stamp the app version and crash time at the moment of the crash, from the compile-time version constant (a PackageManager lookup can fail mid-crash and was the one path that could save version "unknown"; an old crash viewed after an update also no longer borrows the newer app's version label). BuildConfig generation is enabled for this. Reports saved by older versions keep the honest "unknown (crash was saved by an older app version)" label. Reports still contain only app name, version, crash time, phone model, Android version and the sanitized trace; no new data, permission or network change.

2026-10-20 instant time announcements (1.2.35): the Time Speak Out line is now computed when each half-hour alarm is scheduled and carried inside the alarm itself, so the moment a slot fires the phone starts speaking - no looking up or building the sentence first. If the voice engine is still starting (cold process), the ready line is queued and spoken the instant the engine is up instead of being silently dropped, and the status card now only says "announced" when the engine actually accepted the line (it previously recorded an announcement even when nothing could speak; an engine-less phone now honestly shows "Voice engine Unavailable"). The app also warms the voice engine while it is open so scheduled announcements start faster. No new permission, network call or stored data; the announcement text and schedule stay exactly as before, on the phone only.
