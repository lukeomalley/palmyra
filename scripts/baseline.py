"""Measure the current Home without changing the chosen launcher."""
import datetime
import json
import os
from pathlib import Path
import re
import subprocess
from metrics import measure

serial = os.environ["ANDROID_SERIAL"]
result = subprocess.check_output(["adb", "-s", serial, "shell", "cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.HOME"], timeout=15).decode()
match = re.search(r"^([\w.]+/[\w.$]+)$", result, re.MULTILINE)
if not match or "ResolverActivity" in match[1]:
    raise RuntimeError(f"No default Home is selected: {result}")
component = match[1]
output = Path(__file__).resolve().parent.parent / "build/baseline" / (datetime.datetime.now().strftime("%Y%m%d-%H%M%S") + "-" + component.split("/")[0])
output.mkdir(parents=True, exist_ok=True)
report = measure(serial, component, output)
(output / "summary.json").write_text(json.dumps(report, indent=2) + "\n")
print(json.dumps(report, indent=2))
print(f"Raw measurements: {output}")
