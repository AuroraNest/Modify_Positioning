# Modify Positioning

> [!IMPORTANT]
> **iOS 用户请看这里:** 本项目仅支持 Android. iOS 修改定位可参考 [Yu9191/wloc](https://github.com/Yu9191/wloc). 该方案修改 Apple 网络定位(Wi-Fi/基站)返回坐标, 需要代理模块与 MITM 配置; 不会修改 GPS 硬件定位, 请以其仓库说明为准.

Modify Positioning 是一个无 Root Android 虚拟定位 App, 使用 Android 官方 mock location 能力把选定坐标持续注入到系统标准定位链路中. 项目目标是做一个真实, 稳定, 可诊断的 Android Location Simulation Lab, 方便开发者和测试人员验证地图选点, GPS provider, Network provider, Fused Location Provider, 前台服务和位置诊断流程.

关键词: Android mock location, fake GPS, virtual location, location simulator, GPS spoofing for development, Jetpack Compose, Kotlin, LocationManager, FusedLocationProviderClient, foreground service, no root location changer, mock GPS app, GPS/Network/Fused provider diagnostics.

## 功能概览

- 无 Root 虚拟定位: 不依赖 Root, Magisk, Xposed, LSPosed, Hook 或重打包目标 App.
- 一键到达固定点: 在地图上选择目标后启动前台服务, 立即进入目标点附近的稳定停留状态.
- 真实停留漂移: 固定点不会死坐标不动, 也不会机械绕圈, 会围绕目标点做平滑小幅漂移.
- 统一模拟层: `LocationSimulationEngine` 生成单一 `LocationSample`, 再同时喂给 GPS, Network 和 Fused 注入层.
- 多 provider 注入: `AndroidLocationInjector` 写入 GPS / Network test provider, `FusedLocationInjector` 写入 Google Play services Fused mock location.
- Startup burst: 启动前 2 秒左右快速注入多次 sample, 让遵循标准 API 的定位消费者尽快读到目标位置.
- 第三方 App 兼容测试: 启动后前 60 秒保持高频注入, 面向微信, 美团, 高德等使用系统定位链路的 App 做兼容测试.
- Pause 保持当前位置: 暂停只停止移动, 不释放 provider, 继续保持当前位置注入.
- Stop 完全清理: 停止会关闭注入循环, 清理 test provider, 并关闭 Fused mock mode.
- 随机步行和路线模拟: sample 跟随 domain engine 的目标点, 实际非负速度和连续路径 bearing; 暂停, 边界和终点会归零速度并清除 moving bearing.
- 通道级容错: GPS/Network 或 Fused 单通道失败时继续运行健康通道并显示 partial warning; 全部失败才进入 Error, 健康恢复后清除 warning.
- 旅行剧本模型: 内置 Los Angeles Classic Day 和 New York Classic Day preset, 用于后续旅行/城市测试模板.
- 诊断页: 显示设备厂商/型号, Android/API, App 版本, overall/per-injector 状态, mock app, 权限, last known 和 Fused 状态.
- OSM 默认地图: 默认使用 OSM/Nominatim/Photon, 高德地图和高德 Web Key 是高级可选项.

## 架构

```text
UI / Map selection
  -> MapPreferencesStore / MockController
  -> MockLocationService
      -> LocationSimulationEngine
          -> LocationSample
      -> CompositeLocationInjector
          -> AndroidLocationInjector
              -> LocationManager GPS_PROVIDER
              -> LocationManager NETWORK_PROVIDER
          -> FusedLocationInjector
              -> FusedLocationProviderClient.setMockLocation
      -> diagnostics / notification
```

核心原则:

- 只有 simulation 层负责生成真实感数据, 包括 drift, accuracy, speed, bearing 和单调时间戳.
- injector 只负责 provider 初始化, mock mode, set location, recovery 和状态上报.
- GPS, Network 和 Fused 使用同一个 `LocationSample`, 避免三个通道输出互相打架.
- Network provider 只做很小的 provider-level offset, 精度比 GPS/Fused 更粗.

## 当前实现重点

### 统一 LocationSample

`app/src/main/java/com/aurora/modifypositioning/simulation/` 提供:

- `LocationSample`: 单次模拟定位输出.
- `LocationSampleClock`: 保证 wall time 和 elapsed realtime nanos 单调递增.
- `AccuracyModel`: 根据 `EnvironmentProfile` 平滑生成 horizontal / vertical / speed / bearing accuracy.
- `StationaryDriftModel`: 基于 bounded random walk 生成固定点自然漂移.
- `LocationSimulationEngine`: 持有当前 target 和 movement mode, 输出统一 sample.
- `ProviderSampleAdapter`: 把 sample 转成 GPS / Network provider 的 Android `Location`.

### 注入链路

- `MockLocationService` 是唯一注入循环 owner.
- 启动后先执行 30 次 startup burst, 每次间隔 120ms.
- 第三方 App 兼容窗口前 60 秒默认 300ms 注入一次.
- 固定点 steady loop 默认 400ms 注入一次.
- 随机步行 steady loop 默认 900ms 注入一次.
- 路线模拟 steady loop 默认 800ms 注入一次.
- `AndroidLocationInjector` 不再生成 jitter, tick, speed 或 bearing.
- `FusedLocationInjector` 在 mock mode 成功前缓存最新 sample, 成功后立即补注入.
- Android 侧诊断发现疑似真实定位覆盖时, 会追加短 recovery burst.
- Fused 失败不会让 GPS/Network 停止, `CompositeLocationInjector` 会聚合为 `PARTIAL`; 所有通道失败才是 `FAILED`, 后续周期注入会同步恢复状态.

### 旅行剧本 preset

`app/src/main/java/com/aurora/modifypositioning/travel/` 目前包含最小可编译模型:

- `TripScenario`
- `TripStop`
- `TripSegment`
- `TravelScenarioEngine`
- `CityPreset.losAngelesClassicDay`
- `CityPreset.newYorkClassicDay`

这些 preset 是旅行/开发测试模板, 不是用于绕过平台风控的说明.

## 使用步骤

1. 安装并打开 App.
2. 打开 Android 开发者选项, 将 Modify Positioning 设为 "模拟位置信息应用".
3. 开启系统定位服务.
4. 授予定位权限并允许精确位置.
5. 可选: 开启运行通知; 长时间运行时可在系统电池设置中将本 App 设为不限制.
6. 在地图页拖动十字准星或搜索地点.
7. 点击开始虚拟定位.
8. 打开诊断页, 刷新并确认 mock app, GPS/Network/Fused 状态.
9. 打开目标 App 验证定位.

跨国家或地区测试时, 建议搭配可信 VPN, 并让 VPN 出口地区与目标地点大致一致. 微信等 App 可能同时参考 IP 和网络区域; 两者差异过大时, 可能无法定位到目标地址. VPN 只改变网络出口, 不会修改 Android mock location, 也不能保证目标 App 接受模拟位置.

## 构建

环境:

- Android Studio Iguana+
- JDK 17
- Android minSdk 29
- Android targetSdk 35
- Kotlin + Jetpack Compose

命令:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Release unsigned APK:

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

`app-release-unsigned.apk` 不能直接安装. 从 GitHub Release 安装时, 请下载明确标注为 installable 或 debug signed 的 APK.

## 高德地图可选配置

默认地图源是 OSM. 高德地图是高级选项, 未配置 Key 时 App 会继续使用 OSM.

方式 1, 写入 `local.properties`:

```properties
AMAP_API_KEY=your_android_key
AMAP_WEB_API_KEY=your_web_key
```

方式 2, 使用 Gradle property:

```bash
./gradlew assembleDebug -PAMAP_API_KEY=your_android_key -PAMAP_WEB_API_KEY=your_web_key
```

方式 3, 在 App 高级设置中为本机填写高德 Android Key 和 Web Key.

## 诊断

诊断页用于判断系统标准定位链路是否已经接近目标点:

- Mock app 是否已选择.
- 精确定位权限是否完整; 通知权限仅作为运行状态提示, 不阻断定位.
- GPS 和 Network provider 是否开启.
- 最近注入坐标, 精度, 时间和验证距离.
- GPS last known 和 Network last known 是否接近注入目标.
- Fused mock mode 是否开启, 是否 pending, 最近注入是否成功.
- provider 是否被系统移除并触发 rebuild.
- overall/per-injector 状态, partial warning, 设备与 App 版本信息.
- 第三方 App 不生效时的排查建议.

诊断正常只说明本机标准 Android 定位链路工作正常. 未经对应设备和目标 App 的实际验证, 不代表任何第三方 App 已采用或接受这些 sample.

第三方 App 不生效时, 先按这个顺序排查:

1. 确认 Modify Positioning 已被设为 mock location app.
2. 确认前台服务正在运行.
3. 确认 GPS / Network / Fused 至少一个成功, 最好全部成功.
4. 在诊断页确认系统 last known location 已接近目标点.
5. 强制停止并重开目标 App.
6. 清理目标 App 的位置缓存.
7. 若仍无效, 通常是目标 App anti-mock, cache, server validation, IP/Wi-Fi/基站辅助判断或账号风控导致.

### 高德地图为什么可能要开一会儿才稳定

高德地图不一定会立刻使用系统最新 mock 点. 它可能先读取自己的 last location cache, 再结合 GPS, Network, Fused, Wi-Fi, 基站, IP, 传感器和高德定位 SDK 做融合与平滑. 当目标点距离真实位置很远时, 高德还可能先观察一段连续定位, 避免瞬移造成体验异常.

因此启动虚拟定位后, 高德可能需要持续注入一小段时间才会稳定显示目标地点. 建议流程:

1. 先在 Modify Positioning 里开始虚拟定位.
2. 等待 5-15 秒, 或打开诊断页确认 GPS / Network / Fused 最近位置接近目标点.
3. 强制停止并重新打开高德地图, 或在高德内手动刷新定位.
4. 若仍反复回真实位置, 优先检查 mock app 设置, 权限, 电池限制和高德缓存.

这不是绕过高德检测的能力, 而是 Android mock location 与第三方定位缓存/融合策略之间的正常延迟.

### 微信, 美团等 App 兼容测试

本次真机验证中, 微信和美团均已实际请求系统标准 GPS / Network 定位通道, 可以配合本 App 进行虚拟定位测试. 不同机型, 系统版本和目标 App 版本的缓存与融合策略不同, 建议使用这个流程:

1. 先在系统开发者选项中确认 Modify Positioning 是 mock location app.
2. 在 Modify Positioning 里选择目标地点并开始虚拟定位.
3. 等待 10-30 秒, 让兼容窗口持续推送 GPS / Network / Fused sample.
4. 在系统应用信息中清除微信或美团的应用缓存, 不需要清除账号数据.
5. 从最近任务中彻底关闭目标 App, 再重新打开并触发定位.
6. 跨国家或地区测试时, 可让可信 VPN 的出口地区与目标地点大致一致后再重试.
7. 如果诊断页显示系统定位已接近目标, 但目标 App 仍回真实位置, 通常是目标 App 自身 cache, server validation, Wi-Fi/IP/基站辅助判断或 anti-mock 策略导致.

这表示本 App 已在本次测试设备上支持微信, 美团的标准定位链路兼容测试, 不表示所有设备都能得到相同结果, 也不表示可以绕过目标 App 的 mock 检测或风控.

## 现实边界

本 App 使用 Android 官方 mock location 能力. 当目标 App 使用 `LocationManager` 或 `FusedLocationProviderClient` 获取位置时, 通常可以读取到模拟位置. 如果目标 App 使用 anti-mock 检测, 位置缓存, 服务端校验, 账号风控, IP, Wi-Fi, 蓝牙, 基站或传感器辅助判断, 可能仍显示真实位置或拒绝使用模拟位置.

本项目不会实现:

- 隐藏 `Location.isMock()`.
- 绕过微信, 美团, 高德或任何第三方 App 的检测.
- Root / Magisk / KernelSU / Zygisk / LSPosed / Xposed / LSPatch / VirtualXposed.
- Hook 第三方 App 内部方法.
- 上传用户位置或第三方 App 信息.

## 目录速览

```text
app/src/main/java/com/aurora/modifypositioning/service/MockLocationService.kt
app/src/main/java/com/aurora/modifypositioning/location/
app/src/main/java/com/aurora/modifypositioning/simulation/
app/src/main/java/com/aurora/modifypositioning/travel/
app/src/main/java/com/aurora/modifypositioning/domain/
app/src/main/java/com/aurora/modifypositioning/ui/
app/src/test/java/com/aurora/modifypositioning/simulation/
app/src/test/java/com/aurora/modifypositioning/travel/
```

## 验证状态

本仓库的核心检查:

```bash
./gradlew test
./gradlew assembleDebug
```

Debug APK 构建成功后位于:

```text
app/build/outputs/apk/debug/app-debug.apk
```

仓库内同步的测试 APK 位于:

```text
release/Modify_Positioning-debug.apk
```

---

# Modify Positioning English README

Modify Positioning is a no-root Android virtual location app. It uses Android's official mock location APIs to continuously inject a selected coordinate into standard system location channels. The project is intended as an Android Location Simulation Lab for development and testing, with predictable behavior, realistic stationary drift, foreground service stability and diagnostics.

Keywords: Android mock location, fake GPS, virtual location, location simulator, GPS spoofing for development, Jetpack Compose, Kotlin, LocationManager, FusedLocationProviderClient, foreground service, no-root location changer, mock GPS app, GPS Network Fused provider diagnostics.

## Features

- No root required: no Magisk, Xposed, LSPosed, app hooking or target app repackaging.
- One-tap fixed point simulation: select a place on the map and start a foreground service.
- Realistic stationary drift: the coordinate stays near the target but does not remain perfectly frozen or move in a mechanical circle.
- Unified simulation layer: `LocationSimulationEngine` generates one `LocationSample` shared by GPS, Network and Fused injection.
- Multi-provider injection: `AndroidLocationInjector` writes GPS / Network test providers, while `FusedLocationInjector` writes Google Play services Fused mock location.
- Startup burst: the service injects several samples quickly after startup so standard location consumers can pick up the target faster.
- Third-party app compatibility testing: the first 60 seconds use higher frequency injection for apps such as WeChat, Meituan and Amap when they consume standard Android location APIs.
- Pause keeps location: pause stops movement but continues injecting the current point.
- Stop cleans up: stop ends the injection loop, removes test providers and disables Fused mock mode.
- Moving samples follow domain-engine targets with their actual non-negative speed and consecutive-path bearing. Pause, boundary and destination states clear moving speed and bearing.
- Provider health is channel-aware: one failed channel keeps healthy channels running with a partial warning; all failed channels enter Error; recovery clears the warning.
- Travel scenario model includes Los Angeles Classic Day and New York Classic Day presets for future city-trip testing.
- Diagnostics show device manufacturer/model, Android/API, app version, overall/per-injector health, permissions, last known locations and Fused state.

## Architecture

```text
UI / Map selection
  -> MapPreferencesStore / MockController
  -> MockLocationService
      -> LocationSimulationEngine
          -> LocationSample
      -> CompositeLocationInjector
          -> AndroidLocationInjector
              -> LocationManager GPS_PROVIDER
              -> LocationManager NETWORK_PROVIDER
          -> FusedLocationInjector
              -> FusedLocationProviderClient.setMockLocation
      -> diagnostics / notification
```

Only the simulation layer creates realistic movement data: drift, accuracy, speed, bearing and monotonic timestamps. Injectors only initialize providers, enable mock mode, set locations, recover providers and report status. GPS, Network and Fused use the same sample, so they do not fight each other with unrelated coordinates.

## Usage

1. Install and open the app.
2. Enable Android Developer Options.
3. Select Modify Positioning as the mock location app.
4. Grant location permission. Android 13+ also needs notification permission.
5. Search for a place or drag the map crosshair.
6. Tap start virtual location.
7. Open diagnostics and confirm mock app, GPS, Network and Fused status.
8. Open the target app and verify location behavior.

## Why Amap May Need A Short Warm-Up

Amap may not immediately use the newest mock point. It can read its own last location cache first, then fuse GPS, Network, Fused, Wi-Fi, cell towers, IP, sensors and Amap's own location SDK. If the target is far away from the real device location, Amap may also smooth or delay a large jump.

Recommended flow:

1. Start virtual location in Modify Positioning.
2. Wait 5-15 seconds, or use diagnostics to confirm GPS / Network / Fused are near the target.
3. Force stop and reopen Amap, or refresh location inside Amap.
4. If it keeps returning to the real location, check mock app selection, permissions, battery restrictions and Amap cache.

This is not an anti-detection bypass. It is normal latency between Android mock location and third-party app cache/fusion logic.

## WeChat, Meituan And Other App Compatibility Testing

Modify Positioning tries to make apps that consume standard Android `LocationManager` / `FusedLocationProviderClient` data read the virtual point. For WeChat, Meituan, Amap and similar apps, use this flow:

1. Make sure Modify Positioning is selected as the mock location app in Developer Options.
2. Select a target place and start virtual location.
3. Wait 10-30 seconds so the compatibility warm-up window can keep pushing GPS / Network / Fused samples.
4. Force stop and reopen WeChat, Meituan or Amap, then trigger location again.
5. If diagnostics show the system location is near the target but the target app still returns to the real place, the cause is usually app cache, server validation, Wi-Fi/IP/cell-tower fusion or anti-mock logic.

This means the project supports compatibility testing for WeChat, Meituan and similar apps through the standard Android location path. It does not guarantee bypassing their mock detection or risk controls.

## Build

Requirements:

- Android Studio Iguana+
- JDK 17
- minSdk 29
- targetSdk 35
- Kotlin + Jetpack Compose

Commands:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

Debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Repository test APK:

```text
release/Modify_Positioning-debug.apk
```

## Limits

This app uses Android's official mock location capability. Apps that use `LocationManager` or `FusedLocationProviderClient` usually can read the simulated location. Apps with anti-mock checks, local caches, server-side validation, account risk control, IP/Wi-Fi/cell-tower checks or sensor fusion may still show the real location or reject mock data.

A healthy diagnostic report confirms only the standard Android location path on the tested device. It is not evidence that any specific third-party app has adopted or accepted the samples without direct device testing.

This project does not implement:

- Hiding `Location.isMock()`.
- Bypassing WeChat, Amap, Meituan or any third-party app checks.
- Root, Magisk, KernelSU, Zygisk, LSPosed, Xposed, LSPatch or VirtualXposed.
- Hooking third-party app internals.
- Uploading user location or third-party app information.
