package com.taryo.appalarm;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Japanese public holidays, computed from the statutory rules:
 * fixed dates, Happy-Monday dates, the two equinoxes, substitute holidays
 * (振替休日) and the "national holiday" sandwiched between two holidays
 * (国民の休日, e.g. the September Silver Week).
 */
public final class JapaneseHolidays {

    private static final java.util.Map<Integer, Set<LocalDate>> CACHE = new java.util.HashMap<>();

    private JapaneseHolidays() {
    }

    public static boolean isHoliday(LocalDate d) {
        return holidays(d.getYear()).contains(d);
    }

    public static synchronized Set<LocalDate> holidays(int year) {
        Set<LocalDate> cached = CACHE.get(year);
        if (cached != null) return cached;
        Set<LocalDate> s = new HashSet<>();
        s.add(LocalDate.of(year, 1, 1));    // 元日
        s.add(nthMonday(year, 1, 2));        // 成人の日
        s.add(LocalDate.of(year, 2, 11));   // 建国記念の日
        s.add(LocalDate.of(year, 2, 23));   // 天皇誕生日
        s.add(LocalDate.of(year, 3, equinoxDay(year, true)));   // 春分の日
        s.add(LocalDate.of(year, 4, 29));   // 昭和の日
        s.add(LocalDate.of(year, 5, 3));    // 憲法記念日
        s.add(LocalDate.of(year, 5, 4));    // みどりの日
        s.add(LocalDate.of(year, 5, 5));    // こどもの日
        s.add(nthMonday(year, 7, 3));        // 海の日
        s.add(LocalDate.of(year, 8, 11));   // 山の日
        s.add(nthMonday(year, 9, 3));        // 敬老の日
        s.add(LocalDate.of(year, 9, equinoxDay(year, false)));  // 秋分の日
        s.add(nthMonday(year, 10, 2));       // スポーツの日
        s.add(LocalDate.of(year, 11, 3));   // 文化の日
        s.add(LocalDate.of(year, 11, 23));  // 勤労感謝の日

        // 振替休日: when a holiday falls on Sunday, the next non-holiday day is a holiday.
        List<LocalDate> sorted = new ArrayList<>(s);
        Collections.sort(sorted);
        for (LocalDate h : sorted) {
            if (h.getDayOfWeek() == DayOfWeek.SUNDAY) {
                LocalDate next = h.plusDays(1);
                while (s.contains(next)) next = next.plusDays(1);
                s.add(next);
            }
        }

        // 国民の休日: a single day between two holidays becomes a holiday.
        Set<LocalDate> extra = new HashSet<>();
        LocalDate cursor = LocalDate.of(year, 1, 2);
        LocalDate end = LocalDate.of(year, 12, 30);
        while (!cursor.isAfter(end)) {
            if (!s.contains(cursor)
                    && s.contains(cursor.minusDays(1))
                    && s.contains(cursor.plusDays(1))) {
                extra.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        s.addAll(extra);

        CACHE.put(year, s);
        return s;
    }

    private static LocalDate nthMonday(int year, int month, int n) {
        LocalDate d = LocalDate.of(year, month, 1);
        while (d.getDayOfWeek() != DayOfWeek.MONDAY) d = d.plusDays(1);
        return d.plusWeeks(n - 1);
    }

    /** Approximate the equinox day for 1980-2099 (standard Japanese formula). */
    private static int equinoxDay(int year, boolean spring) {
        double base = spring ? 20.8431 : 23.2488;
        double v = base + 0.242194 * (year - 1980) - Math.floor((year - 1980) / 4.0);
        return (int) Math.floor(v);
    }
}
