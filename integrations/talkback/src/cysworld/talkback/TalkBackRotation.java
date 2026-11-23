/*
 * 双指旋转 two-finger rotation
 * Copyright (C) 2026 程溁 <chengying@cys.world>
 * SPDX-License-Identifier: GPL-3.0-only OR LicenseRef-Commercial
 *
 * 按 GPL-3.0 授权时，适用 LICENSING.md 中的附加条款（GPL-3.0 第 7 条）；也可以取得商业授权。
 * Under GPL-3.0, the additional terms (section 7) in LICENSING.md apply; a commercial license
 * is also available. See LICENSING.md.
 */
package cysworld.talkback;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import cysworld.RotationConfig;
import cysworld.RotationDetector;
import cysworld.RotationEvent;
import cysworld.RotationListener;

/**
 * Glue between TalkBack's {@code TouchInteractionMonitor} and the two-finger rotation recognizer.
 *
 * <p>Feed every motion event the monitor receives to {@link #onMotionEvent}. While {@link
 * #isUndecided()} is true the monitor must hold the two-finger touch (no dragging, no delegating).
 * Each detent is reported to the host as a gesture id, which TalkBack maps to "select next/previous
 * reading control".
 *
 * <p>This example uses the medium preset and none of the timer-based features (dwell, repeat,
 * hold-then-turn, twist), so it needs no timer. Call it on the monitor's thread only.
 */
public final class TalkBackRotation implements RotationListener {

    /** Gesture id for one detent clockwise (forward). */
    public static final int GESTURE_ROTATE_CW = -110;
    /** Gesture id for one detent counter-clockwise (backward). */
    public static final int GESTURE_ROTATE_CCW = -111;

    private static final int MAX_POINTERS = 16;
    private static final int MAX_STEPS_PER_EVENT = 12;

    /** What the monitor does for the rotation. */
    public interface Host {
        /** The touch is now a rotation; stop TalkBack's own gesture handling for it. */
        void onRotationStarted();

        /** Dispatch {@code gestureId} to the service, without ending the touch interaction. */
        void dispatchGesture(int gestureId);
    }

    private final Host host;
    private final RotationDetector detector;
    private final int[] ids = new int[MAX_POINTERS];
    private final float[] xs = new float[MAX_POINTERS];
    private final float[] ys = new float[MAX_POINTERS];

    public TalkBackRotation(Context context, Host host) {
        this.host = host;
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        float dpi = dm.xdpi > 0 ? dm.xdpi : dm.densityDpi;
        detector = new RotationDetector(RotationConfig.medium(), dpi / 25.4f, this);
    }

    /** True while two fingers are down and the recognizer has not decided yet. */
    public boolean isUndecided() {
        return detector.isUndecided();
    }

    /**
     * Feeds one motion event.
     *
     * @param eligible false once the touch belongs to someone else (touch exploration, dragging,
     *     delegating); the recognizer then stays out of this touch
     * @return true if the rotation owns this touch, so TalkBack should not handle the event
     */
    public boolean onMotionEvent(MotionEvent e, boolean eligible) {
        int action;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                action = RotationDetector.ACTION_DOWN;
                break;
            case MotionEvent.ACTION_MOVE:
                action = RotationDetector.ACTION_MOVE;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                action = RotationDetector.ACTION_UP;
                break;
            case MotionEvent.ACTION_CANCEL:
                action = RotationDetector.ACTION_CANCEL;
                break;
            default:
                return owns();
        }
        if (!eligible && !owns() && action != RotationDetector.ACTION_CANCEL) {
            if (detector.getState() != RotationDetector.State.IDLE) {
                detector.reset();
            }
            return false;
        }
        int n = Math.min(e.getPointerCount(), MAX_POINTERS);
        for (int i = 0; i < n; i++) {
            ids[i] = e.getPointerId(i);
        }
        if (action == RotationDetector.ACTION_MOVE) {
            for (int k = 0; k < e.getHistorySize(); k++) {
                for (int i = 0; i < n; i++) {
                    xs[i] = e.getHistoricalX(i, k);
                    ys[i] = e.getHistoricalY(i, k);
                }
                detector.onTouchEvent(action, e.getHistoricalEventTime(k), -1, n, ids, xs, ys);
            }
        }
        for (int i = 0; i < n; i++) {
            xs[i] = e.getX(i);
            ys[i] = e.getY(i);
        }
        boolean ownedBefore = owns();
        detector.onTouchEvent(action, e.getEventTime(), e.getPointerId(e.getActionIndex()), n, ids, xs, ys);
        // The last UP resets the recognizer, so remember that this touch was a rotation.
        return ownedBefore || owns();
    }

    private boolean owns() {
        RotationDetector.State s = detector.getState();
        return s == RotationDetector.State.ROTATING || s == RotationDetector.State.ENDED;
    }

    @Override
    public void onRotationStart(RotationEvent e) {
        host.onRotationStarted();
    }

    @Override
    public void onRotationStep(RotationEvent e) {
        int id = e.steps > 0 ? GESTURE_ROTATE_CW : GESTURE_ROTATE_CCW;
        int count = Math.min(Math.abs(e.steps), MAX_STEPS_PER_EVENT);
        for (int i = 0; i < count; i++) {
            host.dispatchGesture(id);
        }
    }
}
