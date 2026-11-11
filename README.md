# Juicebox Mobile

Android and iOS clients for [Juicebox](https://box.juicey.dev), sharing one
Kotlin Multiplatform core.

## Modules

| Module  | What it is                                                        |
| ------- | ----------------------------------------------------------------- |
| `core`  | Shared logic: API client, sessions, settings, library, upload engine |
| `app`   | Android app: Jetpack Compose UI, WorkManager uploads, share target |

The core has no Android or Java imports in `commonMain`; platform details
(HTTP engine, clock, key-value storage, file access) are injected. iOS targets
and the SwiftUI app live alongside `app` once the Apple toolchain is wired up.

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

## Versioning

`versionCode` and `versionName` live in `app/build.gradle.kts`. The version is
also sent to the server in the user agent and `X-Juicebox-Client` header, so
upload statistics can tell platforms apart.

## Tests

```sh
./gradlew :core:test
```

Shared tests run on the JVM and cover transport planning, retries, cookies and
the upload multipart writer.
