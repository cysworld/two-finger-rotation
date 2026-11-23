# TalkBack 接入示例

演示怎样把双指旋转接进 TalkBack：两根手指像拧旋钮一样转，每转一格切换一项阅读控件（TalkBack 里叫"阅读控件"或"选择器"），效果和 TalkBack 原有的"选择下一个/上一个设置"手势一样。

- 补丁基于 [google/talkback](https://github.com/google/talkback) 提交 `229212fd`（TalkBack 16.2）。
- TalkBack 采用 Apache-2.0 许可证。打上补丁后的整体按 GPL-3.0 发布（Apache-2.0 代码可以并入 GPL-3.0 作品），或者另行取得商业授权。

## 目录

| 位置 | 内容 |
|---|---|
| `talkback.patch` | 对 TalkBack 的改动（3 个文件，约 40 行） |
| `src/cysworld/talkback/TalkBackRotation.java` | 接入用的胶水代码 |
| `wrapper/` | 编译 TalkBack 用的 Gradle 8.14.3 包装脚本（TalkBack 仓库本身没有） |

## 改了什么

- `talkback/build.gradle`：把本项目的 `core/src/main/java` 和 `integrations/talkback/src` 加进源码目录。两个仓库要放在同一个父目录下。
- `TouchInteractionMonitor.java`：
  - 每个触摸事件先交给识别器；
  - 两指落下后，识别器判定期间不拖动、不交给应用；
  - 判定为旋转后，整个触摸归 TalkBack，每转一格派发一个手势（编号 -110 顺时针、-111 逆时针）。
- `GestureShortcutMapping.java`：把这两个手势映射到"选择下一个设置"和"选择上一个设置"。

本示例只用中等预设，不用需要定时器的功能（停留、按住连发、先按住再转、扭动），也没有设置界面。完整的设置界面做法见 [docs/integration.md](../../docs/integration.md)。

## 编译

需要 JDK 17 或 21、安卓 SDK 平台 36、Build-Tools 35.0.0 和 NDK 21.4.7075529（TalkBack 的盲文模块要用）。用 JDK 21 时要加上下面命令里的 `-Pkotlin.jvm.target.validation.mode=warning`，因为 TalkBack 的 Java 部分固定编译成 17。

```sh
git clone https://github.com/google/talkback.git
cd talkback
git checkout 229212fd
git apply ../two-finger-rotation/integrations/talkback/talkback.patch
../two-finger-rotation/integrations/talkback/wrapper/gradlew -p . assembleDebug -Pkotlin.jvm.target.validation.mode=warning
```

编出来的手机版在 `build/outputs/apk/phone/debug/talkback-phone-debug.apk`。

应用包名是 `com.android.talkback`，跟谷歌官方版（`com.google.android.marvin.talkback`）不同，可以同时安装。如果手机上已经有一个包名同为 `com.android.talkback` 的应用，签名不同，要先卸载它才能装上。同一时间只能开一个读屏。

## 更新补丁

在 TalkBack 目录运行：

```sh
git diff > ../two-finger-rotation/integrations/talkback/talkback.patch
```
