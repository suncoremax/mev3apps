# AXiion DroneCTRL — Android flight controller

A native Android app (no WebView) that flies the ESP-Drone firmware described
in `CUSTOM_BUILD_NOTES.md`, over its WiFi/UDP/CRTP link. Built as its own
project with its own application ID — **not** copied from any other app you
have (e.g. the Miron Electronics WebView template): the package here is
`com.axiion.dronecontrol`, set in exactly one place, `app/build.gradle`.

## What it does

- Dual on-screen joysticks, Mode-2 style:
  - **Left stick** — throttle (Y axis, holds its position when released) / yaw rate (X axis, self-centers)
  - **Right stick** — roll (X axis) / pitch (Y axis), both self-center
- **ARM / DISARM** — the app only opens the UDP link and starts sending setpoints once armed. Disarming (or backgrounding the app, or losing WiFi) immediately sends zero-thrust packets and closes the socket.
- **STOP** — one-tap emergency zero, always available.
- **Max thrust slider** — caps the top end of the thrust value sent to the drone (default 80%), so a full-stick throttle input still respects the motor voltage limit called out in `CUSTOM_BUILD_NOTES.md` §3.
- **Link status pill** — red = not on the drone's WiFi, cyan = on the WiFi but not armed, yellow = armed and sending but no reply seen from the drone yet, green = armed and the drone has sent *some* UDP traffic back recently.
- **Settings (⚙)** — override the drone's IP/port if you changed `CONFIG_WIFI_...` defaults in the firmware.

## What it deliberately does *not* do yet

- **No battery/attitude telemetry (LOG port).** Reading those needs the CRTP LOG "TOC download + block subscription" handshake. `CUSTOM_BUILD_NOTES.md` itself flags the setpoint packet as needing a cross-check against the official app/`cflib` before trusting it — the LOG handshake is more state machine to get wrong the same way, so it's left out rather than shipped unverified. What the app *does* show honestly is whether any UDP packet has come back at all (the status pill), which is a safe, true signal that doesn't depend on a specific packet format being right.
- **No PID tuning UI (PARAM port).** Same reasoning — straightforward to add later once the setpoint link is confirmed working.

If you want either of those, the cleanest path is to get one confirmed
two-way exchange working (e.g. temporarily add a hardcoded LOG TOC request
copied verbatim from the official ESP-Drone app's source) and build from
there, rather than guessing the frame layout from a spec.

## Before you fly

Everything in `CUSTOM_BUILD_NOTES.md` §3 still applies — flyback diodes on
each motor, props off for the first power-up and `motorsTest()` check, and
set the thrust cap slider conservatively for your motor's actual voltage
rating before trying full stick.

## Build it via GitHub Actions (no local Android SDK needed)

1. Create a new **public** GitHub repo (Actions on private repos need to be
   enabled manually).
2. Push this folder to it:
   ```bash
   cd axiion-drone-controller
   git init
   git add .
   git commit -m "AXiion DroneCTRL"
   git branch -M main
   git remote add origin https://github.com/<you>/<repo>.git
   git push -u origin main
   ```
3. Wait ~2-3 minutes, then go to **Actions tab → latest run →
   `AXiionDroneController-APK` artifact → Download**. Unzip it to get the
   `.apk`.
4. To also get it under **Releases**, push a tag: `git tag v1.0 && git push --tags`.

## Build it locally instead (Android Studio)

Open the folder in Android Studio. If it asks to fix the Gradle wrapper,
let it — this repo intentionally doesn't commit the wrapper's binary jar
(kept the repo dependency-free for the CI path above). Android Studio will
regenerate it automatically on first sync.

## Changing the package / application ID

It's in exactly one place: `app/build.gradle` → `namespace` and
`applicationId` (both currently `com.axiion.dronecontrol`). Change both to
the same new value if you want a different one — do this *before* your
first real install/release, since Android treats a different applicationId
as a different app entirely.

## Protocol reference

See `CUSTOM_BUILD_NOTES.md` §6 for the full CRTP/UDP spec this app
implements. In short: connect your phone to `ESP-DRONE-<MAC>` (password
`12345678`), the app then talks UDP to `192.168.43.42:2390`, sending a
15-byte CRTP setpoint packet (`CrtpPacket.java`) at 60 Hz
(`DroneLink.java`) while armed.
