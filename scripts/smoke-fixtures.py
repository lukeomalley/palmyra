"""Build permission-free fixture APKs. No SDK or runtime dependencies are bundled."""
import os
import sys
from pathlib import Path
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
sdk = Path(os.environ["ANDROID_HOME"])
java = Path(os.environ["JAVA_HOME"]) / "bin"
tools = sdk / "build-tools/36.0.0"
output = root / "build/smoke-fixtures"
output.mkdir(parents=True, exist_ok=True)
key_properties = root / "signing/keystore.properties"
if key_properties.exists():
    properties = dict(line.split("=", 1) for line in key_properties.read_text().splitlines())
    keystore = root / "signing/release.jks"
else:
    # Public Android debug-key defaults, used only for disposable emulator fixtures.
    properties = {"storePassword": "android", "keyPassword": "android"}
    keystore = next(p for p in [Path.home() / ".android/debug.keystore", Path.home() / ".config/.android/debug.keystore"] if p.exists())
env = os.environ.copy()
env["PALMYRA_STORE_PASS"] = properties["storePassword"]
env["PALMYRA_KEY_PASS"] = properties["keyPassword"]
alias = "palmyra" if key_properties.exists() else "androiddebugkey"
fixtures = [("com.google.android.youtube.tvunplugged", "YouTube TV fixture"), ("org.jellyfin.androidtv", "Jellyfin fixture")]
if "--media" in sys.argv:
    fixtures = [("com.spotify.tv.android", "Spotify media fixture")]
for package, label in fixtures:
    work = output / package
    work.mkdir(exist_ok=True)
    (work / "classes").mkdir(exist_ok=True)
    manifest = f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{package}">
<uses-sdk android:minSdkVersion="28" android:targetSdkVersion="28" />
<application android:label="{label}" android:theme="@android:style/Theme.Material.NoActionBar">
<activity android:name=".Main" android:exported="true"><intent-filter>
<action android:name="android.intent.action.MAIN" /><category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter></activity></application></manifest>'''
    (work / "AndroidManifest.xml").write_text(manifest)
    (work / "Main.java").write_text(f'''package {package};
public final class Main extends android.app.Activity {{
@Override public void onCreate(android.os.Bundle state) {{ super.onCreate(state);
android.widget.TextView text = new android.widget.TextView(this);
text.setText("{label}"); text.setTextSize(40); setContentView(text); }}
}}''')
    if package == "com.spotify.tv.android":
        (work / "Main.java").write_text((root / "scripts/fixtures/Spotify.java").read_text())
    def run(args):
        subprocess.run([str(x) for x in args], check=True, env=env, stdout=subprocess.DEVNULL)
    run([tools / "aapt2", "link", "-I", sdk / "platforms/android-36/android.jar", "--manifest", work / "AndroidManifest.xml", "-o", work / "base.apk"])
    run([java / "javac", "-source", "17", "-target", "17", "-classpath", sdk / "platforms/android-36/android.jar", "-d", work / "classes", work / "Main.java"])
    run([tools / "d8", "--min-api", "28", "--output", work, *list((work / "classes").rglob("*.class"))])
    with zipfile.ZipFile(work / "base.apk", "a") as apk:
        apk.write(work / "classes.dex", "classes.dex")
    run([tools / "zipalign", "-f", "4", work / "base.apk", work / "aligned.apk"])
    run([tools / "apksigner", "sign", "--ks", keystore, "--ks-key-alias", alias, "--ks-pass", "env:PALMYRA_STORE_PASS", "--key-pass", "env:PALMYRA_KEY_PASS", "--out", output / f"{package}.apk", work / "aligned.apk"])
print("Built emulator-only fixture APKs under build/smoke-fixtures")
