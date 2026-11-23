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

import java.io.IOException;

/**
 * Recognises a two-finger rotation ("rotation") from raw touch input. Pure Java with no Android
 * dependency, so the same code runs inside any screen reader and in JVM unit tests.
 *
 * <h2>Input</h2>
 * Call {@link #onTouchEvent} for every touch event the screen reader receives (on Android 13+,
 * from {@code TouchInteractionController}), passing the positions of all pointers currently down:
 * <ul>
 *   <li>ACTION_DOWN and ACTION_POINTER_DOWN map to {@link #ACTION_DOWN};</li>
 *   <li>ACTION_UP and ACTION_POINTER_UP map to {@link #ACTION_UP}; as with MotionEvent, the
 *       lifting pointer is still included;</li>
 *   <li>ACTION_MOVE and ACTION_CANCEL map to {@link #ACTION_MOVE} and {@link #ACTION_CANCEL}.</li>
 * </ul>
 * Coordinates are pixels and times are milliseconds from one monotonic clock (e.g.
 * {@code MotionEvent.getEventTime()}). When {@link RotationListener#onRotationDeadline} asks for it,
 * call {@link #onTimeout} at or after that time.
 *
 * <h2>Who owns the touch</h2>
 * While {@link #isUndecided()} is true the host should hold the two-finger touch: neither pass it
 * to the app nor treat it as one of its own gestures. After {@link RotationListener#onRotationStart} the
 * touch belongs to the rotation; after {@link RotationListener#onRotationRejected} the host handles it as
 * usual.
 *
 * <p>Not thread-safe: use one thread. Listeners may call {@link #cancel} from a callback.
 */
public final class RotationDetector {

    public static final int ACTION_DOWN = 0;
    public static final int ACTION_MOVE = 1;
    public static final int ACTION_UP = 2;
    public static final int ACTION_CANCEL = 3;

    /** What the detector is doing. */
    public enum State {
        /** Fewer than two fingers; waiting. */
        IDLE,
        /** Two fingers down; not decided yet. */
        TRACKING,
        /** The rotation is running. */
        ROTATING,
        /** Not a rotation gesture; input is ignored until all fingers lift. */
        REJECTED,
        /** The rotation or a twist finished; input is ignored until all fingers lift. */
        ENDED
    }

    /** Smoothing time constant for the angular velocity. */
    private static final double VELOCITY_TAU_MS = 50.0;
    /** Guards the step loop against pathological configs. */
    private static final int MAX_STEPS_PER_UPDATE = 1000;

    private final RotationListener listener;
    private final RotationEvent event = new RotationEvent();
    private final StringBuilder traceLine = new StringBuilder();
    private RotationConfig config;
    private float pxPerMm;
    private Appendable traceSink;

    private State state = State.IDLE;
    private long lastTime;

    // Single-finger phase.
    private long firstDownTime;
    private float firstX;
    private float firstY;
    private float firstTravelPx;

    // The two tracked pointers: current positions and the origin they are measured from.
    private int idA = -1;
    private int idB = -1;
    private float ax;
    private float ay;
    private float bx;
    private float by;
    private float ax0;
    private float ay0;
    private float bx0;
    private float by0;
    private float d0;
    private float midX0;
    private float midY0;
    private long secondDownTime;
    /** Second finger landing, or the end of the hold when holdBeforeRotateMs is used. */
    private long originTime;
    private boolean armed;

    // Rotation measurement.
    private double lastRawAngle;
    /** Accumulated rotation since the origin, degrees, clockwise positive. */
    private float theta;
    private long onsetTime = -1;
    /** Smoothed angular velocity, degrees per second, clockwise positive. */
    private float velocity;
    private long lastSampleTime;
    private boolean engagePending;
    private RotationEvent.Variant variant = RotationEvent.Variant.BOTH;

    // Rotation running.
    private boolean engaged;
    private long engageTime;
    private float engageTheta;
    /** Angle of the last detent; steps are counted from here. */
    private float anchor;
    /** Physical direction of the last step: +1 clockwise, -1 counter-clockwise. */
    private int lastDir = 1;
    /** Net physical steps, clockwise positive. */
    private int netSteps;
    private float lastAngleEventTheta;
    private float stillRefTheta;
    private long lastMotionTime;
    private boolean dwellFired;
    private long nextRepeatTime = -1;

    private long notifiedDeadline = -1;

    /**
     * @param config      behaviour; copied, see {@link #setConfig}
     * @param pixelsPerMm screen density, e.g. {@code DisplayMetrics.xdpi / 25.4f}
     * @param listener    receives the recognised gestures
     */
    public RotationDetector(RotationConfig config, float pixelsPerMm, RotationListener listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        this.listener = listener;
        setPixelsPerMm(pixelsPerMm);
        RotationConfig c = config.copy();
        c.validate();
        this.config = c;
    }

    // ---- configuration ----------------------------------------------------------------------

    /**
     * Replaces the configuration (a copy is taken). Takes effect immediately; disabling during a
     * gesture ends or rejects it.
     *
     * @throws IllegalArgumentException if the config is invalid
     */
    public void setConfig(RotationConfig newConfig) {
        RotationConfig c = newConfig.copy();
        c.validate();
        config = c;
        if (!c.enabled) {
            if (state == State.ROTATING) {
                end(lastTime, RotationEvent.EndReason.DISABLED);
            } else if (state == State.TRACKING) {
                reject(lastTime, RotationEvent.RejectReason.DISABLED);
            }
        }
        notifyDeadline();
    }

    /** Returns a copy of the current configuration. */
    public RotationConfig getConfig() {
        return config.copy();
    }

    /** Updates the screen density, e.g. after a display change. */
    public void setPixelsPerMm(float pixelsPerMm) {
        if (!(pixelsPerMm > 0) || Float.isInfinite(pixelsPerMm)) {
            throw new IllegalArgumentException("pixelsPerMm");
        }
        pxPerMm = pixelsPerMm;
    }

    public float getPixelsPerMm() {
        return pxPerMm;
    }

    /**
     * Records every input call to {@code sink} in the {@link RotationTrace} format; null stops
     * recording. Recording stops by itself if the sink throws.
     */
    public void setTraceSink(Appendable sink) {
        traceSink = sink;
        if (sink != null) {
            traceLine.setLength(0);
            traceLine.append(RotationTrace.header(pxPerMm));
            writeTrace();
        }
    }

    // ---- state ------------------------------------------------------------------------------

    public State getState() {
        return state;
    }

    /** True while two fingers are down and the detector has not decided yet. */
    public boolean isUndecided() {
        return state == State.TRACKING;
    }

    /** True while the rotation is running. */
    public boolean isRotating() {
        return state == State.ROTATING;
    }

    // ---- input ------------------------------------------------------------------------------

    /**
     * Feeds one touch event. See the class documentation for the mapping from MotionEvent.
     *
     * @param action          one of the {@code ACTION_*} constants
     * @param timeMs          event time
     * @param actionPointerId id of the pointer going down or up; ignored for other actions
     * @param pointerCount    number of pointers in the arrays
     * @param ids             pointer ids
     * @param xs              x coordinates in pixels
     * @param ys              y coordinates in pixels
     */
    public void onTouchEvent(int action, long timeMs, int actionPointerId,
                             int pointerCount, int[] ids, float[] xs, float[] ys) {
        if (action < ACTION_DOWN || action > ACTION_CANCEL) {
            throw new IllegalArgumentException("action " + action);
        }
        if (pointerCount < 0 || ids.length < pointerCount || xs.length < pointerCount
                || ys.length < pointerCount) {
            throw new IllegalArgumentException("pointerCount " + pointerCount);
        }
        if (traceSink != null) {
            traceLine.setLength(0);
            RotationTrace.appendTouch(traceLine, action, timeMs, actionPointerId, pointerCount, ids, xs, ys);
            writeTrace();
        }
        lastTime = timeMs;
        if (!config.enabled) {
            resetInternal();
            notifyDeadline();
            return;
        }
        runTimers(timeMs);
        switch (action) {
            case ACTION_DOWN:
                handleDown(timeMs, pointerCount, ids, xs, ys);
                break;
            case ACTION_MOVE:
                handleMove(timeMs, pointerCount, ids, xs, ys);
                break;
            case ACTION_UP:
                handleUp(timeMs, pointerCount);
                break;
            default:
                handleCancel(timeMs);
                break;
        }
        notifyDeadline();
    }

    /** Call when the time requested through {@link RotationListener#onRotationDeadline} arrives. */
    public void onTimeout(long timeMs) {
        if (traceSink != null) {
            traceLine.setLength(0);
            RotationTrace.appendTimeout(traceLine, timeMs);
            writeTrace();
        }
        lastTime = Math.max(lastTime, timeMs);
        if (config.enabled) {
            runTimers(timeMs);
        }
        notifyDeadline();
    }

    /**
     * Abandons the current gesture, e.g. because the screen reader decided the touch is something
     * else. Reports an end or rejection with reason CANCEL; input is then ignored until all
     * fingers lift.
     */
    public void cancel(long timeMs) {
        if (state == State.ROTATING) {
            end(timeMs, RotationEvent.EndReason.CANCEL);
        } else if (state == State.TRACKING) {
            reject(timeMs, RotationEvent.RejectReason.CANCEL);
        }
        notifyDeadline();
    }

    /** Silently forgets everything and returns to {@link State#IDLE}. */
    public void reset() {
        resetInternal();
        notifyDeadline();
    }

    // ---- event handling ---------------------------------------------------------------------

    private void handleDown(long t, int count, int[] ids, float[] xs, float[] ys) {
        if (count <= 1) {
            resetInternal();
            if (count == 1) {
                firstDownTime = t;
                firstX = xs[0];
                firstY = ys[0];
                firstTravelPx = 0f;
            }
            return;
        }
        if (count == 2) {
            if (state != State.IDLE) {
                return;
            }
            ax = xs[0];
            ay = ys[0];
            bx = xs[1];
            by = ys[1];
            RotationConfig c = config;
            if (c.maxSecondFingerDelayMs > 0 && t - firstDownTime > c.maxSecondFingerDelayMs) {
                reject(t, RotationEvent.RejectReason.SECOND_FINGER_LATE);
                return;
            }
            if (c.maxFirstFingerTravelMm > 0 && firstTravelPx > mm(c.maxFirstFingerTravelMm)) {
                reject(t, RotationEvent.RejectReason.FIRST_FINGER_MOVED);
                return;
            }
            startTracking(t, ids, xs, ys);
            return;
        }
        if (state == State.TRACKING) {
            reject(t, RotationEvent.RejectReason.EXTRA_FINGER);
        } else if (state == State.ROTATING) {
            end(t, RotationEvent.EndReason.EXTRA_FINGER);
        } else if (state == State.IDLE) {
            state = State.ENDED;
        }
    }

    private void handleMove(long t, int count, int[] ids, float[] xs, float[] ys) {
        if (state == State.IDLE) {
            if (count == 1) {
                firstTravelPx = Math.max(firstTravelPx, dist(xs[0], ys[0], firstX, firstY));
            }
            return;
        }
        if (state != State.TRACKING && state != State.ROTATING) {
            return;
        }
        int ia = indexOf(ids, count, idA);
        int ib = indexOf(ids, count, idB);
        if (ia < 0 || ib < 0) {
            return;
        }
        ax = xs[ia];
        ay = ys[ia];
        bx = xs[ib];
        by = ys[ib];
        float delta = updateAngle(t);
        if (state == State.TRACKING) {
            evaluateTracking(t, true);
        } else {
            updateRotating(t, delta);
        }
    }

    private void handleUp(long t, int count) {
        if (state == State.ROTATING) {
            end(t, RotationEvent.EndReason.LIFT);
        } else if (state == State.TRACKING) {
            if (isTwist(t)) {
                emitTwist(t);
            } else {
                reject(t, RotationEvent.RejectReason.LIFTED);
            }
        }
        if (count <= 1) {
            resetInternal();
        }
    }

    private void handleCancel(long t) {
        if (state == State.ROTATING) {
            end(t, RotationEvent.EndReason.CANCEL);
        } else if (state == State.TRACKING) {
            reject(t, RotationEvent.RejectReason.CANCEL);
        }
        resetInternal();
    }

    private void runTimers(long t) {
        if (state == State.TRACKING) {
            evaluateTracking(t, false);
        } else if (state == State.ROTATING) {
            RotationConfig c = config;
            if (c.dwellMs > 0 && !dwellFired && t - lastMotionTime >= c.dwellMs) {
                dwellFired = true;
                fill(t);
                listener.onRotationDwell(event);
                if (state != State.ROTATING) {
                    return;
                }
            }
            if (c.repeatDelayMs > 0) {
                runRepeat(t);
            }
        }
    }

    // ---- tracking: deciding whether this is a rotation -----------------------------------------

    private void startTracking(long t, int[] ids, float[] xs, float[] ys) {
        state = State.TRACKING;
        idA = ids[0];
        idB = ids[1];
        ax = xs[0];
        ay = ys[0];
        bx = xs[1];
        by = ys[1];
        secondDownTime = t;
        originTime = t;
        lastSampleTime = t;
        engaged = false;
        netSteps = 0;
        variant = RotationEvent.Variant.BOTH;
        rebaseOrigin();
        armed = config.holdBeforeRotateMs <= 0;
    }

    /** Measures everything from the current finger positions from now on. */
    private void rebaseOrigin() {
        ax0 = ax;
        ay0 = ay;
        bx0 = bx;
        by0 = by;
        d0 = dist(ax, ay, bx, by);
        midX0 = (ax + bx) / 2f;
        midY0 = (ay + by) / 2f;
        lastRawAngle = Math.atan2(by - ay, bx - ax);
        theta = 0f;
        onsetTime = -1;
        velocity = 0f;
        engagePending = false;
    }

    private void arm(long t) {
        armed = true;
        originTime = t;
        rebaseOrigin();
        if (config.holdBeforeRotateMs > 0) {
            fill(t);
            listener.onRotationArmed(event);
        }
    }

    /**
     * Applies the start and rejection rules. {@code moved} is false when only time has passed,
     * so geometry-based rejections are skipped.
     */
    private void evaluateTracking(long t, boolean moved) {
        RotationConfig c = config;
        if (!armed) {
            if (moved && Math.max(dist(ax, ay, ax0, ay0), dist(bx, by, bx0, by0)) > mm(c.holdSlopMm)) {
                reject(t, RotationEvent.RejectReason.NOT_HELD);
                return;
            }
            if (t - secondDownTime >= c.holdBeforeRotateMs) {
                arm(t);
            }
            return;
        }
        if (c.maxDecisionTimeMs > 0 && t - originTime >= c.maxDecisionTimeMs) {
            reject(t, RotationEvent.RejectReason.TIMEOUT);
            return;
        }
        float absTheta = Math.abs(theta);
        if (onsetTime < 0 && absTheta >= c.onsetAngleDeg) {
            onsetTime = t;
        }
        float dispA = dist(ax, ay, ax0, ay0);
        float dispB = dist(bx, by, bx0, by0);
        variant = Math.min(dispA, dispB) <= c.pivotRatio * Math.max(dispA, dispB)
                ? RotationEvent.Variant.PIVOT : RotationEvent.Variant.BOTH;

        if (moved) {
            float d = dist(ax, ay, bx, by);
            if (d0 > 0 && Math.abs(d / d0 - 1f) > c.pinchTolerance) {
                reject(t, RotationEvent.RejectReason.PINCH);
                return;
            }
            // A turn moves the midpoint by at most about the arc each finger travels around it
            // (exactly that much when one finger pivots); anything beyond that is a pan.
            float midDisp = dist((ax + bx) / 2f, (ay + by) / 2f, midX0, midY0);
            float arc = (float) (d0 / 2.0 * Math.toRadians(absTheta));
            if (midDisp > mm(c.panSlopMm) && midDisp > arc * c.panRatio) {
                reject(t, RotationEvent.RejectReason.PAN);
                return;
            }
        }

        engagePending = false;
        if (c.twistMode == RotationConfig.TwistMode.TWIST_ONLY) {
            if (onsetTime >= 0 && t - onsetTime > c.twistMaxDurationMs) {
                reject(t, RotationEvent.RejectReason.TWIST_TOO_SLOW);
            }
            return;
        }
        float startAngle = variant == RotationEvent.Variant.PIVOT ? c.pivotStartAngleDeg : c.startAngleDeg;
        if (absTheta < startAngle) {
            return;
        }
        if (!variantAllowed()) {
            reject(t, variant == RotationEvent.Variant.PIVOT
                    ? RotationEvent.RejectReason.PIVOT_NOT_ALLOWED : RotationEvent.RejectReason.BOTH_NOT_ALLOWED);
            return;
        }
        if (!distanceOk()) {
            reject(t, RotationEvent.RejectReason.DISTANCE);
            return;
        }
        // The angle is there; the time must be too (and vice versa).
        if (t - durationOrigin() >= requiredDuration()) {
            engage(t);
        } else {
            engagePending = true;
        }
    }

    private long durationOrigin() {
        return config.durationFromSecondFinger ? originTime : onsetTime;
    }

    private long requiredDuration() {
        long r = config.minRotateDurationMs;
        if (config.twistMode == RotationConfig.TwistMode.TWIST_OR_DIAL) {
            r = Math.max(r, config.twistMaxDurationMs);
        }
        return r;
    }

    private boolean variantAllowed() {
        return variant == RotationEvent.Variant.PIVOT ? config.allowPivot : config.allowBoth;
    }

    private boolean distanceOk() {
        float d = dist(ax, ay, bx, by);
        return d >= mm(config.minFingerDistanceMm)
                && (config.maxFingerDistanceMm <= 0 || d <= mm(config.maxFingerDistanceMm));
    }

    private boolean isTwist(long t) {
        RotationConfig c = config;
        return c.twistMode != RotationConfig.TwistMode.OFF
                && armed
                && onsetTime >= 0
                && t - onsetTime < c.twistMaxDurationMs
                && Math.abs(theta) >= c.twistMinAngleDeg
                && variantAllowed()
                && distanceOk();
    }

    private void emitTwist(long t) {
        state = State.ENDED;
        fill(t);
        int dir = theta > 0 ? 1 : -1;
        event.steps = sign() * dir;
        event.clockwise = dir > 0;
        listener.onRotationTwist(event);
    }

    // ---- rotating ---------------------------------------------------------------------------

    private void engage(long t) {
        state = State.ROTATING;
        engaged = true;
        engagePending = false;
        engageTime = t;
        engageTheta = theta;
        lastDir = theta >= 0 ? 1 : -1;
        netSteps = 0;
        lastAngleEventTheta = theta;
        stillRefTheta = theta;
        lastMotionTime = t;
        dwellFired = false;
        nextRepeatTime = -1;
        fill(t);
        listener.onRotationStart(event);
        if (state != State.ROTATING) {
            return;
        }
        if (config.stepOnStart) {
            anchor = theta;
            emitSteps(t, lastDir, 1, false);
            if (state != State.ROTATING) {
                return;
            }
        } else {
            anchor = 0f;
        }
        advanceSteps(t);
    }

    private void updateRotating(long t, float delta) {
        RotationConfig c = config;
        if (delta != 0f) {
            float change = theta - lastAngleEventTheta;
            if (change != 0f && Math.abs(change) >= c.angleEventMinDeltaDeg) {
                fill(t);
                event.deltaDeg = sign() * change;
                event.clockwise = change > 0;
                lastAngleEventTheta = theta;
                listener.onRotationAngle(event);
                if (state != State.ROTATING) {
                    return;
                }
            }
            if (Math.abs(theta - stillRefTheta) > c.dwellSlopDeg) {
                stillRefTheta = theta;
                lastMotionTime = t;
                dwellFired = false;
                nextRepeatTime = -1;
            }
        }
        advanceSteps(t);
    }

    /**
     * Emits the detents crossed since the last one. Continuing in the same direction needs one
     * step angle; reversing needs the step angle plus the hysteresis.
     */
    private void advanceSteps(long t) {
        float s = effectiveStepAngle();
        float h = config.hysteresisDeg;
        int runDir = 0;
        int runCount = 0;
        for (int i = 0; i < MAX_STEPS_PER_UPDATE; i++) {
            int dir;
            if ((theta - anchor) * lastDir >= s) {
                anchor += lastDir * s;
                dir = lastDir;
            } else if ((anchor - theta) * lastDir >= s + h) {
                anchor -= lastDir * (s + h);
                lastDir = -lastDir;
                dir = lastDir;
            } else {
                break;
            }
            if (runCount > 0 && dir != runDir) {
                emitSteps(t, runDir, runCount, false);
                if (state != State.ROTATING) {
                    return;
                }
                runCount = 0;
            }
            runDir = dir;
            runCount++;
        }
        if (runCount > 0) {
            emitSteps(t, runDir, runCount, false);
        }
    }

    private float effectiveStepAngle() {
        RotationConfig c = config;
        float s = c.stepAngleDeg;
        float v = Math.abs(velocity);
        if (c.accelVelocityDegPerSec > 0 && v > c.accelVelocityDegPerSec) {
            s = Math.max(c.minStepAngleDeg, s * c.accelVelocityDegPerSec / v);
        }
        return s;
    }

    private void emitSteps(long t, int dir, int count, boolean repeat) {
        netSteps += dir * count;
        fill(t);
        event.steps = sign() * dir * count;
        event.repeat = repeat;
        event.clockwise = dir > 0;
        listener.onRotationStep(event);
    }

    private void runRepeat(long t) {
        RotationConfig c = config;
        float p = theta - engageTheta;
        float ap = Math.abs(p);
        if (ap < c.repeatMinAngleDeg || t - lastMotionTime < c.repeatDelayMs) {
            nextRepeatTime = -1;
            return;
        }
        if (nextRepeatTime < 0) {
            nextRepeatTime = lastMotionTime + c.repeatDelayMs;
        }
        if (t >= nextRepeatTime) {
            emitSteps(t, p > 0 ? 1 : -1, 1, true);
            nextRepeatTime = t + repeatInterval(ap);
        }
    }

    private long repeatInterval(float absAngle) {
        RotationConfig c = config;
        float f = (absAngle - c.repeatMinAngleDeg) / (c.repeatMaxAngleDeg - c.repeatMinAngleDeg);
        f = Math.max(0f, Math.min(1f, f));
        return Math.round(c.repeatSlowIntervalMs + (c.repeatFastIntervalMs - c.repeatSlowIntervalMs) * f);
    }

    // ---- shared helpers ---------------------------------------------------------------------

    /** Updates theta and velocity from the current positions; returns the change (clockwise positive). */
    private float updateAngle(long t) {
        float dx = bx - ax;
        float dy = by - ay;
        double a = Math.atan2(dy, dx);
        float delta = 0f;
        // Below 1 mm apart the angle is noise; skip the change but keep tracking the direction.
        if (Math.hypot(dx, dy) >= pxPerMm) {
            double d = a - lastRawAngle;
            while (d > Math.PI) {
                d -= 2 * Math.PI;
            }
            while (d < -Math.PI) {
                d += 2 * Math.PI;
            }
            delta = (float) Math.toDegrees(d);
            theta += delta;
        }
        lastRawAngle = a;
        long dt = t - lastSampleTime;
        if (dt > 0) {
            float inst = delta * 1000f / dt;
            float alpha = (float) (1.0 - Math.exp(-dt / VELOCITY_TAU_MS));
            velocity += alpha * (inst - velocity);
            lastSampleTime = t;
        }
        return delta;
    }

    private void end(long t, RotationEvent.EndReason reason) {
        state = State.ENDED;
        nextRepeatTime = -1;
        fill(t);
        event.endReason = reason;
        listener.onRotationEnd(event);
    }

    private void reject(long t, RotationEvent.RejectReason reason) {
        state = State.REJECTED;
        engagePending = false;
        fill(t);
        event.rejectReason = reason;
        listener.onRotationRejected(event);
    }

    private void fill(long t) {
        RotationEvent e = event;
        e.clear();
        int f = sign();
        float raw = engaged ? theta - engageTheta : theta;
        e.timeMs = t;
        e.durationMs = engaged ? t - engageTime : (onsetTime >= 0 ? t - onsetTime : 0);
        e.rawAngleDeg = raw;
        e.angleDeg = f * raw;
        e.velocityDegPerSec = f * velocity;
        e.netSteps = f * netSteps;
        e.clockwise = engaged ? lastDir > 0 : theta > 0;
        e.centerX = (ax + bx) / 2f;
        e.centerY = (ay + by) / 2f;
        e.distancePx = dist(ax, ay, bx, by);
        e.variant = variant;
    }

    private void resetInternal() {
        state = State.IDLE;
        idA = -1;
        idB = -1;
        armed = false;
        engaged = false;
        engagePending = false;
        onsetTime = -1;
        nextRepeatTime = -1;
        netSteps = 0;
        theta = 0f;
        velocity = 0f;
    }

    private long computeDeadline() {
        RotationConfig c = config;
        if (!c.enabled) {
            return -1;
        }
        long dl = -1;
        if (state == State.TRACKING) {
            if (!armed) {
                dl = earliest(dl, secondDownTime + c.holdBeforeRotateMs);
            } else {
                if (c.maxDecisionTimeMs > 0) {
                    dl = earliest(dl, originTime + c.maxDecisionTimeMs);
                }
                if (engagePending) {
                    dl = earliest(dl, durationOrigin() + requiredDuration());
                }
                if (c.twistMode == RotationConfig.TwistMode.TWIST_ONLY && onsetTime >= 0) {
                    dl = earliest(dl, onsetTime + c.twistMaxDurationMs + 1);
                }
            }
        } else if (state == State.ROTATING) {
            if (c.dwellMs > 0 && !dwellFired) {
                dl = earliest(dl, lastMotionTime + c.dwellMs);
            }
            if (c.repeatDelayMs > 0 && Math.abs(theta - engageTheta) >= c.repeatMinAngleDeg) {
                dl = earliest(dl, nextRepeatTime >= 0 ? nextRepeatTime : lastMotionTime + c.repeatDelayMs);
            }
        }
        return dl;
    }

    private void notifyDeadline() {
        long dl = computeDeadline();
        if (dl != notifiedDeadline) {
            notifiedDeadline = dl;
            listener.onRotationDeadline(dl);
        }
    }

    private void writeTrace() {
        try {
            traceSink.append(traceLine);
        } catch (IOException e) {
            traceSink = null;
        }
    }

    private int sign() {
        return config.invertDirection ? -1 : 1;
    }

    private float mm(float millimetres) {
        return millimetres * pxPerMm;
    }

    private static long earliest(long a, long b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static float dist(float x1, float y1, float x2, float y2) {
        return (float) Math.hypot(x2 - x1, y2 - y1);
    }

    private static int indexOf(int[] ids, int count, int id) {
        for (int i = 0; i < count; i++) {
            if (ids[i] == id) {
                return i;
            }
        }
        return -1;
    }
}
