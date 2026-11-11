# Trying the iOS app on a real iPhone

The app is built on GitHub Actions, because linking the iOS framework needs
macOS. The artifact is **unsigned**: iOS only runs signed code, so the signing
happens on the machine the phone is plugged into.

Two ways to sign, both fine for a temporary device:

| Route | Cost | Lifetime per install | Needs |
| --- | --- | --- | --- |
| Free Apple ID via `iloader` | free | 7 days | USB, this Linux box |
| TestFlight | $99/year | 90 days per build | Apple Developer Program |

Use the free route to try the build. Move to TestFlight once you want people
you can't hand a cable to.

## 1. Get the IPA

```sh
gh run list --workflow=ios-device.yml --limit 1
gh run download <run-id> -R juiceydev/juicebox-mobile -n Juicebox-unsigned-ipa
```

`ios-device.yml` runs on every `v*` tag and on demand
(`gh workflow run ios-device.yml`).

## 2. Sign and install from Linux

```sh
sudo pacman -S iloader      # pulls usbmuxd
sudo systemctl start usbmuxd
iloader
```

1. Plug the iPhone in, unlock it, tap **Trust** on the phone.
2. Check the device shows up: `idevice_id -l`.
3. In iloader, sign in with the Apple ID. With two-factor auth enabled, use an
   app-specific password from <https://appleid.apple.com>, or answer the code
   prompt.
4. Pick the device, choose `Juicebox-unsigned.ipa`, install.

On the phone, the first launch needs:

- **Settings → Privacy & Security → Developer Mode** on, then reboot (iOS 16+).
- **Settings → General → VPN & Device Management → <your Apple ID> → Trust**.

## Free-account limits

- A free certificate expires after **7 days**; reinstall with iloader to renew.
  iloader keeps a background service that can refresh automatically while the
  phone is on the same network.
- At most **3 apps** stay installed at once, and **10 new App IDs per week**.
- The bundle id keeps its `dev.juicey.juicebox` suffix behind your team prefix,
  so it can coexist with a future TestFlight install.

## Platform checks

Run these on the phone before calling a build good.

| Check | Where to look | Passes when |
| --- | --- | --- |
| Liquid Glass | Settings → Appearance | On iOS 26 the toggle exists and the cards/tab bar go translucent; turning it off restores solid surfaces. On iOS 17-25 the row is hidden and the app looks unchanged. |
| Dynamic Island | Start an upload, then leave the app | Island device: compact leading icon plus live percentage; long-press expands to filename, byte counts and a progress bar. No-island device: the same activity shows on the Lock Screen. |
| Live Activity in Notification Centre | Start an upload, pull down Notification Centre | The upload card is listed with its progress and updates while the upload runs. |
| Shortcuts action | Shortcuts app → search "Juicebox" | "Upload to Juicebox" appears under the app's actions, takes a file input, and returns a share link. `Upload text to Juicebox` does the same for a string. |

Notes:

- Live Activities can be disabled per app (Settings → Juicebox → Live Activities);
  if they are off, uploads simply don't create one.
- The Shortcuts action runs in the background, so keep demo files small; long
  uploads can be suspended when the intent's time slice ends.
- The app now ships a widget extension (`dev.juicey.juicebox.widgets`). Sideloaders
  have to re-sign nested bundles; if yours refuses, say so and the device
  workflow can be pointed at a build without the extension.

## What to look at

- Uploads from Files, Photos and pasted text, and whether the retention chips
  match what the server allows.
- Uploading a big file (over the TUS threshold) — progress and resume.
- The five themes, and that the light/dark switch keeps contrast readable.
- Killing the app mid-upload: v1 deliberately runs uploads in the foreground,
  so the transfer stops. That is expected, note it rather than file it.

Known gaps before TestFlight: cookies are stored in `UserDefaults` rather than
the Keychain, there is no camera capture, and there is no share extension
(App Groups need a paid team).
