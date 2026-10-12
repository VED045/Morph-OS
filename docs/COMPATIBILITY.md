# Compatibility matrix

Android API 29–36 is the configured public-API baseline. Compilation support does not establish physical/OEM acceptance.

| Environment | Current evidence |
|---|---|
| AOSP Android 16 / API 36 x86_64 Pixel 7 emulator | 9 instrumentation tests passed; manual theme/widget/control-center smoke passed |
| Robolectric API 29 | Domain/repository unit checks passed; not an Android 10 device test |
| OnePlus 15 / OxygenOS 16 | Primary intended target; no physical testing |
| Other OnePlus, Samsung, Pixel, Nothing, Xiaomi, Motorola, Vivo, Oppo | Untested physical devices |
| Tablets, foldables, work/private profiles | Untested; profile UI not implemented |
| GitHub API 29/36 CI matrix | Configured; inspect actual workflow results separately |

Third-party launcher navigation, recents animations, notification access and background work behavior vary by OEM. Host app shortcuts require LauncherApps shortcut-host permission. Wallpaper colors depend on the system wallpaper provider. Floating UI requires user-granted overlay access and is activity-owned. The Glance screen is an internal launcher view; Android keyguard remains unchanged.

Keep the original launcher installed. Android Settings → Apps → Default apps → Home app restores it.

Optional on-device inference ships arm64-v8a and x86_64 native libraries. A compatible model must be imported separately. Other ABIs may still run the launcher but cannot use this CPU runtime. No physical phone inference benchmark has been performed.
