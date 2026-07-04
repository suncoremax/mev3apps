# Miron Electronics – Stock Inventory APK

Android app that wraps [https://mev5.vercel.app/](https://mev5.vercel.app/) with:
- ✅ Custom Miron Electronics splash screen & logo
- ✅ Pull-to-refresh (swipe down anywhere)
- ✅ All popups / dialogs work natively (alert, confirm, prompt)
- ✅ Back button navigation
- ✅ Progress bar while loading
- ✅ No-internet error dialog with retry

---

## 🚀 How to get your APK (Step-by-step)

### Step 1 — Create a GitHub repository

1. Go to [github.com](https://github.com) and sign in (or create a free account)
2. Click the **+** button → **New repository**
3. Name it: `miron-electronics-app`
4. Set it to **Private** (recommended)
5. Click **Create repository**

---

### Step 2 — Upload these files to GitHub

**Option A — GitHub website (easiest):**
1. In your new repo click **uploading an existing file**
2. Drag and drop ALL the files and folders from this zip
3. Click **Commit changes**

**Option B — Git command line:**
```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/YOUR_USERNAME/miron-electronics-app.git
git push -u origin main
```

---

### Step 3 — Get your APK (Debug version — works immediately!)

1. In your GitHub repo click the **Actions** tab
2. You'll see the workflow running (yellow dot = building, green = done)
3. Click the workflow run → scroll down to **Artifacts**
4. Download **MironElectronics-Debug-APK**
5. Unzip it → you have your `.apk` file!

> The debug APK works perfectly and you can install it on any Android phone.

---

### Step 4 — Install on Android phone

1. Transfer the `.apk` file to your phone (WhatsApp, email, USB, etc.)
2. On the phone go to **Settings → Security** (or Privacy)
3. Enable **"Install unknown apps"** or **"Unknown sources"**
4. Open the APK file and tap **Install**
5. Open **Miron Electronics** from your app drawer 🎉

---

## 🏷️ Create a Release (optional but recommended)

To create a proper versioned release that appears in GitHub Releases:

```bash
git tag v1.0
git push origin v1.0
```

This triggers the workflow and creates a GitHub Release with the APK attached for easy download.

---

## 🔐 Signed Release APK (for Play Store — optional)

For a signed release APK, add these **GitHub Secrets** (repo Settings → Secrets → Actions):

| Secret Name | Value |
|---|---|
| `KEYSTORE_BASE64` | Your keystore file encoded in base64 |
| `KEYSTORE_PASSWORD` | Your keystore password |
| `KEY_ALIAS` | Your key alias |
| `KEY_PASSWORD` | Your key password |

**Generate a keystore:**
```bash
keytool -genkey -v -keystore miron.jks -keyalg RSA -keysize 2048 -validity 10000 -alias miron

# Then encode it to base64:
base64 -w 0 miron.jks
```

Copy the base64 output and paste it as the `KEYSTORE_BASE64` secret.

---

## 📁 Project Structure

```
miron-electronics-app/
├── .github/
│   └── workflows/
│       └── build-apk.yml        ← GitHub Actions workflow
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/miron/electronics/stock/
│       │   └── MainActivity.java ← WebView + all features
│       └── res/
│           ├── layout/activity_main.xml
│           ├── mipmap-*/ic_launcher.png  ← Your logo (all sizes)
│           └── values/{colors,strings,styles}.xml
├── gradle/wrapper/
├── build.gradle
├── settings.gradle
└── gradlew
```

---

## ❓ Troubleshooting

**Actions tab shows red (failed build)?**
- Click the failed run → click the job → read the error log
- Most common: Java version mismatch (already set to 17 in workflow)

**App won't install on phone?**
- Make sure "Install unknown apps" is enabled for your file manager or browser
- If it says "App not installed", check if a previous version exists and uninstall it first

**App shows blank/error screen?**
- Check your internet connection
- Pull down to refresh
- The site https://mev5.vercel.app/ must be online
