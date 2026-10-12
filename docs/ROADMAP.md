# Roadmap and explicit backlog

Version 0.3.0 advances home arrangement and on-device inference. These are MVP slices, not full completion of each phase in the original master prompt.

| Phase | Implemented | Remaining acceptance/scope |
|---|---|---|
| 0 — foundation | Kotlin/Compose/Hilt, domain/data/UI boundaries, FastAPI, CI and configuration | Production infrastructure and release ownership |
| 1 — launcher | HOME, installed apps, search, editable horizontal pages, app drag/swap, independent dock, 14 themes and persistence | Physical OEM/profile/navigation acceptance |
| 2 — design agent | Auth/quota/bounds, catalog-based Nemotron configuration, strict patches, preview/apply and undo/redo | Live Nebius account/model/HTTPS end-to-end verification; richer capability planning |
| 3 — widgets | Six registered types, five shapes, prompts, persistence, gallery, grid placement/resize, rotation/scale and collection reorder | Arbitrary bounded node trees, connectors, charts, external AppWidget hosting, arbitrary placement/pinch |
| 4 — image reference | Photo picker, normalization, verified-model gate and theme-token proposal | Real vision verification; hierarchy/bounds extraction and editable reconstruction |
| 5 — local intelligence | Contact ranking, optional local app history/Usage Access, confidence/hide/delete, unified entity search | Broader context/session prediction and real provider-data acceptance |
| 6 — dashboard | Notification listener, local grouping/search, encrypted opt-in history, PendingIntents, media/volume, activity brightness, overlay | Full grant/revoke/history/media/overlay acceptance, summaries with consent, bubbles and richer live connectivity |
| 7 — advanced launcher | Deferrable offline-theme rules, radial drawer/pages/list alternative, search swipes, wallpaper double tap/accent, host app shortcuts and internal Glance | Folders, shortcut pin hosting, broader gestures/actions, OEM worker/reboot validation |
| 8 — hardening | Private native AI process, strict local-output/snapshot validation, bounded model import, unit/instrumentation/backend checks, lint, debug/release compilation and manual emulator smoke | Physical performance/memory/battery, TalkBack/large font/reduced motion, security/supply-chain review, production signing |

## Known limitations

- Native launcher baseline is API 29–36; the optional native model runtime includes arm64-v8a and x86_64 libraries; only API 36 emulator instrumentation was executed locally. Robolectric API 29 is not an Android 10 device acceptance test.
- Current-user apps only. Work/private profiles, OEM recents and global gestures need separate capability and device handling.
- Six widget kinds are genuinely functional within their public data-source boundaries. Timer is a persistent on-screen countdown, with no alarm/notification on completion. Calendar/birthday access remains optional.
- Scale/rotation can visually extend beyond a card. Grid collision checks protect placements; formal large-font/TalkBack and physical-device acceptance remain pending. Manual layout changes persist across themes.
- Notification grouping is local; no cloud summaries are sent. No system shade or secure lock-screen replacement occurs.
- Theme rules only select installed offline themes. Background execution may be delayed by Android/OEM power policy. No exact scheduling or arbitrary device operations are claimed.
- Live Nemotron/vision requests, deployed HTTPS integration, Docker and Nebius Serverless Endpoints/Jobs are unverified. Model IDs are configured from account availability.
- A shared client token and per-process quota are suitable for a private MVP, not a public multi-tenant service. The unsigned release build is compilation evidence; the delivered APK is debug-signed.
- Lint warnings per variant remain, including native overlay accessibility and localization. Dependency upgrades are advisory, not a completed vulnerability assessment.

The next work should close these gaps deliberately. Historical phase verification files describe incremental milestones; VERIFICATION.md records the current final checks.

- On-device import and inference are integrated. Model artifact/runtime compatibility, RAM and output quality vary. Model setup/evidence is in ON_DEVICE_AI.md; physical phone inference is unverified.

## 0.4 connected spaces increment

Implemented: community API and Android browse/upload/import/export/preview, invitation-based creator keys and moderator queue; paired TLS Windows companion and Android foreground clipboard/share/media transfers; coordinated theme icons plus original option; local image colors and text-model refinement; optional multimodal GPU runtime path. Physical-phone vision, full Windows clipboard/media interoperability, public hosting/account recovery and production signing remain acceptance work.
