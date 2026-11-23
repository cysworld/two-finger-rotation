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
 * Plain-text touch traces: record what a detector receives on a device
 * ({@link RotationDetector#setTraceSink}) and replay it on the JVM to tune parameters or to write
 * regression tests.
 *
 * <p>Format, one call per line:
 * <pre>
 * # rotation-trace 1 pxPerMm=17.3
 * D 1000 0 0:512.0,900.0                  touch: D/M/U/C, time, action pointer id, id:x,y ...
 * D 1040 1 0:512.0,900.0 1:800.0,880.0
 * M 1050 0 0:510.5,903.2 1:801.0,876.4
 * T 1400                                  timeout fired at this time
 * </pre>
 * Lines starting with {@code #} are comments.
 */
public final class RotationTrace {

    /** First line of every trace. */
    public static final String HEADER = "# rotation-trace 1";

    private RotationTrace() {}

    static String header(float pxPerMm) {
        return HEADER + " pxPerMm=" + pxPerMm + "\n";
    }

    static void appendTouch(StringBuilder sb, int action, long timeMs, int actionPointerId,
                            int pointerCount, int[] ids, float[] xs, float[] ys) {
        sb.append(codeFor(action)).append(' ').append(timeMs).append(' ').append(actionPointerId);
        for (int i = 0; i < pointerCount; i++) {
            sb.append(' ').append(ids[i]).append(':').append(xs[i]).append(',').append(ys[i]);
        }
        sb.append('\n');
    }

    static void appendTimeout(StringBuilder sb, long timeMs) {
        sb.append("T ").append(timeMs).append('\n');
    }

    /** Reads the screen density from the header, or returns {@code fallback} if there is none. */
    public static float readPixelsPerMm(CharSequence trace, float fallback) {
        for (String line : trace.toString().split("\r?\n")) {
            String s = line.trim();
            if (s.startsWith(HEADER)) {
                int i = s.indexOf("pxPerMm=");
                if (i >= 0) {
                    String v = s.substring(i + "pxPerMm=".length()).trim().split("\\s+")[0];
                    return Float.parseFloat(v);
                }
            }
        }
        return fallback;
    }

    /**
     * Feeds every line of {@code trace} to {@code detector}. Recorded timeouts are replayed as
     * recorded. Returns the number of calls made.
     *
     * @throws IllegalArgumentException if a line cannot be parsed
     */
    public static int replay(CharSequence trace, RotationDetector detector) {
        String[] lines = trace.toString().split("\r?\n");
        int calls = 0;
        for (int n = 0; n < lines.length; n++) {
            String line = lines[n].trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            try {
                String[] tok = line.split("\\s+");
                char code = tok[0].charAt(0);
                long t = Long.parseLong(tok[1]);
                if (code == 'T') {
                    detector.onTimeout(t);
                } else {
                    int action = actionFor(code);
                    int actionPointerId = Integer.parseInt(tok[2]);
                    int count = tok.length - 3;
                    int[] ids = new int[count];
                    float[] xs = new float[count];
                    float[] ys = new float[count];
                    for (int i = 0; i < count; i++) {
                        String p = tok[3 + i];
                        int colon = p.indexOf(':');
                        int comma = p.indexOf(',', colon);
                        ids[i] = Integer.parseInt(p.substring(0, colon));
                        xs[i] = Float.parseFloat(p.substring(colon + 1, comma));
                        ys[i] = Float.parseFloat(p.substring(comma + 1));
                    }
                    detector.onTouchEvent(action, t, actionPointerId, count, ids, xs, ys);
                }
                calls++;
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Bad trace line " + (n + 1) + ": " + line, e);
            }
        }
        return calls;
    }

    private static char codeFor(int action) {
        switch (action) {
            case RotationDetector.ACTION_DOWN:
                return 'D';
            case RotationDetector.ACTION_MOVE:
                return 'M';
            case RotationDetector.ACTION_UP:
                return 'U';
            case RotationDetector.ACTION_CANCEL:
                return 'C';
            default:
                throw new IllegalArgumentException("action " + action);
        }
    }

    private static int actionFor(char code) {
        switch (code) {
            case 'D':
                return RotationDetector.ACTION_DOWN;
            case 'M':
                return RotationDetector.ACTION_MOVE;
            case 'U':
                return RotationDetector.ACTION_UP;
            case 'C':
                return RotationDetector.ACTION_CANCEL;
            default:
                throw new IllegalArgumentException("code " + code);
        }
    }
}
