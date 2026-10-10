package com.barber.admin;

import java.util.List;

/**
 * تخمین تاریخ پایان لایسنس مشتری برای یادآوری تمدید (بدون وابستگی به اندروید).
 * چون برنامه‌ی مدیریتی روز فعال‌سازی را نمی‌داند، فرض می‌شود هر کد همان روز صدور فعال شده؛ پس تخمین ممکن است
 * تا ۳۰ روز زودتر از پایان واقعی باشد. کد جابجایی، پایان را روی تاریخ ثبت‌شده‌ی خودش می‌گذارد.
 */
public final class Renewal {

    public static final int WARN_DAYS = 30;

    public static final class Info {
        public long customerId;
        public long endEpochDay;   // پایان تخمینی
        public long daysLeft;      // منفی = تمام شده
    }

    private Renewal() {
    }

    /** لایسنس‌ها باید از قدیمی به جدید باشند؛ ‎-1 اگر لایسنس زمان‌داری نیست */
    public static long estimateEnd(List<Models.License> oldestFirst) {
        long end = -1;
        for (Models.License l : oldestFirst) {
            if (l.voided) continue;
            long issue = IssueLogic.epochDayOfIso(l.issueDate);
            if (issue < 0) continue;
            if (l.type == LicenseCodec.TYPE_TRANSFER) {
                long e = IssueLogic.epochDayOfIso(l.endDate);
                if (e >= 0) end = e;
                continue;
            }
            int days = LicenseCodec.daysForType(l.type);
            if (days == 0) continue;
            long base = (end >= issue) ? end + 1 : issue;
            end = base + days - 1;
        }
        return end;
    }

    public static Info info(long customerId, List<Models.License> oldestFirst, long today) {
        long end = estimateEnd(oldestFirst);
        if (end < 0) return null;
        Info i = new Info();
        i.customerId = customerId;
        i.endEpochDay = end;
        i.daysLeft = end - today;
        return i;
    }
}
