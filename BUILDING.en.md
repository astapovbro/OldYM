# Building OldYM with Gradle

[Русский](BUILDING.md)

## Scope

This build uses **Gradle Wrapper 7.6.4** with separate tasks for resources, Java,
JAR creation, Bouncy Castle relocation, ProGuard, DEX generation, packaging,
signing and signature verification. It does **not** apply the Android Gradle
Plugin (AGP), and does not call `build_portable.py`.

Sources stay in `src/`, resources in `res/`, and the application ID stays
`ru.oldym`. Moving files to `app/src/main` is unnecessary. This configuration
does not provide the standard Android Studio Android-module integration.

The existing API 5 toolchain is retained: `aapt`, Java 7 bytecode, ProGuard
and `dx`, producing DEX 035. This update adds no application features.

## Requirements

- Your complete current project: `AndroidManifest.xml`, `src/`, `res/`,
  `assets/`, `proguard.cfg`, and all existing files under `tools/`.
- **JDK 11**, not only a JRE. `JAVA_HOME` must point to the JDK root, without `bin`.
- **Python 3.8+**, used only by `tools/relocate.py`. Default: `python3` on Linux,
  `py -3` on Windows. Override with `-Ppython=python` or a full executable path.
- Internet access on first run. The wrapper downloads Gradle 7.6.4 and checks
  its SHA-256. Installing Gradle separately is unnecessary.
- Linux **x86-64**: the bundled `tools/sdk/android-9/aapt` and its system libraries.
- Windows **x64**: **Android SDK Build Tools 28.0.3 for Windows**, installed
  using SDK Manager. Keep its DLLs beside `aapt.exe`; the Linux executable
  cannot run on Windows.

Required bundled tools: `tools/api5/android-2.0_r01-linux/android.jar`,
`tools/sdk/android-9/lib/dx.jar`, `tools/sdk/android-9/lib/apksigner.jar`,
`tools/proguard-base.jar`, `tools/bcprov.jar`, `tools/bctls.jar`,
`tools/bcutil.jar`, and `tools/relocate.py`.

## Applying the update

Extract `OldYM-Gradle-build-update.zip` into your **current project root**,
replacing the build files. It is an overlay, **not a complete source project**.
It contains no application sources, resources, bundled dependencies, private
keys or APKs. Do not replace your current sources with an older project.

The update does not modify `src/`, `res/`, `assets/`, or your source manifest.
During compilation it generates a manifest copy and resolves relative component
names against actual source classes. This handles the existing `ru.oldym`
application ID / `ru.retromusic` Java namespace mismatch without changing the ID.

## Linux

Run from the project root; substitute the actual JDK 11 directory:

```sh
export JAVA_HOME=/path/to/jdk-11
export PATH="$JAVA_HOME/bin:$PATH"
java -version
javac -version
python3 --version
chmod +x gradlew build.sh
./gradlew assembleDebug
```

If ZIP extraction lost the executable bit, use `sh gradlew assembleDebug`.
`sh build.sh` is an equivalent shortcut.

## Windows

Example for **cmd**, not PowerShell:

```bat
set "JAVA_HOME=C:\Java\jdk-11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "ANDROID_SDK_ROOT=C:\Android\Sdk"
java -version
javac -version
py -3 --version
gradlew.bat assembleDebug
```

To set a specific aapt executable:

```bat
gradlew.bat assembleDebug "-Paapt=C:/Android/Sdk/build-tools/28.0.3/aapt.exe"
```

Windows aapt lookup: `-Paapt`, `AAPT`, `tools/windows/aapt.exe`, SDK directories
from `ANDROID_SDK_ROOT` / `ANDROID_HOME`, then
`%LOCALAPPDATA%/Android/Sdk/build-tools/28.0.3/aapt.exe`.
`build.cmd` is an equivalent shortcut. In PowerShell, use `./gradlew.bat`
and set environment variables using `$env:...`.

## Tasks and output

| Task | Output |
| --- | --- |
| `assembleDebug` | `build/outputs/apk/debug/OldYM-debug.apk`, locally signed and verified |
| `assembleUnsigned` | `build/unsigned.apk`, not installable without a signature |
| `assembleRelease`, no release key | `build/outputs/apk/release/OldYM-release-unsigned.apk` |
| `assembleRelease`, release key configured | Also `build/outputs/apk/release/OldYM-release.apk`, signed and verified |
| `checkBuildEnvironment` | Checks JDK and required tools |
| `clean` | Deletes `build/` |
| `tasks --all` | Lists all build stages |

Prefix tasks with `./gradlew` on Linux, or `gradlew.bat` on Windows.
`build.sh` and `build.cmd` now accept **Gradle** arguments.
Replace old invocations such as `build.cmd --unsigned` with
`build.cmd assembleUnsigned`.

Gradle skips unchanged build stages. The local debug key is kept under
`GRADLE_USER_HOME/oldym/debug.keystore` (normally `~/.gradle/oldym/debug.keystore`),
outside `build/`, so `clean` does not destroy it. Do not use it for official releases.
Debug here means a local signing identity; `android:debuggable` is preserved
from the source manifest rather than automatically enabled.

## Creating a release signing key

**Keep using your existing key if you already have one.** Updates require the
same signing identity. Store the key outside the repository and back it up
privately along with its password.

Linux:

```sh
mkdir -p "$HOME/.oldym"
"$JAVA_HOME/bin/keytool" -genkeypair -storetype JKS -keystore "$HOME/.oldym/oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

Windows cmd, after creating `D:\Keys` or choosing your own directory:

```bat
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -storetype JKS -keystore "D:\Keys\oldym-release.jks" -alias oldym -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 10000
```

`keytool` prompts for the password and owner details. The example alias is
`oldym`; for an existing keystore use its actual alias. To list aliases:

```sh
keytool -list -keystore /path/to/oldym-release.jks
```

## Release signing

Copy `signing.properties.example` to `signing.properties` in the project root.
Set your own key path, alias and passwords locally:

```properties
oldym.keystore=D:/Keys/oldym-release.jks
oldym.alias=oldym
oldym.storePassword=YOUR_LOCAL_PASSWORD
oldym.keyPassword=YOUR_LOCAL_PASSWORD
```

On Linux use `/home/you/.oldym/oldym-release.jks` instead. On Windows, forward
slashes avoid escaping. This is a Java properties file: literal backslashes
must be escaped as `\\`; do not add quotation marks to passwords unless they
are part of the password. For complex passwords prefer environment variables.

`signing.properties` is ignored by Git but contains plaintext secrets.
Never publish it. Gitignore cannot untrack a file already committed.
On Linux restrict permissions with `chmod 600 signing.properties`.

Alternatively use the same properties in your user `~/.gradle/gradle.properties`,
or these environment variables: `OLDYM_KEYSTORE`, `OLDYM_KEY_ALIAS`,
`OLDYM_KEYSTORE_PASSWORD`, `OLDYM_KEY_PASSWORD`.
The key password defaults to the store password when omitted.
Password values are not placed in command-line arguments or printed by the build.

Build:

```sh
./gradlew assembleRelease
```

```bat
gradlew.bat assembleRelease
```

Updates need the same application ID and signing key, and a suitable
`versionCode` in the source `AndroidManifest.xml`.
V1/JAR signing is enabled; V2 is disabled to retain the previous signing setup.
The minimum API is read from the manifest; verification runs automatically.

## Repository and verification

Commit both wrapper scripts, **`gradle/wrapper/gradle-wrapper.jar`**, its
properties file, `build.gradle`, `settings.gradle`, `gradle.properties`, and the
documentation. Do not ignore all JARs globally: this project needs bundled
build tools and dependencies. `.gradle/`, `build/` and signing keys are ignored.

If necessary, record Linux executable bits in Git:

```sh
git update-index --chmod=+x gradlew build.sh
```

A successful build does not test sign-in, playback or downloads. Test on target
devices. Actual checks for this update are recorded in `GRADLE-TEST-RESULTS.md`.

Using Gradle does not require raising minSdk or changing the application
license. This update does not claim all modern AGP versions are incompatible
with API 5; an AGP migration would require separate implementation and testing.
