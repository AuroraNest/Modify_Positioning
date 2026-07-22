# iOS 独立方案

> [!IMPORTANT]
> 本目录引用的 iOS 方案完整来源于 [Yu9191/wloc](https://github.com/Yu9191/wloc), 原作者为 [Yu9191](https://github.com/Yu9191). Modify Positioning 仅使用 Git submodule 引用, 不声明该项目或其代码的原创权与所有权.

## 目录

- [`wloc/`](wloc/): 指向原仓库的 Git submodule, 当前内容及使用方法以原仓库为准.

## 获取方式

克隆本仓库时同时获取 iOS 外部项目:

```bash
git clone --recurse-submodules <本仓库地址>
```

已经克隆本仓库时:

```bash
git submodule update --init --recursive
```

## 能力边界

`wloc` 修改 Apple WLOC 网络定位(Wi-Fi/基站)返回坐标, 依赖支持脚本和 MITM 的代理工具, 不修改 GPS 硬件定位. 安装、兼容性、风险和最新说明均以 [原仓库 README](https://github.com/Yu9191/wloc#readme) 为准.
