# ROADMAP — Eddirasa TV (Android TV Box App)

> Read this file fully before doing anything. Work ONE phase at a time. After each phase: build, test, commit, then stop and report. Do not start the next phase until told "next phase".

## 1. Goal

Build a lightweight Android app that makes **https://eddirasa.com/** (Algerian education website, Arabic, RTL) easy to use on a TV box with a **remote control (D-pad only, no touch, no mouse, maybe no keyboard)**.

- Target device: **Mortal TX1-S, 1 GB RAM / 8 GB storage, Android 10 (API 29)**
- Must be fast on low RAM, simple, big text, big buttons, usable by a kid or a parent.
- Package name: `com.youcef.eddirasatv`  |  App name: **الدراسة TV** (Eddirasa TV)

## 2. What the website contains (already inspected)

- Language: Arabic, `dir=rtl`, theme color `#008b57` (green)
- Main sections (use as native home tiles):
  | Tile (Arabic) | URL |
  |---|---|
  | التعليم الابتدائي | https://eddirasa.com/ens-pri/ |
  | التعليم المتوسط | https://eddirasa.com/ens-cm/ |
  | التعليم الثانوي | https://eddirasa.com/ens-sec/ |
  | التعليم الجامعي | https://eddirasa.com/ens-uni/ |
  | بنك الاختبارات | https://eddirasa.com/exams/ |
  | حاسبات المعدلات | https://eddirasa.com/average/ |
  | الأستاذ | https://eddirasa.com/teachers/ |
  | التكوين | https://eddirasa.com/training/ |
  | الأسئلة والأجوبة | https://eddirasa.com/questions/ |
  | الغرف الدراسية | https://eddirasa.com/classrooms/ |
  | أخبار الدراسة | https://eddirasa.com/news/ |
  | بحث | https://eddirasa.com/search/ |
- Sub-levels: primary `first-primary … fifth-primary`, middle `1am … 4am`, secondary `1as … 3as`
- Content is mostly **PDF files** (exams, lessons, exercises) → PDF viewing is the most important feature.
- The site shows an **ad-block wall** ("please allow ads"). The app must NOT block ads and the wall's button must be reachable with the remote.
- A phone app already exists on Google Play (`com.eddirasa.app`) but it is for touch phones. We build our own TV version, loading the live website (no scraping, no copying content).

## 3. Tech decisions (do not change without asking)

- **Language:** Kotlin. **UI:** classic Android Views (XML). **NO Jetpack Compose, NO Leanback library** (too heavy for 1 GB RAM).
- **Core:** one `WebView` + native home screen + native PDF viewer using built-in `android.graphics.pdf.PdfRenderer`.
- `minSdk 24`, `targetSdk 29`, `compileSdk 34`. Add as few dependencies as possible (AndroidX core/appcompat only, maybe OkHttp for PDF download — or use `HttpURLConnection` instead).
- **Build with GitHub Actions** (the Termux/Ubuntu environment cannot reliably run the full Android SDK). Agent only writes code and pushes; Actions builds the APK.
- Repo: `YoucefSennoun/eddirasa-tv`

## 4. Rules for the AI agent (important for free models)

1. Small steps. Edit only the files needed. Never rewrite a whole file if a small edit works.
2. After every task: `git add -A && git commit -m "<short message>"`.
3. If a build fails, read the full GitHub Actions error log, fix only that error, push again. Do not guess.
4. Do not add features that are not in this roadmap.
5. Do not hardcode secrets. Do not commit keystore passwords (use GitHub Secrets).
6. Never assume the website's search URL or HTML structure — open the site and check the real search form before using it.
7. Keep every screen usable with **only**: UP, DOWN, LEFT, RIGHT, OK (CENTER/ENTER), BACK, MENU.
8. All UI text in Arabic (with `android:supportsRtl="true"`). Code comments in English.
9. When finished with a phase, write a short report: what was done, what to test on the TV box, known issues.

## 5. Remote key mapping (target behaviour)

| Key | Home screen | Browser screen | PDF viewer |
|---|---|---|---|
| D-pad arrows | Move focus between tiles | Move virtual cursor (hold = faster); at screen edge → scroll page | LEFT/RIGHT = previous/next page, UP/DOWN = scroll inside page |
| OK / ENTER | Open tile | Click at cursor position | Toggle zoom (fit width ↔ 150%) |
| BACK | Exit confirm dialog | Go back in page history; if none → Home | Close PDF, return to page |
| MENU | — | Open quick menu (Home, Reload, Zoom +/−, Search, Favorites) | Page jump / zoom menu |
| Channel/Page Up/Down (if exist) | — | Scroll page up/down | Previous/next page |

---

## PHASE 0 — Project setup

- [ ] Create repo `eddirasa-tv`, add `.gitignore` (Android), `README.md`, this `roadmap.md`.
- [ ] Create Gradle Android project (Kotlin, package `com.youcef.eddirasatv`).
- [ ] `AndroidManifest.xml`:
  - `<uses-permission android:name="android.permission.INTERNET"/>`
  - `<uses-feature android:name="android.hardware.touchscreen" android:required="false"/>`
  - `<uses-feature android:name="android.software.leanback" android:required="false"/>`
  - Launcher activity with BOTH `LAUNCHER` and `LEANBACK_LAUNCHER` categories
  - `android:banner` (320×180 PNG, green `#008b57` with the app name) and `android:icon`
  - `android:supportsRtl="true"`, `android:usesCleartextTraffic="false"`, `android:largeHeap="false"`
- [ ] App shows an empty screen "مرحبا" and runs.

**Done when:** project opens with no errors.

## PHASE 1 — GitHub Actions build (do this early)

- [ ] `.github/workflows/build.yml`: on push → JDK 17, Gradle cache, `./gradlew assembleDebug`, upload APK as artifact `eddirasa-tv-debug`.
- [ ] Second workflow (manual `workflow_dispatch`): `assembleRelease`, sign with a keystore stored in GitHub Secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEYSTORE_PASSWORD`, `KEY_PASSWORD`), attach APK to a GitHub Release.
- [ ] README: how to download the APK and install it.

**Done when:** a green build produces a downloadable APK.

## PHASE 2 — Native Home screen (D-pad friendly)

- [ ] `HomeActivity` with a grid of **big tiles** (min 220dp × 140dp, 3–4 columns), icon + Arabic title, from the table in section 2.
- [ ] Every tile is focusable (`focusable=true`, `focusableInTouchMode=false`), clear focus highlight (thick white/yellow border + slight scale 1.08), green theme.
- [ ] Focus order is correct for RTL (first tile top-right, LEFT moves to next).
- [ ] OK on a tile → open `BrowserActivity` with that URL.
- [ ] Row at the top: "آخر ما زرته" (Recently visited, max 5) and "المفضلة" (Favorites) — stored in `SharedPreferences`.
- [ ] Text size ≥ 22sp, high contrast, no tiny elements.

**Done when:** all tiles reachable and openable using only the remote.

## PHASE 3 — WebView core

- [ ] `BrowserActivity` with a full-screen `WebView`.
- [ ] Settings: JavaScript ON, DOM storage ON, `setSupportZoom(true)` with built-in zoom controls hidden, `mediaPlaybackRequiresUserGesture=false`, cache mode `LOAD_DEFAULT`.
- [ ] Use a **desktop-friendly but TV-readable** setup: default mobile UA is NOT recommended; test both and keep the one that gives the best layout on a 1080p TV (start with default WebView UA + `setUseWideViewPort(true)` + `setLoadWithOverviewMode(true)`).
- [ ] Default text zoom ~130–150% so Arabic text is readable from the sofa; menu option to change it and remember it.
- [ ] Top thin progress bar while loading; friendly offline/error page in Arabic with a "إعادة المحاولة" (Retry) button reachable by remote.
- [ ] `WebViewClient.shouldOverrideUrlLoading`:
  - Keep `eddirasa.com` links inside the app.
  - Links to other domains (Facebook, Telegram, Play Store…) → show a small toast "رابط خارجي غير مدعوم" instead of opening other apps. Block `intent://`, `market://`, `tg://` schemes (they crash on TV boxes).
- [ ] `WebChromeClient.onCreateWindow` / `target=_blank` → load in the same WebView (no popups).
- [ ] Handle BACK with `webView.canGoBack()`.
- [ ] Save last URL for "recently visited".
- [ ] Destroy WebView correctly in `onDestroy` (low RAM).

**Done when:** every section of the site opens and BACK works as in the table.

## PHASE 4 — Virtual cursor (the key feature)

A website is made for a mouse; on a remote we need a pointer.

- [ ] Draw a cursor overlay (round arrow/circle, 32dp, white with black border) above the WebView.
- [ ] D-pad moves the cursor; speed starts at 12 px/step and accelerates while the key is held (up to ~60 px/step), resets on key up.
- [ ] When the cursor touches the top/bottom edge and the user keeps pressing UP/DOWN → scroll the page (`webView.scrollBy`).
- [ ] OK/ENTER → inject a real click: dispatch `MotionEvent` ACTION_DOWN + ACTION_UP at the cursor position to the WebView.
- [ ] Cursor auto-hides after 4 s without movement; shows again on any key.
- [ ] **Smart snap (nice to have):** when the cursor is close (<40px) to a link/button, snap to its center. Implement with `evaluateJavascript` using `document.elementFromPoint`.
- [ ] Text fields: when OK is pressed on an `input/textarea`, open the in-app keyboard (Phase 5).
- [ ] MENU key opens quick menu: الرئيسية (Home) • إعادة التحميل (Reload) • تكبير/تصغير (Zoom +/−) • بحث (Search) • أضف للمفضلة (Add favorite) • خروج (Exit).

**Done when:** user can open any link, scroll, and press buttons (incl. the "تحقق مجددًا" ad-wall button) using only the remote.

## PHASE 5 — Search + on-screen keyboard

TV boxes often have no keyboard and the system IME may be missing or not D-pad friendly.

- [ ] Native `SearchActivity`/dialog with a **built-in D-pad keyboard**: Arabic layout, Latin layout, digits, space, delete, "بحث", language switch key. Big keys, focus highlight, OK types the letter.
- [ ] Inspect the real search form on https://eddirasa.com/search/ (field name / query parameter) and build the search URL from it. Do not guess.
- [ ] Also support the Android voice/IME input if the remote has a mic (optional: `RecognizerIntent`, must not crash if missing).
- [ ] Keep last 10 searches in `SharedPreferences`.

**Done when:** user can search "رياضيات 3 متوسط" with the remote only and get results.

## PHASE 6 — PDF handling (most important content)

- [ ] Detect PDF links: URL ends with `.pdf` or response `Content-Type: application/pdf` (use `DownloadListener` and `shouldOverrideUrlLoading`).
- [ ] Download to `cacheDir/pdf/` with a progress dialog "جاري التحميل…" and a cancel option (BACK). Limit cache to ~150 MB, delete oldest first (8 GB storage is small).
- [ ] `PdfViewerActivity` using `PdfRenderer`:
  - Render one page at a time to a `Bitmap` sized to the screen (fit width), recycle bitmaps, render in a background thread.
  - LEFT/RIGHT = previous/next page (in RTL documents keep it simple and consistent: RIGHT = previous, LEFT = next, make this switchable in the menu).
  - UP/DOWN = scroll within a zoomed page.
  - OK = zoom toggle (fit width ↔ 150% ↔ 200%).
  - Show page counter "3 / 12" at the bottom.
  - BACK = close.
- [ ] If the PDF is too heavy / fails to render → show Arabic message and an option to retry.
- [ ] "حفظ في التنزيلات" (Save a copy) in the menu (optional).
- [ ] Do NOT load all pages at once (1 GB RAM).

**Done when:** a typical exam PDF (3–10 pages) opens in under ~5 s on the box and all pages can be navigated with the remote.

## PHASE 7 — Ads, popups, and stability

- [ ] Do not block ads. Make sure ad iframes don't steal D-pad focus or open external apps/redirect loops.
- [ ] Handle the "ad-block detected" overlay: it must be dismissable by the cursor.
- [ ] Block auto-redirects to non-http(s) schemes.
- [ ] SSL errors: do not silently proceed; show an Arabic error page.
- [ ] Add `onRenderProcessGone` handling to recover from WebView crash without closing the app.
- [ ] Memory: clear WebView cache when it exceeds a limit; avoid keeping more than one WebView alive.
- [ ] Check app on Android 10 old WebView version: if the WebView is outdated and the site layout breaks, document it in README (user can update "Android System WebView" APK).

## PHASE 8 — UX polish ("easy for use")

- [ ] Splash with logo (≤1.5 s).
- [ ] First-run help overlay (once): shows the remote key mapping in Arabic with arrows icons. Re-open from MENU → "مساعدة".
- [ ] Favorites screen (add/remove with MENU on any page).
- [ ] "Continue where I stopped" tile on Home (last page opened).
- [ ] Settings screen (D-pad friendly): text size, cursor speed, default zoom, PDF direction, clear cache, clear history.
- [ ] Exit confirmation dialog on Home with BACK: "هل تريد الخروج؟" [نعم] [لا] (focus on "لا" by default).
- [ ] Keep screen on while using (`FLAG_KEEP_SCREEN_ON`).
- [ ] Every focusable item has a visible focus state. No dead-ends where focus gets trapped.

## PHASE 9 — Testing & release

Test on the real device (Mortal TX1-S, Android 10) with the remote only:

- [ ] Install APK (USB drive / ADB / "Downloader" app with the GitHub release link).
- [ ] App appears in the TV launcher with the banner.
- [ ] Home → each tile loads.
- [ ] Open a level → subject → a PDF exam → read all pages → BACK to the page.
- [ ] Search using the built-in keyboard.
- [ ] Open the Classrooms/Questions pages and click around with the cursor.
- [ ] Leave the app running 30 min: no crash, no RAM kill.
- [ ] Rotate/sleep/wake the box: app recovers.
- [ ] Fix bugs, bump `versionName` to `1.0.0`, run the release workflow, publish a GitHub Release with the signed APK and a short Arabic+English README with screenshots.

## 6. Definition of Done (v1.0)

1. Installs and launches on Mortal TX1-S 1/8 (Android 10) from the TV launcher.
2. Fully usable with the remote only (no touch, no mouse, no physical keyboard).
3. Home tiles, browsing the site, search, and PDF reading all work.
4. Stays stable on 1 GB RAM.
5. APK is built automatically by GitHub Actions.

## 7. Notes

- This app only loads the public website; it does not copy, scrape, or re-host the site's content and keeps the site's ads working. Consider messaging the site owner (contact page: https://eddirasa.com/contact/) if the app will be shared publicly.
- Future ideas (NOT in v1): offline downloads of favorite PDFs, text-to-speech for lessons, parental "kids mode", Arabic voice search.

---

## Prompt to paste to the agent at the start of each phase

```
Read roadmap.md. We are now on PHASE <N>. Implement only the unchecked tasks of this phase, following the Rules in section 4. Commit after each task. When done, push, wait for the GitHub Actions build, fix errors if any, and give me a short report with what to test on the TV box.
```
