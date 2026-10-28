# Gradle build verification

## Completed on Linux x86-64

- Official Gradle 7.6.4 wrapper JAR verified against the published SHA-256.
- Downloaded Gradle distribution checksum verified.
- JDK 11: clean assembleDebug assembleRelease completed.
- Debug APK V1/JAR signature verified for min API 5.
- Release APK signed with a private local test key and verified. No test key is included.
- Password values absent from captured build logs; signer receives env references.
- Unsigned release works without signing credentials; stale signed release removed.
- Compiled Java classes reused on the next invocation (UP-TO-DATE).
- DEX header 035, no native .so files, application ID ru.oldym.
- Generated manifest component names resolve to classes present in the DEX.
- Source Java, resources, assets and original manifest unchanged.
- Test inputs: latest available OldYM 1.1 project. This update contains no app source.

## Not verified

- Actual Windows execution, including Windows aapt.exe. Windows wrapper and
  OS-specific aapt selection are included, but no Windows runtime is available.
- Installation, sign-in, playback and downloads on Android devices.
- Standard Android Studio/AGP integration (not part of this custom Gradle build).

## Archive re-check

See ARCHIVE-CHECK.txt for the test of the packaged overlay applied to a clean
project directory with spaces in its path.
