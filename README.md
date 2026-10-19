# Quest Server Mode

[English](README.en.md) | 简体中文

让 Meta Quest 2 **关闭物理屏幕，同时保持系统唤醒**，用于运行自己的后台服务器。通过 ADB 启动，无需 root。

普通休眠会改变 Quest 的调度策略，仅持有 CPU 唤醒锁仍可能受限。本工具使用 SurfaceControl 关闭物理面板，同时保留屏幕唤醒锁与虚拟佩戴状态。它不锁定 CPU/GPU 频率，动态调频和温控仍由系统控制。

## 已验证的设备

- Quest 2，Android 14 / API 34。
- 固件：`52242990035800150`。
- 验证日期：2026-10-18。

本项目依赖 Meta 广播和 Android 内部 API，其他机型、系统版本尚未验证。这里的“性能模式”是避免普通休眠引入的限制，并非开启某个官方超频档位。

## 准备

1. 在 Quest 上开启开发者模式。
2. 电脑安装 [Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools)。
3. 用 USB 连接 Quest，在头显内允许 USB 调试。
4. 下载或克隆本仓库，在仓库目录打开终端。以下命令假设 `adb` 已加入 PATH；否则使用它的完整路径。

```shell
git clone https://github.com/HaoweiLi97/quest-server-mode.git
cd quest-server-mode
adb devices
```

`adb devices` 应显示设备状态为 `device`，不是 `unauthorized`。

## 首次安装文件

必须推送 **DEX 辅助程序和两个脚本**，单独复制 `.sh` 不够。仓库已提供由源码构建的 DEX，也可以自行重新构建。

```shell
adb push quest-display-panel.dex /data/local/tmp/quest-display-panel.dex
adb push quest-server-mode-start.sh /data/local/tmp/quest-server-mode-start.sh
adb push quest-server-mode-stop.sh /data/local/tmp/quest-server-mode-stop.sh
```

脚本通过 `sh` 执行，不需要额外 `chmod`。文件通常在重启后仍保留；若被系统或用户清理，重新推送即可。

## 日常启动

先在头显内打开自己的服务器应用并启动服务，再放下头显，通过电脑执行：

```shell
adb shell sh /data/local/tmp/quest-server-mode-start.sh
```

成功时输出：

```text
SERVER_MODE_READY pid=... physical_panel=off logical_state=awake
```

重复启动会提示 `Server mode is already running`。即使头显放下后已经进入普通休眠，脚本也会先唤醒它，再关闭面板。

本工具不启动你的服务器应用；启动服务器与启用黑屏模式是两件事。

## 再次佩戴、亮屏和息屏时会怎样？

脚本会在 Quest 本地留下一个轻量后台进程，每约 5 秒检查一次状态：

- 面板再次亮起时，会再次关闭面板。
- 普通电源键或系统行为让系统休眠时，会重新唤醒系统，再关闭面板。
- 再次佩戴头显不会自动退出此模式；正常使用头显前应先执行停止命令。

因此，模式不会仅因一次亮屏或息屏就失效，但电源键也不能让它持续休眠。电脑终端关闭后，后台进程仍会继续运行。

**Quest 重启或辅助进程被终止后，需要重新运行启动命令。** 本项目不设置开机自启。拔掉 USB 后长时间使用电池的稳定性尚未验证。

## 停止并恢复正常使用

```shell
adb shell sh /data/local/tmp/quest-server-mode-stop.sh
```

停止脚本会终止辅助进程、释放其唤醒锁、打开物理面板，并退出虚拟佩戴测试模式。它不会停止你的服务器应用。

需要正常佩戴、休眠或关机时，先运行停止命令。重启 Quest 也会清除运行中的黑屏模式。

## 查看状态与日志

```shell
adb shell cat /data/local/tmp/quest-server-mode.log
adb shell "dumpsys power | grep -E 'mWakefulness=|QuestServer:PanelOffPerformance'"
adb shell "dumpsys SurfaceFlinger | grep powerMode="
adb shell cat /dev/cpuset/foreground/cpus
```

模式运行时，系统应报告 `mWakefulness=Awake`，并持有 `QuestServer:PanelOffPerformance`；主物理显示面板应报告 `powerMode=Off`。

foreground CPU 集合只是一个调度组的状态，应用实际所在组可能不同。实测普通休眠时 foreground 集合为 `0-3`，黑屏模式为 `0-6`；服务器进程在后续前台状态也曾位于 `/top-app`，允许 CPU `4-7`。不要将固定 CPU 编号当作所有设备的成功条件。

## GPU 与 Wi-Fi 实测

在上述 Quest 2 上，以服务器应用相同的 UID 运行诊断程序，仅切换物理面板，亮屏/黑屏各三轮，得到以下中位数：

| 项目 | 亮屏 | 黑屏模式 |
| --- | ---: | ---: |
| GPU 离屏合成测试 FPS | 434 | 681 |
| Quest → 电脑 | 360 Mbps | 370 Mbps |
| 电脑 → Quest | 503 Mbps | 467 Mbps |

GPU 测试是 512×512 EGL pbuffer 中执行着色器，每轮计时三秒，数值不代表面板刷新率或实际业务 FPS。网络测试每轮双向各传输 64 MiB 合成数据，经局域网连接而非 ADB 转发。两种状态的 Wi-Fi 省电策略相同，各轮吞吐范围有重叠。

短测未观察到明确的黑屏性能限制，但不能保证吞吐完全一致。这些是合成测试，未覆盖所有转码、Vulkan 图像增强或长期满载业务。测试时 USB 连接并充电；温控、电量、负载和环境仍会影响性能。保持系统唤醒会增加耗电和发热。

## 从源码构建

需要 JDK 11 或更新版本，以及 Android SDK Build-Tools。已使用 JDK 21、Build-Tools `36.0.0` 构建验证。

Windows PowerShell：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

自定义 SDK 或 Build-Tools 位置：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -SdkPath "C:\Android\Sdk" -BuildToolsVersion "36.0.0"
```

`Bypass` 只用于本次 PowerShell 子进程，不更改系统全局执行策略。构建会更新根目录的 `quest-display-panel.dex`。

Linux/macOS（已设置 `ANDROID_HOME`）：

```shell
mkdir -p build
javac --release 8 -d build src/DisplayPanel.java
"$ANDROID_HOME/build-tools/36.0.0/d8" --output build build/DisplayPanel.class
cp build/classes.dex quest-display-panel.dex
```

## 文件与实现

- `quest-server-mode-start.sh`：启用虚拟佩戴状态并启动本地辅助进程。
- `quest-server-mode-stop.sh`：停止辅助进程并恢复显示和佩戴检测。
- `src/DisplayPanel.java`：物理面板控制与唤醒锁的完整源码。
- `quest-display-panel.dex`：可直接通过 ADB 推送的编译结果。
- `build.ps1`：Windows 源码构建入口。

Android 14 的 DisplayControl 加载方式参考了 [scrcpy 的 DisplayControl 包装器](https://github.com/Genymobile/scrcpy/blob/master/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)。本仓库不包含 scrcpy 二进制或第三方 root 工具。

## 许可证

[MIT](LICENSE)。
