# Trying the iOS app from Linux

There is no way to run an iOS app locally on Linux. The Simulator ships inside
Xcode and needs macOS; nobody — free or paid — has reimplemented it for Linux.
So "interact with it" means running the Simulator **somewhere that has macOS**
and streaming the screen, or running on a real iPhone.

The app itself is ready for this: CI builds an unsigned simulator `.app` that
needs no Apple account, and that is exactly what browser-based simulators eat.

## Tap through it in a browser — Appetize (free)

[Appetize](https://appetize.io) streams a real iOS simulator to a browser tab.
The free plan is enough to try a build: **30 minutes per month, 3 minutes per
session, 2 concurrent devices**, and iOS **26.0** images (iPhone 17 Pro,
iPhone 15 Pro, iPhone 16 Pro…). It takes our simulator build directly.

```sh
./iosApp/scripts/download-simulator-app.sh
```

Then:

1. Sign up at <https://appetize.io> (free, no card).
2. <https://appetize.io/upload> → drop `iosApp/build/appetize/Juicebox-simulator.zip`.
3. On the app page pick **iPhone 17 Pro** and **iOS 26.0**.
4. Tap. It is the app, not a video: type the server URL, log in, upload a photo
   from the simulator's library, switch themes, toggle Liquid Glass.

Notes:

- 3-minute sessions are the free-plan limit; the app keeps its state between
  sessions on the same device, so you can work in batches.
- The simulator has real networking, so `https://box.juicey.dev` works. Set it
  under Settings if the build's default is not reachable.
- Liquid Glass needs the iOS 26 image; on the iOS 17/18 images the toggle is
  correctly absent.

What you can exercise there: every screen, the Files/Photos pickers, uploads,
QR/link/delete, themes, light and dark, the glass toggle, Notification Centre,
and the Dynamic Island (long-press it during an upload). What is less faithful:
Live Activity lifecycle and Shortcuts indexing are worth re-checking on the
phone, because the simulator is not a phone.

## Real iPhones on demand — AWS Device Farm (free trial)

Device Farm gives a **one-time 1,000 device minutes** free (then $0.17/min) and
remote-access sessions are genuinely interactive on real iPhones. It expects an
installable `.ipa`, so it opens up once the build is signed — same free-Apple-ID
route as the sideloading steps in [test-on-device.md](test-on-device.md). It is
not a substitute for a Mac if you also want Xcode.

## A Mac in the cloud, by the hour

If you want the actual Simulator app (ActivityKit previews, Xcode, Instruments,
the ability to sign the device IPA with a free Apple ID), rent Apple silicon by
the hour — Scaleway, MacStadium, MacinCloud and friends — and drive it over VNC
or Screen Sharing. This is the only route that is both interactive *and* able
to produce signed device builds without owning a Mac. It costs money, it is
billed hourly, and the setup is a one-off afternoon.

## The free local path is Android

`jb34` on this machine is a real Android emulator running the same shared core
and the same server contract. Uploads, retention, lock/password, library
deletion and theming are all exercised there for free. Use it for everything
platform-neutral; use the routes above only for iOS-specific behaviour.

## What CI already proves, and what only a running device proves

Every push now also captures each tab in light and dark (plus the glass toggle
off) from a simulator; see the **Juicebox-screenshots** artifact on the `iOS`
workflow. That catches layout breakage, not behaviour.

From the artifacts, without running anything: the app plist has
`NSSupportsLiveActivities`, `PlugIns/` contains the widget extension with the
right bundle id, and `Metadata.appintents` carries both App Intents and their
Siri phrases.

Only a running device can confirm: a Live Activity appearing in Notification
Centre, the Dynamic Island presentations, Shortcuts indexing "Upload to
Juicebox", and how the uploads feel against the real server.
