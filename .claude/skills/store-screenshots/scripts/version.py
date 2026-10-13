#!/usr/bin/env python3
"""Read or set the version the store screenshots belong to, on both apps.

  version.py status        # both versions, the features/fixes since iOS's was set, and a verdict
  version.py set 1.12.0    # write it to iOS MARKETING_VERSION and Android versionName

iOS: the app target's MARKETING_VERSION in ios/voyage.xcodeproj/project.pbxproj
(the build configurations whose PRODUCT_BUNDLE_IDENTIFIER is com.anmol.voyage;
the test and generator targets keep their own). Android: versionName in
android/app/build.gradle.kts. versionCode and the build number are CI's.
"""
import datetime as dt
import json
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[4]
PBXPROJ = ROOT / "ios/voyage.xcodeproj/project.pbxproj"
GRADLE = ROOT / "android/app/build.gradle.kts"
BUNDLE_ID = "com.anmol.voyage"
# The version counts as fresh while at most this many user-facing commits
# (features and fixes) have landed since it was set.
MAX_CHANGES_SINCE = 2
# Conventional types that don't change what users get. Anything else counts,
# including subjects without a type (some squash-merged PRs have none).
NON_USER_FACING = ("chore", "docs", "test", "style", "refactor", "ci", "build")

BLOCK = re.compile(r"buildSettings = \{(.*?)\n\t\t\t\};", re.S)
IOS_VERSION = re.compile(r"(MARKETING_VERSION = )([^;]+)(;)")
ANDROID_VERSION = re.compile(r'(versionName = ")([^"]+)(")')


def app_blocks(text):
    """Spans of the app target's buildSettings blocks."""
    return [m.span(1) for m in BLOCK.finditer(text)
            if re.search(rf"PRODUCT_BUNDLE_IDENTIFIER = {re.escape(BUNDLE_ID)};", m.group(1))]


def ios_version(text):
    found = {IOS_VERSION.search(text[a:b]).group(2) for a, b in app_blocks(text)}
    if len(found) != 1:
        sys.exit(f"Expected one app MARKETING_VERSION, found {found or 'none'}")
    return found.pop()


def git(*args):
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True, text=True).stdout.strip()


def last_change(path, needle):
    """(commit, date, subject) of the latest change to a line containing needle.

    An uncommitted edit returns commit None, dated today.
    """
    rel = str(path.relative_to(ROOT))
    if needle in git("diff", "HEAD", "--", rel):
        return None, dt.date.today().isoformat(), "uncommitted"
    out = git("log", "-1", "--format=%H\t%cs\t%s", "-G", re.escape(needle), "--", rel)
    return tuple(out.split("\t", 2)) if out else (None, None, None)


def changes_since(commit):
    """User-facing commit subjects after commit (none if it is uncommitted)."""
    if commit is None:
        return []
    subjects = git("log", f"{commit}..HEAD", "--no-merges", "--format=%s").splitlines()
    return [s for s in subjects if not re.match(rf"({'|'.join(NON_USER_FACING)})(\(.*\))?!?:", s)]


def status():
    ios = ios_version(PBXPROJ.read_text())
    android = ANDROID_VERSION.search(GRADLE.read_text()).group(2)
    ios_commit, ios_date, ios_subject = last_change(PBXPROJ, f"MARKETING_VERSION = {ios};")
    _, android_date, android_subject = last_change(GRADLE, f'versionName = "{android}"')
    since = changes_since(ios_commit)
    print(json.dumps({
        "ios": {"version": ios, "set_in": ios_subject, "date": ios_date},
        "android": {"version": android, "set_in": android_subject, "date": android_date},
        "changes_since_ios_version": since,
        "recent": ios_date is not None and len(since) <= MAX_CHANGES_SINCE,
        "platforms_agree": ios == android,
    }, indent=2))


def set_version(new):
    if not re.fullmatch(r"\d+\.\d+(\.\d+)?", new):
        sys.exit(f"Not a version number: {new}")
    text = PBXPROJ.read_text()
    # Rewrite back to front so earlier spans stay valid.
    for a, b in reversed(app_blocks(text)):
        text = text[:a] + IOS_VERSION.sub(rf"\g<1>{new}\3", text[a:b], count=1) + text[b:]
    PBXPROJ.write_text(text)
    GRADLE.write_text(ANDROID_VERSION.sub(rf"\g<1>{new}\3", GRADLE.read_text(), count=1))
    print(f"iOS MARKETING_VERSION and Android versionName set to {new}")


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "status":
        status()
    elif len(sys.argv) == 3 and sys.argv[1] == "set":
        set_version(sys.argv[2])
    else:
        sys.exit(__doc__)
