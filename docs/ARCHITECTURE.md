# Architecture — MorphOS 0.3.0

MorphOS is a native Kotlin/Compose Android launcher with an optional FastAPI design backend. It uses public Android APIs and declarative proposals. The current snapshot contains MVP slices across phases 0–7; scope and gaps are recorded in ROADMAP.md.

## Android data flow

MainActivity owns HOME selection, lifecycle and permissions. LauncherViewModel coordinates repositories and exposes StateFlow to Compose. AppsRepository uses LauncherApps for current-user activities, package callbacks, real launch intents and eligible host shortcuts. The bounded icon cache loads icons off the main thread.

ThemeRepository loads 14 strict offline assets, with a programmatic fallback. ThemeSpec accepts supported schema versions 1 and 2; v2 adds bounded home, clock and wallpaper tokens. The standalone schemas/theme-v1.schema.json is a legacy v1 reference, not the v2 contract. Runtime Kotlin validation and backend Pydantic models are the current executable contracts.

PreferencesRepository serializes bounded v2 snapshots through atomic DataStore edits. Legacy v1 keys migrate on read. Design proposals carry the current revision; apply rejects stale previews. Up to 20 prior snapshots support undo/redo, and theme editing preserves unaffected fields. Preference reset and Room history deletion are separate explicit actions.

WidgetSpec is a bounded registry entry, not arbitrary executable content. Six widget types bind to clock, Android battery, a persistent timer deadline, counter, ContactsContract birthdays and CalendarContract events. Contacts/calendar queries run off the main thread and recheck permission on resume. Five supported shapes use Compose clipping. Custom nested widget trees, arbitrary paths, external providers and third-party AppWidget hosting are outside this snapshot.

PersonalRepository stores bounded local selection IDs/timestamps in Room. App suggestions require opt-in and repeated observations; optional Usage Access imports app-resume events. Contacts use stable lookup keys, names/phones/nicknames, favorites and local ranking. No contact/usage provider contents are sent to inference.

NotificationListenerService publishes authorized active notifications. History is off by default; when enabled, a bounded private AtomicFile uses AES-GCM with an Android Keystore key. Native PendingIntents implement notification actions. Media sessions require notification-listener authorization. Optional WindowManager floating UI is activity-owned and needs overlay access; it is removed on activity destruction.

AutomationWorker evaluates the first matching enabled time/charging/battery rule against offline themes. New rules start disabled. WorkManager schedules unique deferrable 15-minute work. A persisted activation marker prevents repeated application during one matching episode. Theme application uses an expected revision and is undoable. No exact-time execution is promised.

## Design request flow

Android sends a user prompt, current theme and revision over HTTPS to /design, or a registry widget prompt to /widget. /reference additionally sends an explicitly chosen, normalized image. Retrofit/OkHttp enforces timeouts, no redirects and a bounded response body. The separate client token is held in UI memory, never the provider key.

FastAPI authenticates a bearer token, limits request sizes and applies a per-process quota. HTTPX queries the account model catalog and routes text/widget requests to configured Nemotron models. JSON-schema output is validated with strict Pydantic contracts, then revalidated on Android before preview/apply. Unknown tokens, malformed envelopes and incomplete output fail without applying a change.

Image requests require a separately configured model and operator verification flag. PIL checks type, dimensions and size, strips metadata and normalizes the image. The current result is a theme-token patch, not arbitrary hierarchy reconstruction. Tests mock provider transport; no live provider or vision-capability verification has occurred.

## Deployment boundaries

The FastAPI service is a small single-process MVP with shared client-token authentication and an in-memory quota. Multi-tenant authentication, distributed quota, deployment validation and production signing remain backlog. The Dockerfile is included but untested. Provider credentials exist only in backend environment configuration. Android backups are disabled and cleartext traffic is rejected.

## Editable workspace and native fallback

Workspace is a bounded four-column, six-row grid on up to six pages. App cells are 1×1; widgets have validated rectangular spans. Collision checks reject widget overlap and allow app swaps. Home, dock, pages and icon treatment persist in atomic snapshots and undo history. Theme defaults can reseed untouched layouts; manual edits preserve placement. HorizontalPager and spring placement transitions respect reduced motion.

PhoneInference imports a bounded user-selected CPU .litertlm file into private storage. It binds a non-exported Messenger service in :local_ai, runs LiteRT-LM 0.13.1 off the main thread and closes the engine after each request. CPU generation uses a 1024-token context, greedy sampling, response/time bounds and disabled tool execution. Native failure/disconnect returns an error without applying a design; the launcher retains its own process heap. The local adapter validates exact envelopes, allowed fields, types, ranges and current revision before returning a device-labeled preview. Local-only requests skip Nebius; backup mode tries the imported model after a remote exception. A runtime check establishes loading/generation, not model accuracy.

Model files, model metadata and personal data are not sent to inference providers. The installed local model sees only the current declarative theme and user prompt, or a widget prompt. Text-only local inference does not handle screenshot uploads.

## Connected spaces (0.4)

CommunityClient uses normal HTTPS trust and validated ThemeSpec JSON against a SQLite-backed FastAPI community router. ClipboardSync uses a distinct paired TLS certificate and AES-GCM/Keystore credential storage against the Windows companion; payloads are bounded, file hashes verified and cached media exposed via scoped FileProvider grants. The companion uses Win32 clipboard formats and a loopback-only browser UI, with automatic capture paused initially. ImagePersonalizer extracts colors locally; PhoneInference can refine those palettes or use a compatible image model through the optional GPU vision backend.
