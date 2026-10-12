# Try MorphOS on your Android phone

## 1. Install the development APK

1. Use Android 10 or newer. A modern arm64 phone is recommended for local AI.
2. Download the `debug-apk` artifact from a successful [Verify MorphOS run](https://github.com/VED045/Morph-OS/actions/workflows/ci.yml), unzip it and copy `app-debug.apk` to your phone. GitHub may require sign-in to download artifacts. Alternatively build with `gradlew.bat assembleDebug`, or use the APK delivered with this development session.
3. Open the APK in your phone's Files app. Allow that app to install unknown apps when Android asks, then install. This is a development build, not a Play Store release.
4. Open **MorphOS AI → Set as your home launcher → MorphOS AI**. On OnePlus, you can also use **Settings → Apps → Default apps → Home app**; wording varies by OS version.
5. If an existing build has a different signing certificate, Android will refuse an update. Back up/export your theme before uninstalling the older build; uninstalling removes local models, widgets and preferences. Local debug builds and GitHub Actions builds may use different debug certificates.

With USB debugging enabled and your own computer authorized, installation can instead use:

```powershell
adb devices
adb install -r app-debug.apk
```

## 2. Make it feel like your phone

- Open the home **⋮ → Themes & wallpaper** and choose **Nothing OS** or **OxygenOS**.
- In **⋮ → Launcher settings → App icons**, select **Theme** for coordinated system glyphs and matching containers, **Original** for installed app icons, or **Monochrome** for app monochrome layers/grayscale fallback. Unknown third-party apps keep their recognizable artwork. These are original designs, not bundled vendor icon packs.
- Swipe left/right between home pages. OxygenOS starts with a separate widget Shelf.
- Swipe up for apps. Hold an app → **Add to home** or **Add to dock**.
- Hold empty home space → edit. Hold/drag an item; drop at an edge for another page. Tap an item in edit mode for accessible placement, size and remove controls.
- Choose **Add widgets** for functional clocks, battery, timers, counters, calendar and birthdays. Calendar/contacts access is optional.
- Swipe down for MorphOS controls. Android's own system shade and secure lock screen remain controlled by Android.

## 3. Personalize with prompts or an image

1. Open the home star → **Design your space**.
2. For a fast offline test, choose **Use monochrome icons**, preview, apply and undo.
3. For free model inference directly on the phone, follow the [pinned Qwen model setup](ON_DEVICE_AI.md). Import the `.litertlm` file, check it, then select **On phone**.
4. Describe a bounded change, such as softer corners or a different drawer. Review the proposed result before applying.
5. Add an image → **Extract colors** for a model-free, private palette preview. **Analyze & preview** with a text-only local model samples the image colors and asks the model to refine them using your prompt. It does not understand image objects or recreate arbitrary screenshot layouts.
6. The optional **Image-capable model (GPU vision)** switch enables genuine image input for a compatible multimodal model such as Gemma 3n. This route is implemented but has not been validated on a physical phone; artifact compatibility, GPU and RAM requirements apply. Leave it off for Qwen text models.
7. For stronger reasoning, configure the separate Nebius backend and select **Backup** or **Nebius**. Credentials and exact account model IDs are required; no NVIDIA/Nebius credits are supplied by the app.

## 4. Connect your Windows laptop

1. Install Python 3.11+ and download/clone this repository to your Windows laptop.
2. Open PowerShell in the repository and run `powershell -ExecutionPolicy Bypass -File .\companion\start.ps1`. It creates a local virtual environment, installs two dependency families and opens the companion window.
3. Keep both devices on the same trusted private network. If Windows Firewall asks, allow the companion on **Private networks**. Its encrypted Android endpoint uses TCP **8767**; the local browser window uses loopback-only **8766**. It is not an internet relay.
4. On Android open **Launcher settings → Windows shared clipboard**. Enter the address, certificate SHA-256 fingerprint and pairing key shown in the companion window. Verify these values from your own laptop. Tap **Pair laptop**.
5. Drop images/videos/text into the Windows window, or choose files. On Android tap **Receive from laptop**, then **Copy**, **Save** or **Share**.
6. From Android use **Send text**, **Send media**, another app's **Share → MorphOS**, or drag an item into MorphOS in split screen. Review shared items and tap **Send shared items**. Windows receives them in the transfer queue.
7. To automate supported clipboard changes, enable **clipboard sync** in the Windows window and **Sync while MorphOS is open** on Android. Windows then reads new text/image/media clipboard changes and copies received phone items to its clipboard. Android reads/writes while MorphOS has focus. **Android 10+ does not permit ordinary apps to silently read other apps' background clipboard**; use Share → MorphOS outside the launcher.
8. Pause either switch or close the companion to stop automatic sync. Android **Disconnect** removes its encrypted pairing credentials. To revoke the laptop key for all previous pairings, stop the companion and remove only `companion/private/pair.key`, then restart and pair again.

Limits: 32,768 text characters; 100 MB per media file; 10 items per send; Windows keeps at most 20 queue items/250 MB for up to 24 hours within a running session. Android received media is a bounded session cache; save important files elsewhere. Protected clips marked sensitive by their source are excluded from Android automatic sending; Windows honors `ExcludeClipboardContentFromMonitorProcessing`. Do not enable automatic sync if you do not want newly copied material sent to your paired device.

## 5. Share themes

Open **Themes & wallpaper → Community**. Export/import theme JSON immediately without a server. For online sharing, enter a hosted community HTTPS URL. Use an administrator-provided invite to create your creator key, save the key in your password manager, and upload your current theme. Reviewed themes become browsable and can be previewed before applying. There is no public hosted community deployed by this repository yet. [Host and moderate your community](COMMUNITY.md).

To return to your old launcher, change Android's default Home app. You do not need to uninstall MorphOS.
