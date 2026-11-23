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
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class RotationTraceTest {

    @Test
    public void replayReproducesTheSameEvents() {
        RotationConfig c = RotationConfig.medium();
        c.dwellMs = 300;
        StringBuilder trace = new StringBuilder();
        Harness rec = new Harness(c);
        rec.detector.setTraceSink(trace);
        rec.down(35, 80, 65, 80).rotate(70, 350).hold(400).rotate(-50, 250).liftAll();
        assertEquals(1, rec.count("dwell"));

        assertEquals(Harness.PX, RotationTrace.readPixelsPerMm(trace, -1f), 0f);
        Harness play = new Harness(c);
        RotationTrace.replay(trace, play.detector);
        assertEquals(rec.events.toString(), play.events.toString());
    }

    @Test
    public void badLineIsReported() {
        final RotationDetector d = new Harness(RotationConfig.medium()).detector;
        assertThrows(IllegalArgumentException.class, () -> RotationTrace.replay("M 10 0 garbage", d));
    }
}
