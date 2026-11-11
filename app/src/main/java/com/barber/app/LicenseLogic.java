package com.barber.app;

/** منطق خالص وضعیت لایسنس (آزمایشی/فعال/تمام‌شده) و اعمال کد؛ بدون وابستگی به اندروید. */
public final class LicenseLogic {

    /** دوره‌ی آزمایشی داخلی حذف شده؛ آزمایشی فقط با «کد آزمایشی» (نوع ۷) شروع می‌شود. */
    public static final int TRIAL_DAYS = 0;
    public static final int ACTIVATION_WINDOW_DAYS = 30;
    public static final int CLOCK_TOLERANCE_DAYS = 2;
    public static final int WARN_DAYS = 14;
    public static final int DAYS_PER_YEAR = 365;

    /** NONE = هنوز هیچ کدی فعال نشده؛ TRIAL دیگر برگردانده نمی‌شود (آزمایشی یک لایسنس ACTIVE از نوع ۷ است) */
    public enum Status { TRIAL, ACTIVE, EXPIRED, TAMPERED, NONE }

    public static final class State {
        public Status status;
        /** روزهای باقی‌مانده (۰ = امروز آخرین روز است) */
        public int daysLeft;

        public boolean readOnly() {
            return status == Status.EXPIRED || status == Status.TAMPERED || status == Status.NONE;
        }
    }

    public enum Result { OK, ISSUED_IN_FUTURE, WINDOW_PASSED, TRANSFER_ENDED, UNKNOWN_TYPE, TRIAL_ENDED, ALREADY_COVERED }

    public static final class Activation {
        public Result result;
        public long start;
        public long end;
    }

    private LicenseLogic() {
    }

    /** شماره‌ی روز (از ۱۹۷۰) برای زمان و منطقه‌ی زمانی داده‌شده */
    public static long epochDay(long millis, long tzOffsetMillis) {
        return Math.floorDiv(millis + tzOffsetMillis, 86_400_000L);
    }

    /** تاریخ میلادی {سال، ماه، روز} از شماره‌ی روز (الگوریتم Howard Hinnant) */
    public static int[] civilFromEpochDay(long z) {
        z += 719468;
        long era = Math.floorDiv(z, 146097L);
        long doe = z - era * 146097L;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp < 10 ? mp + 3 : mp - 9;
        if (m <= 2) y++;
        return new int[]{(int) y, (int) m, (int) d};
    }

    /** شماره‌ی روز از تاریخ میلادی */
    public static long epochDayFromCivil(int y, int m, int d) {
        long yy = y - (m <= 2 ? 1 : 0);
        long era = Math.floorDiv(yy, 400L);
        long yoe = yy - era * 400;
        long doy = (153L * (m + (m > 2 ? -3 : 9)) + 2) / 5 + d - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /**
     * @param licEnd آخرین روزِ معتبرِ لایسنس (۰ = لایسنسی فعال نشده)
     * @param lastSeen آخرین روزی که برنامه باز شده (برای تشخیص عقب‌کشیدن ساعت)
     */
    public static State evaluate(long today, long trialStart, long licEnd, long lastSeen) {
        State s = new State();
        if (lastSeen > 0 && today < lastSeen - CLOCK_TOLERANCE_DAYS) {
            s.status = Status.TAMPERED;
            return s;
        }
        if (licEnd > 0) {
            if (today <= licEnd) {
                s.status = Status.ACTIVE;
                s.daysLeft = (int) (licEnd - today);
            } else {
                s.status = Status.EXPIRED;
            }
            return s;
        }
        // لایسنسی فعال نیست (هنوز کدی وارد نشده یا ذخیره‌ی نامعتبر پاک شده)
        s.status = Status.NONE;
        return s;
    }

    /** اعمال یک کد معتبر روی وضعیت فعلی (curStart/curEnd = ۰ یعنی لایسنسی نبوده) */
    public static Activation activate(LicenseCodec.Decoded d, long today, long curStart, long curEnd) {
        return activate(d, today, curStart, curEnd, false);
    }

    /** @param curIsTrial لایسنس فعلی از نوع آزمایشی است (روزهای باقی‌مانده‌ی آزمایشی به لایسنس خریداری‌شده اضافه نمی‌شود) */
    public static Activation activate(LicenseCodec.Decoded d, long today, long curStart, long curEnd,
                                      boolean curIsTrial) {
        Activation a = new Activation();
        long issue = d.issueEpochDay();
        if (today < issue - 1) {
            a.result = Result.ISSUED_IN_FUTURE;
            return a;
        }
        if (today > issue + ACTIVATION_WINDOW_DAYS) {
            a.result = Result.WINDOW_PASSED;
            return a;
        }
        if (d.isTrial()) {
            long end = issue + d.extra - 1;
            if (d.extra < 1 || end < today) {
                a.result = Result.TRIAL_ENDED;
                return a;
            }
            if (curEnd >= end) {
                a.result = Result.ALREADY_COVERED;
                return a;
            }
            a.result = Result.OK;
            a.start = today;
            a.end = end;
            return a;
        }
        if (d.isTransfer()) {
            long end = issue + d.extra;
            if (end < today) {
                a.result = Result.TRANSFER_ENDED;
                return a;
            }
            a.result = Result.OK;
            a.start = today;
            a.end = end;
            return a;
        }
        int days = d.days();
        if (days == 0) {
            a.result = Result.UNKNOWN_TYPE;
            return a;
        }
        boolean active = curEnd > 0 && curEnd >= today && !curIsTrial;
        long base = active ? curEnd + 1 : today;
        a.result = Result.OK;
        a.start = active ? curStart : today;
        a.end = base + days - 1;
        return a;
    }
}
