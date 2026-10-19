#!/system/bin/sh
set -eu
for arg in "$@"; do
    case "$arg" in
        --help|-h) echo 'Usage: sh quest-server-mode-start.sh'; exit 0;;
        *) echo "Unknown argument: $arg" >&2; exit 2;;
    esac
done
pidfile=/data/local/tmp/quest-server-mode.pid
if [ -f "$pidfile" ]; then
    oldpid=$(cat "$pidfile")
    case "$oldpid" in ''|*[!0-9]*) exit 1;; esac
    if [ -r "/proc/$oldpid/cmdline" ] && tr '\000' ' ' < "/proc/$oldpid/cmdline" | grep -q 'DisplayPanel hold'; then
        echo "Server mode is already running (pid $oldpid)"
        exit 0
    fi
fi
am broadcast -a com.oculus.vrpowermanager.prox_close --ei duration 0
input keyevent KEYCODE_WAKEUP
nohup env CLASSPATH=/data/local/tmp/quest-display-panel.dex app_process / DisplayPanel hold </dev/null >/data/local/tmp/quest-server-mode.log 2>&1 &
sleep 2
cat /data/local/tmp/quest-server-mode.log
grep -q SERVER_MODE_READY /data/local/tmp/quest-server-mode.log
