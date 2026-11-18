#!/bin/bash
# Monta o APK sem Gradle: aapt + javac + dx + apksigner (pacotes do Ubuntu).
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk/platforms/android-23/android.jar
[ -f "$SDK" ] || SDK=$(ls /usr/lib/android-sdk/platforms/*/android.jar | head -1)
OUT=build; rm -rf $OUT; mkdir -p $OUT/gen $OUT/classes
aapt package -f -m -J $OUT/gen -M src/AndroidManifest.xml -S src/res -I "$SDK" \
  --min-sdk-version 23 --target-sdk-version 34
javac -nowarn -Xlint:-options -source 8 -target 8 -bootclasspath "$SDK" -classpath "$SDK" -d $OUT/classes \
  $(find src/java $OUT/gen -name '*.java')
dalvik-exchange --dex --min-sdk-version=23 --output=$OUT/classes.dex $OUT/classes
aapt package -f -M src/AndroidManifest.xml -S src/res -A src/assets -I "$SDK" \
  --min-sdk-version 23 --target-sdk-version 34 -F $OUT/base.apk
(cd $OUT && aapt add base.apk classes.dex >/dev/null)
zipalign -f -p 4 $OUT/base.apk $OUT/aligned.apk
KS=${KEYSTORE:-registro.keystore}
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -alias registro -keyalg RSA -keysize 2048 \
  -validity 10000 -storepass registro -keypass registro -dname "CN=Registro" >/dev/null 2>&1
apksigner sign --ks "$KS" --ks-pass pass:registro --ks-key-alias registro --out Registro.apk $OUT/aligned.apk
apksigner verify Registro.apk && echo "OK -> $(pwd)/Registro.apk"
