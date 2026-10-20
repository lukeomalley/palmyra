"""Release smoke checks. Never connects adb; uses only the explicitly selected serial."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from metrics import measure

root = Path(__file__).resolve().parent.parent
output = root / "build/smoke"
output.mkdir(parents=True, exist_ok=True)
serial = os.environ["ANDROID_SERIAL"]
pkg = "xyz.lukeomalley.palmyra"
home = pkg + "/.HomeActivity"


def adb(*args, timeout=30):
    return subprocess.check_output(["adb", "-s", serial, *args], timeout=timeout).decode(errors="replace")


def shell(*args):
    return adb("shell", *args)


def key(value):
    shell("input", "keyevent", value)
    time.sleep(.35)


def resumed():
    text = shell("dumpsys", "activity", "activities")
    lines = [x for x in text.splitlines() if "mResumedActivity" in x or "topResumedActivity" in x]
    return "\n".join(lines)


def wait_activity(component, seconds=15):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        text = resumed()
        if component in text:
            return
        time.sleep(.25)
    raise AssertionError(f"Expected resumed activity {component}, got {text}")


def snapshot(name):
    adb("shell", "uiautomator", "dump", "/sdcard/palmyra-window.xml")
    xml = adb("exec-out", "cat", "/sdcard/palmyra-window.xml")
    (output / f"{name}.xml").write_text(xml)
    png = subprocess.check_output(["adb", "-s", serial, "exec-out", "screencap", "-p"], timeout=15)
    (output / f"{name}.png").write_bytes(png)
    return ET.fromstring(xml)


def description(tree, value):
    descriptions = [n.get("content-desc") for n in tree.iter("node")]
    assert value in descriptions, f"Missing {value}: {descriptions}"


def reset():
    key("KEYCODE_HOME")
    wait_activity(".HomeActivity")
    key("KEYCODE_HOME")


assert adb("get-state").strip() == "device"
assert shell("getprop", "sys.boot_completed").strip() == "1", "Emulator has not finished booting"
if "--fixtures" in sys.argv:
    assert serial.startswith("emulator-"), "Fixtures must never replace real TV applications"
    subprocess.run([str(root / "scripts/build-smoke-fixtures")], check=True)
    for name in ["com.google.android.youtube.tvunplugged", "org.jellyfin.androidtv"]:
        apk = root / "build/smoke-fixtures" / (name + ".apk")
        adb("install", "-r", str(apk))
    shell("settings", "put", "global", "animator_duration_scale", "0.25")

adb("install", "-r", str(root / "app/build/outputs/apk/release/app-release.apk"))
assert "Success" in shell("cmd", "package", "set-home-activity", home)
resolved_home = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME")
if pkg not in resolved_home:
    if serial.startswith("emulator-") and "com.google.android.tvlauncher/.MainActivity" in resolved_home:
        # This system image gives the stock launcher priority 2, above user Home apps.
        shell("pm", "disable-user", "--user", "0", "com.google.android.tvlauncher")
        (output / "emulator-home-fallback.txt").write_text("Disabled emulator-only stock com.google.android.tvlauncher after set-home-activity was ignored. Re-enable with pm enable.\n")
    else:
        raise AssertionError("The OS ignored set-home-activity. No physical-device packages were changed.")
reset()
time.sleep(2)
packages = set(re.findall(r"package:([^\s]+)", shell("pm", "list", "packages")))
catalog = re.findall(r'AppEntry\("([^"]+)", "([^"]+)"', (root / "app/src/main/java/xyz/lukeomalley/palmyra/AppCatalog.kt").read_text())
assert len(catalog) == 11
installed = [(label, package) for label, package in catalog if package in packages]
first = "App: " + installed[0][0] if installed else "Menu: Art Mode"
tree = snapshot("01-home")
description(tree, first)
visible = {n.get("text") for n in tree.iter("node")}
for label, package in catalog:
    assert (label in visible) == (package in packages), f"Incorrect installed filtering: {label}"
print("PASS installed app filtering")

key("KEYCODE_DPAD_DOWN")
selected = min(1, len(installed) - 1)
second = "App: " + installed[selected][0] if installed else "Menu: All apps"
description(snapshot("02-down"), second)
key("KEYCODE_DPAD_RIGHT")
if installed:
    description(snapshot("03-right"), "Menu: Art Mode")
key("KEYCODE_DPAD_LEFT")
description(snapshot("04-left"), second)
print("PASS DPAD navigation")

if installed:
    key("KEYCODE_DPAD_CENTER")
    wait_activity(installed[selected][1])
    snapshot("05-launched-app")
    key("KEYCODE_HOME")
    wait_activity(".HomeActivity")
    description(snapshot("06-restored-focus"), second)
    key("KEYCODE_DPAD_CENTER")
    wait_activity(installed[selected][1])
    shell("am", "force-stop", pkg)
    key("KEYCODE_HOME")
    wait_activity(".HomeActivity")
    description(snapshot("07-restored-after-process-death"), second)
    print("PASS focus restoration after app return and process death")

reset()
description(snapshot("08-home-reset"), first)
key("KEYCODE_BACK")
wait_activity(".HomeActivity")
key("KEYCODE_DPAD_RIGHT")
key("KEYCODE_DPAD_DOWN")
key("KEYCODE_DPAD_DOWN")
key("KEYCODE_DPAD_CENTER")
time.sleep(1)
assert pkg not in resumed(), "Settings did not open"
snapshot("09-settings")
key("KEYCODE_BACK")
wait_activity(".HomeActivity")
print("PASS Settings and Back")

reset()
key("KEYCODE_DPAD_RIGHT")
key("KEYCODE_DPAD_DOWN")
key("KEYCODE_DPAD_CENTER")
wait_activity(".AllAppsActivity")
all_tree = snapshot("10-all-apps")
assert any(n.get("text") for n in all_tree.iter("node")), "All apps is empty"
key("KEYCODE_BACK")
wait_activity(".HomeActivity")
print("PASS All apps and Back")

reset()
key("KEYCODE_DPAD_RIGHT")
key("KEYCODE_DPAD_CENTER")
art = snapshot("11-art")
description(art, "Art Mode")
assert not any(n.get("text") for n in art.iter("node")), "Art Mode still exposes UI"
key("KEYCODE_DPAD_RIGHT")
time.sleep(2)
snapshot("12-next-photo")
assert (output / "11-art.png").read_bytes() != (output / "12-next-photo.png").read_bytes()
key("KEYCODE_DPAD_LEFT")
time.sleep(1)
key("KEYCODE_DPAD_DOWN")
description(snapshot("13-art-exit"), "Menu: Art Mode")
key("KEYCODE_DPAD_CENTER")
key("KEYCODE_HOME")
description(snapshot("14-home-exits-art"), first)
print("PASS Art Mode, photo switching and Home")

report = measure(serial, home, output)
report["installed_favorites"] = installed
(output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
assert report["idle_frames_10s"] <= 1, f"Idle frame budget exceeded: {report}"
assert report["pss_kb"] < 50 * 1024, f"PSS budget exceeded: {report}"
assert report["idle_cpu_percent"] < 1, f"Idle CPU budget exceeded: {report}"
print(json.dumps(report, indent=2))
print("PASS release smoke checks. Emulator timing and PSS do not predict the API 28 TV.")
