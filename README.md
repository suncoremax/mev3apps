# AXIION Billboard — Android app

Native Android app (Bluetooth Low Energy) for controlling the AXIION
billboard alongside its existing WiFi web UI. UI is in Bengali.

## What it does
- **ছবি (Picture) tab** — pick a photo, it's cropped/resized to 128×128
  with the same quality logic as the web UI (aspect-preserving crop,
  Floyd-Steinberg dithering), then sent to the board over Bluetooth in
  chunks.
- **টেক্সট (Text) tab** — type a slide, pick a color and scroll speed,
  send it.
- **বিলবোর্ড (Billboard) tab** — set brightness, see the current slide
  list, delete slides.

## How it talks to the board
Over BLE, using the protocol documented in the firmware's
`ble_config.h` (service/characteristic UUIDs, JSON commands, chunked
image transfer). The board must be running the BLE-enabled firmware
build for this to work.

## Building the APK
This repo builds itself via GitHub Actions — push it to a GitHub repo
and check the **Actions** tab; a debug APK will appear as a build
artifact you can download and install (Settings → allow install from
this source if prompted).

To build locally instead: open in Android Studio, or run
`./gradlew assembleDebug` from a terminal with the Android SDK
installed. Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Permissions
Requests Bluetooth (scan/connect) permission on first use. On Android
11 and below this also requires location permission — that's an
Android platform requirement for BLE scanning, not something this app
actually uses your location for.
