# Modify Positioning (Android)

无 Root 安卓定位修改 App。

## 当前能力

- 地图精细选点控制台（OpenStreetMap）：
  - 输入地址联想搜索（Nominatim）
  - 地图拖动十字准星选点
  - 手动输入经纬度
- 收藏点管理：新增 / 重命名 / 删除 / 一键使用
- 坐标校准开关：关闭 / 中国大陆兼容
- 无需 Google Key，国内可直接使用
- 前台服务持续注入定位，支持开始 / 暂停 / 停止
- 诊断页面：mock 状态、注入状态、校准模式、搜索请求计数

## 运行环境

- Android Studio Iguana+
- JDK 17
- Android 10 (API 29) 及以上

## 构建与安装

```bash
./gradlew assembleDebug
./gradlew installDebug
```

Debug APK：

`app/build/outputs/apk/debug/app-debug.apk`

## 使用步骤（HyperOS/MIUI）

1. 安装并打开 App。
2. 在开发者选项把本 App 设为“模拟位置信息应用”。
3. 授予定位权限（必要时通知权限）。
4. 在地图页搜索或拖动选点，点击开始模拟。
5. 打开第三方 App 验证定位。

## 注意事项

- 无 Root 模式对系统定位读取统一生效。
- 部分应用可能存在反模拟检测，表现与版本/环境有关。
- 建议将本 App 电池策略设为“不限制”并锁定后台。
