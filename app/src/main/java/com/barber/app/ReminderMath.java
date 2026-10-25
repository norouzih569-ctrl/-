package com.barber.app;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** منطق خالص یادآورهای پرداخت (اجاره، قسط، خرید): تاریخ بعدی و روزهای مانده */
public final class ReminderMath {
    private ReminderMath() {
    }

    /** سررسید ماه شمسی بعد، با همان روز (اگر ماه بعد آن روز را ندارد، آخرین روز ماه) */
    public static String nextMonthDue(String dueIso, int anchorDay) {
        int[] j = JalaliCalendar.jalaliOfIso(dueIso);
        int y = j[0], m = j[1] + 1;
        if (m > 12) {
            m = 1;
            y++;
        }
        int d = Math.min(anchorDay > 0 ? anchorDay : j[2], JalaliCalendar.monthLength(y, m));
        return JalaliCalendar.jalaliToGregorianString(y, m, d);
    }

    /** مثبت = روز مانده؛ ۰ = امروز؛ منفی = روز گذشته از سررسید */
    public static long daysLeft(String dueIso, String todayIso) {
        return ChronoUnit.DAYS.between(LocalDate.parse(todayIso), LocalDate.parse(dueIso));
    }

    /** آیا باید در برنامه هشدار داد؟ (گذشته یا در بازه‌ی leadDays روز مانده) */
    public static boolean isDueSoon(String dueIso, String todayIso, int leadDays) {
        return daysLeft(dueIso, todayIso) <= leadDays;
    }

    public static String describePlain(long days) {
        if (days < 0) return (-days) + " روز گذشته";
        if (days == 0) return "امروز";
        if (days == 1) return "فردا";
        return days + " روز دیگر";
    }

    public static String describe(long days) {
        String icon = days < 0 ? "🔴 " : days == 0 ? "🟠 " : days == 1 ? "🟡 " : "";
        return icon + describePlain(days);
    }
}
