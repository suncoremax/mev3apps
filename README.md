# GK App Builder (GitHub version)

Builds a client's **Android APK** and **Windows EXE** from one form. No local tools needed.

## One-time
1. Create a GitHub repo, upload everything in this folder (keep `.github/` folder). Public repo = free build minutes.
2. Optional: replace `logo.png` with your default logo.

## Every new client
1. Repo -> **Actions** tab -> **Build client app** -> **Run workflow**.
2. Fill the form: App name, Package name, Link, colour, and the logo:
   - **Direct PNG upload (best):** repo -> `logos/` folder -> **Add file -> Upload files** (drop the PNG, commit). Then type just the file name (e.g. `shop.png`) in the **logo file** box.
   - Or paste a direct image link in **logo url** (Google Drive / Dropbox share links are auto-converted).
   - Both empty = default `logo.png`.
3. Wait ~5-8 min. Open the finished run -> **Artifacts** -> download `APK-...` / `EXE-...` (zip, extract it).

## Rules
- **New package name = installs as a new app** beside the old one (Android + Windows).
- **Same package name = updates** the existing install. Keep each client's package name.
- Android APKs use GitHub's per-build debug key, so an update will NOT install over an older APK built in a different run
  unless you add your own keystore. (Tell me if you want fixed signing.)
- Windows: on-screen **← Back** button (top-left, appears when there is a page to go back to), **Esc = Back** (not while typing, in a popup, or fullscreen), mouse back button works too. Android keeps its own back button.
- Windows keyboard shortcuts: F5/Ctrl+R reload, Ctrl+F5 hard reload, F11 fullscreen (Esc exits it), Alt+Left/Right back/forward,
  Alt+Home start page, Ctrl +/-/0 zoom, Ctrl+P print, Ctrl+Q quit.
- Push notifications need a per-package `google-services.json` in `android/app/`.
- Use `https://` links. Windows EXE is unsigned, so SmartScreen may warn once ("More info" -> "Run anyway").
