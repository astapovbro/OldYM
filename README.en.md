# OldYM

![OldYM](https://github.com/astapovbro/OldYM/blob/main/img/icon.png)

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

The project builds without Gradle. Both systems use the same `tools/build_portable.py` script; `build.cmd` and `build.sh` are wrappers for it. By default, the result is an **unsigned** APK: no password is requested and you do not need to close a window to stop signing. To install the APK, you must sign it with your own key.

### Requirements

- **JDK 11**, not only a JRE. If `JAVA_HOME` is set, it must point to the JDK root, without `bin` at the end. Other JDK versions have not been tested with this guide.
- **Python 3.8+**; on Windows, Python Launcher with the `py -3` command is also required.
- The complete source project: `src/`, `res/`, `assets/`, `tools/`, `AndroidManifest.xml`, and `proguard.cfg`. A README alone or an archive containing only Java files is not enough.
- On **Windows x64**, a native `aapt.exe` from **Android SDK Build Tools 28.0.3** is also required. Install it through Android SDK Manager: SDK Tools → Show Package Details → Android SDK Build-Tools → 28.0.3. Android Studio itself is not required for compilation. You can specify the SDK directory through `ANDROID_SDK_ROOT`, or pass the exact executable path with `--aapt`. Keep the other DLLs from the Build Tools package next to it.
- On **Linux x86-64**, the bundled `tools/sdk/android-9/aapt` is used; the Windows `aapt.exe` is not needed there. The bundled executable will not work on other architectures: a suitable `aapt` and a separate build check will be required.

`tools/` must already contain `android.jar` API 5, `dx.jar`, `apksigner.jar`, ProGuard, and the Bouncy Castle libraries. Do not replace them with arbitrary newer versions: the current toolchain targets old Android versions.

For the first run, it is convenient to extract the project to a short path without Cyrillic characters, for example `C:\OldYM` or `~/OldYM`. Run all commands below **from the project root**, where `AndroidManifest.xml` is located.

### Windows: environment check and unsigned build

Open **Command Prompt (`cmd`)** in the project directory. The following commands are for cmd, not PowerShell. Substitute the path to your installed JDK:

```bat
set "JAVA_HOME=C:\Java\jdk-11"
"%JAVA_HOME%\bin\javac.exe" -version
py -3 --version
```

If Android SDK is not installed in the standard directory, specify its location:

```bat
set "ANDROID_SDK_ROOT=C:\Android\Sdk"
```

Build the APK:

```bat
build.cmd --unsigned
```

Result: `build\unsigned.apk`. It is not ready for installation yet. The builder searches for `aapt.exe` in `tools\windows\`, the SDK from `ANDROID_SDK_ROOT` / `ANDROID_HOME`, and then `%LOCALAPPDATA%\Android\Sdk\build-tools\28.0.3\`. To specify an explicit path:

```bat
build.cmd --unsigned --aapt "C:\Android\Sdk\build-tools\28.0.3\aapt.exe"
```

### Linux: environment check and unsigned build

Install JDK 11 and Python 3 using your distribution's package manager. Check them:

```sh
java -version
javac -version
python3 --version
```

If multiple JDK versions are installed, set the path to version 11, replacing the directory with the actual path:

```sh
export JAVA_HOME=/path/to/jdk-11
export PATH="$JAVA_HOME/bin:$PATH"
```

Build the APK:

```sh
sh build.sh --unsigned
```

Result: `build/unsigned.apk`. The builder automatically adds execute permission to the bundled Linux `aapt` file. Running from a filesystem mounted with `noexec` will still be impossible — move the project to a normal filesystem.

### Creating your personal signing key

**If you already have a key, do not create a new one.** Use the same file and alias that were used to sign previous releases. Create the key once, not before every build.

It is best to keep the key **outside the repository**. Windows example (cmd):

```bat
if not exist "%USERPROFILE%\.oldym" mkdir "%USERPROFILE%\.oldym"
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -storetype JKS -keystore "%USERPROFILE%\.oldym\oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

Linux example:

```sh
mkdir -p "$HOME/.oldym"
keytool -genkeypair -storetype JKS -keystore "$HOME/.oldym/oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

`keytool` will interactively ask for the keystore password and certificate-owner details. Password input is not displayed — this is normal. A certificate from a paid certificate authority is not required for distributing an APK yourself. Certificate details are visible to other people: do not enter your home address or other unnecessary personal information. When asked for the key password, you can press Enter to use the keystore password.

- `oldym-release.jks` — a keystore containing the private key; do not publish it.
- `oldym` — the alias, meaning the key's name inside the keystore; it is not a password or an Android package identifier.
- `10000` — the certificate validity period in days.
- Keep a private backup of the file, alias, and password. The password without the key file cannot be used to sign updates, and the file without its password may also be useless.
- Never attach the key to GitHub Releases, source code, or bug reports. The project's official key and users' OAuth tokens are not needed for building from source.

Instead of the manual command, you can use the wrapper: `create-key.cmd` on Windows or `python3 tools/build_portable.py --create-key` on Linux. By default, it creates the same `~/.oldym/oldym-release.jks` with the `oldym` alias and refuses to overwrite an existing file. For a custom location, add `--keystore`; for another alias, add `--alias`.

### Building and signing with your own key

Windows (cmd):

```bat
build.cmd --keystore "%USERPROFILE%\.oldym\oldym-release.jks" --alias oldym
```

Linux:

```sh
sh build.sh --keystore "$HOME/.oldym/oldym-release.jks" --alias oldym
```

After compilation, `apksigner` will ask for the password. Do not put the password in the command or source code. If the key has a separate password, the tool may ask for that one as well.

Results:

- `build/unsigned.apk` — an intermediate unsigned APK.
- `build/OldYM-<versionName>.apk` — the signed APK for installation; the version is taken from `AndroidManifest.xml`, for example `OldYM-1.1.apk`.
- `build/shrink.log` — the ProGuard log.

The signature is verified automatically, and certificate information is printed in the terminal. RSA and the **v1/JAR** signature are used, which are required by Android 2.x; the absence of a v2 signature in this build is not an error. Do not change the APK contents after signing.

If `unsigned.apk` has already been built, recompilation is not required:

```bat
py -3 tools\build_portable.py --sign-only build\unsigned.apk --keystore "%USERPROFILE%\.oldym\oldym-release.jks" --alias oldym
```

```sh
python3 tools/build_portable.py --sign-only build/unsigned.apk --keystore "$HOME/.oldym/oldym-release.jks" --alias oldym
```

For a different signed APK filename, add `--output`. Full help: `py -3 tools/build_portable.py --help` or `python3 tools/build_portable.py --help`.

### Updates and certificate verification

To update an already installed app without deleting its data, keep the Android package identifier unchanged and sign the APK with **the same key**. For each new release, also increase `android:versionCode` in `AndroidManifest.xml`; `versionName` is the version displayed to users. A self-built APK signed with a different key will not install over the official version with the same package identifier: first save any needed data and uninstall the previous installation.

You can view the keystore's keys and aliases with `keytool -list -v -keystore PATH_TO_KEY`. To verify an APK independently of the builder:

Windows (cmd):

```bat
"%JAVA_HOME%\bin\java.exe" -jar tools\sdk\android-9\lib\apksigner.jar verify --min-sdk-version 5 --verbose --print-certs build\OldYM-1.1.apk
```

Linux:

```sh
java -jar tools/sdk/android-9/lib/apksigner.jar verify --min-sdk-version 5 --verbose --print-certs build/OldYM-1.1.apk
```

Replace `OldYM-1.1.apk` with the actual filename. You can publish the signing **certificate's** public SHA-256 fingerprint on the project page: it helps distinguish the official signature from someone else's. This is not the APK's SHA-256 checksum: the APK checksum changes with every build. The certificate fingerprint is not secret.

### If the build fails

| Message or situation | What to check |
| --- | --- |
| `py` not found | Is Python Launcher installed? Alternatively, run `python tools\build_portable.py` directly with your installed Python 3. |
| `javac` not found / invalid `JAVA_HOME` | Is JDK 11 installed specifically? The path must point to the JDK root, not `bin`. |
| `tools/build_portable.py` missing | Incomplete build kit: the script and its corresponding `build.cmd` / `build.sh` are required. The old README cannot replace them. |
| `aapt.exe` missing on Windows | Is Build Tools 28.0.3 installed? If needed, pass `--aapt` with the full path. The Linux `aapt` file does not work on Windows. |
| System DLL error from `aapt.exe` | Keep all Windows Build Tools files together; if Visual C++ Runtime is reported missing, install Microsoft's official package, not individual DLLs from third-party websites. |
| `Permission denied` / `Exec format error` on Linux | Check execute permission, that the filesystem is not mounted with `noexec`, the x86-64 architecture, and that the required dynamic libraries for `aapt` are available. |
| ProGuard error | Check the first build error and `build/shrink.log`. Do not rely only on an APK left by a previous run. |
| Invalid password / alias | Check the keystore with `keytool -list`; do not create a new key in place of the old one when updating a release. |
| APK does not update over the installed version | Do the package identifier and certificate match, and has `versionCode` been increased? |

The complete compilation, packaging, and signing chain has been checked on Linux with JDK 11. Running the Windows commands and the Windows `aapt.exe` requires a check on Windows; a successful build does not replace testing sign-in, playback, and downloads on a phone.

## Source structure

The application source is in `src/`, interface strings are in `res/values/strings.xml`, main colors are in `res/values/colors.xml`, the icon is in `res/drawable/icon.png`, and root certificates are in `assets/roots.pem`. The app is written in Java and uses old Android APIs.

Build environment: JDK 11, Python 3, Android SDK Platform 5 (`android.jar`), Build Tools 28.0.3 (`aapt`, `dx.jar`, `apksigner.jar`), ProGuard 6.2.2 base JAR, and Bouncy Castle bctls/bcprov/bcutil jdk15to18 1.86. Windows requires a separate native `aapt.exe` from the same Build Tools version. Before publishing the source, update the checksum file if it is included in the project.

Bouncy Castle cryptographic classes are relocated to a separate namespace before compilation to avoid conflicts with the old built-in Android version. The source code and certificates do not disable HTTPS verification.

## Dependencies and licensing

TLS/cryptography: Bouncy Castle (MIT), certificates: Mozilla/curl (MPL-2.0). API interaction is adapted from the yandex-music-api source (LGPL-3.0), including OAuth Device Flow. Copies of the licenses and links to the sources are included in `licenses/`. The source code of this client is distributed under the GNU LGPL-3.0-or-later. Service names belong to their respective owners.

## Screenshots
![OldYM — главное меню](screenshots/menu.png)
![OldYM — экран плеера](screenshots/player.png)
![OldYM — "Моя волна"](screenshots/myvibe.png)
![OldYM — результаты поиска](screenshots/search.png)

## Download
* [GitHub Releases](https://github.com/astapovbro/OldYM/releases/latest)
