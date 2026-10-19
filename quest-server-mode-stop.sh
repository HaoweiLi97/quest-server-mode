#!/system/bin/sh
set -eu
for arg in "$@"; do
    case "$arg" in
        --help|-h) echo 'Usage: sh quest-server-mode-stop.sh'; exit 0;;
        *) echo "Unknown argument: $arg" >&2; exit 2;;
    esac
done
pidfile=/data/local/tmp/quest-server-mode.pid
if [ -f "$pidfile" ]; then
    modepid=$(cat "$pidfile")
    case "$modepid" in ''|*[!0-9]*) exit 1;; esac
    if [ -r "/proc/$modepid/cmdline" ] && tr '\000' ' ' < "/proc/$modepid/cmdline" | grep -q 'DisplayPanel hold'; then
        kill "$modepid"
        sleep 1
    fi
    rm -f "$pidfile"
fi
env CLASSPATH=/data/local/tmp/quest-display-panel.dex app_process / DisplayPanel on
am broadcast -a com.oculus.vrpowermanager.automation_disable
input keyevent KEYCODE_WAKEUP
echo 'Normal display and proximity handling restored.'
