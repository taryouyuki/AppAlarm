import com.taryo.appalarm.JapaneseHolidays;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Plain-JVM check of the holiday calculator: prints and asserts known dates. */
public class HolidayCheck {
    public static void main(String[] args) {
        int[] years = {2026, 2027};
        for (int y : years) {
            List<LocalDate> list = new ArrayList<>(JapaneseHolidays.holidays(y));
            Collections.sort(list);
            System.out.println("== " + y + " (" + list.size() + " days) ==");
            for (LocalDate d : list) {
                System.out.println("  " + d + " " + jp(d.getDayOfWeek()));
            }
        }

        check("2026-01-01", true);
        check("2026-01-12", true);   // 成人の日
        check("2026-03-20", true);   // 春分の日
        check("2026-05-06", true);   // 振替休日 for 5/3 (Sunday)
        check("2026-09-22", true);   // 国民の休日 between 敬老の日 and 秋分の日
        check("2026-10-12", true);   // スポーツの日
        check("2026-06-15", false);
        check("2026-05-01", false);
        check("2027-03-21", true);   // 春分の日
        System.out.println("all assertions passed");
    }

    private static void check(String date, boolean expected) {
        boolean actual = JapaneseHolidays.isHoliday(LocalDate.parse(date));
        System.out.println((actual == expected ? "OK   " : "FAIL ") + date
                + " expected=" + expected + " actual=" + actual);
        if (actual != expected) throw new AssertionError(date);
    }

    private static String jp(DayOfWeek d) {
        switch (d) {
            case MONDAY: return "月";
            case TUESDAY: return "火";
            case WEDNESDAY: return "水";
            case THURSDAY: return "木";
            case FRIDAY: return "金";
            case SATURDAY: return "土";
            default: return "日";
        }
    }
}
