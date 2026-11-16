# الدراسة TV — Eddirasa TV

تطبيق Android TV خفيف لتصفح موقع [eddirasa.com](https://eddirasa.com/) باستخدام ريموت فقط (بدون لمس).
A lightweight Android TV app for browsing [eddirasa.com](https://eddirasa.com/) with a remote control only.

- Package: `com.youcef.eddirasatv`
- Target device: Android TV box, Android 10 (API 29), 1 GB RAM
- Built automatically by GitHub Actions

## Download and install / التحميل والتثبيت

1. Open the **Actions** tab of this repository.
2. Open the latest green **Build debug APK** run.
3. Download the `eddirasa-tv-debug` artifact (a ZIP containing `app-debug.apk`).
4. Copy the APK to a USB drive, or open the artifact link on the TV box with the **Downloader** app.
5. Install it (you may need to allow "Install unknown apps" for the file manager / Downloader).
6. The app appears in the TV launcher as **الدراسة TV**.

For a signed release: maintainers run the **Release signed APK** workflow
(`workflow_dispatch`) and the APK is attached to the GitHub Release.

## Build locally / البناء محليًا

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

Requirements: JDK 17 and Android SDK 34.

## Release signing secrets

The release workflow reads these repository secrets:

| Secret | Meaning |
|---|---|
| `KEYSTORE_BASE64` | base64 of the `.keystore` file |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | key alias |
| `KEY_PASSWORD` | key password |

Create the keystore once:

```bash
keytool -genkeypair -v -keystore release.keystore -alias eddirasa \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.keystore   # paste into KEYSTORE_BASE64
```

Never commit the keystore or its passwords.

## Remote keys

| Key | Home | Browser | PDF viewer |
|---|---|---|---|
| D-pad | Move focus | Move highlight between elements | Page turn / scroll |
| OK | Open | Open highlighted element | Toggle zoom |
| BACK | Exit dialog | Back | Close |
| MENU | — | Quick menu | Page / zoom menu |

## Privacy

The app only loads the public website. It does not copy, scrape, or re-host content,
and it keeps the website's ads working.

## WebView note

The app renders the site with the device's system WebView. On an old Android 10
box the preinstalled WebView build may be outdated and the site layout can look
broken; updating "Android System WebView" (or installing a current WebView APK)
fixes it. The app itself needs no update.

## Roadmap

Development follows [roadmap.md](roadmap.md).
