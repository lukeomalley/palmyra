"""One measurement method for both the existing Home app and Palmyra."""
import re
import statistics
import subprocess
import time


def measure(serial, component, output):
    package = component.split("/", 1)[0]

    def shell(*args):
        return subprocess.check_output(["adb", "-s", serial, "shell", *args], timeout=30).decode(errors="replace")

    parking = shell("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LEANBACK_LAUNCHER", "-p", "com.android.vending")
    parking_match = re.search(r"^(com\.android\.vending/[\w.$]+)$", parking, re.MULTILINE)
    if not parking_match:
        raise RuntimeError("Cold Home measurement requires the opaque Play Store TV activity to prevent automatic Home restart.")
    cold = []
    for number in range(3):
        shell("am", "start", "-W", "-n", parking_match[1])
        time.sleep(.5)
        shell("am", "force-stop", package)
        running = subprocess.run(["adb", "-s", serial, "shell", "pidof", package], capture_output=True, text=True, timeout=30)
        if running.stdout.strip():
            raise RuntimeError("Home auto-restarted before the cold measurement. No hot sample was accepted.")
        result = shell("am", "start", "-W", "-n", component)
        (output / f"cold-{number + 1}.txt").write_text(result)
        match = re.search(r"TotalTime:\s*(\d+)", result)
        if not match or "Warning: Activity not started" in result or not re.search(r"^Activity: " + re.escape(package) + r"/", result, re.MULTILINE):
            raise RuntimeError(f"Could not measure a fresh launch of {package}: {result}")
        cold.append(int(match[1]))
        time.sleep(2)
    time.sleep(2)
    shell("dumpsys", "gfxinfo", package, "reset")
    time.sleep(10)
    gfx = shell("dumpsys", "gfxinfo", package)
    memory = shell("dumpsys", "meminfo", package)
    pid = shell("pidof", package).strip().split()[0]
    cpu = shell("top", "-b", "-n", "3", "-d", "1", "-p", pid)
    (output / "gfxinfo.txt").write_text(gfx)
    (output / "meminfo.txt").write_text(memory)
    (output / "top.txt").write_text(cpu)
    frames = int(re.search(r"Total frames rendered:\s*(\d+)", gfx)[1])
    pss = re.search(r"TOTAL PSS:\s*(\d+)", memory) or re.search(r"TOTAL\s+(\d+)", memory)
    cpu_values = [float(line.split()[8]) for line in cpu.splitlines() if line.split() and line.split()[0] == pid]
    if not cpu_values or not pss:
        raise RuntimeError("Could not parse CPU or PSS. Read the saved raw output.")
    return dict(serial=serial, component=component, sdk=shell("getprop", "ro.build.version.sdk").strip(), cold_start_ms=cold, cold_start_median_ms=statistics.median(cold), idle_frames_10s=frames, pss_kb=int(pss[1]), idle_cpu_percent=statistics.mean(cpu_values[-2:]))
