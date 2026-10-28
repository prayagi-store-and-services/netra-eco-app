# Netra Ecosystem (app)

Browse released and planned Netra apps, read descriptions and release notes, view real screenshots, and use download/install/update controls. Android still asks before installing. Current and Future lists are separate; unavailable data is labelled.

App previews are screenshot-only. There is no in-app video player, video poster, tap-to-play or autoplay. Website videos are unchanged. This supersedes older video-preview descriptions.

Part of Netra Ecosystem: https://prayagi-store-and-services.github.io/netra-eco/
Releases: https://github.com/prayagi-store-and-services/netra-eco-app/releases

## What's new in v1.2.46
Removed all in-app video preview paths and video metadata parsing. Screenshots retain display-sized decoding and a bounded cache. No new permission, dependency or network endpoint. Automated checks only, not phone-tested. See SECURITY.md for scope and limits.

## Description maintenance
Whenever shipped features or architecture change, update app/store/site/Eco descriptions, README and release notes to match verified behavior. Do not describe planned work as shipped or promise unmeasured battery savings.

## v1.2.47 candidate (not released)
Current release-time sorting, named available updates, observed new-app release notifications and Future target date/time ordering are under test. Notification delivery depends on Android permissions and background scheduling; not instant push. Manual self-update checks are removed in this candidate. Eco retains explicit download/install controls and Android confirmation.

## Community links

The Current tab includes five official Netra social profiles. Each released app card opens its own verified website, real community ratings/reviews, or a private contact form in a browser only after a tap. There is no automatic follow, rating prompt, reward, device identifier or new background upload. Only declared browser apps are eligible, preventing Eco app-link loops; if none exists, an error is shown. This uses an APP_BROWSER visibility query, not a new permission. Reviews are community submissions, not Google Play ratings. Phone model and any entered name or area are publicly stored; a copy is emailed through FormSubmit after the web form consent. Use the separate private contact form for private feedback. Private contact details are sent only through the website form after its separate consent; they are not public reviews. Browser/network failures do not imply a submission succeeded.
