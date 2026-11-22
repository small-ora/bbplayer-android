#!/usr/bin/env python3
"""Tiny uiautomator helper: dump current UI, find nodes, tap. Used by smoke.sh."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

def adb(*args):
    """Run adb against the first connected device (serial can change between USB reconnects)."""
    out = subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout
    serials = [line.split()[0] for line in out.splitlines()[1:] if line.strip().endswith("device")]
    if not serials:
        raise SystemExit("no adb device connected")
    return ["adb", "-s", serials[0], *args]

def dump():
    subprocess.run(adb("shell", "uiautomator", "dump", "/sdcard/ui.xml"),
                   check=True, capture_output=True)
    xml = subprocess.run(adb("shell", "cat", "/sdcard/ui.xml"),
                         check=True, capture_output=True, text=True).stdout
    return ET.fromstring(xml)

def bounds(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    if not m:
        return None
    x1, y1, x2, y2 = map(int, m.groups())
    return ((x1 + x2) // 2, (y1 + y2) // 2)

def find(node, *, desc=None, text=None, contains=None, cls=None):
    for n in node.iter():
        if desc is not None and n.get("content-desc") != desc:
            continue
        if text is not None and n.get("text") != text:
            continue
        if contains is not None and contains not in (n.get("text") or ""):
            continue
        if cls is not None and n.get("class") != cls:
            continue
        if desc is None and text is None and contains is None and cls is None:
            continue
        b = bounds(n)
        if b:
            return n, b
    return None, None

def tap(xy):
    subprocess.run(adb("shell", "input", "tap", str(xy[0]), str(xy[1])), check=True)

def main():
    cmd = sys.argv[1]
    if cmd == "tap":
        node, b = find(dump(), **{sys.argv[2]: sys.argv[3]})
        if not b:
            print(f"NOT FOUND: {sys.argv[2]}={sys.argv[3]}", file=sys.stderr)
            sys.exit(2)
        print(f"tap {sys.argv[2]}={sys.argv[3]} at {b}")
        tap(b)
    elif cmd == "wait":
        needle = sys.argv[2]
        deadline = time.time() + float(sys.argv[3]) if len(sys.argv) > 3 else time.time() + 15
        while time.time() < deadline:
            if find(dump(), contains=needle)[1]:
                print(f"found: {needle}")
                return
            time.sleep(0.8)
        print(f"TIMEOUT waiting for {needle}", file=sys.stderr)
        sys.exit(3)
    elif cmd == "screenshot":
        subprocess.run(" ".join(adb("exec-out", "screencap", "-p")) + f" > {sys.argv[2]}", shell=True, check=True)
        print("screenshot", sys.argv[2])
    elif cmd == "type":
        subprocess.run(adb("shell", "input", "text", sys.argv[2]), check=True)
    elif cmd == "key":
        subprocess.run(adb("shell", "input", "keyevent", sys.argv[2]), check=True)
    elif cmd == "dump":
        print(ET.tostring(dump(), encoding="unicode")[:4000])
    else:
        print("unknown command", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
