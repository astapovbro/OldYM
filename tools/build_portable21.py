"""Build the original RetroMusic project on Windows or Linux, without Gradle."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent.parent
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"


def run(args, cwd=None, log=None):
    print("\n> " + " ".join(str(a) for a in args), flush=True)
    if log:
        with open(log, "w", encoding="utf-8") as stream:
            subprocess.run([str(a) for a in args], cwd=cwd or ROOT,
                           stdout=stream, stderr=subprocess.STDOUT, check=True)
    else:
        subprocess.run([str(a) for a in args], cwd=cwd or ROOT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--create-key", action="store_true")
    parser.add_argument("--unsigned-only", action="store_true",
                        help="Build and validate an aligned unsigned APK without requiring a keystore")
    parser.add_argument("--keystore", default="release-keystore.jks")
    parser.add_argument("--alias", default="oldym")
    parser.add_argument("--password-env", help="Optional environment variable name; never a password")
    args = parser.parse_args()
    os.chdir(ROOT)
    suffix = ".exe" if os.name == "nt" else ""
    home = os.environ.get("JAVA_HOME")
    javac = Path(home) / "bin" / ("javac" + suffix) if home else Path(shutil.which("javac") or "__missing_javac__")
    if not javac.is_file():
        raise RuntimeError("Install JDK 11 and set JAVA_HOME to its folder.")
    java = javac.parent / ("java" + suffix)
    keytool = javac.parent / ("keytool" + suffix)
    result = subprocess.run([str(javac), "-version"], capture_output=True, text=True)
    version = result.stdout + result.stderr
    if not re.search(r"javac (?:11(?:\.|\s|$)|1\.8\.|21(?:\.|\s|$))", version):
        raise RuntimeError("Expected JDK 8, 11, or 21. Found: " + version.strip())
    keystore = Path(args.keystore).resolve()
    if args.create_key:
        if keystore.exists():
            raise RuntimeError("Keystore already exists; refusing to overwrite: " + str(keystore))
        keystore.parent.mkdir(parents=True, exist_ok=True)
        run([keytool, "-genkeypair", "-keystore", keystore, "-storetype", "JKS",
             "-alias", args.alias, "-keyalg", "RSA", "-keysize", "2048",
             "-validity", "10000", "-dname", "CN=OldYM Developer"])
        print("Keep a private backup of the keystore and passwords. Never share them.")
        return
    if not args.unsigned_only and not keystore.is_file():
        raise RuntimeError("Create your signing key first: create-key.cmd")
    manifest = ET.parse(ROOT / "AndroidManifest.xml").getroot()
    minimum = int(manifest.find("uses-sdk").get(ANDROID_NS + "minSdkVersion", "1"))
    version = manifest.get(ANDROID_NS + "versionName", "local")
    version = re.sub(r"[^A-Za-z0-9._-]", "_", version)
    package = manifest.get("package")
    if not package or not re.fullmatch(r"[A-Za-z0-9_.]+", package):
        raise RuntimeError("Invalid manifest package")
    build = ROOT / "build"
    build.mkdir(exist_ok=True)
    for name in ("classes", "generated"):
        shutil.rmtree(build / name, ignore_errors=True)
        (build / name).mkdir()
    tools = ROOT / "tools"
    native = tools / "windows" if os.name == "nt" else tools / "sdk" / "android-9"
    aapt = native / ("aapt" + suffix)
    if not aapt.is_file():
        raise RuntimeError("aapt missing: " + str(aapt))
    android = tools / "api5" / "android-2.0_r01-linux" / "android.jar"
    libs = os.pathsep.join(str(tools / n) for n in ("bctls.jar", "bcprov.jar", "bcutil.jar"))
    run([aapt, "package", "-f", "-m", "-J", build / "generated", "-M",
         "AndroidManifest.xml", "-S", "res", "-A", "assets", "-I", android])
    sources = sorted((ROOT / "src").rglob("*.java")) + sorted((build / "generated").rglob("*.java"))
    # A javac response file avoids the Windows command length limit and handles spaces.
    response = build / "sources.args"
    response.write_text("\n".join('"' + p.as_posix() + '"' for p in sources), encoding="utf-8")
    run([javac, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-bootclasspath", android, "-classpath", libs + os.pathsep + str(android),
         "-d", build / "classes", "@" + str(response)])
    with zipfile.ZipFile(build / "app.jar", "w", zipfile.ZIP_DEFLATED) as archive:
        for path in sorted((build / "classes").rglob("*")):
            if path.is_file():
                archive.write(path, path.relative_to(build / "classes").as_posix())
    run([sys.executable, tools / "relocate.py"])
    run([java, "-jar", tools / "proguard-base.jar", "@proguard.cfg"], log=build / "shrink.log")
    shared = tools / "sdk" / "android-9" / "lib"
    run([java, "-Xmx1500m", "-jar", shared / "dx.jar", "--dex",
         "--min-sdk-version=" + str(minimum), "--output=" + str(build / "classes.dex"),
         build / "shrunk.jar"])
    unsigned = build / "unsigned.apk"
    run([aapt, "package", "-f", "-M", "AndroidManifest.xml", "-S", "res",
         "-A", "assets", "-I", android, "-F", unsigned])
    run([aapt, "add", "unsigned.apk", "classes.dex"], cwd=build)
    aligner = native / ("zipalign" + suffix)
    if aligner.is_file():
        aligned = build / "unsigned-aligned.apk"
        run([aligner, "-f", "4", unsigned, aligned])
        unsigned = aligned
    if args.unsigned_only:
        apk = build / ("OldYM-" + version + "-unsigned.apk")
        shutil.copy2(unsigned, apk)
        run([aapt, "dump", "badging", apk])
        print("\nDONE (unsigned APK; not installable until signed): " + str(apk))
        return
    apk = build / ("OldYM-" + version + ".apk")
    signer = [java, "-jar", shared / "apksigner.jar"]
    signing = signer + ["sign", "--min-sdk-version", str(minimum),
                       "--v1-signing-enabled", "true", "--v2-signing-enabled", "false",
                       "--v1-signer-name", "OLDYM", "--ks", keystore,
                       "--ks-key-alias", args.alias]
    if args.password_env:
        if args.password_env not in os.environ:
            raise RuntimeError("Password environment variable is not set")
        signing += ["--ks-pass", "env:" + args.password_env,
                    "--key-pass", "env:" + args.password_env]
    signing += ["--out", apk, unsigned]
    run(signing)
    run(signer + ["verify", "--min-sdk-version", str(minimum), "--verbose", "--print-certs", apk])
    run([aapt, "dump", "badging", apk])
    print("\nDONE: " + str(apk))


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as exc:
        print("\nBUILD FAILED at the command shown above. Also check build/shrink.log.", file=sys.stderr)
        sys.exit(exc.returncode or 1)
    except Exception as exc:
        print("\nERROR: " + str(exc), file=sys.stderr)
        sys.exit(1)
