# Quest Settings Patcher (Vector module)
Unhides Home and Travel Mode in Settings. Hides Meta AI section if toggle is disabled. Stops VrShell from re-enabling passthrough via double-tap when it's turned off.

## Requirements
* Rooted Meta Quest Headset (Pre-August 4th 2026 firmware)
* Magisk w/ Zygisk: Yes
* LSPosed/Vector installed and active in Magisk

## How to use (first install)
1. If your Quest headset isn't rooted and it's on a supported firmware for rooting, do so with [Singularity](https://github.com/Lumince/singularity/releases/)
2. Open Magisk Manager and install the latest stable [Vector](https://github.com/JingMatrix/Vector/releases/) Magisk module
3. Check that Magisk shows `Zygisk: Yes` on the main page. If it does, reboot and go to step 5
4. If `Zygisk: Yes` isn't shown, go into Magisk settings and toggle Zygisk off then on. Then open Singularity → AIO Tweaks → Utils → Fix Magisk Zygisk → Apply
5. Root your device again and install `SettingsPatcher.apk`
6. Open Vector, enable Settings Patcher, and select the 3 apps it prompts you to scope
7. Open Settings Patcher, accept magisk root propmt, and press both `kill process` buttons

## App UI
Opening Settings Patcher shows your device build incremental, whether each app is on a supported version, and a **Kill Process** button for Settings and VrShell. The kill buttons use root (`am force-stop`) — Magisk will prompt for root access the first time.

## Supported versions
**Tested on v205, v206, and v207. If you have issues, open an issue with the `versionName` of `com.oculus.panelapp.settings` and `com.oculus.vrshell`, plus logs from the module.**

| versionCode | versionName | Home | Meta AI hide | Travel Mode |
|---|---|---|---|---|
| 675101304 | 1044.0.0.x (v207) | yes | yes | yes |
| 675101053 | 1044.0.0.x (v207) | yes | yes | yes |
| 674401131 | 1043.0.0.137.429 (v206) | yes | yes | yes |
| 674401129 | 1043.0.0.136.429 (v206) | yes | yes | yes |
| 673301462 | 1042.0.0.76.542 (v205) | yes | yes | yes |
| 673301368 | 1042.0.0.x (v205) | yes | yes | yes |
| 672201326 | 1041.0.0.236.431 (v204) | yes | yes | yes |
| 671701119 | 1040.0.0.132.418 (v203) | **no** | yes | yes |
| 671701082 | 1040.0.0.113.418 (v203) | **no** | yes | yes |
| 665903155 | 81.0.0.1061.170 (v81) | **no** | yes | yes |

## Logging

```
adb logcat -s SettingsPatcher:* AndroidRuntime:E
```
