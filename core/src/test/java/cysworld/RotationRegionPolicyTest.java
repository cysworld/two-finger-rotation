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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import org.junit.Test;

public class RotationRegionPolicyTest {

    private static long utc(int year, int month, int day) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.clear();
        c.set(year, month - 1, day);
        return c.getTimeInMillis();
    }

    @Test
    public void startsWithNoRules() {
        RotationRegionPolicy p = new RotationRegionPolicy();
        assertTrue(p.isAllowed(utc(2026, 11, 22), "AA", "CN", "US"));
    }

    @Test
    public void datedBlockEndsOnItsAllowDay() {
        RotationRegionPolicy p = new RotationRegionPolicy().block("aa", 20280301);
        assertFalse(p.isAllowed(utc(2026, 11, 22), "AA"));
        assertFalse(p.isAllowed(utc(2028, 2, 29), "aa"));
        assertTrue(p.isAllowed(utc(2028, 3, 1), "AA"));
        assertTrue(p.isAllowed(utc(2026, 11, 22), "BB"));
    }

    @Test
    public void anyBlockedCodeBlocksAndBlankCodesAreIgnored() {
        RotationRegionPolicy p = new RotationRegionPolicy().block("AA", 20280301);
        long now = utc(2026, 11, 22);
        assertFalse(p.isAllowed(now, "BB", "AA"));
        assertTrue(p.isAllowed(now, null, "", " bb "));
        assertTrue(p.isAllowed(now));
    }

    @Test
    public void customRules() {
        RotationRegionPolicy p = new RotationRegionPolicy().blockForever("xx");
        assertFalse(p.isAllowed(utc(2100, 1, 1), "XX"));
        p.unblock("XX");
        assertTrue(p.isAllowed(utc(2100, 1, 1), "XX"));
        p.block("YY", 20270101);
        assertFalse(p.isAllowed(utc(2026, 12, 31), "YY"));
        assertTrue(p.isAllowed(utc(2027, 1, 1), "YY"));
    }
}
