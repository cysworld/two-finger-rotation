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
 * Every tunable behaviour of {@link RotationDetector}. Start from a preset ({@link #medium()},
 * {@link #slow()}, {@link #fast()}), change the fields you care about, and pass the config to
 * the detector. The detector takes a copy, so later changes need {@link RotationDetector#setConfig}.
 *
 * <p>Distances are in millimetres, angles in degrees, times in milliseconds. A time or limit of
 * {@code 0} turns that feature off unless its documentation says otherwise.
 *
 * <p>Which of these the end user may change is the screen-reader vendor's decision.
 */
public final class RotationConfig implements Cloneable {

    /** How quick twists relate to the dial. */
    public enum TwistMode {
        /** Dial only. */
        OFF,
        /** No dial: a quick turn followed by lifting is reported as a twist; slow turns are rejected. */
        TWIST_ONLY,
        /** A quick turn and lift is a twist; a turn longer than {@link #twistMaxDurationMs} is the dial. */
        TWIST_OR_DIAL
    }

    /** Step-angle presets; see {@link #sensitivity}. */
    public enum Sensitivity {
        /** 45° per step. */
        LOW,
        /** 30° per step. */
        MEDIUM,
        /** 20° per step. */
        HIGH
    }

    // ---- master switch ----------------------------------------------------------------------

    /** Master switch. Combine the user's choice with {@link RotationRegionPolicy} here. */
    public boolean enabled = true;

    // ---- recognition ------------------------------------------------------------------------

    /** Minimum finger distance when the rotation starts; closer than this the angle is unreliable. */
    public float minFingerDistanceMm = 15f;
    /** Maximum finger distance when the rotation starts; 0 = no limit. */
    public float maxFingerDistanceMm = 0f;
    /** Rotation needed to start when both fingers turn. */
    public float startAngleDeg = 12f;
    /** Rotation needed to start when one finger pivots around the other. */
    public float pivotStartAngleDeg = 18f;
    /**
     * Rotation that counts as "the fingers started turning". The time for
     * {@link #minRotateDurationMs} and for twists is measured from this moment. Must not exceed the
     * start angles or {@link #twistMinAngleDeg}.
     */
    public float onsetAngleDeg = 3f;
    /** Allowed relative change of finger distance before the touch counts as a pinch (0.25 = ±25%). */
    public float pinchTolerance = 0.25f;
    /**
     * The touch is a pan when the midpoint moves more than {@code panRatio} × the rotation arc
     * (half the finger distance × angle). A pivot turn moves the midpoint by about 1 × the arc,
     * so keep this above 1 when pivots are allowed.
     */
    public float panRatio = 1.5f;
    /** Midpoint movement below this is never treated as a pan. */
    public float panSlopMm = 3f;
    /** One finger counts as the pivot when it moved less than this fraction of the other finger. */
    public float pivotRatio = 0.35f;
    /** Accept both fingers turning (including one finger from each hand). */
    public boolean allowBoth = true;
    /** Accept one finger pivoting around the other. */
    public boolean allowPivot = true;
    /** Reject when the second finger lands later than this after the first; 0 = no limit. */
    public long maxSecondFingerDelayMs = 0;
    /** Reject when the first finger moved more than this before the second landed; 0 = no limit. */
    public float maxFirstFingerTravelMm = 0f;
    /** Reject when no decision is reached within this time; 0 = wait as long as it takes. */
    public long maxDecisionTimeMs = 0;

    // ---- start conditions -------------------------------------------------------------------

    /**
     * The rotation must also have lasted this long, measured from the onset (or from the second
     * finger, see {@link #durationFromSecondFinger}). Angle and time must both be satisfied: a
     * small angle reached quickly waits for the time, and enough time without the angle never
     * starts.
     */
    public long minRotateDurationMs = 0;
    /**
     * Measure {@link #minRotateDurationMs} from the second finger landing (or from the end of
     * {@link #holdBeforeRotateMs}) instead of the onset.
     */
    public boolean durationFromSecondFinger = false;
    /**
     * Both fingers must first stay still this long before turning counts; moving earlier rejects
     * the touch so it goes to the app. Lets apps keep their own two-finger rotation.
     */
    public long holdBeforeRotateMs = 0;
    /** Movement allowed while holding for {@link #holdBeforeRotateMs}. */
    public float holdSlopMm = 2f;

    // ---- steps ------------------------------------------------------------------------------

    /** Rotation per step. */
    public float stepAngleDeg = 30f;
    /** Extra rotation needed to step back after the direction reverses. */
    public float hysteresisDeg = 10f;
    /**
     * Emit the first step the moment the rotation starts. When false, steps are counted from the
     * angle the fingers had when they landed.
     */
    public boolean stepOnStart = true;
    /** Make counter-clockwise the forward direction. */
    public boolean invertDirection = false;
    /** Above this angular speed the step angle shrinks proportionally; 0 = no acceleration. */
    public float accelVelocityDegPerSec = 0f;
    /** Smallest step angle that acceleration may reach. */
    public float minStepAngleDeg = 10f;

    // ---- continuous angle -------------------------------------------------------------------

    /** Minimum angle change between angle events; 0 = report every change. */
    public float angleEventMinDeltaDeg = 0f;

    // ---- dwell ------------------------------------------------------------------------------

    /** Report a dwell after the fingers stay still this long once the rotation has started; 0 = off. */
    public long dwellMs = 0;
    /** Rotation below this does not count as movement for dwell and repeat. */
    public float dwellSlopDeg = 3f;

    // ---- hold to repeat ---------------------------------------------------------------------

    /** Start auto-repeating after the fingers stay still this long; 0 = off. */
    public long repeatDelayMs = 0;
    /** Repeat only when the rotation since start is at least this. */
    public float repeatMinAngleDeg = 45f;
    /** At this rotation and beyond, repeats run at {@link #repeatFastIntervalMs}. */
    public float repeatMaxAngleDeg = 120f;
    /** Repeat interval at {@link #repeatMinAngleDeg}. */
    public long repeatSlowIntervalMs = 600;
    /** Repeat interval at {@link #repeatMaxAngleDeg}. */
    public long repeatFastIntervalMs = 120;

    // ---- twist ------------------------------------------------------------------------------

    /** See {@link TwistMode}. */
    public TwistMode twistMode = TwistMode.OFF;
    /** Rotation a twist needs. */
    public float twistMinAngleDeg = 20f;
    /** A twist must be lifted within this time after the onset. */
    public long twistMaxDurationMs = 300;

    // ---- presets ----------------------------------------------------------------------------

    /** Balanced behaviour; these are the field defaults. */
    public static RotationConfig medium() {
        return new RotationConfig();
    }

    /** Deliberate: hold first, turn longer, larger steps; fewer accidental triggers. */
    public static RotationConfig slow() {
        RotationConfig c = new RotationConfig();
        c.minFingerDistanceMm = 20f;
        c.startAngleDeg = 15f;
        c.pivotStartAngleDeg = 25f;
        c.minRotateDurationMs = 150;
        c.holdBeforeRotateMs = 250;
        c.stepAngleDeg = 40f;
        c.hysteresisDeg = 12f;
        return c;
    }

    /**
     * Quick response: small angles. Acceleration stays off; set
     * {@link #accelVelocityDegPerSec} yourself if you want it.
     */
    public static RotationConfig fast() {
        RotationConfig c = new RotationConfig();
        c.startAngleDeg = 8f;
        c.pivotStartAngleDeg = 12f;
        c.stepAngleDeg = 20f;
        c.hysteresisDeg = 6f;
        return c;
    }

    /** Sets {@link #stepAngleDeg} from a three-level sensitivity; returns this config. */
    public RotationConfig sensitivity(Sensitivity s) {
        switch (s) {
            case LOW:
                stepAngleDeg = 45f;
                break;
            case HIGH:
                stepAngleDeg = 20f;
                break;
            default:
                stepAngleDeg = 30f;
                break;
        }
        if (minStepAngleDeg > stepAngleDeg) {
            minStepAngleDeg = stepAngleDeg;
        }
        return this;
    }

    /** Returns an independent copy. */
    public RotationConfig copy() {
        try {
            return (RotationConfig) clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    /** Throws {@link IllegalArgumentException} naming the first invalid field. */
    public void validate() {
        check(minFingerDistanceMm >= 0, "minFingerDistanceMm");
        check(maxFingerDistanceMm == 0 || maxFingerDistanceMm >= minFingerDistanceMm, "maxFingerDistanceMm");
        check(startAngleDeg > 0, "startAngleDeg");
        check(pivotStartAngleDeg > 0, "pivotStartAngleDeg");
        check(onsetAngleDeg > 0 && onsetAngleDeg <= startAngleDeg && onsetAngleDeg <= pivotStartAngleDeg,
                "onsetAngleDeg (must be > 0 and not above the start angles)");
        check(pinchTolerance > 0, "pinchTolerance");
        check(panRatio > 0, "panRatio");
        check(panSlopMm >= 0, "panSlopMm");
        check(pivotRatio >= 0 && pivotRatio < 1, "pivotRatio");
        check(maxSecondFingerDelayMs >= 0, "maxSecondFingerDelayMs");
        check(maxFirstFingerTravelMm >= 0, "maxFirstFingerTravelMm");
        check(maxDecisionTimeMs >= 0, "maxDecisionTimeMs");
        check(minRotateDurationMs >= 0, "minRotateDurationMs");
        check(holdBeforeRotateMs >= 0, "holdBeforeRotateMs");
        check(holdSlopMm >= 0, "holdSlopMm");
        check(stepAngleDeg > 0, "stepAngleDeg");
        check(hysteresisDeg >= 0, "hysteresisDeg");
        check(accelVelocityDegPerSec >= 0, "accelVelocityDegPerSec");
        check(minStepAngleDeg > 0 && minStepAngleDeg <= stepAngleDeg, "minStepAngleDeg (must be > 0 and not above stepAngleDeg)");
        check(angleEventMinDeltaDeg >= 0, "angleEventMinDeltaDeg");
        check(dwellMs >= 0, "dwellMs");
        check(dwellSlopDeg >= 0, "dwellSlopDeg");
        check(repeatDelayMs >= 0, "repeatDelayMs");
        check(repeatMinAngleDeg >= 0, "repeatMinAngleDeg");
        check(repeatMaxAngleDeg > repeatMinAngleDeg, "repeatMaxAngleDeg (must be above repeatMinAngleDeg)");
        check(repeatFastIntervalMs > 0, "repeatFastIntervalMs");
        check(repeatSlowIntervalMs >= repeatFastIntervalMs, "repeatSlowIntervalMs (must not be below repeatFastIntervalMs)");
        check(twistMode != null, "twistMode");
        check(twistMinAngleDeg >= onsetAngleDeg, "twistMinAngleDeg (must not be below onsetAngleDeg)");
        check(twistMaxDurationMs > 0, "twistMaxDurationMs");
    }

    private static void check(boolean ok, String field) {
        if (!ok) {
            throw new IllegalArgumentException("Invalid RotationConfig." + field);
        }
    }
}
