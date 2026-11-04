# 📱 Axion DMS — Android APK

বিজ্ঞাপনমুক্ত, সম্পূর্ণ বিনামূল্যে Android APK।

> 🆕 **নতুন ভার্সন (Axion DMS):** এই wrapper-এর App ID, নাম ও আইকন সম্পূর্ণ নতুন — App ID: **`com.axion.dms`**, নাম: **Axion DMS**। আগের অ্যাপের App ID ছিল `com.miron.electronics`। App ID আলাদা হওয়ায় Android এটাকে **সম্পূর্ণ নতুন অ্যাপ** ধরে — একই ফোনে পুরনো ও নতুন অ্যাপ **পাশাপাশি ইনস্টল** থাকবে, একটা অন্যটাকে replace বা update করবে না। নতুন অ্যাপের ডেটা/লগইন আলাদা থাকে।
>
> ⚠️ **বিল্ডের আগে ২টি কাজ বাকি:** (১) `MainActivity.java`-তে `APP_URL` নতুন ওয়েব অ্যাপের লিংকে বদলান (এখনো আগের লিংকই আছে)। (২) Push notification লাগলে Firebase-এ **`com.axion.dms`** নামে নতুন Android app রেজিস্টার করে নতুন `google-services.json` দিন — পুরনো অ্যাপের `google-services.json` এখানে **কাজ করবে না** (package name না মিললে বিল্ড fail করবে)।

> 🔔 **Push Notifications:** ওয়েব অ্যাপ থেকে ফোনে নোটিফিকেশন পাঠানোর সম্পূর্ণ protocol/spec **[PUSH_NOTIFICATIONS.md](./PUSH_NOTIFICATIONS.md)** ফাইলে লেখা আছে — এটা আপনার web dev কে দিন। App-side কোড (Firebase Cloud Messaging) ইতিমধ্যে বসানো হয়েছে; শুধু একটা `google-services.json` ফাইল লাগবে (doc-এর ধাপ ২ দেখুন), তারপর normal build দিলেই কাজ করবে।

---

## ⚡ সবচেয়ে সহজ পদ্ধতি — GitHub দিয়ে Auto Build

### ধাপ ১ — URL বসান

`app/src/main/java/com/axion/dms/MainActivity.java` ফাইল খুলুন।

এই লাইনটি খুঁজুন:
```java
private static final String APP_URL = "https://YOUR-APP.vercel.app";
```

আপনার Vercel URL দিন:
```java
private static final String APP_URL = "https://YOUR-APP.vercel.app";
```

### ধাপ ২ — GitHub Repository তৈরি করুন

1. **github.com** → New Repository → নাম দিন: `axion-dms-android`
2. **Public** রাখুন (Private হলে Actions কাজ করবে)

### ধাপ ৩ — Code Upload করুন

```bash
cd axion-dms-android
git init
git add .
git commit -m "first commit"
git branch -M main
git remote add origin https://github.com/আপনার-username/axion-dms-android.git
git push -u origin main
```

### ধাপ ৪ — APK Download করুন

GitHub push করার **৫-৭ মিনিট** পর:

```
আপনার Repository → Actions tab → সবচেয়ে নতুন workflow
→ "AxionDMS-APK" artifact → Download
```

অথবা **Releases** section থেকেও পাবেন।

---

## 📲 APK Install করুন

1. ZIP extract করুন → `AxionDMS.apk` পাবেন
2. Phone এ পাঠান (WhatsApp / USB / Google Drive)
3. Phone Settings → **"Unknown sources"** বা **"Install unknown apps"** চালু করুন
4. APK তে tap করুন → Install

---

## 🔄 App Update করতে

শুধু code change করে GitHub এ push করুন:
```bash
git add .
git commit -m "update"
git push
```
**৫-৭ মিনিটে নতুন APK তৈরি হবে।**

---

## ✨ Features

- ✅ কোনো বিজ্ঞাপন নেই
- ✅ Pull-to-refresh (নিচে টানলে reload)
- ✅ Back button কাজ করে
- ✅ Axion DMS লোগো — সব density + adaptive icon সহ
- ✅ Internet না থাকলে বাংলা গ্রাফিক্স সহ "ইন্টারনেট সংযোগ নেই" স্ক্রিন, auto-retry + সরাসরি WiFi Settings বাটন
- ✅ লিংক/সার্ভার কাজ না করলে (ভুল লিংক, deployment মুছে গেলে, সার্ভার ডাউন) আলাদা "অ্যাপটি রক্ষণাবেক্ষণে আছে" স্ক্রিন
- ✅ Retry button (উভয় স্ক্রিনে)
- ✅ Full screen (action bar নেই)
- ✅ নতুন Permissions: Camera, Microphone, Notifications, Bluetooth, Storage — future আপডেটের জন্য প্রস্তুত (web app চাইলেই ব্যবহার করতে পারবে, নতুন APK লাগবে না)
- ✅ Print সাপোর্ট — web app থেকে JS call করলে ফোনের নিজস্ব Print App (যেকোনো প্রিন্টার: HP/Canon/Epson/PDF/Bluetooth printer) খুলে যাবে

---

## 🖨️ Print কীভাবে ব্যবহার করবেন (ওয়েব অ্যাপ থেকে)

আপনার ওয়েব অ্যাপে "Print" বাটনে ক্লিক করলে এই JS কল করুন:

```javascript
// পুরো পেজ/রিসিট প্রিন্ট করতে (যা স্ক্রিনে আছে)
window.AndroidBridge.printPage("Invoice #1234");

// শুধু একটা ছবি (base64 JPG/PNG, data: prefix ছাড়া) প্রিন্ট করতে
window.AndroidBridge.printImage(base64String, "Slip");
```

দুটোই ফোনে ইনস্টল করা প্রিন্টার অ্যাপের native "Choose a printer" ডায়ালগ খুলে দেবে।

## 🌐 No-Internet / Maintenance স্ক্রিন

- ফোনে ইন্টারনেট না থাকলে → বাংলা "ইন্টারনেট সংযোগ নেই" স্ক্রিন, স্বয়ংক্রিয়ভাবে সংযোগ ফিরলে reload হয়ে যাবে।
- ইন্টারনেট আছে কিন্তু `APP_URL` লিংক কাজ করছে না (ভুল লিংক / deployment ডিলিট / সার্ভার ৪xx-৫xx error) → "অ্যাপটি রক্ষণাবেক্ষণে আছে" স্ক্রিন।
- দুটো পেজই `app/src/main/assets/no_internet.html` ও `maintenance.html` এ আছে — চাইলে টেক্সট/রং/লোগো এখান থেকেই বদলাতে পারবেন।

---

## 📁 Project Structure

```
axion-dms-android/
├── .github/workflows/build.yml  ← Auto APK builder
├── app/src/main/
│   ├── java/com/axion/dms/
│   │   └── MainActivity.java    ← 👈 URL এখানে বদলান
│   ├── res/
│   │   ├── mipmap-*/            ← App icons
│   │   └── values/strings.xml  ← App name
│   └── AndroidManifest.xml
├── build.gradle
├── settings.gradle
└── gradlew
```
