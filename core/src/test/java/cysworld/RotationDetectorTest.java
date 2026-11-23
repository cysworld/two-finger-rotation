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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import cysworld.Harness.Ev;
import cysworld.RotationEvent.EndReason;
import cysworld.RotationEvent.RejectReason;
import cysworld.RotationEvent.Variant;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/** Behaviour of {@link RotationDetector}; numbers refer to docs/design.md. */
public class RotationDetectorTest {

    /** Fingers 30 mm apart, side by side. */
    private static Harness with(RotationConfig c) {
        return new Harness(c).down(35, 80, 65, 80);
    }

    private static Harness medium() {
        return with(RotationConfig.medium());
    }

    // ---- basic turning ----------------------------------------------------------------------

    @Test
    public void clockwiseTurnStartsAndStepsForward() {
        Harness h = medium().rotate(100, 500);
        assertEquals(1, h.count("start"));
        assertEquals(Variant.BOTH, h.first("start").variant);
        assertEquals(3, h.stepSum()); // detents at 12°, 42°, 72°
        for (Ev e : h.of("step")) {
            assertEquals(1, e.steps);
            assertTrue(e.clockwise);
        }
        h.liftAll();
        Ev end = h.first("end");
        assertEquals(EndReason.LIFT, end.endReason);
        assertEquals(3, end.netSteps);
        assertEquals(RotationDetector.State.IDLE, h.detector.getState());
    }

    @Test
    public void counterClockwiseTurnStepsBackward() {
        Harness h = medium().rotate(-100, 500);
        assertEquals(-3, h.stepSum());
        for (Ev e : h.of("step")) {
            assertFalse(e.clockwise);
        }
    }

    @Test
    public void invertDirectionMakesCounterClockwiseForward() {
        RotationConfig c = RotationConfig.medium();
        c.invertDirection = true;
        Harness h = with(c).rotate(100, 500);
        assertEquals(-3, h.stepSum());
        assertTrue(h.first("step").clockwise);
    }

    @Test
    public void reversingNeedsTheHysteresis() {
        Harness h = medium().rotate(50, 250); // detents at 12° and 42°
        assertEquals(2, h.stepSum());
        h.rotate(-46, 230); // back to 4°: 38° from the last detent, short of 30° + 10°
        assertEquals(2, h.stepSum());
        h.rotate(-4, 20); // back to 0°: 42°
        assertEquals(1, h.stepSum());
    }

    @Test
    public void stepOnStartOffCountsFromTheLandingAngle() {
        RotationConfig c = RotationConfig.medium();
        c.stepOnStart = false;
        Harness h = with(c).rotate(100, 500); // detents at 30°, 60°, 90°
        assertEquals(3, h.stepSum());
        assertTrue(h.first("step").time > h.first("start").time);
    }

    @Test
    public void angleEventsAddUpToTheReportedAngle() {
        Harness h = medium().rotate(100, 500);
        List<Ev> angles = h.of("angle");
        float sum = 0f;
        for (Ev e : angles) {
            assertTrue(e.delta > 0);
            sum += e.delta;
        }
        assertEquals(angles.get(angles.size() - 1).angle, sum, 0.01f);
        assertEquals(88f, sum, 2.5f); // 100° minus the 12° start angle
    }

    @Test
    public void accelerationAddsStepsWhenTurningFast() {
        RotationConfig c = RotationConfig.medium();
        c.accelVelocityDegPerSec = 300f;
        c.minStepAngleDeg = 10f;
        int slow = with(c).rotate(180, 1500).stepSum(); // 120°/s: no acceleration
        int fast = with(c).rotate(180, 150).stepSum(); // 1200°/s
        assertEquals(6, slow);
        assertTrue("fast=" + fast, fast > slow);
    }

    // ---- what is not a rotation ----------------------------------------------------------------

    @Test
    public void pinchIsRejected() {
        Harness h = medium().scale(1.5f, 300);
        assertEquals(0, h.count("start"));
        assertEquals(RejectReason.PINCH, h.first("rejected").rejectReason);
        assertFalse(h.detector.isUndecided());
    }

    @Test
    public void panIsRejected() {
        Harness h = medium().translate(0, 20, 300);
        assertEquals(0, h.count("start"));
        assertEquals(RejectReason.PAN, h.first("rejected").rejectReason);
    }

    @Test
    public void turnWithSomeDriftStillStarts() {
        // 3.5 mm of drift after 10°: within 1.5 × the 2.6 mm arc.
        Harness h = medium().rotate(10, 50).translate(3.5f, 0, 50).rotate(20, 100);
        assertEquals(0, h.count("rejected"));
        assertEquals(1, h.count("start"));
    }

    @Test
    public void fingersTooCloseAreRejected() {
        Harness h = new Harness(RotationConfig.medium()).down(45, 80, 55, 80).rotate(40, 300);
        assertEquals(0, h.count("start"));
        assertEquals(RejectReason.DISTANCE, h.first("rejected").rejectReason);
    }

    @Test
    public void secondFingerTooLateIsRejected() {
        RotationConfig c = RotationConfig.medium();
        c.maxSecondFingerDelayMs = 250;
        Harness h = new Harness(c).downStaggered(35, 80, 65, 80, 400);
        assertEquals(RejectReason.SECOND_FINGER_LATE, h.first("rejected").rejectReason);
    }

    // ---- ways of turning --------------------------------------------------------------------

    @Test
    public void pivotTurnStartsAtThePivotAngle() {
        Harness h = medium().pivot(40, 400); // 1° per frame
        Ev start = h.first("start");
        assertNotNull(start);
        assertEquals(Variant.PIVOT, start.variant);
        assertTrue("start at " + start.time, start.time >= 180 && start.time <= 190); // 18°
        assertEquals(1, h.stepSum()); // the next detent would be at 48°
    }

    @Test
    public void pivotCanBeDisallowed() {
        RotationConfig c = RotationConfig.medium();
        c.allowPivot = false;
        Harness h = with(c).pivot(40, 400);
        assertEquals(0, h.count("start"));
        assertEquals(RejectReason.PIVOT_NOT_ALLOWED, h.first("rejected").rejectReason);
    }

    @Test
    public void oneFingerFromEachHandDraggingOppositeWays() {
        Harness h = new Harness(RotationConfig.medium()).down(20, 80, 80, 80).opposite(20, 400);
        assertEquals(1, h.count("start"));
        assertEquals(Variant.BOTH, h.first("start").variant);
        assertEquals(1, h.stepSum()); // ends at about 33.7°
    }

    // ---- angle and time together (author decision 4) ----------------------------------------

    @Test
    public void angleReachedEarlyWaitsForTheDuration() {
        RotationConfig c = RotationConfig.medium();
        c.minRotateDurationMs = 300;
        Harness h = with(c).rotate(40, 100); // onset (3°) at t=10, 12° by t=30
        assertEquals(0, h.count("start"));
        h.hold(400);
        assertEquals(310, h.first("start").time); // onset + 300 ms
    }

    @Test
    public void durationWithoutTheAngleNeverStarts() {
        RotationConfig c = RotationConfig.medium();
        c.minRotateDurationMs = 300;
        Harness h = with(c).rotate(8, 1000).hold(500);
        assertEquals(0, h.count("start"));
        assertEquals(0, h.count("rejected"));
        assertTrue(h.detector.isUndecided());
    }

    @Test
    public void turningBackBeforeTheDurationCancelsTheStart() {
        RotationConfig c = RotationConfig.medium();
        c.minRotateDurationMs = 300;
        Harness h = with(c).rotate(20, 100).rotate(-15, 100).hold(400);
        assertEquals(0, h.count("start"));
    }

    @Test
    public void durationCanBeMeasuredFromTheSecondFinger() {
        RotationConfig c = RotationConfig.medium();
        c.minRotateDurationMs = 300;
        c.durationFromSecondFinger = true;
        Harness h = with(c).hold(200).rotate(40, 100); // 12° at about t=230
        assertEquals(300, h.first("start").time);
    }

    // ---- hold before turning ----------------------------------------------------------------

    @Test
    public void holdFirstRejectsAnImmediateTurn() {
        RotationConfig c = RotationConfig.medium();
        c.holdBeforeRotateMs = 300;
        Harness h = with(c).rotate(40, 300);
        assertEquals(RejectReason.NOT_HELD, h.first("rejected").rejectReason);
        assertEquals(0, h.count("armed"));
    }

    @Test
    public void holdFirstThenTurnStarts() {
        RotationConfig c = RotationConfig.medium();
        c.holdBeforeRotateMs = 300;
        Harness h = with(c).hold(300);
        assertEquals(1, h.count("armed"));
        h.rotate(40, 300);
        assertEquals(1, h.count("start"));
    }

    // ---- time-based features ----------------------------------------------------------------

    @Test
    public void dwellFiresOncePerPause() {
        RotationConfig c = RotationConfig.medium();
        c.dwellMs = 500;
        Harness h = with(c).rotate(50, 250).hold(600);
        assertEquals(1, h.count("dwell"));
        h.hold(600);
        assertEquals(1, h.count("dwell"));
        h.rotate(10, 50).hold(600);
        assertEquals(2, h.count("dwell"));
    }

    @Test
    public void holdToRepeatKeepsStepping() {
        RotationConfig c = RotationConfig.medium();
        c.repeatDelayMs = 400;
        Harness h = with(c).rotate(90, 450);
        assertEquals(3, h.stepSum()); // 12°, 42°, 72°
        h.hold(2000);
        List<Ev> repeats = new ArrayList<>();
        for (Ev e : h.of("step")) {
            if (e.repeat) {
                repeats.add(e);
            }
        }
        assertTrue("repeats=" + repeats.size(), repeats.size() >= 4);
        for (Ev e : repeats) {
            assertEquals(1, e.steps);
        }
    }

    // ---- twists -----------------------------------------------------------------------------

    @Test
    public void twistOnlyReportsQuickTwists() {
        RotationConfig c = RotationConfig.medium();
        c.twistMode = RotationConfig.TwistMode.TWIST_ONLY;
        Harness quick = with(c).rotate(40, 100).liftAll();
        assertEquals(1, quick.count("twist"));
        assertEquals(1, quick.first("twist").steps);
        assertEquals(0, quick.count("start"));

        Harness slow = with(c).rotate(40, 1000).liftAll();
        assertEquals(0, slow.count("twist"));
        assertEquals(RejectReason.TWIST_TOO_SLOW, slow.first("rejected").rejectReason);
    }

    @Test
    public void twistOrDialSeparatesByDuration() {
        RotationConfig c = RotationConfig.medium();
        c.twistMode = RotationConfig.TwistMode.TWIST_OR_DIAL;
        Harness quick = with(c).rotate(-40, 100).liftAll();
        assertEquals(1, quick.count("twist"));
        assertEquals(-1, quick.first("twist").steps);
        assertEquals(0, quick.count("start"));

        Harness slow = with(c).rotate(60, 600).liftAll();
        assertEquals(0, slow.count("twist"));
        assertEquals(1, slow.count("start"));
    }

    // ---- ending -----------------------------------------------------------------------------

    @Test
    public void thirdFingerEndsTheRotation() {
        Harness h = medium().rotate(50, 250).thirdFinger();
        assertEquals(EndReason.EXTRA_FINGER, h.first("end").endReason);
        int steps = h.stepSum();
        h.rotate(60, 300);
        assertEquals(steps, h.stepSum());
    }

    @Test
    public void cancelIgnoresTheRestOfTheTouch() {
        Harness h = medium().rotate(50, 250);
        h.detector.cancel(h.now);
        assertEquals(EndReason.CANCEL, h.first("end").endReason);
        int steps = h.stepSum();
        h.rotate(60, 300).liftAll();
        assertEquals(steps, h.stepSum());
        h.down(35, 80, 65, 80).rotate(50, 250); // a new touch works again
        assertEquals(2, h.count("start"));
    }

    @Test
    public void disabledReportsNothing() {
        RotationConfig c = RotationConfig.medium();
        c.enabled = false;
        Harness h = with(c).rotate(100, 500).liftAll();
        assertTrue(h.events.toString(), h.events.isEmpty());
    }

    @Test
    public void disablingMidTurnEndsTheRotation() {
        Harness h = medium().rotate(50, 250);
        RotationConfig off = RotationConfig.medium();
        off.enabled = false;
        h.detector.setConfig(off);
        assertEquals(EndReason.DISABLED, h.first("end").endReason);
    }

    // ---- configuration ----------------------------------------------------------------------

    @Test
    public void presetsAreValid() {
        RotationConfig.medium().validate();
        RotationConfig.slow().validate();
        RotationConfig.fast().validate();
        for (RotationConfig.Sensitivity s : RotationConfig.Sensitivity.values()) {
            RotationConfig.medium().sensitivity(s).validate();
            RotationConfig.fast().sensitivity(s).validate();
        }
    }

    @Test
    public void invalidConfigIsRefused() {
        final RotationConfig c = RotationConfig.medium();
        c.stepAngleDeg = 0f;
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Harness(c));
        assertTrue(e.getMessage(), e.getMessage().contains("stepAngleDeg"));
    }
}
