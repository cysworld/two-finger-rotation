# 接入指南

本文说明怎样把双指旋转接进一个安卓读屏。交互行为和参数含义见 [design.md](design.md)。

## 1. 前提

- 安卓 13（API 33）及以上。
- 读屏通过 `TouchInteractionController` 自己识别手势，能拿到原始触摸事件。
- 核心在 `cysworld` 包里，纯 Java，编译成 Java 8 字节码。可以直接把 `core/src/main/java` 加进读屏的源码目录，也可以使用编译好的包。

## 2. 创建识别器

```java
float pxPerMm = displayMetrics.xdpi / 25.4f;
RotationDetector detector = new RotationDetector(RotationConfig.medium(), pxPerMm, listener);
```

- 预设有 `fast()`、`medium()`、`slow()`；先选一个预设，再改个别字段。
- 识别器不是线程安全的，所有调用放在同一个线程（通常就是读屏处理触摸的线程）。

## 3. 喂触摸事件

每收到一个触摸事件，就把当前所有手指的位置交给识别器：

| MotionEvent | 识别器 |
|---|---|
| `ACTION_DOWN`、`ACTION_POINTER_DOWN` | `ACTION_DOWN` |
| `ACTION_MOVE` | `ACTION_MOVE` |
| `ACTION_UP`、`ACTION_POINTER_UP` | `ACTION_UP`（抬起的手指仍包含在数组里，和 MotionEvent 一样） |
| `ACTION_CANCEL` | `ACTION_CANCEL` |

```java
detector.onTouchEvent(action, event.getEventTime(), event.getPointerId(event.getActionIndex()),
        count, ids, xs, ys);
```

- 时间用 `getEventTime()`（开机以来的毫秒数）。
- `ACTION_MOVE` 带有历史采样点时，先按时间顺序把历史点逐个喂进去，再喂当前点，角度会更准。

## 4. 触摸归谁

这是接入中最关键的一点：

1. 两指落下后，识别器进入判定（`isUndecided()` 为真）。**判定期间，读屏要先拿着这次触摸**：不要交给应用，不要转成拖动，也不要当成读屏自己的其他手势。
2. 收到 `onRotationStart`：这次触摸归读屏，一直到所有手指抬起。
3. 收到 `onRotationRejected`：不是旋转（例如捏合、双指滚动、两指太近），读屏照原来的逻辑处理，比如把双指滚动交给应用。

如果读屏原来一看到两指移动就立刻交给应用，就需要在判定期间暂停这一步，否则旋转会被应用拿走。

## 5. 给第二根手指留时间

很多读屏在单指按住几十毫秒后就转入"触摸浏览"，交给系统处理。之后再落下的第二根手指，读屏就看不到了。

- 读屏要在转入触摸浏览之前，给第二根手指一点落下的时间。
- 人的两根手指一般不会完全同时落下，实测间隔常在 0～60 毫秒，偶尔超过 100 毫秒。
- 参考做法：提供一个"等待第二根手指"的设置，默认沿用读屏原来的时间，用户可以调长。

## 6. 定时器

停留、按住连发、先按住再转、扭动等功能需要定时器：

```java
@Override public void onRotationDeadline(long uptimeMs) {
    handler.removeCallbacksAndMessages(TOKEN);
    if (uptimeMs >= 0) {
        handler.postAtTime(() -> detector.onTimeout(SystemClock.uptimeMillis()), TOKEN, uptimeMs);
    }
}
```

`-1` 表示取消。晚一点调用没有关系，早调用不起作用。

## 7. 处理识别结果

| 回调 | 时机 | 参考做法 |
|---|---|---|
| `onRotationStep` | 每转过一格；`steps` 为正表示前进方向（默认顺时针） | 每格切换一项阅读控件，播放短提示音并读出名称 |
| `onRotationTwist` | 快速扭一下（需要打开扭动） | 作为"右扭""左扭"两个新手势，放进读屏的手势自定义列表 |
| `onRotationDwell` | 转动后手指不抬、停住（需要打开停留） | 作为一个新手势，由用户选择命令 |
| `onRotationEnd` | 抬手、第三根手指落下或被取消 | 一般不需要处理 |

手势用来做什么由读屏厂商决定，厂商也负责确认这种用法在当地合法（见 [LICENSING.md](../LICENSING.md) 的法律提示）。

## 8. 设置

- 参数都在 `RotationConfig` 里，可以全部开放给用户，也可以只开放一部分。
- 用户改了设置后，用 `setConfig()` 换上新配置。建议在新的一次触摸开始时再换，不要在旋转进行中换。
- 按地区停用的工具（`RotationRegionPolicy`）由厂商自己决定是否使用，不建议做成用户设置。

## 9. 调试

- `setTraceSink()` 可以把每个触摸事件和定时器调用记成文本，文件开头记录屏幕密度。
- 在电脑上用 `RotationTrace.replay()` 重放，就能离线调参数、写回归测试，见 `core/src/test/java/cysworld/RecordedTraceTest.java`。
- 有的手机会屏蔽应用的系统日志，最好把轨迹写进读屏自己的文件。

## 10. 已知问题

**有的手机上，读屏收不到触摸事件**：实测一款安卓 16 手机上，读屏在服务连接时注册的 `TouchInteractionController` 回调一个事件都收不到，所有手势都由系统识别，双指旋转当然也不会生效。

对策：读屏收到 `TYPE_TOUCH_INTERACTION_END` 时，如果最近一段时间（例如 1 秒）一个回调都没收到，就注销再重新注册回调。实测重新注册后恢复正常。
