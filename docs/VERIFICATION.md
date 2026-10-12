# Verification record — 12 October 2026, version 0.2.0

No new features were added during the final test/publish pass. One packaging omission was corrected: Pillow 11.3.0, already used by the implemented image route and installed during its tests, is now pinned in requirements-lock.txt.

## Automated checks

| Check | Result |
|---|---|
| testDebugUnitTest | 20 passed, zero failures/errors/skips |
| connectedDebugAndroidTest | 7 passed, zero failures/errors/skips on API 36 / Android 16 AOSP x86_64 |
| Backend pytest | 19 passed; one upstream AnyIO deprecation warning |
| assembleDebug / assembleRelease | Successful; release output is unsigned |
| lintDebug / lintRelease | Successful, zero errors and 39 warnings per variant |
| Debug APK signature | APK Signature Scheme v2 verification recorded in test-results/apk-signature.txt |

Tests cover strict schema/bounds, 14 assets/fallback, v1 preference migration, corruption recovery, snapshot restoration, stale proposal rejection, minimal patching, undo/redo, timer/counter persistence, denied widget-data access, Room history recreation/deletion, local ranking, cold-start suggestions and automation window/battery cases.

Instrumentation uses real Compose views for drawer/search/back, theme application/activity recreation, offline proposal/apply/undo/redo, persistent star-clock generation, unified Clock search without contacts authorization, notification-denied fallback, and radial pagination/list alternative.

Backend tests use HTTPX MockTransport. They check HTTPS settings, catalog access, strict untrusted output rejection, route selection, authentication, quotas, body bounds, incomplete responses, registered widget generation and image normalization/vision gating. Passing mocked-provider tests is not live provider verification.

Lint warnings: pinned dependency/tooling updates, KAPT/KTX guidance, native floating-panel touch accessibility and text localization. The notification IO dispatcher also produces an experimental-coroutines opt-in compiler advisory. These are recorded, with no disabled lint analysis or claim of complete accessibility/security acceptance.

## Final manual emulator smoke

- Installed the current debug APK and selected MorphOS as the emulator HOME handler.
- Dot dark and Oxygen light rendered the current dot/digital clock, real battery card, analog clock, focus timer and counter. Fresh screenshots were visually inspected.
- Incremented the counter, started a timer, force-stopped/reopened the app, and confirmed the counter value and active timer persisted. Stopped the timer after verification.
- Opened the control center and checked the real emulator battery and permission-denied media state. Notification denial is also covered by instrumentation.
- Left the current app installed and running, with Oxygen light selected.

Machine-readable manual evidence is in test-results/manual-api36.json. Its single observed cold start was 1357 ms on this development emulator. This is not a device benchmark or proof of the proposed physical-device performance budget.

## Unverified

Live Nebius inference/model availability/image capability and Android→deployed HTTPS backend flow; Docker/cloud deployment; physical OnePlus or other OEMs; API 29 device instrumentation; work/private profiles; formal TalkBack/large-font/reduced-motion acceptance; complete notification/media/overlay grants and revocations; real contacts/calendar data; shortcut/wallpaper-color provider acceptance; worker/reboot/OEM execution; physical performance/memory/battery; production signing and security/supply-chain review.

Selected XML/text evidence is under test-results. Repository copies normalize local absolute workspace paths. Phase records are historical milestones; this file is the current verification record. Reproduction commands are in README.md. Development is paused after the tested snapshot is published.
