import com.taryo.appalarm.AlarmEntry;
import com.taryo.appalarm.NextTime;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * The per-weekday search used to stop after 8 days. When the only matching
 * weekday in that window is a holiday, nextTrigger returned 0 and the alarm
 * was never armed. 2026-01-12 is Coming-of-Age Day (Monday).
 */
public class NextTimeCheck {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.systemDefault();
        // Tuesday 2026-01-06 08:00. Monday 07:00 is the only configured day.
        long now = LocalDateTime.of(2026, 1, 6, 8, 0).atZone(zone).toInstant().toEpochMilli();

        AlarmEntry perDay = new AlarmEntry();
        perDay.mode = AlarmEntry.MODE_PERDAY;
        perDay.skipHolidays = true;
        perDay.perDayHour[0] = 7;
        long at = NextTime.nextTrigger(perDay, now);
        long expected = LocalDateTime.of(2026, 1, 19, 7, 0).atZone(zone).toInstant().toEpochMilli();
        if (at != expected) {
            System.out.println("FAIL per-day holiday skip at=" + at + " expected=" + expected);
            throw new AssertionError("per-day holiday skip");
        }
        System.out.println("OK per-day skips holiday Monday 2026-01-12 and fires 2026-01-19 07:00");

        AlarmEntry soon = new AlarmEntry();
        soon.mode = AlarmEntry.MODE_PERDAY;
        soon.hour = 7;
        soon.perDayHour[0] = 7;
        long mondayMorning = LocalDateTime.of(2026, 1, 5, 6, 0).atZone(zone).toInstant().toEpochMilli();
        long today = NextTime.nextTrigger(soon, mondayMorning);
        long todayExpected = LocalDateTime.of(2026, 1, 5, 7, 0).atZone(zone).toInstant().toEpochMilli();
        if (today != todayExpected) {
            System.out.println("FAIL same-morning at=" + today + " expected=" + todayExpected);
            throw new AssertionError("same morning");
        }
        System.out.println("OK per-day still fires later the same morning");

        AlarmEntry daily = new AlarmEntry();
        daily.mode = AlarmEntry.MODE_DAILY;
        daily.hour = 7;
        daily.minute = 0;
        long dAt = NextTime.nextTrigger(daily, now);
        long dExp = LocalDateTime.of(2026, 1, 7, 7, 0).atZone(zone).toInstant().toEpochMilli();
        if (dAt != dExp) throw new AssertionError("daily");
        System.out.println("OK daily next morning");
        System.out.println("all assertions passed");
    }
}
