# Play Protect investigation,11 November2026

## Verified evidence
Owner10:49screenshot shows 'Harmful app blocked', app label Netra Ecosystem, installed UI1.3.1 behind dialog. Owner10:50clarifies 'Netra ecosystem'. Blocked package/version/hash not exposed by screenshot. Do not infer SensorGuard from an older todo.

Downloaded public Eco assets:
-1.3.1/code67, com.prayagi.netraeco, SHA25675f0caee8d2ff17e5f5d1157f028148ba97135d6c8ab26babab6f52ce5515aba.
-1.3.2/code68, com.prayagi.netraeco, SHA25630b8c62ab99fa0db9cf7e866d586288fb0cea45d8a06c8bd18d4f60cc63381e1.
-Both target36, APKv2signer certificateSHA256543b46402206ae0022d352f65bea618386bd7aa681b4d5bcfb5ce02bac49c8c4. Extracted signer equality is not an independent full apksig cryptographic verification in this investigation.
-Permissions unchanged between versions: internet/network, notifications, package install/uninstall requests, exact alarms/boot, coarse location, normal WorkManager foreground/wakelock, dynamic receiver permission. No SMS/accessibility/notification-listener declaration in either manifest. Standard ZIP header. No engine/native executable payload introduced by1.3.2.
-Source1.3.1→1.3.2 changes Games UI/docs/version/CI, not installer/manifest/dependencies. No evidence that a new permission or signing change caused warning.

## Legitimate repair in this PR
Removed unused ACCESS_COARSE_LOCATION. Embedded Trikaal UI was removed in1.3.1; its dormant location helper has no production caller outside its dormant card. Store does not need location. Manifest regression test prevents reintroduction and high-risk unrelated permission creep. This minimizes unnecessary access; it is NOT proven Play Protect cause or a guaranteed warning fix.

## Installer/security observations
APK bytes checked for declared size/hash and cached identity/package/version/certificate before opening Android installer. System confirmation remains, no silent install. Update notification uses an internal one-use token. Background self-update pre-download exists; user tap still required to launch installer. This behavior merits disclosure/policy review, not an unsupported malware verdict. Removing installer permissions or changing identity solely to avoid classification is not the chosen fix.

No hidden SMS/accessibility/device-admin or remote code-loading API found in targeted source inspection. This is not an exhaustive malware certification. Usage count opt-in defaults off. Crash reports are user-previewed/user-tap HTTPS through FormSubmit, not file-level E2EE. No claim of Play Protect clearance.

## Unknowns / next proof
Need actual blocked APK filename/hash or version from download item and Google's classification/review details. Cannot reproduce proprietary device/cloud classification from local unit tests or an emulator without that state. After verified compliance audit, prepare an appeal for owner review; no appeal submitted by this PR. Do not disable Play Protect or choose Install anyway as a workaround.

## Sources
-https://github.com/prayagi-store-and-services/netra-eco-app/releases/tag/v1.3.1
-https://github.com/prayagi-store-and-services/netra-eco-app/releases/tag/v1.3.2
-https://developers.google.com/android/play-protect/warning-dev-guidance
-https://developers.google.com/android/play-protect/phacategories

Google guidance distinguishes harmful-app classification from unknown-app scan or sensitive-permission warnings. Only Google can confirm its specific classification reason. Full phone install acceptance still pending. No release authorized.
