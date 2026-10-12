# Verification record — 12 October 2026, version 0.4.0

This snapshot advances the launcher UI, workspace arrangement, widget gallery, on-device AI, image palettes, community sharing and a paired Windows clipboard companion. Phase records describe historical milestones; this is the current record.

## Automated checks

| Check | Result |
|---|---|
| testDebugUnitTest | 31 passed, zero failures/errors/skips |
| connectedDebugAndroidTest | 10 passed, zero failures/errors/skips on Android 16 / API 36 x86_64 |
| Backend pytest | 22 backend tests plus 3 companion tests passed; provider transport mocked |
| assembleDebug / assembleRelease | Successful; release output is unsigned |
| lintDebug / lintRelease | Zero errors and 53 warnings per variant |
| Debug signature | Scheme v2 verification recorded in test-results/apk-signature.txt |

New unit coverage checks distinct default home/Shelf layouts, bounds/collisions/app swaps/full-grid placement, persistence/page protection/dock settings, local output envelopes and unsafe fields, and undo for icon/home changes. Existing checks cover schema/assets/fallback, migration, corruption recovery, snapshots, stale previews, timer/counter, denied providers, history/ranking and offline rules.

Instrumentation verifies theme/activity recreation, drawer search/back and alternate modes, offline proposal/apply/undo/redo, persistent generated star clock, unified search without Contacts permission, notification-denied UI, page addition/recreation/swiping and model setup matching actual private-file presence. The final UI and the imported-model status path were rerun in the ten-test instrumentation suite.

Lint warnings include tooling/library updates, localization, native overlay touch accessibility and SDK/minification advisories. Compiler advisories include KAPT, parameter annotation targeting and experimental coroutine dispatching. No lint analysis was disabled. Final visual review also corrected clipped home app labels and circular widget content.

## Native inference

The real Android document picker imported both tested artifacts. The private CPU runtime initialized and generated with SmolLM2 135M; its first widget response failed strict validation and was rejected. A pinned Qwen3 0.6B INT4 export loaded and generated a valid three-minute timer, which explicit Add placed on home. Model checks and proposal results are captured in JSON under test-results and explained in [ON_DEVICE_AI.md](ON_DEVICE_AI.md). Provider responses were not mocked for these local native checks.

This establishes real emulator inference and one validated widget request. It does not establish broad model accuracy, physical-phone inference/performance, image capability or equivalence to Nemotron Ultra. Image palette extraction is local and unit-tested. The optional GPU vision route is implemented but has not been run with a multimodal model.

## Manual UI acceptance

Fresh screenshots under screenshots show the running Android app: Nothing OS with optional monochrome icons, OxygenOS home, separate Shelf, drawer, widget gallery, controls and local-model setup. The manual record covers native home dragging/persistence and UI checks; only observed results are recorded. Notification access was granted on the emulator, an Android shell QA notification appeared in the native dashboard, and revocation returned to the safe connection prompt. History stayed off. See notifications-api36-0.3.json. Visual treatments are original code assets informed by the supplied references and official vendor pages.

## Remaining acceptance

Live Nebius account/model/image capability and Android→deployed HTTPS flow; cloud/container deployment; physical OnePlus/OEMs; Android 10 instrumentation; work/private profiles; formal TalkBack, large-font/reduced-motion acceptance; full notification/media/overlay grant/revocation; real contacts/calendar; worker/reboot/OEM policies; physical memory/battery/performance; production signing and security/supply-chain review.

Backend tests use HTTPX MockTransport; passing them is not live provider verification. Model credentials remain deferred. Evidence files normalize local paths before publication. CI configuration is distinct from observed local passes.

## 0.4 connected features

Community API tests cover registration, ownership, pending review, approval, persistence, public browse, withdrawal, schema rejection and upload quotas. Companion tests cover pairing-key/certificate persistence, authentication/origin rejection, transfer queue bounds, deduplication, file names, invalid media and session cleanup. The Win32 clipboard adapter initializes on this Windows machine; automated tests do not read or overwrite the user's Windows clipboard.

A separate emulator integration check uses a loopback TLS companion fixture and synthetic content. It verifies wrong-certificate rejection, successful pinned pairing, Windows-origin text/image download with checksum, phone image/text upload, and disconnect. This is transport validation; full physical phone/LAN, Android drag/drop between other apps, Windows native clipboard format interoperability and video playback remain manual acceptance items.

No public community is deployed. Invite-based moderation and creator keys are implemented; public operations and account recovery are not. Model weights are not included; credentials remain deferred.
