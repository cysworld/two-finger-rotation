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

import java.util.ArrayList;
import java.util.List;

/**
 * Drives a {@link RotationDetector} with synthetic two-finger gestures and records what it reports.
 * Positions are given in millimetres; 1 mm = {@link #PX} pixels. Timers fire automatically as
 * the clock advances.
 */
final class Harness implements RotationListener {

    static final float PX = 10f;
    static final long FRAME_MS = 10;

    /** A recorded callback (copied, since the detector reuses its event object). */
    static final class Ev {
        final String type;
        final long time;
        final int steps;
        final int netSteps;
        final boolean repeat;
        final boolean clockwise;
        final float angle;
        final float delta;
        final RotationEvent.Variant variant;
        final RotationEvent.EndReason endReason;
        final RotationEvent.RejectReason rejectReason;

        Ev(String type, RotationEvent e) {
            this.type = type;
            time = e.timeMs;
            steps = e.steps;
            netSteps = e.netSteps;
            repeat = e.repeat;
            clockwise = e.clockwise;
            angle = e.angleDeg;
            delta = e.deltaDeg;
            variant = e.variant;
            endReason = e.endReason;
            rejectReason = e.rejectReason;
        }

        @Override
        public String toString() {
            return type + "@" + time + " steps=" + steps + " net=" + netSteps + (repeat ? " repeat" : "")
                    + " cw=" + clockwise + " angle=" + angle + " delta=" + delta + " " + variant
                    + (endReason != null ? " " + endReason : "")
                    + (rejectReason != null ? " " + rejectReason : "");
        }
    }

    private interface Frame {
        void at(float fraction);
    }

    final RotationDetector detector;
    final List<Ev> events = new ArrayList<>();
    long now;
    long deadline = -1;
    float ax;
    float ay;
    float bx;
    float by;

    Harness(RotationConfig config) {
        detector = new RotationDetector(config, PX, this);
    }

    // ---- listener -----------------------------------------------------------------------------

    @Override
    public void onRotationArmed(RotationEvent e) {
        events.add(new Ev("armed", e));
    }

    @Override
    public void onRotationStart(RotationEvent e) {
        events.add(new Ev("start", e));
    }

    @Override
    public void onRotationStep(RotationEvent e) {
        events.add(new Ev("step", e));
    }

    @Override
    public void onRotationAngle(RotationEvent e) {
        events.add(new Ev("angle", e));
    }

    @Override
    public void onRotationDwell(RotationEvent e) {
        events.add(new Ev("dwell", e));
    }

    @Override
    public void onRotationEnd(RotationEvent e) {
        events.add(new Ev("end", e));
    }

    @Override
    public void onRotationTwist(RotationEvent e) {
        events.add(new Ev("twist", e));
    }

    @Override
    public void onRotationRejected(RotationEvent e) {
        events.add(new Ev("rejected", e));
    }

    @Override
    public void onRotationDeadline(long uptimeMs) {
        deadline = uptimeMs;
    }

    // ---- clock --------------------------------------------------------------------------------

    /** Moves the clock to {@code t}, firing timers that fall due on the way. */
    void advanceTo(long t) {
        while (deadline >= 0 && deadline <= t) {
            long due = deadline;
            now = Math.max(now, due);
            detector.onTimeout(now);
            if (deadline == due) {
                break;
            }
        }
        now = Math.max(now, t);
    }

    Harness hold(long ms) {
        advanceTo(now + ms);
        return this;
    }

    // ---- gestures -----------------------------------------------------------------------------

    Harness down(float axMm, float ayMm, float bxMm, float byMm) {
        return downStaggered(axMm, ayMm, bxMm, byMm, 0);
    }

    /** Finger A lands, then finger B {@code delayMs} later. */
    Harness downStaggered(float axMm, float ayMm, float bxMm, float byMm, long delayMs) {
        ax = axMm * PX;
        ay = ayMm * PX;
        bx = bxMm * PX;
        by = byMm * PX;
        detector.onTouchEvent(RotationDetector.ACTION_DOWN, now, 0, 1,
                new int[] {0}, new float[] {ax}, new float[] {ay});
        advanceTo(now + delayMs);
        detector.onTouchEvent(RotationDetector.ACTION_DOWN, now, 1, 2, ids(), xs(), ys());
        return this;
    }

    /** Both fingers turn about their midpoint; positive = clockwise on screen. */
    Harness rotate(final float sweepDeg, long durMs) {
        final float mx = (ax + bx) / 2f;
        final float my = (ay + by) / 2f;
        final double half = Math.hypot(bx - ax, by - ay) / 2;
        final double phi0 = Math.atan2(by - ay, bx - ax);
        return animate(durMs, f -> {
            double phi = phi0 + Math.toRadians(sweepDeg) * f;
            ax = (float) (mx - half * Math.cos(phi));
            ay = (float) (my - half * Math.sin(phi));
            bx = (float) (mx + half * Math.cos(phi));
            by = (float) (my + half * Math.sin(phi));
        });
    }

    /** Finger A stays put; finger B circles around it. */
    Harness pivot(final float sweepDeg, long durMs) {
        final float px = ax;
        final float py = ay;
        final double r = Math.hypot(bx - ax, by - ay);
        final double phi0 = Math.atan2(by - ay, bx - ax);
        return animate(durMs, f -> {
            double phi = phi0 + Math.toRadians(sweepDeg) * f;
            bx = (float) (px + r * Math.cos(phi));
            by = (float) (py + r * Math.sin(phi));
        });
    }

    /** Both fingers move the same way (a two-finger scroll). */
    Harness translate(final float dxMm, final float dyMm, long durMs) {
        final float ax0 = ax;
        final float ay0 = ay;
        final float bx0 = bx;
        final float by0 = by;
        return animate(durMs, f -> {
            ax = ax0 + dxMm * PX * f;
            ay = ay0 + dyMm * PX * f;
            bx = bx0 + dxMm * PX * f;
            by = by0 + dyMm * PX * f;
        });
    }

    /** Fingers spread (factor > 1) or close about their midpoint. */
    Harness scale(final float factor, long durMs) {
        final float mx = (ax + bx) / 2f;
        final float my = (ay + by) / 2f;
        final float ax0 = ax;
        final float ay0 = ay;
        final float bx0 = bx;
        final float by0 = by;
        return animate(durMs, f -> {
            float s = 1f + (factor - 1f) * f;
            ax = mx + (ax0 - mx) * s;
            ay = my + (ay0 - my) * s;
            bx = mx + (bx0 - mx) * s;
            by = my + (by0 - my) * s;
        });
    }

    /** One finger from each hand: A drags up, B drags down, by {@code dyMm} each. */
    Harness opposite(final float dyMm, long durMs) {
        final float ay0 = ay;
        final float by0 = by;
        return animate(durMs, f -> {
            ay = ay0 - dyMm * PX * f;
            by = by0 + dyMm * PX * f;
        });
    }

    Harness thirdFinger() {
        detector.onTouchEvent(RotationDetector.ACTION_DOWN, now, 2, 3,
                new int[] {0, 1, 2}, new float[] {ax, bx, (ax + bx) / 2f}, new float[] {ay, by, ay + 200f});
        return this;
    }

    Harness liftAll() {
        detector.onTouchEvent(RotationDetector.ACTION_UP, now, 1, 2, ids(), xs(), ys());
        detector.onTouchEvent(RotationDetector.ACTION_UP, now, 0, 1,
                new int[] {0}, new float[] {ax}, new float[] {ay});
        return this;
    }

    private Harness animate(long durMs, Frame frame) {
        int frames = (int) Math.max(1, durMs / FRAME_MS);
        long start = now;
        for (int i = 1; i <= frames; i++) {
            frame.at((float) i / frames);
            advanceTo(start + durMs * i / frames);
            detector.onTouchEvent(RotationDetector.ACTION_MOVE, now, 0, 2, ids(), xs(), ys());
        }
        return this;
    }

    private int[] ids() {
        return new int[] {0, 1};
    }

    private float[] xs() {
        return new float[] {ax, bx};
    }

    private float[] ys() {
        return new float[] {ay, by};
    }

    // ---- queries ------------------------------------------------------------------------------

    List<Ev> of(String type) {
        List<Ev> out = new ArrayList<>();
        for (Ev e : events) {
            if (e.type.equals(type)) {
                out.add(e);
            }
        }
        return out;
    }

    int count(String type) {
        return of(type).size();
    }

    Ev first(String type) {
        List<Ev> list = of(type);
        return list.isEmpty() ? null : list.get(0);
    }

    int stepSum() {
        int sum = 0;
        for (Ev e : of("step")) {
            sum += e.steps;
        }
        return sum;
    }
}
