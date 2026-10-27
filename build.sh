#!/bin/sh
set -eu
cd "$(dirname "$0")"
if [ -z "${JAVA_HOME:-}" ]; then JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")"); fi
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
command -v javac >/dev/null || { echo "Install JDK 11 and set JAVA_HOME"; exit 1; }
APKVER=$(grep -o 'versionName="[^"]*"' AndroidManifest.xml | head -1 | sed 's/.*="//;s/-alpha"//;s/"//')
APKNAME=build/OldYM-$APKVER.apk
TOOLS=tools/sdk/android-9
ANDROID=tools/api5/android-2.0_r01-linux/android.jar
LIBS=tools/bctls.jar:tools/bcprov.jar:tools/bcutil.jar
rm -rf build/classes build/generated
mkdir -p build/classes build/generated
"$TOOLS/aapt" package -f -m -J build/generated -M AndroidManifest.xml -S res -A assets -I "$ANDROID"
javac -encoding UTF-8 -source 7 -target 7 -bootclasspath "$ANDROID" -classpath "$LIBS:$ANDROID" -d build/classes src/ru/oldym/*.java build/generated/ru/oldym/R.java
jar cf build/app.jar -C build/classes .
python3 tools/relocate.py
java -jar tools/proguard-base.jar @proguard.cfg > build/shrink.log 2>&1
java -Xmx1800m -jar "$TOOLS/lib/dx.jar" --dex --min-sdk-version=5 --output=build/classes.dex build/shrunk.jar
"$TOOLS/aapt" package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID" -F build/unsigned.apk
(cd build && ../"$TOOLS/aapt" add unsigned.apk classes.dex)
if [ ! -f build/test-signing.p12 ]; then
 keytool -genkeypair -keystore build/test-signing.p12 -storetype PKCS12 -storepass retromusic -keypass retromusic -alias retro -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=Retro Music Test Build'
fi
java -jar "$TOOLS/lib/apksigner.jar" sign --min-sdk-version 5 --v1-signing-enabled true --v2-signing-enabled false --v1-signer-name RETRO --ks build/test-signing.p12 --ks-type PKCS12 --ks-pass pass:retromusic --key-pass pass:retromusic --out $APKNAME build/unsigned.apk
java -jar "$TOOLS/lib/apksigner.jar" verify --min-sdk-version 5 --verbose $APKNAME
"$TOOLS/aapt" dump badging $APKNAME | head -8
