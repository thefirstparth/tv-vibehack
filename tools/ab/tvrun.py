#!/usr/bin/env python3
"""tvrun.py <timeout s> <stdin file|-> <tv.py args...>: run tools/tv.py and kill it if the TV stops answering.

Run from the repo root (the A/B scripts cd there). Exit 124 on a timeout.
"""
import subprocess
import sys

t, src, args = float(sys.argv[1]), sys.argv[2], sys.argv[3:]
inp = None if src == "-" else open(src, "rb")
try:
    r = subprocess.run(["tools/venv/bin/python", "-I", "tools/tv.py", *args], stdin=inp,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, timeout=t)
    sys.stdout.buffer.write(r.stdout)
    sys.exit(r.returncode)
except subprocess.TimeoutExpired:
    print("TIMEOUT", args[:1])
    sys.exit(124)
