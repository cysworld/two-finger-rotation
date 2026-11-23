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
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

/**
 * Replays touches recorded on a real phone. Guards against changes that would alter what a real
 * tester got; if a change is intended, update the expected numbers together with design.md.
 */
public class RecordedTraceTest {

    private static String read(String name) throws IOException {
        try (InputStream in = RecordedTraceTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("missing " + name);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static long count(Harness h, String type, Object detail) {
        return h.events.stream()
                .filter(e -> e.type.equals(type))
                .filter(e -> detail == null || detail == e.variant || detail == e.rejectReason)
                .count();
    }

    @Test
    public void firstDeviceSessionWithMediumPreset() throws IOException {
        String trace = read("/traces/first-device-2026-11-23.txt");
        Harness h = new Harness(RotationConfig.medium());
        h.detector.setPixelsPerMm(RotationTrace.readPixelsPerMm(trace, Harness.PX));
        assertTrue(RotationTrace.replay(trace, h.detector) > 1000);

        assertEquals(37, count(h, "start", null));
        assertEquals(21, count(h, "start", RotationEvent.Variant.BOTH));
        assertEquals(16, count(h, "start", RotationEvent.Variant.PIVOT));
        assertEquals(37, count(h, "end", null));
        assertEquals(4, count(h, "rejected", RotationEvent.RejectReason.PINCH));
        assertEquals(1, count(h, "rejected", RotationEvent.RejectReason.PAN));
    }
}
