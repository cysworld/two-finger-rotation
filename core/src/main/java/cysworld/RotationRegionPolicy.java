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

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/**
 * Decides whether the rotation may be used, from country codes the host supplies (for example the
 * SIM, network and locale countries). If any supplied code is blocked on the given date, the rotation
 * is not allowed.
 *
 * <p>It starts with no rules. This is a tool, not legal advice: the vendor decides which regions
 * to block and until when, based on its own review of the laws, including patents, where its
 * product is used.
 */
public final class RotationRegionPolicy {

    /** ISO 3166 alpha-2 code (upper case) to first allowed day as yyyymmdd; 0 = blocked forever. */
    private final Map<String, Integer> blocked = new LinkedHashMap<>();

    /** Blocks a country until the day before {@code allowFromYyyymmdd} (UTC); 0 blocks it forever. */
    public RotationRegionPolicy block(String countryIso, int allowFromYyyymmdd) {
        if (allowFromYyyymmdd < 0) {
            throw new IllegalArgumentException("allowFromYyyymmdd");
        }
        blocked.put(normalize(countryIso), allowFromYyyymmdd);
        return this;
    }

    /** Blocks a country with no end date. */
    public RotationRegionPolicy blockForever(String countryIso) {
        return block(countryIso, 0);
    }

    /** Removes any rule for a country. */
    public RotationRegionPolicy unblock(String countryIso) {
        blocked.remove(normalize(countryIso));
        return this;
    }

    /**
     * Returns false if any of the given country codes is blocked at {@code nowEpochMs}. Null or
     * blank codes are ignored, so pass whatever the device reports.
     */
    public boolean isAllowed(long nowEpochMs, String... countryIsos) {
        int today = yyyymmdd(nowEpochMs);
        for (String iso : countryIsos) {
            if (iso == null || iso.trim().isEmpty()) {
                continue;
            }
            Integer allowFrom = blocked.get(normalize(iso));
            if (allowFrom != null && (allowFrom == 0 || today < allowFrom)) {
                return false;
            }
        }
        return true;
    }

    static int yyyymmdd(long epochMs) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        c.setTimeInMillis(epochMs);
        return c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH);
    }

    private static String normalize(String countryIso) {
        if (countryIso == null || countryIso.trim().isEmpty()) {
            throw new IllegalArgumentException("countryIso");
        }
        return countryIso.trim().toUpperCase(Locale.ROOT);
    }
}
