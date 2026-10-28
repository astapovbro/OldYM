# OldYM

**Language:** English | [Русский](README.md)

An unofficial Yandex Music client for Android 2.0+. Not affiliated with Yandex.

## ARMv6 and ARMv7 processors

One APK supports both architectures: the app and TLS libraries contain Java/DEX code, with no native `.so` files. `minSdkVersion=5`, DEX 035. Android 2.0 checks were performed on an ARM926EJ-S emulator (ARMv5TEJ, `armeabi`), so the app does not require ARMv7 instructions. The app has been tested on these ARMv6 and ARMv7 devices:
- Samsung Galaxy Ace (Android 2.3.6)
- LG Optimus Me (Android 2.2)
- LG Optimus Sol (Android 4.0.4)
- Samsung Galaxy S II (Android 4.0.4)
- Honor 7 (Android 5.0.2)

## Installation and sign-in

1. Transfer the OldYM APK to your phone (for building it yourself, see [Build: Windows and Linux](#build-windows-and-linux)). Enable installation from unknown sources in Android settings and open the APK with a file manager.
2. Open the app and tap **"Sign in with a device code"** on the first screen.
3. The app will show a code and a Yandex page address. Open that address on a modern phone or computer, sign in directly on the Yandex website, and enter the code. Return to the device running OldYM: the app waits for confirmation automatically.
4. Alternatively, enter your own OAuth token with Music access. Do not send the token to other people or enter it into third-party websites.

A computer may only be needed to confirm the sign-in if the old browser cannot open the website. No permanently running PC, proxy server, or root access is required. Networking and TLS 1.2 are implemented directly inside the APK. Certificate-chain and server-name verification are enabled. The phone must have the correct date and time set.

## Features

- Player artwork: an increased download limit and fallback image sizes for older devices.
- View liked tracks and add or remove a like from the player.
- Your own and saved playlists: browse and play them.
- Paginated tracks from likes and search results: 20 results first, then 20 more with the **"Load 20 more tracks"** button (5 tracks at a time in **My Wave**).
- **My Wave** — loads personalized recommendations from Yandex Music.
- Player: pause, next/previous track, seeking, shuffle, and background playback.
- Download an individual track or the entire open list.
- Downloaded music section.
- Sign in with a device code or manually with a token.

Account features, subscription status, regional restrictions, and full MP3 availability are checked by the service. The app does not bypass these restrictions. Compatibility with the unofficial API may change.

## Current limitations

- Before playback, a new online track is downloaded completely into a temporary buffer. Playback does not start as a stream; the first download may take some time.
- Offline downloads are processed one at a time while the app is open. Do not close the app while downloading a playlist.
- MP3 at 128 kbps is preferred when available; codecs and DRM variants are not converted.
- Creating or editing playlists, lyrics, and device synchronization are not implemented. Getting and managing **My Wave** tracks depends on the unofficial API.
- The token is stored in the app's private data and is not hardware-encrypted; root access can expose it. Older Android versions do not receive modern security updates by themselves.
- Music is stored in `Android/data/ru.oldym/files/music` on the memory card, or in the app's internal data if no card is available. Artwork is cached separately.
- HTTPS verification does not include online certificate-revocation checks; root certificates are bundled in the APK and updated in new builds.

## Build: Windows and Linux

Build using **Gradle Wrapper 7.6.4** and **JDK 11**. You do not need a separate Gradle installation. Sources remain in `src/` and resources in `res/`. Individual Gradle tasks drive the existing API 5 toolchain (`aapt`, Java, ProGuard, `dx`, signing); the Android Gradle Plugin is not applied.

- Linux x86-64: `./gradlew assembleDebug` (or `sh gradlew assembleDebug`).
- Windows x64: `gradlew.bat assembleDebug`.
- Local test APK: `build/outputs/apk/debug/OldYM-debug.apk`.
- Unsigned build: `assembleUnsigned`; release build: `assembleRelease`.

You need the complete project with its bundled tools and Python 3.8+ for Bouncy Castle relocation. Windows also requires Windows Android SDK Build Tools 28.0.3; set `ANDROID_SDK_ROOT` or pass `-Paapt=...`. The wrapper needs internet access on first run.

**Full instructions for both systems, creating a permanent key, and release signing: [BUILDING.en.md](BUILDING.en.md).** The local debug signing key is not your official release key. Keep using your existing release key for updates.

`build.sh` and `build.cmd` now accept Gradle task names: use `build.cmd assembleUnsigned`, not the old `build.cmd --unsigned`. Actual test results: [GRADLE-TEST-RESULTS.md](GRADLE-TEST-RESULTS.md).

## Source structure

The application source is in `src/`, interface strings are in `res/values/strings.xml`, main colors are in `res/values/colors.xml`, the icon is in `res/drawable/icon.png`, and root certificates are in `assets/roots.pem`. The app is written in Java and uses old Android APIs.

Build environment: JDK 11, Python 3, Android SDK Platform 5 (`android.jar`), Build Tools 28.0.3 (`aapt`, `dx.jar`, `apksigner.jar`), ProGuard 6.2.2 base JAR, and Bouncy Castle bctls/bcprov/bcutil jdk15to18 1.86. Windows requires a separate native `aapt.exe` from the same Build Tools version. Before publishing the source, update the checksum file if it is included in the project.

Bouncy Castle cryptographic classes are relocated to a separate namespace before compilation to avoid conflicts with the old built-in Android version. The source code and certificates do not disable HTTPS verification.

## Dependencies and licensing

TLS/cryptography: Bouncy Castle (MIT), certificates: Mozilla/curl (MPL-2.0). API interaction is adapted from the yandex-music-api source (LGPL-3.0), including OAuth Device Flow. Copies of the licenses and links to the sources are included in `licenses/`. The source code of this client is distributed under the GNU LGPL-3.0-or-later. Service names belong to their respective owners.
