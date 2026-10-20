"""Emulator-only cross-app MediaSession checks. No real Spotify account or audio."""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
serial = os.environ.get("ANDROID_SERIAL", "")
assert serial.startswith("emulator-"), "Media fixtures are emulator-only"
output = root / "build/media-smoke"
output.mkdir(parents=True, exist_ok=True)
pkg = "xyz.lukeomalley.palmyra"
spotify = "com.spotify.tv.android"
listener = pkg + "/.MediaAccessService"


def adb(*args):
    return subprocess.check_output(["adb", "-s", serial, *args], timeout=30).decode(errors="replace")


def shell(*args):
    return adb("shell", *args)


def key(code):
    shell("input", "keyevent", "KEYCODE_" + code)
    time.sleep(.3)


def home():
    key("HOME")
    key("HOME")
    time.sleep(.5)


def focus_playback():
    key("DPAD_RIGHT")
    for _ in range(3):
        key("DPAD_DOWN")


def fixture(command):
    shell("am", "start", "-f", "0x20000000", "-n", spotify + "/.Main", "--es", "command", command)
    time.sleep(.4)
    home()


def snapshot(name, present=None, absent=None, description=None):
    shell("uiautomator", "dump", "/sdcard/palmyra-media.xml")
    xml = adb("exec-out", "cat", "/sdcard/palmyra-media.xml")
    (output / (name + ".xml")).write_text(xml)
    tree = ET.fromstring(xml)
    texts = [n.get("text", "") for n in tree.iter("node")]
    descriptions = [n.get("content-desc", "") for n in tree.iter("node")]
    if present:
        assert present in texts, (name, texts)
    if absent:
        assert absent not in texts, (name, texts)
    if description:
        assert description in descriptions, (name, descriptions)
    (output / (name + ".png")).write_bytes(subprocess.check_output(
        ["adb", "-s", serial, "exec-out", "screencap", "-p"], timeout=15))
    return texts


subprocess.run(["python3", str(root / "scripts/smoke-fixtures.py"), "--media"], check=True)
adb("install", "-r", str(root / "app/build/outputs/apk/release/app-release.apk"))
adb("install", "-r", str(root / "build/smoke-fixtures/com.spotify.tv.android.apk"))
try:
    shell("cmd", "notification", "disallow_listener", listener)
    fixture("play")
    snapshot("01-access-denied", absent="Dreams")
    shell("cmd", "notification", "allow_listener", listener)
    time.sleep(1)
    snapshot("02-playing", present="Dreams")
    focus_playback()
    snapshot("03-focused", description="Spotify: Pause")
    key("DPAD_UP")
    snapshot("03a-settings-above", description="Menu: Settings")
    key("DPAD_DOWN")
    key("DPAD_CENTER")
    snapshot("04-paused", present="Dreams", description="Spotify: Play")
    key("DPAD_CENTER")
    snapshot("05-resumed", present="Dreams", description="Spotify: Pause")
    key("DPAD_RIGHT")
    key("DPAD_CENTER")
    snapshot("06-next-track", present="Everywhere", description="Spotify: Next track")
    key("BACK")
    focus_playback()
    key("DPAD_LEFT")
    key("DPAD_CENTER")
    assert spotify in shell("dumpsys", "activity", "activities").split("mResumedActivity")[-1]
    home()
    snapshot("07-returned", present="Everywhere")
    key("DPAD_RIGHT")
    key("DPAD_CENTER")
    snapshot("08-art", absent="Everywhere", description="Art Mode")
    key("BACK")
    snapshot("09-art-exit", present="Everywhere")
    fixture("limited")
    focus_playback()
    key("DPAD_RIGHT")
    snapshot("10-no-controls", description="Spotify: Everywhere, Fleetwood Mac. Open Spotify")
    fixture("play")
    focus_playback()
    shell("cmd", "notification", "disallow_listener", listener)
    time.sleep(1)
    snapshot("11-revoked-live", absent="Everywhere", description="Menu: Settings")
    shell("cmd", "notification", "allow_listener", listener)
    time.sleep(1)
    snapshot("12-reenabled-live", present="Everywhere")
    shell("am", "force-stop", pkg)
    home()
    snapshot("13-home-process-recreated", present="Everywhere")
    fixture("stop")
    snapshot("14-stopped", absent="Everywhere")
    fixture("play")
    focus_playback()
    fixture("destroy")
    snapshot("15-session-destroyed", absent="Everywhere")
    fixture("play")
    snapshot("16-before-producer-kill", present="Everywhere")
    shell("dumpsys", "gfxinfo", pkg, "reset")
    time.sleep(10)
    gfx = shell("dumpsys", "gfxinfo", pkg)
    (output / "idle-gfx.txt").write_text(gfx)
    frames = int(re.search(r"Total frames rendered: (\d+)", gfx).group(1))
    assert frames <= 1, f"Unexpected idle frames: {frames}"
    memory = shell("dumpsys", "meminfo", pkg)
    (output / "meminfo.txt").write_text(memory)
    match = re.search(r"TOTAL PSS:\s+(\d+)", memory)
    pss = int(match.group(1))
    assert pss < 50 * 1024, pss
    shell("am", "force-stop", spotify)
    snapshot("17-producer-killed", absent="Everywhere")
    summary = {"serial": serial, "fixture": "MediaSession only; no Spotify account or audio",
               "checks": 18, "idle_frames_10s": frames, "pss_kb": pss}
    (output / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary))
finally:
    shell("cmd", "notification", "disallow_listener", listener)
    shell("am", "force-stop", spotify)
    adb("uninstall", spotify)
    home()
