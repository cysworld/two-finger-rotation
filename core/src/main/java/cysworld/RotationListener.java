/*
 * 双指旋转 two-finger rotation
 * Copyright (C) 2026 程溁 <chengying@cys.world>
 * SPDX-License-Identifier: GPL-3.0-only OR LicenseRef-Commercial
 *
 * 按 GPL-3.0 授权时，适用 LICENSING.md 中的附加条款（GPL-3.0 第 7 条）；也可以取得商业授权。
 * Under GPL-3.0, the additional terms (section 7) in LICENSING.md apply; a commercial license
 * is also available. See LICENSING.md.
 */
package cysworld;

/**
 * Receives what {@link RotationDetector} recognises. Every method has an empty default, so implement
 * only the ones you need.
 *
 * <p>The detector only reports gestures. What a step, a dwell or a twist <em>does</em> (switch the
 * reading control, move by the current granularity, change speech rate, ...) is up to the host.
 *
 * <p>Callbacks run synchronously inside {@link RotationDetector#onTouchEvent} or
 * {@link RotationDetector#onTimeout}. The {@link RotationEvent} instance is reused; copy what you need.
 */
public interface RotationListener {

    /** {@link RotationConfig#holdBeforeRotateMs} has passed with both fingers still; turning may start. */
    default void onRotationArmed(RotationEvent e) {}

    /** The touch is now a rotation gesture. The host should keep it and not pass it to the app. */
    default void onRotationStart(RotationEvent e) {}

    /** The rotation moved by {@link RotationEvent#steps} detents (positive = forward). */
    default void onRotationStep(RotationEvent e) {}

    /** The angle changed; for continuous adjustments. See {@link RotationConfig#angleEventMinDeltaDeg}. */
    default void onRotationAngle(RotationEvent e) {}

    /** The fingers stayed still for {@link RotationConfig#dwellMs} after turning. */
    default void onRotationDwell(RotationEvent e) {}

    /** The rotation gesture is over; see {@link RotationEvent#endReason}. */
    default void onRotationEnd(RotationEvent e) {}

    /** A quick twist was recognised; see {@link RotationConfig#twistMode}. */
    default void onRotationTwist(RotationEvent e) {}

    /**
     * The two-finger touch is not a rotation gesture (see {@link RotationEvent#rejectReason}). The host
     * should handle it as it normally would.
     */
    default void onRotationRejected(RotationEvent e) {}

    /**
     * The detector needs {@link RotationDetector#onTimeout} to be called at or after
     * {@code uptimeMs}, in the same clock as the touch events. {@code -1} cancels any pending
     * timeout. A later call replaces the earlier one. Calling it late is harmless; calling it
     * early does nothing.
     */
    default void onRotationDeadline(long uptimeMs) {}
}
