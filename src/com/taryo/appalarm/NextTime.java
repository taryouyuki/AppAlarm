package com.taryo.appalarm;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Computes the next moment an alarm should fire. */
public final class NextTime {

    private NextTime() {
    }

    public static long nextTrigger(AlarmEntry e, long nowMillis) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDateTime now = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMillis), zone);
        LocalDate today = now.toLocalDate();

        if (e.mode == AlarmEntry.MODE_DATE) {
            if (e.year <= 0 || e.month <= 0 || e.day <= 0) return 0L;
            LocalDate day;
            try {
                day = LocalDate.of(e.year, e.month, e.day);
            } catch (Exception ex) {
                return 0L;
            }
            LocalDateTime candidate = day.atTime(e.hour, e.minute);
            if (!candidate.isAfter(now)) return 0L;
            if (e.skipHolidays && JapaneseHolidays.isHoliday(day)) return 0L;
            return candidate.atZone(zone).toInstant().toEpochMilli();
        }

        if (e.mode == AlarmEntry.MODE_PERDAY) {
            // A matching weekday inside the next week can itself be a holiday, so the
            // following one may be two weeks out. Stopping at 8 days drops the alarm.
            for (int i = 0; i <= 400; i++) {
                LocalDate day = today.plusDays(i);
                int idx = day.getDayOfWeek().getValue() - 1;
                if (e.perDayHour[idx] < 0) continue;
                LocalDateTime candidate = day.atTime(e.perDayHour[idx], e.perDayMinute[idx]);
                if (!candidate.isAfter(now)) continue;
                if (e.skipHolidays && JapaneseHolidays.isHoliday(day)) continue;
                return candidate.atZone(zone).toInstant().toEpochMilli();
            }
            return 0L;
        }

        for (int i = 0; i <= 400; i++) {
            LocalDate day = today.plusDays(i);
            LocalDateTime candidate = day.atTime(e.hour, e.minute);
            if (!candidate.isAfter(now)) continue;

            if (e.mode == AlarmEntry.MODE_WEEKLY) {
                int bit = 1 << (day.getDayOfWeek().getValue() - 1);
                if ((e.days & bit) == 0) continue;
            }
            if (e.skipHolidays && JapaneseHolidays.isHoliday(day)) continue;

            return candidate.atZone(zone).toInstant().toEpochMilli();
        }
        return 0L;
    }
}
