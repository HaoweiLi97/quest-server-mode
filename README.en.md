# Quest Server Mode

English | [简体中文](README.md)

Turn off the physical display on a Meta Quest 2 **while keeping the system awake**, so your own server can continue running. Start it through ADB; no root is required.

Ordinary sleep changes Quest scheduling policies, even when an application holds a partial wake lock. This tool powers down the physical panel through SurfaceControl while maintaining a screen wake lock and simulated headset proximity. CPU/GPU frequencies remain under the system's dynamic frequency and thermal management.

## Tested device

- Quest 2, Android 14 / API 34.
- Firmware: `52242990035800150`.
- Tested on October 18, 2026.

This project uses Meta broadcasts and internal Android APIs. Other devices and firmware versions have not been verified. “Performance mode” means avoiding ordinary sleep restrictions; it does not select an official overclocking profile.

## Prerequisites

1. Enable developer mode on the Quest.
2. Install [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) on your computer.
3. Connect the Quest over USB and approve USB debugging inside the headset.
4. Download or clone this repository and open a terminal in its directory. Commands below assume `adb` is on PATH; otherwise use its full path.

```shell
git clone https://github.com/HaoweiLi97/quest-server-mode.git
cd quest-server-mode
adb devices
```

The device should appear as `device`, not `unauthorized`.

## Initial setup

Push **the DEX helper and both scripts**. A shell script alone is not sufficient. A DEX built from the included source is provided; you can also rebuild it yourself.

```shell
adb push quest-display-panel.dex /data/local/tmp/quest-display-panel.dex
adb push quest-server-mode-start.sh /data/local/tmp/quest-server-mode-start.sh
adb push quest-server-mode-stop.sh /data/local/tmp/quest-server-mode-stop.sh
```

The scripts run through `sh`, so `chmod` is unnecessary. Files normally remain after reboot; push them again if they are removed by the system or user.

## Start

Open your server application and start its service inside the headset. Put the headset down, then run this command on your computer:

```shell
adb shell sh /data/local/tmp/quest-server-mode-start.sh
```

Successful startup prints:

```text
SERVER_MODE_READY pid=... physical_panel=off logical_state=awake
```

Starting it again prints `Server mode is already running`. If the headset has already entered ordinary sleep, the script wakes it before turning off the panel.

This tool does not launch your server application. Starting your server and enabling this mode are separate actions.

## What happens if the display wakes or sleeps again?

A small background process runs locally on the Quest and checks its state approximately every five seconds:

- If the physical panel turns on, it turns the panel off again.
- If the power button or system puts Android to sleep, it wakes Android and turns the panel off again.
- Putting the headset back on does not exit this mode. Run the stop command before normal headset use.

A display wake/sleep event alone therefore does not disable the mode. The power button also cannot keep the system asleep while the helper is active. Closing the computer terminal does not stop the local helper.

**After rebooting the Quest or terminating the helper, run the start command again.** There is no automatic startup at boot. Long-term operation on battery after disconnecting USB has not been verified.

## Stop and restore normal use

```shell
adb shell sh /data/local/tmp/quest-server-mode-stop.sh
```

This stops the helper, releases its wake lock, turns on the physical panel, and disables simulated proximity testing. It does not stop your server application.

Run the stop command before normal headset use, normal sleep, or shutdown. Rebooting the Quest also clears the running mode.

## Status and logs

```shell
adb shell cat /data/local/tmp/quest-server-mode.log
adb shell "dumpsys power | grep -E 'mWakefulness=|QuestServer:PanelOffPerformance'"
adb shell "dumpsys SurfaceFlinger | grep powerMode="
adb shell cat /dev/cpuset/foreground/cpus
```

While active, Android should report `mWakefulness=Awake` and hold `QuestServer:PanelOffPerformance`. The primary physical display should report `powerMode=Off`.

The foreground CPU set describes one scheduling group; the application may belong to another group. On the tested device, ordinary sleep reduced the foreground set to `0-3`, while this mode retained `0-6`. In a later foreground application state, the server belonged to `/top-app` with CPUs `4-7` allowed. Do not treat a particular CPU list as a universal success condition.

## GPU and Wi-Fi measurements

On the tested Quest 2, diagnostics ran under the same UID as the server application, with only physical panel state changed. Medians from three trials per state were:

| Measurement | Display on | Panel-off mode |
| --- | ---: | ---: |
| Offscreen GPU synthetic test FPS | 434 | 681 |
| Quest → computer | 360 Mbps | 370 Mbps |
| Computer → Quest | 503 Mbps | 467 Mbps |

The GPU test ran a shader in a 512×512 EGL pbuffer for three seconds per trial. These numbers are not panel refresh rates or application FPS. Each network trial transferred 64 MiB of synthetic data in each direction over the LAN, without ADB forwarding. Wi-Fi suspend policies were identical, and throughput ranges overlapped between states.

These short tests showed no clear panel-off performance restriction, but do not establish identical throughput. They do not cover every transcoding task, Vulkan image enhancement workload, or sustained full-load condition. USB was connected and charging during testing. Temperature, battery state, workload, and environment can still affect performance. Keeping the system awake increases power consumption and heat.

## Build from source

Requires JDK 11 or newer and Android SDK Build-Tools. Verified with JDK 21 and Build-Tools `36.0.0`.

Windows PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

Use a different SDK location or Build-Tools version:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -SdkPath "C:\Android\Sdk" -BuildToolsVersion "36.0.0"
```

`Bypass` applies only to this PowerShell child process and does not change the global execution policy. The build updates `quest-display-panel.dex` in the repository root.

Linux/macOS, with `ANDROID_HOME` configured:

```shell
mkdir -p build
javac --release 8 -d build src/DisplayPanel.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --output build build/DisplayPanel.class
cp build/classes.dex quest-display-panel.dex
```

## Files and implementation

- `quest-server-mode-start.sh`: simulate headset proximity and start the local helper.
- `quest-server-mode-stop.sh`: stop the helper and restore display/proximity handling.
- `src/DisplayPanel.java`: complete source for panel control and the wake lock.
- `quest-display-panel.dex`: compiled helper, ready to push over ADB.
- `build.ps1`: Windows build entry point.

The Android 14 DisplayControl loading approach was informed by [scrcpy's DisplayControl wrapper](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java). This repository contains no scrcpy binaries or third-party root tools.

## License

[MIT](LICENSE).
