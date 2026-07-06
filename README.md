# Modify Positioning

Modify Positioning 是一个无 Root Android 虚拟定位 App, 使用 Android 官方 mock location 能力把选定坐标持续注入到系统标准定位链路中. 项目目标是做一个真实, 稳定, 可诊断的 Android Location Simulation Lab, 方便开发者和测试人员验证地图选点, GPS provider, Network provider, Fused Location Provider, 前台服务和位置诊断流程.

关键词: Android mock location, fake GPS, virtual location, location simulator, GPS spoofing for development, Jetpack Compose, Kotlin, LocationManager, FusedLocationProviderClient, foreground service, no root location changer, mock GPS app, GPS/Network/Fused provider diagnostics.

## 功能概览

- 无 Root 虚拟定位: 不依赖 Root, Magisk, Xposed, LSPosed, Hook 或重打包目标 App.
- 一键到达固定点: 在地图上选择目标后启动前台服务, 立即进入目标点附近的稳定停留状态.
- 真实停留漂移: 固定点不会死坐标不动, 也不会机械绕圈, 会围绕目标点做平滑小幅漂移.
- 统一模拟层: `LocationSimulationEngine` 生成单一 `LocationSample`, 再同时喂给 GPS, Network 和 Fused 注入层.
- 多 provider 注入: `AndroidLocationInjector` 写入 GPS / Network test provider, `FusedLocationInjector` 写入 Google Play services Fused mock location.
- Startup burst: 启动前 2 秒左右快速注入多次 sample, 让遵循标准 API 的定位消费者尽快读到目标位置.
- Pause 保持当前位置: 暂停只停止移动, 不释放 provider, 继续保持当前位置注入.
- Stop 完全清理: 停止会关闭注入循环, 清理 test provider, 并关闭 Fused mock mode.
- 随机步行和路线模拟: 继续保留现有 `RandomWalkEngine` 和 `RouteSimulationEngine`, 由 service 统一生成最终 sample.
- 旅行剧本模型: 内置 Los Angeles Classic Day 和 New York Classic Day preset, 用于后续旅行/城市测试模板.
- 诊断页: 显示 mock app, 权限, GPS/Network last known, Fused mock mode, 最近注入, provider 重建和第三方 App 排查建议.
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
- 启动后先执行 12 次 startup burst, 每次间隔 150ms.
- 固定点 steady loop 默认 1.5s 注入一次.
- 随机步行 steady loop 默认 900ms 注入一次.
- 路线模拟 steady loop 默认 800ms 注入一次.
- `AndroidLocationInjector` 不再生成 jitter, tick, speed 或 bearing.
- `FusedLocationInjector` 在 mock mode 成功前缓存最新 sample, 成功后立即补注入.
- Fused 失败不会让 GPS/Network 停止, 由 `CompositeLocationInjector` 聚合 partial 状态.

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
2. 打开 Android 开发者选项.
3. 将 Modify Positioning 设为 "模拟位置信息应用".
4. 授予定位权限, Android 13+ 还需要通知权限.
5. 在地图页拖动十字准星或搜索地点.
6. 点击开始虚拟定位.
7. 打开诊断页, 刷新并确认 mock app, GPS/Network/Fused 状态.
8. 打开目标 App 验证定位.

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
- 定位权限和通知权限是否完整.
- GPS 和 Network provider 是否开启.
- 最近注入坐标, 精度, 时间和验证距离.
- GPS last known 和 Network last known 是否接近注入目标.
- Fused mock mode 是否开启, 是否 pending, 最近注入是否成功.
- provider 是否被系统移除并触发 rebuild.
- 第三方 App 不生效时的排查建议.

第三方 App 不生效时, 先按这个顺序排查:

1. 确认 Modify Positioning 已被设为 mock location app.
2. 确认前台服务正在运行.
3. 确认 GPS / Network / Fused 至少一个成功, 最好全部成功.
4. 在诊断页确认系统 last known location 已接近目标点.
5. 强制停止并重开目标 App.
6. 清理目标 App 的位置缓存.
7. 若仍无效, 通常是目标 App anti-mock, cache, server validation, IP/Wi-Fi/基站辅助判断或账号风控导致.

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
