# Permissions and privacy — current APK

The base launcher can run without optional personal-data permissions. Scoped MAIN/LAUNCHER queries enumerate installed launchable apps; there is no QUERY_ALL_PACKAGES, accessibility service, SMS, call-log or broad storage access.

| Capability | Public authorization | Denied/revoked behavior |
|---|---|---|
| Home launcher | User-selected HOME role | App remains usable; original launcher can be restored in Settings |
| Connected design | INTERNET, user-configured HTTPS backend | Offline themes/commands remain available; failed proposals do not change the layout |
| Contact search/birthdays | READ_CONTACTS at feature entry | Contact results/data are absent; app search still works |
| Calendar widget | READ_CALENDAR at feature entry | Connection prompt or no provider data |
| App suggestions | Explicit opt-in; optional Usage Access settings | No UsageStats import without authorization; launcher-owned events only when opted in |
| Notifications/media sessions | User-authorized NotificationListenerService settings | Connection explanation and no active contents |
| Floating panel | SYSTEM_ALERT_WINDOW settings | Launcher-hosted control center remains available |
| Brightness | Activity window brightness; public Display Settings | Does not change global brightness or request WRITE_SETTINGS |

Contacts, calendar, notifications and usage records stay on the phone. Design requests send the typed prompt, current theme and revision; reference analysis sends a chosen image only after an explicit Analyze action. The backend normalizes images and strips metadata. There are no advertising or telemetry SDKs in this project.

Pins, theme, widgets, rules and revisions reside in private DataStore. Local selection history is bounded in Room. Notification history is disabled by default; when enabled it is bounded and encrypted using AES-GCM with an Android Keystore key. Turning it off deletes its file; the dashboard also offers explicit history deletion. Revoking notification access hides active and historical contents, but does not automatically delete an already stored encrypted file.

Settings exposes deletion of selection history and reset of hidden apps. Turning predictions off stops learning; use the delete control to remove existing history. Reset launcher preferences restores layout state and is separate from deleting Room history. Uninstall removes app-private storage; backup and extraction are disabled.

Provider keys stay in backend/.env or server environment variables. The Android client uses a separate bearer token in memory. .env, local.properties, keystores, caches and build outputs are excluded from source publication. Example/test credentials are synthetic. Live credentials are deferred.

Formal security, accessibility and complete permission revocation testing remain open. See VERIFICATION.md for evidence rather than interpreting permission support as full acceptance.
