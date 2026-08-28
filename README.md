# Quest Settings Patcher (Vector module)
Unhides Passthrough, Home, and Travel Mode. Hides Meta AI section if toggle is disabled.

## Requirements
* Rooted Meta Quest Headset (Pre-August 4th 2026 firmware)
* Magisk w/ Zygisk: Yes
* Lsposed/Vector installed and active in magisk

# How to use
1. If your quest headset isn't rooted and its on a supported firmware for rooting, do so with [Singularity](https://github.com/Lumince/singularity/releases/tag/v1.0.5)
2. Open Magisk Manager and install the latest [Vector](https://github.com/JingMatrix/Vector/releases/tag/v2.2) magisk module
3. Check to make sure that Magisk shows `Zygisk: Yes` in the main page, if it is, reboot your device and go to step 5
4. If `Zygisk: Yes` isn't displayed in Magisk, go into settings and disable, then enable Zygisk. Then open Singularity, go to AIO Tweaks > Utils > Fix Magisk Zygisk > Apply
5. Root your device again, and install SettingsPatcher.apk
6. Open Vector, enable Settings Patcher, and select the 3 apps it prompts you to
7. Run the following commands on first install so it can hook things properly
* `adb shell am force-stop com.oculus.vrshell`
* `adb shell am force-stop com.oculus.systemux`
* `adb shell am force-stop com.oculus.panelapp.settings`


## Supported versions
**This has been tested on v205 and v206 so far. If you have any issues, please post an issue with the versionName of com.oculus.panelapp.settings & com.oculus.vrshell, along with logs from the module**
| versionCode | versionName | Home | Passthrough | Meta AI hide | Travel Mode |
|---|---|---|---|---|---|
| 675101053 | 1044.0.0.x (v207) | yes | yes | yes | yes |
| 674401129 | 1043.0.0.136.429 (v206) | yes | yes | yes | yes |
| 674401131 | 1043.0.0.137.429 (v206) | yes | yes | yes | yes |
| 673301462 | 1042.0.0.76.542 (v205) | yes | yes | yes | yes |
| 672201326 | 1041.0.0.236.431 (v204) | yes | yes | yes | yes |
| 671701119 | 1040.0.0.132.418 (v203) | **no** | yes | yes | yes |
| 671701082 | 1040.0.0.113.418 (v203) | **no** | yes | yes | yes |
| 668901581 | 1036.0.0.243.549 (v85) | **no** | **no** | **no** | yes |
| 668901518 | 1036.0.0.211.549 (v85) | **no** | **no** | **no** | yes |
| 667203067 | 1034.0.0.273.1293 (v83) | **no** | **no** | **no** | yes |
| 667203238 | 1034.0.0.359.1293 (v83) | **no** | **no** | **no** | yes |
| 665903155 | 81.0.0.1061.170 (v81) | **no** | yes | yes | yes |


## Logging

```
adb logcat -s SettingsPatcher:* AndroidRuntime:E
```
