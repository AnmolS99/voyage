#!/usr/bin/env python3
"""Seed demo state, tidy the status bar and capture app screens for store screenshots.

Both platforms read the same demo-state.json, so the iOS and Android sets show
the same traveller. Usage (from anywhere):

  store_shots.py seed ios <sim-udid> [--theme light|dark] [--style realistic|natural|stylized]
  store_shots.py seed android <serial> [--theme ...] [--style ...]
  store_shots.py statusbar ios|android <device>
  store_shots.py capture ios|android <device> <out.png>

Seeding force-stops the app and REPLACES its saved state; relaunch it afterwards.
"""
import argparse
import datetime as dt
import json
import plistlib
import subprocess
import sys
import tempfile
from pathlib import Path

BUNDLE_ID = "com.anmol.voyage"  # iOS bundle id and Android applicationId alike
DEMO_STATE = Path(__file__).resolve().parent.parent / "demo-state.json"
WORLD = Path(__file__).resolve().parents[4] / "shared/data/world.geojson"


def run(*cmd, **kw):
    return subprocess.run(cmd, check=True, **kw)


def adb(serial, *args, **kw):
    return run("adb", "-s", serial, *args, **kw)


def load_demo():
    """demo-state.json with its ISO codes turned into country names.

    Both apps key visited and wishlist countries by name; the demo file uses
    codes so it reads easily and survives a renamed country.
    """
    with open(DEMO_STATE) as f:
        demo = json.load(f)
    with open(WORLD) as f:
        names = {feat["id"]: feat["properties"]["name"] for feat in json.load(f)["features"]}
    for key in ("visitedCountries", "wishlistCountries"):
        unknown = [c for c in demo[key] if c not in names]
        if unknown:
            sys.exit(f"{key}: not in world.geojson: {unknown}")
        demo[key] = [names[c] for c in demo[key]]
    return demo


def daily_results(spec):
    """iOS ChallengeStore results for the last `days` days, keyed yyyy-MM-dd."""
    today = dt.date.today()
    failed = set(spec.get("failedDaysAgo", []))
    results = {}
    for ago in range(1, spec["days"] + 1):
        day = (today - dt.timedelta(days=ago)).isoformat()
        solved = ago not in failed
        attempts = 2 if solved else 5
        results[day] = {
            "date": day, "attempts": attempts, "solved": solved, "completed": True,
            "completedOnChallengeDay": True, "guesses": ["?"] * attempts,
        }
    return results


def seed_ios(udid, theme, style):
    demo = load_demo()
    stats = {k: {"gamesPlayed": v["gamesPlayed"], "bestCorrect": v["bestCorrect"],
                 "bestTotal": v["bestTotal"], "bestTime": v["bestTime"]}
             for k, v in demo["challengeStats"].items()}
    prefs = {
        "visitedCountries": demo["visitedCountries"],
        "wishlistCountries": demo["wishlistCountries"],
        "checkedCities": demo["checkedCities"],
        "checkedAttractions": demo["checkedAttractions"],
        "globeStyle": style, "mapStyle": style, "themeMode": theme,
        # Codable blobs, stored as Data exactly as the app writes them.
        "challengeGameStats": json.dumps(stats).encode(),
        "dailyChallengeResults": json.dumps(daily_results(demo["dailyChallenges"])).encode(),
    }
    subprocess.run(["xcrun", "simctl", "terminate", udid, BUNDLE_ID], capture_output=True)
    with tempfile.NamedTemporaryFile(suffix=".plist", delete=False) as f:
        plistlib.dump(prefs, f)
    # A bare domain would land in the simulator's global preferences, which the
    # app never reads: name the app container's plist by path instead. Going
    # through `defaults` (cfprefsd) rather than writing the file keeps the
    # preferences cache honest, so the app sees it on next launch.
    container = run("xcrun", "simctl", "get_app_container", udid, BUNDLE_ID, "data",
                    capture_output=True, text=True).stdout.strip()
    domain = f"{container}/Library/Preferences/{BUNDLE_ID}"
    run("xcrun", "simctl", "spawn", udid, "defaults", "import", domain, f.name)
    print(f"Seeded iOS {udid}: {len(prefs['visitedCountries'])} visited, theme={theme}")


def seed_android(serial, theme, style):
    demo = load_demo()
    doc = {
        "visitedCountries": demo["visitedCountries"],
        "wishlistCountries": demo["wishlistCountries"],
        "checkedCities": demo["checkedCities"],
        "checkedAttractions": demo["checkedAttractions"],
        "viewMode": "globe", "globeStyle": style, "mapStyle": style, "themeMode": theme,
        "challengeStats": {k: {"gamesPlayed": v["gamesPlayed"], "bestCorrect": v["bestCorrect"],
                               "bestTotal": v["bestTotal"], "bestTimeSeconds": v["bestTime"]}
                           for k, v in demo["challengeStats"].items()},
    }
    adb(serial, "shell", "am", "force-stop", BUNDLE_ID)
    # run-as needs a debuggable (debug) build: DataStore lives in app-private storage.
    adb(serial, "shell", "run-as", BUNDLE_ID, "sh", "-c",
        "'mkdir -p files/datastore && cat > files/datastore/voyage_state.json'",
        input=json.dumps(doc).encode())
    print(f"Seeded Android {serial}: {len(doc['visitedCountries'])} visited, theme={theme}")


def statusbar(platform, device):
    """9:41, full battery, full signal, no notifications."""
    if platform == "ios":
        run("xcrun", "simctl", "status_bar", device, "override", "--time", "9:41",
            "--dataNetwork", "wifi", "--wifiMode", "active", "--wifiBars", "3",
            "--cellularMode", "active", "--cellularBars", "4",
            "--batteryState", "charged", "--batteryLevel", "100")
        return
    adb(device, "shell", "settings", "put", "global", "sysui_demo_allowed", "1")
    for extra in (["enter"],
                  ["clock", "-e", "hhmm", "0941"],
                  ["battery", "-e", "level", "100", "-e", "plugged", "false"],
                  ["network", "-e", "wifi", "show", "-e", "level", "4", "-e", "fully", "true"],
                  ["network", "-e", "mobile", "hide"],
                  ["notifications", "-e", "visible", "false"]):
        adb(device, "shell", "am", "broadcast", "-a", "com.android.systemui.demo",
            "-e", "command", *extra, capture_output=True)


def capture(platform, device, out):
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    if platform == "ios":
        run("xcrun", "simctl", "io", device, "screenshot", "--type=png", str(out))
    else:
        with open(out, "wb") as f:
            adb(device, "exec-out", "screencap", "-p", stdout=f)
    print(out)


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("seed")
    s.add_argument("platform", choices=["ios", "android"])
    s.add_argument("device")
    s.add_argument("--theme", default="light", choices=["system", "light", "dark"])
    s.add_argument("--style", default="realistic", choices=["realistic", "natural", "stylized"])
    b = sub.add_parser("statusbar")
    b.add_argument("platform", choices=["ios", "android"])
    b.add_argument("device")
    c = sub.add_parser("capture")
    c.add_argument("platform", choices=["ios", "android"])
    c.add_argument("device")
    c.add_argument("out")
    a = p.parse_args()
    if a.cmd == "seed":
        (seed_ios if a.platform == "ios" else seed_android)(a.device, a.theme, a.style)
    elif a.cmd == "statusbar":
        statusbar(a.platform, a.device)
    else:
        capture(a.platform, a.device, a.out)


if __name__ == "__main__":
    sys.exit(main())
