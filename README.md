# Modify Positioning (Android)

无 Root Android 定位修改 App.

## 当前能力

- OSM 默认地图源:
  - 国内外默认都使用 OSM.
  - 高德地图是高级选项, 支持 Android Key 和 Web Key.
  - 未配置高德 Key 时自动使用 OSM.
- 地图精细选点控制台:
  - 地图拖动十字准星选点.
  - 输入地址联想搜索, OSM 模式使用 Nominatim / Photon 自动兜底.
- 高德高级地图:
  - 可选启用高德 Android SDK 地图.
  - 可选填写 Web Key 用于高德 Web API 搜索.
- 修改定位:
  - 使用 `AndroidLocationInjector` + `FusedLocationInjector` 组合注入.
  - 覆盖 GPS / Network / Fused provider.
  - 不承诺绕过第三方 App 的 anti-mock 检测, 位置缓存或服务端校验.
- 收藏点管理: 新增 / 重命名 / 删除 / 一键使用.
- 坐标校准开关: 关闭 / 中国大陆兼容.
- 前台服务持续注入定位, 支持开始 / 暂停 / 停止.
- 诊断页面: mock 状态, 注入状态, 校准模式, 搜索请求计数, 诊断复制.

## 运行环境

- Android Studio Iguana+
- JDK 17
- Android 10 (API 29) 及以上

## 构建与安装

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew assembleRelease
```

Debug APK:

`app/build/outputs/apk/debug/app-debug.apk`

Release APK:

`app/build/outputs/apk/release/app-release-unsigned.apk`

`assembleRelease` 用于 release 变体打包. 产物是否可分发取决于当前签名配置, 未配置正式签名时不应直接作为正式分发包使用.

高德 Key 是可选配置. 不配置时默认使用 OSM.

方式 1, 在 `local.properties` 中配置:

```properties
AMAP_API_KEY=your_android_key
AMAP_WEB_API_KEY=your_web_key
```

方式 2, 通过 Gradle property 配置:

```bash
./gradlew assembleDebug -PAMAP_API_KEY=your_android_key -PAMAP_WEB_API_KEY=your_web_key
```

方式 3, 在 App 高级设置中为本机填写高德 Android Key 和 Web Key.

## 使用步骤

1. 安装并打开 App.
2. 在开发者选项中把本 App 设为 mock location app, 即"模拟位置信息应用".
3. 授予定位权限, 必要时授予通知权限.
4. 默认使用 OSM 地图搜索或拖动十字准星选点.
5. 如需高德, 进入高级设置填写 Android Key / Web Key, 再启用高德地图能力.
6. 回到地图页选择目标位置, 点击开始模拟.
7. 打开诊断页, 刷新状态, 确认 mock app, provider 注入和服务状态正常.
8. 如需排查, 点击复制诊断, 保留当前设备, provider 和注入状态信息.
9. 打开第三方 App 验证定位.

## 第三方 App checklist

- 确认系统开发者选项中已选择本 App 作为 mock location app.
- 确认 App 前台服务正在运行, 且没有被电池策略限制.
- 在诊断页刷新后确认 GPS / Network / Fused 注入状态正常.
- 关闭并重新打开目标 App, 必要时清理目标 App 的位置缓存.
- 对微信, 美团, 高德等国内 App, 需要用真机按目标版本验证.

## 注意事项

- 无 Root 模式依赖 Android mock location 能力, 不是 Root / Xposed / Hook 方案.
- 诊断正常但微信 / 美团 / 高德仍读取真实位置时, 多半是目标 App anti-mock, cache 或 server validation 导致.
- 免 Root 方案不能保证通杀所有第三方 App.
- Samsung 设备和国内 App 的定位表现需要真机验证.
- 建议将本 App 电池策略设为"不限制"并锁定后台.
