# 双指旋转 · two-finger rotation

给安卓读屏用的双指旋转手势识别引擎：两根手指像拧旋钮一样转动，引擎报告转了几格、往哪个方向。纯 Java 编写，不依赖安卓，也不依赖任何读屏，可以直接接入现有读屏。

[English below](#english)

## 能识别什么

- 两指一起转，或者一指不动、另一指绕着转，两种转法可以分别开关。
- 起转要同时满足角度和持续时间，能区分捏合缩放和双指滚动，不会把它们当成旋转。
- 每转一格报告一次，带方向和累计格数；也可以报告连续角度。
- 可选功能：先按住再转、停留、按住连发、加速、快速扭动。
- 快速、中等、慢速三套预设，所有参数都可以单独调整。
- 按地区停用的工具（默认没有任何规则）。
- 轨迹录制和回放：把真机上的触摸记下来，在电脑上重放、调参数、写测试。

引擎只报告手势本身，手势用来做什么由读屏厂商决定。参考做法是：每转一格，切换一项阅读控件（按字、按词、按行、按标题等）。

## 要求

- 安卓 13（API 33）及以上。
- 读屏要通过 `TouchInteractionController` 自己识别手势。

## 目录

| 位置 | 内容 |
|---|---|
| `core/` | 识别引擎和单元测试，其中包括真机录下的轨迹回放测试 |
| `docs/design.md` | 设计规格：交互行为、事件、参数和预设（中文） |
| `docs/integration.md` | 接入指南（中文） |
| `integrations/talkback/` | TalkBack 接入示例，已在真机上测试 |

## 编译和测试

需要 JDK 17 或更高版本。引擎本身编译成 Java 8 字节码，任何读屏都能直接使用。

```sh
./gradlew :core:test
```

## 授权

本项目采用双重授权，任选其一：

- **开源授权**：GPL-3.0-only，并附加 GPL-3.0 第 7 条允许的几项附加条款；
- **商业授权**：用于不想公开源代码的产品。按产品收费，每款产品参考价 1024 元人民币（境外参考价 154 美元），一次付清，具体价格可以商量；请联系 chengying@cys.world。

详见 [LICENSING.md](LICENSING.md)。在产品里使用之前，请先阅读其中的法律提示：你需要自行确认在你的国家和销售地区可以合法使用，包括专利。

## 反馈

欢迎在 Issues 里反馈问题和建议。本项目不接受外部代码合并请求，原因见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

<a id="english"></a>

## English

A two-finger rotation gesture recognizer for Android screen readers. Turn two fingers on the screen like a knob; the engine reports how many detents were turned and in which direction. It is plain Java with no dependency on Android or on any screen reader, so existing screen readers can integrate it directly.

### What it recognizes

- Both fingers turning, or one finger still while the other circles it; each style can be switched on or off.
- Starting requires both an angle and a duration. Pinch-zoom and two-finger scrolling are told apart and are not treated as rotation.
- One event per detent, with direction and running total; a continuous angle can also be reported.
- Optional: hold-then-turn, dwell, hold-to-repeat, acceleration and quick twists.
- Fast, medium and slow presets; every parameter can be tuned.
- A tool to switch the feature off by region (no rules by default).
- Trace recording and replay: record touches on a phone, then replay them on a computer to tune parameters and write tests.

The engine reports only the gesture; what it does is up to the screen reader vendor. The reference behavior: each detent switches to the next reading control (character, word, line, heading and so on).

### Requirements

- Android 13 (API 33) or later.
- The screen reader must recognize gestures itself through `TouchInteractionController`.

### Layout

| Path | Contents |
|---|---|
| `core/` | The recognizer and its unit tests, including a replay of a trace recorded on a real phone |
| `docs/design.md` | Design specification: behavior, events, parameters and presets (Chinese) |
| `docs/integration.md` | Integration guide (Chinese) |
| `integrations/talkback/` | TalkBack integration example, tested on a real phone |

### Build and test

Requires JDK 17 or later. The engine itself compiles to Java 8 bytecode, so any screen reader can use it.

```sh
./gradlew :core:test
```

### License

Dual-licensed; choose one:

- **Open source**: GPL-3.0-only, with additional terms permitted by section 7 of GPL-3.0;
- **Commercial**: for products that do not want to release their source code. Priced per product: reference price US$154 (CNY 1024) per product, paid once; the actual price is negotiable. Contact chengying@cys.world.

See [LICENSING.md](LICENSING.md). Before using it in a product, read the legal notice there: you must make sure on your own that it may be used lawfully, including with respect to patents, in your country and wherever you sell.

### Feedback

Problems and suggestions are welcome in Issues. External pull requests are not accepted; see [CONTRIBUTING.md](CONTRIBUTING.md).
