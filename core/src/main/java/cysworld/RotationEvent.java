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
 * Data passed to {@link RotationListener} callbacks.
 *
 * <p>The detector reuses a single instance for every callback. Copy the fields you need; do not
 * keep a reference to the event.
 *
 * <p>Sign convention: {@link #steps}, {@link #netSteps}, {@link #angleDeg}, {@link #deltaDeg} and
 * {@link #velocityDegPerSec} are positive in the <em>forward</em> direction. Forward is clockwise
 * unless {@link RotationConfig#invertDirection} is set. {@link #clockwise} and {@link #rawAngleDeg}
 * always describe the physical rotation on screen.
 */
public final class RotationEvent {

    /** How the two fingers are turning. */
    public enum Variant {
        /**
         * Both fingers move around a common centre. One finger from each hand dragging in opposite
         * directions also counts as this variant.
         */
        BOTH,
        /** One finger stays (almost) still and the other circles around it. */
        PIVOT
    }

    /** Why a rotation gesture ended after it had started. */
    public enum EndReason {
        /** A finger was lifted. */
        LIFT,
        /** A third finger touched the screen. */
        EXTRA_FINGER,
        /** The touch stream was cancelled, or the host called {@link RotationDetector#cancel}. */
        CANCEL,
        /** The rotation was disabled through {@link RotationDetector#setConfig}. */
        DISABLED
    }

    /**
     * Why a two-finger touch was judged not to be a rotation gesture. From then on the host should
     * handle the touch as it normally would, e.g. pass a scroll or pinch through to the app.
     */
    public enum RejectReason {
        /** The finger distance changed beyond {@link RotationConfig#pinchTolerance}. */
        PINCH,
        /** The midpoint moved more than the rotation explains. */
        PAN,
        /** The rotation reached the start angle with the fingers too close or too far apart. */
        DISTANCE,
        /** The fingers moved during {@link RotationConfig#holdBeforeRotateMs}. */
        NOT_HELD,
        /** One finger pivoted but {@link RotationConfig#allowPivot} is off. */
        PIVOT_NOT_ALLOWED,
        /** Both fingers turned but {@link RotationConfig#allowBoth} is off. */
        BOTH_NOT_ALLOWED,
        /** A third finger touched the screen before the rotation started. */
        EXTRA_FINGER,
        /** The second finger landed later than {@link RotationConfig#maxSecondFingerDelayMs}. */
        SECOND_FINGER_LATE,
        /** The first finger had moved more than {@link RotationConfig#maxFirstFingerTravelMm}. */
        FIRST_FINGER_MOVED,
        /** No decision within {@link RotationConfig#maxDecisionTimeMs}. */
        TIMEOUT,
        /** {@link RotationConfig.TwistMode#TWIST_ONLY}: the turn took longer than a twist may. */
        TWIST_TOO_SLOW,
        /** A finger was lifted before the rotation started (and it was not a twist). */
        LIFTED,
        /** The touch stream was cancelled, or the host called {@link RotationDetector#cancel}. */
        CANCEL,
        /** The rotation was disabled through {@link RotationDetector#setConfig}. */
        DISABLED
    }

    /** Time of the input or timeout that produced this event, in the host's clock (ms). */
    public long timeMs;
    /** Since the rotation started; for a twist or a rejection, since the rotation onset. */
    public long durationMs;
    /** Steps in this event (step events only); positive = forward. */
    public int steps;
    /** Net steps since the rotation started, including this event; positive = forward. */
    public int netSteps;
    /** True when the step comes from hold-to-repeat rather than from turning. */
    public boolean repeat;
    /** Rotation since the rotation started (before that: since tracking began); positive = forward. */
    public float angleDeg;
    /** Same as {@link #angleDeg} but always positive = clockwise. */
    public float rawAngleDeg;
    /** Change since the previous angle event (angle events only); positive = forward. */
    public float deltaDeg;
    /** Smoothed angular velocity; positive = forward. */
    public float velocityDegPerSec;
    /** Physical direction of the step, the angle change, or the gesture as a whole. */
    public boolean clockwise;
    /** Midpoint between the two fingers, in the host's pixel coordinates. */
    public float centerX;
    /** Midpoint between the two fingers, in the host's pixel coordinates. */
    public float centerY;
    /** Distance between the two fingers in pixels. */
    public float distancePx;
    /** How the fingers are turning. */
    public Variant variant;
    /** Set on end events only. */
    public EndReason endReason;
    /** Set on rejection events only. */
    public RejectReason rejectReason;

    void clear() {
        timeMs = 0;
        durationMs = 0;
        steps = 0;
        netSteps = 0;
        repeat = false;
        angleDeg = 0f;
        rawAngleDeg = 0f;
        deltaDeg = 0f;
        velocityDegPerSec = 0f;
        clockwise = false;
        centerX = 0f;
        centerY = 0f;
        distancePx = 0f;
        variant = null;
        endReason = null;
        rejectReason = null;
    }

    @Override
    public String toString() {
        return "RotationEvent{t=" + timeMs
                + ", dur=" + durationMs
                + ", steps=" + steps
                + ", net=" + netSteps
                + (repeat ? ", repeat" : "")
                + ", angle=" + angleDeg
                + ", delta=" + deltaDeg
                + ", vel=" + velocityDegPerSec
                + ", cw=" + clockwise
                + ", variant=" + variant
                + (endReason != null ? ", end=" + endReason : "")
                + (rejectReason != null ? ", reject=" + rejectReason : "")
                + "}";
    }
}
