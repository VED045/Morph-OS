# Roadmap and explicit backlog

Feature development is paused at the tested 0.2.0 snapshot. These are MVP slices, not full completion of each phase in the original master prompt.

| Phase | Implemented | Remaining acceptance/scope |
|---|---|---|
| 0 — foundation | Kotlin/Compose/Hilt, domain/data/UI boundaries, FastAPI, CI and configuration | Production infrastructure and release ownership |
| 1 — launcher | HOME, installed apps, search, pins/dock, pages, 14 themes and persistence | Physical OEM/profile/navigation acceptance |
| 2 — design agent | Auth/quota/bounds, catalog-based Nemotron configuration, strict patches, preview/apply and undo/redo | Live Nebius account/model/HTTPS end-to-end verification; richer capability planning |
| 3 — widgets | Six registered types, five shapes, prompts, persistence, rotation/scale and vertical reorder | Arbitrary bounded node trees, connectors, charts, external AppWidget hosting, free placement/pinch |
| 4 — image reference | Photo picker, normalization, verified-model gate and theme-token proposal | Real vision verification; hierarchy/bounds extraction and editable reconstruction |
| 5 — local intelligence | Contact ranking, optional local app history/Usage Access, confidence/hide/delete, unified entity search | Broader context/session prediction and real provider-data acceptance |
| 6 — dashboard | Notification listener, local grouping/search, encrypted opt-in history, PendingIntents, media/volume, activity brightness, overlay | Full grant/revoke/history/media/overlay acceptance, summaries with consent, bubbles and richer live connectivity |
| 7 — advanced launcher | Deferrable offline-theme rules, radial drawer/pages/list alternative, search swipes, wallpaper double tap/accent, host app shortcuts and internal Glance | Folders, app drag/drop, shortcut pin hosting, custom docks, broader gestures/actions, OEM worker/reboot validation |
| 8 — hardening | Current unit/instrumentation/backend checks, lint, debug/release compilation and manual emulator smoke | Physical performance/memory/battery, TalkBack/large font/reduced motion, security/supply-chain review, production signing |

## Known limitations

- Native launcher baseline is API 29–36; only API 36 emulator instrumentation was executed locally. Robolectric API 29 is not an Android 10 device acceptance test.
- Current-user apps only. Work/private profiles, OEM recents and global gestures need separate capability and device handling.
- Six widget kinds are genuinely functional within their public data-source boundaries. Timer is a persistent on-screen countdown, with no alarm/notification on completion. Calendar/birthday access remains optional.
- Scale/rotation can visually extend beyond a card; full large-font and overlap acceptance is pending. Home app pins are ordered by selection; app drag arrangement is not implemented.
- Notification grouping is local; no cloud summaries are sent. No system shade or secure lock-screen replacement occurs.
- Theme rules only select installed offline themes. Background execution may be delayed by Android/OEM power policy. No exact scheduling or arbitrary device operations are claimed.
- Live Nemotron/vision requests, deployed HTTPS integration, Docker and Nebius Serverless Endpoints/Jobs are unverified. Model IDs are configured from account availability.
- A shared client token and per-process quota are suitable for a private MVP, not a public multi-tenant service. The unsigned release build is compilation evidence; the delivered APK is debug-signed.
- 39 lint warnings per variant remain, including native overlay accessibility and localization. Dependency upgrades are advisory, not a completed vulnerability assessment.

The next work should close these gaps deliberately. Historical phase verification files describe incremental milestones; VERIFICATION.md records the current final checks.
