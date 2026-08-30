# Codex 最终提示词：完善 `AuroraNest/Modify_Positioning`，让免 Root 虚拟定位更真实、更稳定、更像“真的到了某地”

> 这份提示词是给 Codex 直接执行的工程任务说明。  
> 仓库：`AuroraNest/Modify_Positioning`  
> 当前定位：Android Kotlin / Jetpack Compose / 无 Root 虚拟定位 App。  
> 核心目标：用户点击“开始模拟/开始定位”按钮后，系统标准定位链路尽快、稳定、持续地输出目标位置；让遵循 Android 标准定位 API 的地图类 App、社交 App、生活服务 App 尽可能显示目标地点。  
> 重要边界：不要在 main 分支引入 Root / Xposed / Hook / 重打包目标 App 方案。要做的是把当前免 Root mock location 方案做到更真实、更稳定、更可诊断。

---

## 0. 你要扮演的角色

你是一个资深 Android 工程师，熟悉：

- Kotlin
- Jetpack Compose
- Android Foreground Service
- `LocationManager`
- Google Play Services `FusedLocationProviderClient`
- Android mock location / test provider
- Android 10+ 后台限制、前台服务、权限和电池策略
- 可测试的架构设计
- 地图选点、路线模拟、运动轨迹模拟

你的任务不是“随便改几个参数”，而是把这个项目从“能修改定位”升级成一个稳定、真实、有诊断能力的 **Android Location Simulation Lab**。

---

## 1. 当前项目真实代码背景

请先阅读并理解以下文件，不要盲改：

```text
README.md
app/build.gradle.kts
app/src/main/AndroidManifest.xml

app/src/main/java/com/aurora/modifypositioning/service/MockLocationService.kt

app/src/main/java/com/aurora/modifypositioning/location/LocationInjector.kt
app/src/main/java/com/aurora/modifypositioning/location/CompositeLocationInjector.kt
app/src/main/java/com/aurora/modifypositioning/location/AndroidLocationInjector.kt
app/src/main/java/com/aurora/modifypositioning/location/FusedLocationInjector.kt

app/src/main/java/com/aurora/modifypositioning/domain/RandomWalkEngine.kt
app/src/main/java/com/aurora/modifypositioning/domain/RouteSimulationEngine.kt

app/src/main/java/com/aurora/modifypositioning/domain/calibration/MainlandCoordinateCalibrator.kt
app/src/main/java/com/aurora/modifypositioning/util/MockEnvironmentChecker.kt
app/src/main/java/com/aurora/modifypositioning/util/LocationDiagnosticsReader.kt

app/src/main/java/com/aurora/modifypositioning/ui/DiagnosticScreen.kt
app/src/main/java/com/aurora/modifypositioning/data/MapPreferencesStore.kt
```

当前项目已有这些基础能力：

1. README 定位为“无 Root Android 定位修改 App”。
2. 当前修改定位方案是 `AndroidLocationInjector + FusedLocationInjector` 组合注入。
3. `AndroidLocationInjector` 会创建并启用 GPS / Network test provider，然后通过 `LocationManager.setTestProviderLocation(...)` 注入位置。
4. `FusedLocationInjector` 会通过 Google Play Services 的 `FusedLocationProviderClient.setMockMode(true)` 和 `setMockLocation(...)` 注入 Fused 位置。
5. `MockLocationService` 使用前台服务持续注入，并组合两个 injector。
6. 项目已有随机步行和路线模拟基础：`RandomWalkEngine`、`RouteSimulationEngine`。
7. 项目已有诊断页、权限检查、mock app 是否选择检查、坐标校准等基础能力。
8. README 已经写明：无 Root 模式依赖 Android mock location，不是 Root / Xposed / Hook 方案，也不承诺绕过第三方 App anti-mock / cache / server validation。

这些方向是正确的。不要推翻它们。

---

## 2. 最终产品目标

### 2.1 用户视角目标

用户在地图上选一个点，例如：

- Los Angeles
- New York
- Tokyo
- 上海
- 某个商场
- 某个酒店
- 某个景点

然后点击一个按钮：

```text
开始虚拟定位
```

App 应做到：

1. 立即启动前台服务。
2. 检查定位权限、通知权限、mock app 是否选择、定位服务是否开启、Google Play Services 是否可用。
3. 如果缺少必要条件，给出明确、可操作的引导。
4. 如果条件满足，在 1–3 秒内让系统标准定位链路开始输出目标位置。
5. 同时向 GPS provider、Network provider、Fused provider 持续输出协调一致的位置数据。
6. 进入“到达目标地点”的稳定停留状态。
7. 在停留状态下，不要让坐标死死不动，也不要机械绕圈；要像真实手机在目标地点附近轻微漂移。
8. 可选开启“旅行模式”，让位置沿着真实的城市行程移动，而不是瞬移。
9. 诊断页能清楚显示：当前是否真的在持续注入、每个 provider 是否成功、最后注入时间、最后校验距离、失败原因。

### 2.2 工程目标

将项目升级为：

```text
统一模拟层 -> 多 provider 注入层 -> 服务稳定层 -> 诊断层 -> UI 体验层
```

不要让 `AndroidLocationInjector` 和 `FusedLocationInjector` 各自生成不同的 jitter / speed / bearing。  
所有“真实感”都应该来自统一的 simulation 层。

---

## 3. 非目标 / 禁止事项

请严格遵守：

1. 实现第三方 App anti-mock 绕过。
2. 尝试隐藏 `Location.isMock()`。
3. Hook 微信、美团、高德、Google Maps 或任何第三方 App 的内部方法。
4. 不要在 main 分支引入 Root、Magisk、KernelSU、Zygisk、LSPosed、Xposed、LSPatch、VirtualXposed。
7. 不要让服务在用户点击停止后继续运行。
8. 不要收集、上传用户位置或第三方 App 信息。
9. 可以牺牲稳定性去做“反检测”。

Xposed 的思想可以借鉴，但只借鉴：

```text
统一状态源
策略化输出
多通道注入
可插拔架构
诊断聚合
```

不要在 main 分支实现 Hook。

---

## 4. 核心架构改造：统一 `LocationSample`

### 4.1 新增 package

新增：

```text
app/src/main/java/com/aurora/modifypositioning/simulation/
```

在里面新增：

```text
LocationSample.kt
LocationSampleClock.kt
LocationSimulationEngine.kt
StationaryDriftModel.kt
AccuracyModel.kt
MotionProfile.kt
MovementSampleGenerator.kt
ProviderSampleAdapter.kt
SimulationDiagnostics.kt
```

### 4.2 `LocationSample`

新增数据模型：

```kotlin
package com.aurora.modifypositioning.simulation

import com.aurora.modifypositioning.model.MovementMode

data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val accuracyMeters: Float,
    val verticalAccuracyMeters: Float?,
    val speedMps: Float?,
    val speedAccuracyMps: Float?,
    val bearingDegrees: Float?,
    val bearingAccuracyDegrees: Float?,
    val timestampMillis: Long,
    val elapsedRealtimeNanos: Long,
    val movementMode: MovementMode,
    val environment: EnvironmentProfile,
    val sourceLabel: String,
)
```

新增：

```kotlin
enum class EnvironmentProfile {
    OUTDOOR_OPEN,
    URBAN_CANYON,
    INDOOR_MALL,
    AIRPORT,
    HOTEL,
    RESTAURANT,
    TRANSIT_STATION,
    MOVING_VEHICLE,
}
```

新增：

```kotlin
enum class SampleQuality {
    EXCELLENT,
    GOOD,
    DEGRADED,
    INDOOR_WEAK,
}
```

### 4.3 时间必须单调递增

新增 `LocationSampleClock`：

```kotlin
class LocationSampleClock {
    fun nextWallTimeMillis(): Long
    fun nextElapsedRealtimeNanos(): Long
}
```

要求：

1. `timestampMillis` 不允许倒退。
2. `elapsedRealtimeNanos` 不允许倒退。
3. Fused provider 和 Android provider 使用同一个 sample 的时间。
4. 快速 burst 注入时，时间也要单调增长。
5. 单元测试必须覆盖时间单调性。

---

## 5. 真实感模型：不要机械 tick

当前 `AndroidLocationInjector` / `FusedLocationInjector` 里存在类似这种固定周期逻辑：

```kotlin
val accuracy = 4f + ((tick % 3).toFloat())
val speed = 0.2f + ((tick % 5).toFloat() * 0.12f)
val bearing = ((tick * 17L) % 360L).toFloat()
```

这类逻辑要从 injector 中移除。  
injector 只负责注入，不负责“造假数据”。

### 5.1 `AccuracyModel`

新增：

```kotlin
class AccuracyModel {
    fun nextAccuracy(
        environment: EnvironmentProfile,
        movementMode: MovementMode,
        previous: Float?,
    ): AccuracyReading
}

data class AccuracyReading(
    val horizontalMeters: Float,
    val verticalMeters: Float?,
    val speedAccuracyMps: Float?,
    val bearingAccuracyDegrees: Float?,
)
```

建议范围：

```text
OUTDOOR_OPEN:
  horizontal = 3m - 8m
  vertical = 5m - 15m

URBAN_CANYON:
  horizontal = 8m - 35m
  vertical = 10m - 40m

INDOOR_MALL:
  horizontal = 15m - 60m
  vertical = 20m - 80m

AIRPORT:
  horizontal = 15m - 50m

HOTEL:
  horizontal = 15m - 80m

RESTAURANT:
  horizontal = 10m - 45m

TRANSIT_STATION:
  horizontal = 20m - 80m

MOVING_VEHICLE:
  horizontal = 4m - 20m
```

不要每次完全随机跳变。  
使用平滑函数，让 accuracy 缓慢变化：

```text
newAccuracy = previous * 0.75 + randomTarget * 0.25
```

或其他平滑方式。

### 5.2 `StationaryDriftModel`

新增：

```kotlin
class StationaryDriftModel {
    fun next(
        anchorLatitude: Double,
        anchorLongitude: Double,
        environment: EnvironmentProfile,
        previous: LocationSample?,
        nowMillis: Long,
        elapsedRealtimeNanos: Long,
    ): DriftPoint
}

data class DriftPoint(
    val latitude: Double,
    val longitude: Double,
    val speedMps: Float,
    val bearingDegrees: Float?,
)
```

要求：

1. 停留时不能完全不动。
2. 停留时也不能完美圆形绕圈。
3. 漂移应该是缓慢、随机、带惯性的。
4. 不要出现 10 米以上的突然跳变，除非环境是 indoor/urban 且有明确原因。
5. speed 大多数时候接近 0。
6. 偶尔出现 0.1–0.4 m/s 的轻微漂移。
7. 坐标必须围绕 anchor，不能越漂越远。

建议实现：

```text
使用 bounded random walk / Ornstein-Uhlenbeck-like drift
anchor 是吸引中心
previous point 有惯性
每次加入小随机扰动
超过半径时拉回
```

漂移半径建议：

```text
OUTDOOR_OPEN: 1m - 5m
URBAN_CANYON: 3m - 20m
INDOOR_MALL: 5m - 30m
AIRPORT: 5m - 35m
HOTEL: 5m - 25m
RESTAURANT: 3m - 18m
```

### 5.3 `MotionProfile`

新增：

```kotlin
enum class TravelMotionType {
    STATIONARY,
    WALKING,
    DRIVING,
    CYCLING,
    TRANSIT,
}
```

新增：

```kotlin
data class MotionProfile(
    val type: TravelMotionType,
    val minSpeedMps: Double,
    val maxSpeedMps: Double,
    val accelerationMps2: Double,
    val pauseProbability: Double,
    val environment: EnvironmentProfile,
)
```

默认建议：

```text
STATIONARY:
  speed = 0 - 0.4 m/s

WALKING:
  speed = 0.8 - 1.7 m/s
  偶尔停顿

CYCLING:
  speed = 3 - 7 m/s

DRIVING:
  speed = 0 - 22 m/s
  有起步、刹车、红灯、堵车

TRANSIT:
  speed = 0 - 18 m/s
  站点停留 + 区间移动
```

---

## 6. 重构 injector：注入 sample，不再自己生成真实感

### 6.1 目标结构

将注入链路调整为：

```text
MockLocationService
  -> LocationSimulationEngine
      -> LocationSample
  -> CompositeLocationInjector
      -> AndroidLocationInjector.inject(sample)
      -> FusedLocationInjector.inject(sample)
  -> DiagnosticsStore
```

### 6.2 修改 `LocationInjector`

当前接口如果只有 `start(target)` / `updateTarget(target)`，请谨慎重构。  
推荐最终接口：

```kotlin
interface LocationInjector {
    fun start()
    fun inject(sample: LocationSample)
    fun pause()
    fun stop()
    fun cleanup()
    fun status(): InjectorStatus
}
```

如果一次性改动太大，可以采用兼容方案：

```kotlin
interface SampledLocationInjector : LocationInjector {
    fun inject(sample: LocationSample)
}
```

但最终目标是：  
**injector 不再根据 tick 自己生成 jitter / accuracy / speed / bearing。**

### 6.3 `AndroidLocationInjector`

修改目标：

1. 保留 GPS / Network provider rebuild 逻辑。
2. 保留 provider readiness 检查。
3. 保留 recovery burst 思路。
4. 将 `jitterPoint()`、机械 accuracy/speed/bearing 逻辑移出。
5. 新增 `toGpsLocation(sample)` / `toNetworkLocation(sample)`。
6. GPS 与 Network 共享同一个 sample，但 Network accuracy 应更粗。
7. Network provider 不要生成完全不同的坐标，只允许非常小的 provider-level offset。
8. 每次注入后继续执行 last known verification。
9. provider 被移除或 disabled 时自动 rebuild。
10. 失败时输出结构化错误码。

示例设计：

```kotlin
private fun LocationSample.toGpsLocation(): Location {
    return Location(LocationManager.GPS_PROVIDER).apply {
        latitude = this@toGpsLocation.latitude
        longitude = this@toGpsLocation.longitude
        accuracy = this@toGpsLocation.accuracyMeters
        time = this@toGpsLocation.timestampMillis
        elapsedRealtimeNanos = this@toGpsLocation.elapsedRealtimeNanos
        altitude = this@toGpsLocation.altitudeMeters ?: 0.0
        this@toGpsLocation.speedMps?.let { speed = it }
        this@toGpsLocation.bearingDegrees?.let { bearing = it }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = this@toGpsLocation.verticalAccuracyMeters ?: 8f
            speedAccuracyMetersPerSecond = this@toGpsLocation.speedAccuracyMps ?: 0.4f
            bearingAccuracyDegrees = this@toGpsLocation.bearingAccuracyDegrees ?: 8f
        }
    }
}
```

Network location：

```text
same lat/lng or tiny offset
accuracy = max(sample.accuracyMeters * 2.5, 12f)
verticalAccuracy 可更大
speed/bearing 可保留，但 accuracy 更粗
```

### 6.4 `FusedLocationInjector`

修改目标：

1. 保留 `setMockMode(true)`。
2. 保留 `setMockLocation(...)`。
3. 移除内部 jitter/speed/bearing 生成。
4. 只消费 `LocationSample`。
5. 增加明确状态机：
   - `Unavailable`
   - `MockModePending`
   - `MockModeEnabled`
   - `Injecting`
   - `Running`
   - `Failed`
6. `setMockMode(true)` 成功前，不要丢失最新 sample；成功后立即注入 latest sample。
7. `setMockMode(false)` 只在用户停止时调用；pause 时是否关闭 mock mode 需要由产品语义决定。建议：
   - pause movement：保持当前位置继续注入
   - stop service：关闭 mock mode
8. 如果 Fused 失败，不要让整个服务直接失败；进入 partial-running，Android GPS/Network 仍继续。

---

## 7. `CompositeLocationInjector`：从简单广播升级为状态聚合器

当前 `CompositeLocationInjector` 只是逐个调用 injector，失败后拼字符串。  
请升级为：

```kotlin
data class InjectorStatus(
    val id: String,
    val displayName: String,
    val state: InjectorState,
    val lastSuccessAtMillis: Long?,
    val lastFailureAtMillis: Long?,
    val lastErrorCode: InjectorErrorCode?,
    val lastErrorMessage: String?,
    val lastInjectedSample: LocationSample?,
)

enum class InjectorState {
    IDLE,
    STARTING,
    RUNNING,
    PARTIAL,
    DEGRADED,
    FAILED,
    STOPPED,
}

enum class InjectorErrorCode {
    MOCK_APP_NOT_SELECTED,
    MISSING_FINE_LOCATION,
    MISSING_COARSE_LOCATION,
    MISSING_NOTIFICATION_PERMISSION,
    LOCATION_SERVICE_DISABLED,
    GOOGLE_PLAY_SERVICES_UNAVAILABLE,
    FUSED_MOCK_MODE_FAILED,
    FUSED_SET_LOCATION_FAILED,
    TEST_PROVIDER_ADD_FAILED,
    TEST_PROVIDER_ENABLE_FAILED,
    TEST_PROVIDER_SET_LOCATION_FAILED,
    PROVIDER_NOT_READY,
    SERVICE_KILLED,
    BATTERY_RESTRICTED,
    UNKNOWN,
}
```

`CompositeLocationInjector` 应提供：

```kotlin
fun aggregateStatus(): CompositeInjectorStatus
```

其中包含：

```kotlin
data class CompositeInjectorStatus(
    val overallState: InjectorState,
    val activeCount: Int,
    val failedCount: Int,
    val statuses: List<InjectorStatus>,
)
```

规则：

```text
两个 injector 都成功：RUNNING
一个成功一个失败：PARTIAL
全部失败：FAILED
还在启动：STARTING
用户停止：STOPPED
```

---

## 8. `MockLocationService`：一键启动必须更强

当前 `MockLocationService` 已经负责前台服务、组合 injector、权限检查、路线/随机步行循环。  
请把它升级为稳定的一键控制中心。

### 8.1 启动流程

点击“开始虚拟定位”后，服务启动流程必须是：

```text
1. startForeground()
2. 读取目标位置、校准模式、运动模式、旅行模式配置
3. 检查必要权限
4. 检查 mock location app 是否选中
5. 检查系统定位开关是否开启
6. 检查 Google Play Services / Fused client 可用性
7. 创建 LocationSimulationEngine
8. 创建初始 LocationSample
9. 启动 CompositeLocationInjector
10. startup burst：快速注入多次 sample
11. 进入 steady loop：按动态 interval 持续注入
12. 更新 controller 状态和通知
```

### 8.2 startup burst

启动时要更 aggressive，但不能时间倒退。

建议：

```text
前 2 秒：
  每 150ms 注入一次 sample，共 10–14 次

之后：
  固定点停留：1s - 3s 动态间隔
  步行/路线：800ms - 1200ms
  车辆：500ms - 1000ms
```

这个 burst 的目标是让标准定位消费者更快拿到目标位置。

### 8.3 pause / stop 语义

重新定义：

```text
Pause:
  暂停移动，但保持当前位置继续注入
  不要让手机回到真实位置
  不释放所有 provider
  通知显示“已暂停移动，保持当前位置”

Stop:
  停止虚拟定位
  停止所有循环
  injector.stop()
  Fused setMockMode(false)
  remove test providers
  release wakelock
  stopForeground
  stopSelf
```

当前代码里 pause 逻辑对不同 movement mode 有分支，请统一并测试。

### 8.4 服务恢复

增强：

1. `START_REDELIVER_INTENT` 保留。
2. `onTaskRemoved` 恢复保留。
3. `onDestroy` 非显式停止恢复保留，但要避免无限崩溃重启。
4. 增加 crash/restart 计数，短时间多次失败后停止并显示错误。
5. 服务恢复后读取上一次 target / scenario / movement state。
6. provider 被系统移除时自动 rebuild。
7. Fused 失败时，不停止 GPS/Network。
8. 前台通知每次状态变化更新。

---

## 9. UI：让“真实跳转到目标地点”变成清楚的一键体验

### 9.1 主按钮语义

主界面按钮建议改成：

```text
开始虚拟定位
暂停移动
继续移动
停止虚拟定位
```

不要让用户不知道当前是“停留”还是“停止”。

### 9.2 启动前检查卡片

用户点击开始前，如果没设置 mock app，显示：

```text
需要在开发者选项中将 Modify Positioning 设为“模拟位置信息应用”
```

提供跳转设置页 intent：

```kotlin
Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
```

如果缺少通知权限、定位权限、定位开关关闭、电池限制，分别显示。

### 9.3 运行状态卡片

运行中显示：

```text
当前虚拟地点：Los Angeles / 自定义地点名
当前坐标：lat, lng
当前模式：固定点停留 / 随机步行 / 路线模拟 / 旅行剧本
当前环境：Outdoor / Urban / Indoor
当前精度：6.8m
最后注入：0.8 秒前
GPS Provider：运行中
Network Provider：运行中
Fused Provider：运行中 / 失败 / 不可用
Mock App：已选择
服务：前台运行
电池策略：未知 / 可能受限 / 不受限
```

### 9.4 自测按钮

新增一个“本机验证”按钮：

```text
读取当前系统定位
```

它在本 App 内部读取：

```text
LocationManager GPS lastKnown
LocationManager Network lastKnown
FusedLocationProviderClient lastLocation
```

显示：

```text
Provider
lat/lng
accuracy
isMock
time age
distance to target
```

这个功能非常重要：它能告诉用户“系统标准定位链路现在是不是已经到目标地点”。

---

## 10. 旅行剧本模式：让定位像真的到某个城市

新增 package：

```text
app/src/main/java/com/aurora/modifypositioning/travel/
```

新增：

```text
TripScenario.kt
CityPreset.kt
TravelScenarioEngine.kt
TripStop.kt
TripSegment.kt
```

### 10.1 数据模型

```kotlin
data class TripScenario(
    val id: String,
    val title: String,
    val cityName: String,
    val timezoneId: String,
    val stops: List<TripStop>,
)

data class TripStop(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val environment: EnvironmentProfile,
    val stayMinutesMin: Int,
    val stayMinutesMax: Int,
)

data class TripSegment(
    val fromStopId: String,
    val toStopId: String,
    val motionType: TravelMotionType,
    val durationMinutesMin: Int,
    val durationMinutesMax: Int,
)
```

### 10.2 内置城市 preset

新增至少两个 demo preset：

```text
Los Angeles Classic Day
New York Classic Day
```

注意：这些 preset 是“旅行/开发测试模板”，不是用于欺骗平台的说明。README 里要这样写。

Los Angeles 示例停留点：

```text
LAX Airport
Santa Monica Pier
Beverly Hills
The Grove
Griffith Observatory
Hotel Area
```

New York 示例停留点：

```text
JFK Airport
Times Square
Bryant Park
Central Park
Brooklyn Bridge
Hotel Area
```

不要写成简单瞬移。  
要按时间线推进：

```text
机场 -> 酒店 -> 景点 -> 午餐 -> 景点 -> 夜景 -> 酒店
```

### 10.3 旅行模式行为

旅行剧本启动后：

1. 当前 stop：使用 `StationaryDriftModel` 停留。
2. 到移动 segment：使用 `RouteSimulationEngine` 或简单插值路线移动。
3. 移动过程中根据 motion type 输出 speed/bearing。
4. 到下一个 stop 后切换到 dwell。
5. UI 显示：
   - 当前 stop
   - 下一个 stop
   - 已停留多久
   - 预计还有多久出发
   - 当前 motion type
6. 允许用户：
   - 跳到下一站
   - 暂停在当前站
   - 结束旅行

---

## 11. 固定点模式：用户真正想要的“一键到那里”

固定点模式是最核心的。

当用户在地图上选中一个点并点击“开始虚拟定位”：

```text
不要慢慢走过去。
不要路线模拟。
立即进入 ARRIVED_AT_TARGET 状态。
```

但注入行为要真实：

```text
前 2 秒：startup burst 快速稳定到目标点
之后：以该点为 anchor 做自然 drift
```

状态名称建议：

```kotlin
enum class SimulationMode {
    ARRIVED_FIXED_POINT,
    RANDOM_WALK,
    ROUTE_SIMULATION,
    TRAVEL_SCENARIO,
}
```

固定点不是“完全不动”，而是：

```text
像手机真的在该地点附近停留
GPS/Fused 坐标在合理范围内漂移
Network provider 精度更粗
accuracy 随环境自然波动
time/elapsedRealtime 单调递增
```

---

## 12. 地图类 App / 社交 App / 生活服务 App 的现实边界

README 和 UI 文案必须诚实：

```text
本 App 使用 Android 官方 mock location 能力。
当目标 App 使用系统 LocationManager / FusedLocationProvider 获取位置时，通常能读取到模拟位置。
如果目标 App 使用 anti-mock 检测、位置缓存、服务器校验、账号风控、IP/Wi-Fi/基站辅助判断，可能仍显示真实位置或拒绝使用模拟位置。
本项目不提供绕过这些检测的功能。
```

在诊断页里新增：

```text
第三方 App 不生效排查
```

内容包括：

```text
1. 确认本 App 已被选为 mock location app
2. 确认服务正在前台运行
3. 确认 GPS / Network / Fused 至少一个成功，最好全部成功
4. 点击“本机验证”确认系统定位链路已到目标点
5. 关闭并重启目标 App
6. 清理目标 App 的定位缓存
7. 目标 App 如仍不生效，可能是其 anti-mock / cache / server validation 导致
```

不要出现“绕过微信/美团检测”的实现任务。

---

## 13. Manifest / 权限 / 环境检查

当前 Manifest 已有：

```text
ACCESS_FINE_LOCATION
ACCESS_COARSE_LOCATION
INTERNET
ACCESS_NETWORK_STATE
ACCESS_WIFI_STATE
ACCESS_MOCK_LOCATION
WAKE_LOCK
FOREGROUND_SERVICE
FOREGROUND_SERVICE_LOCATION
POST_NOTIFICATIONS
```

请检查是否需要：

```text
ACCESS_BACKGROUND_LOCATION
```

注意：不要默认强加后台定位权限。  
如果当前前台服务 location type 在 targetSdk 35 下需要特殊处理，请以 Android 官方要求为准，做最小必要修改。

增强 `MockEnvironmentChecker`：

```kotlin
fun missingRuntimePermissions(context: Context): List<MissingPermission>
fun isMockLocationAppSelected(context: Context): Boolean
fun isLocationServiceEnabled(context: Context): Boolean
fun isIgnoringBatteryOptimizations(context: Context): Boolean?
fun canStartLocationForegroundService(context: Context): EnvironmentCheckResult
fun googlePlayServicesStatus(context: Context): GooglePlayServicesStatus
```

---

## 14. 诊断体系

新增：

```text
app/src/main/java/com/aurora/modifypositioning/diagnostics/
```

建议：

```text
MockRuntimeDiagnostics.kt
ProviderDiagnostics.kt
EnvironmentDiagnostics.kt
DiagnosticFormatter.kt
```

诊断输出要支持复制：

```text
Modify Positioning Diagnostics
App version:
Android version:
Device:
Target SDK:
Mock app selected:
Location service enabled:
Battery optimization:
Notification permission:
Fine location permission:
Coarse location permission:
Foreground service state:
Current simulation mode:
Current target:
Calibration mode:
Last sample:
GPS injector status:
Network injector status:
Fused injector status:
Last known GPS:
Last known Network:
Fused lastLocation:
Distance to target:
Recent errors:
```

---

## 15. 测试要求

必须新增或更新测试。

### 15.1 单元测试

新增：

```text
app/src/test/java/com/aurora/modifypositioning/simulation/LocationSampleClockTest.kt
app/src/test/java/com/aurora/modifypositioning/simulation/StationaryDriftModelTest.kt
app/src/test/java/com/aurora/modifypositioning/simulation/AccuracyModelTest.kt
app/src/test/java/com/aurora/modifypositioning/simulation/LocationSimulationEngineTest.kt
app/src/test/java/com/aurora/modifypositioning/travel/TravelScenarioEngineTest.kt
app/src/test/java/com/aurora/modifypositioning/location/CompositeLocationInjectorTest.kt
app/src/test/java/com/aurora/modifypositioning/location/FusedLocationInjectorCoreTest.kt
```

测试点：

```text
时间不倒退
elapsedRealtimeNanos 不倒退
固定点漂移不超过半径
accuracy 在环境范围内
accuracy 平滑变化
固定点 speed 大部分接近 0
路线移动 speed/bearing 合理
旅行剧本站点顺序正确
pause 保持当前位置
stop 清理 injector
Fused 失败时 Composite 进入 PARTIAL，而不是全局 FAILED
```

### 15.2 现有测试

保留并更新：

```text
AndroidLocationInjectorTest
```

现有 last known verification、provider profile 测试不要删。

### 15.3 编译验证

最终必须保证：

```bash
./gradlew test
./gradlew assembleDebug
```

都通过。

如果有 Android instrumentation test，也尽量保持通过。

---

## 16. 文件级改造建议

### 16.1 `AndroidLocationInjector.kt`

做：

```text
保留 addTestProvider / setTestProviderEnabled / setTestProviderLocation
保留 verification / recovery
移除内部 jitterPoint 和机械 tick 参数
新增 inject(sample)
新增 provider-specific adapter
新增结构化 status
```

### 16.2 `FusedLocationInjector.kt`

做：

```text
保留 GoogleFusedMockLocationClient
保留 setMockMode / setMockLocation
移除内部 jitterPoint 和机械 tick 参数
新增 latestSample 缓存
新增 mock mode 成功后立即注入 latest sample
新增 status
新增 fake client 单测
```

### 16.3 `CompositeLocationInjector.kt`

做：

```text
从单纯 callEach 升级为状态聚合
partial-running 支持
错误码聚合
对 UI 暴露 diagnostics
```

### 16.4 `MockLocationService.kt`

做：

```text
新增 LocationSimulationEngine
统一 startup burst
统一 steady loop
pause = 保持当前位置
stop = 完全停止
服务恢复读取上次状态
状态变化更新通知
错误不再只是一段字符串
```

### 16.5 `RandomWalkEngine.kt`

做：

```text
继续保留
接入 LocationSample / MotionProfile
避免完全随机方向导致不自然
增加边界回弹的平滑性
```

### 16.6 `RouteSimulationEngine.kt`

做：

```text
继续保留
接入 MotionProfile
speed 更自然
支持停顿/红灯/堵车
bearing 根据路线方向
```

### 16.7 `DiagnosticScreen.kt`

做：

```text
显示 provider 级状态
显示本机验证结果
显示 target distance
显示 last sample age
显示环境检查
显示第三方 App 不生效排查 checklist
```

### 16.8 `README.md`

更新：

```text
新增“一键到达固定点”
新增“真实停留漂移”
新增“旅行剧本模式”
新增“本机验证”
新增“诊断说明”
保留不绕过 anti-mock / cache / server validation 的边界
```

---

## 17. UI 文案建议

使用清晰、真实的文案：

```text
开始虚拟定位
正在虚拟定位
已到达目标地点
暂停移动，保持当前位置
继续移动
停止虚拟定位
本机验证
复制诊断
旅行剧本
固定点停留
随机步行
路线模拟
```

避免：

```text
无敌定位
绕过检测
防封
隐藏 mock
通杀所有 App
```

---

## 18. 验收标准

完成后，项目必须满足：

### 18.1 固定点模式

1. 用户选点后点击开始。
2. 如果 mock app 未选择，UI 明确提示并可跳转设置。
3. 如果权限缺失，UI 明确提示。
4. 条件满足时，1–3 秒内进入 running。
5. 诊断页显示 GPS / Network / Fused 状态。
6. 本机验证显示 last known location 接近目标点。
7. 停留 10 分钟后仍持续注入。
8. 暂停移动后，仍保持当前位置。
9. 点击停止后，停止服务并清理 mock mode/provider。

### 18.2 真实感

1. 固定点不再机械绕圈。
2. accuracy 不再固定周期跳动。
3. speed/bearing 不再机械 tick。
4. GPS / Network / Fused 数据协调一致。
5. Network provider 精度比 GPS/Fused 粗。
6. 时间戳单调递增。
7. 室内/城市/户外环境 profile 不同。

### 18.3 稳定性

1. Fused 失败不导致 GPS/Network 停止。
2. GPS/Network provider 被移除后能 rebuild。
3. 服务被任务划掉后，如用户选择保持运行，应恢复。
4. 非显式 stop 的 onDestroy 不应无脑无限重启。
5. 诊断能说明失败原因。

### 18.4 测试

1. `./gradlew test` 通过。
2. `./gradlew assembleDebug` 通过。
3. 新增 simulation/travel/injector 状态测试。

---

## 19. 建议提交粒度

请按小步提交，不要一次塞完所有变化。建议顺序：

```text
commit 1: add LocationSample, clock, accuracy model, drift model + tests
commit 2: refactor AndroidLocationInjector to consume LocationSample
commit 3: refactor FusedLocationInjector to consume LocationSample
commit 4: add Composite injector status aggregation
commit 5: refactor MockLocationService startup burst / steady loop / pause semantics
commit 6: add diagnostics/self verification UI
commit 7: add travel scenario presets LA/NY
commit 8: update README and tests
```

---

## 20. 最终回答格式

完成后请输出：

```text
Summary:
- 改了什么
- 为什么这样改
- 哪些文件最重要

Tests:
- ./gradlew test
- ./gradlew assembleDebug

Behavior:
- 固定点模式如何工作
- 旅行模式如何工作
- 诊断页如何验证

Limitations:
- 使用官方 mock location
- 不保证绕过第三方 App anti-mock/cache/server validation
- 如果某些 App 不显示目标位置，诊断页会帮助区分系统注入是否成功
```

---

## 21. 一句话目标

把 `Modify_Positioning` 做成：

```text
点击开始后，手机标准定位链路稳定、持续、真实地输出目标地点；
固定点像真的在那里停留；
路线像真的在移动；
诊断能解释为什么某些 App 生效或不生效；
主线保持免 Root、可公开、可维护。
```
