# Juicebox Mobile

Android and iOS clients for [Juicebox](https://box.juicey.dev), sharing one
Kotlin Multiplatform core.

## Modules

| Module    | What it is                                                          |
| --------- | ------------------------------------------------------------------- |
| `core`    | Shared logic: API client, sessions, settings, library, upload engine |
| `app`     | Android app: Jetpack Compose UI, WorkManager uploads, share target   |
| `iosApp`  | iOS app: SwiftUI, generated from `project.yml` by XcodeGen           |

The core has no Android, Java or Apple imports in `commonMain`; platform
details (HTTP engine, clock, key-value storage, file access) are injected.
Android talks to the core directly; iOS goes through `IosBridge`, a small
callback-based surface in `iosMain` so Swift never touches coroutine flows.

## Android

Requirements: JDK 21 and Android SDK 36.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`minSdk` is 26, `targetSdk` is 36. Debug builds use the
`dev.juicey.juicebox.debug` package so they can sit next to a release install.

### Release builds

Release builds are deliberately **unsigned** in this repository:

```sh
./gradlew :app:assembleRelease
# app/build/outputs/apk/release/app-release-unsigned.apk
```

To produce a signed APK, copy `keystore.properties.example` to
`keystore.properties` (git-ignored) and fill in a keystore outside the repo.
The Gradle config picks it up automatically when present.

## iOS

Requirements: macOS with Xcode 16 or newer and JDK 21, plus `xcodegen`. The iOS
workflow pins Xcode 26.3 for the iOS 26 SDK.

```sh
cd iosApp
xcodegen generate
xcodebuild -project Juicebox.xcodeproj -scheme Juicebox \
  -configuration Debug -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath build CODE_SIGNING_ALLOWED=NO build
xcrun simctl install booted build/Build/Products/Debug-iphonesimulator/Juicebox.app
```

The Xcode target runs `./gradlew :core:embedAndSignAppleFrameworkForXcode` as a
pre-build step, so the shared framework is always current. Deployment target is
iOS 17 and the bundle id is `dev.juicey.juicebox`.

`.github/workflows/ios.yml` builds the simulator app on a macOS runner and
publishes `Juicebox-simulator.zip` as an artifact. The same workflow launches
the app on a simulator and uploads light and dark screenshots of every screen
to `Juicebox-screenshots`: each upload mode, the files list and file detail,
every settings page, and every settings dialog. The capture script lives at
`iosApp/scripts/capture-screenshots.sh` and starts each run on the right tab,
page, mode or dialog with launch environment variables. A separate UI-test job
drives the Dynamic Island, so the same artifact also has the Live Activity
compact, expanded and in Notification Centre.
`.github/workflows/ios-device.yml` builds an unsigned device IPA for
sideloading; see [`docs/test-on-device.md`](docs/test-on-device.md) for signing
it with a free Apple ID. Device and TestFlight builds
need an Apple Developer team and a signing profile in the repository secrets;
none are configured yet.

Themes are the five static palettes (Juicebox, Violet, Ocean, Forest, Rose);
Material You is Android-only. On iOS 26 a Liquid Glass toggle switches the
cards to molten surfaces, while the bottom bar is the system Liquid Glass tab
bar on every launch. The toggle folds away on older systems.

An upload drives an ActivityKit Live Activity — Lock Screen, Notification
Centre and Dynamic Island, with compact, minimal and expanded presentations.
`JuiceboxWidgets` renders it and shares `UploadActivityAttributes` with the app.
Two App Intents expose **Upload to Juicebox** (file) and **Upload text to
Juicebox** to Shortcuts and Siri; both reuse the app's single bridge, so they
respect the session, retention and upload password.

Uploads run in the foreground for now, and picking files happens in-app
because a share extension would need App Groups from a paid Apple Developer
account.

## Versioning

`versionCode` and `versionName` live in `app/build.gradle.kts`; the iOS
`MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` live in `iosApp/project.yml`.
The version is also sent to the server in the user agent, formatted as
`Juicebox/<os>/<version>` (for example `Juicebox/Android/1.0.0`), plus the
`X-Juicebox-Client` platform header.

## Tests

```sh
./gradlew :core:test
```

Shared tests run on the JVM and cover transport planning, retries, cookies and
the upload multipart writer. `:core:compileKotlinIosSimulatorArm64` checks the
Apple side of the core without needing a Mac (linking needs one).
