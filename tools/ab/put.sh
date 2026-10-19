#!/bin/bash
# put.sh <apk>: install one APK on the TV with pm install -r (never uninstalls). Exit 4 unless it says Success.
source "$(dirname "$0")/common.sh"
apk=$1
tools/venv/bin/python -I tools/b64push.py "$apk" /data/local/tmp/ab.apk </dev/null | grep -o 'md5.*'
out=$(T=180 t "pm install -r /data/local/tmp/ab.apk")
t "rm -f /data/local/tmp/ab.apk" >/dev/null
echo "$out"
case "$out" in *Success*) ;; *) exit 4 ;; esac
