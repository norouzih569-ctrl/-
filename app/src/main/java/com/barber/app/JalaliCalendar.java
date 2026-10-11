package com.barber.app;

/**
 * تبدیل تاریخ شمسی و میلادی (بدون وابستگی به کتابخانه خارجی).
 * الگوریتم بر پایه jalaali-js است.
 */
public final class JalaliCalendar {

    private static final int[] BREAKS = {
            -61, 9, 38, 199, 426, 686, 756, 818, 1111, 1181, 1210,
            1635, 2060, 2097, 2192, 2262, 2324, 2394, 2456, 3178
    };

    public static final String[] MONTH_NAMES = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
    };

    // ایندکس 0 = شنبه
    public static final String[] DAY_NAMES = {
            "شنبه", "یک‌شنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"
    };

    private JalaliCalendar() {
    }

    /** تاریخ شمسی به شکل {سال، ماه، روز} */
    public static int[] toJalali(int gy, int gm, int gd) {
        return d2j(g2d(gy, gm, gd));
    }

    /** تاریخ میلادی به شکل {سال، ماه، روز} */
    public static int[] toGregorian(int jy, int jm, int jd) {
        return d2g(j2d(jy, jm, jd));
    }

    /** تبدیل رشته میلادی yyyy-MM-dd به رشته شمسی yyyy/MM/dd */
    public static String gregorianStringToJalali(String iso) {
        try {
            String[] p = iso.split("-");
            int[] j = toJalali(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
            return format(j[0], j[1], j[2]);
        } catch (Exception e) {
            return iso;
        }
    }

    /** تاریخ میلادی yyyy-MM-dd به {سال، ماه، روز} شمسی */
    public static int[] jalaliOfIso(String iso) {
        String[] p = iso.split("-");
        return toJalali(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]));
    }

    /** تاریخ شمسی به رشته میلادی yyyy-MM-dd (فرمت ذخیره در دیتابیس) */
    public static String jalaliToGregorianString(int jy, int jm, int jd) {
        int[] g = toGregorian(jy, jm, jd);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", g[0], g[1], g[2]);
    }

    public static String format(int jy, int jm, int jd) {
        return String.format(java.util.Locale.US, "%04d/%02d/%02d", jy, jm, jd);
    }

    public static boolean isLeap(int jy) {
        return jalCal(jy, false)[0] == 0;
    }

    public static int monthLength(int jy, int jm) {
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        return isLeap(jy) ? 30 : 29;
    }

    /** ایندکس روز هفته: 0 = شنبه ... 6 = جمعه */
    public static int dayOfWeek(int jy, int jm, int jd) {
        long jdn = j2d(jy, jm, jd);
        return (int) ((jdn + 2) % 7);
    }

    /** تاریخ شمسی یک روز بعد/قبل */
    public static int[] addDays(int jy, int jm, int jd, int delta) {
        return d2j(j2d(jy, jm, jd) + delta);
    }

    /** شروع و پایان یک ماه شمسی به‌صورت رشته میلادی {از، تا} (yyyy-MM-dd) */
    public static String[] monthRangeIso(int jy, int jm) {
        return new String[]{
                jalaliToGregorianString(jy, jm, 1),
                jalaliToGregorianString(jy, jm, monthLength(jy, jm))
        };
    }

    public static int[] today() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return toJalali(c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    /** امروز به شکل رشته میلادی yyyy-MM-dd */
    public static String todayGregorianString() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                c.get(java.util.Calendar.YEAR),
                c.get(java.util.Calendar.MONTH) + 1,
                c.get(java.util.Calendar.DAY_OF_MONTH));
    }

    // ---------------- الگوریتم داخلی ----------------

    /** خروجی: {leap, gy, march} */
    private static int[] jalCal(int jy, boolean withoutLeap) {
        int bl = BREAKS.length;
        int gy = jy + 621;
        int leapJ = -14;
        int jp = BREAKS[0];
        int jm;
        int jump = 0;
        int n;
        if (jy < jp || jy >= BREAKS[bl - 1]) {
            throw new IllegalArgumentException("Invalid Jalaali year " + jy);
        }
        for (int i = 1; i < bl; i++) {
            jm = BREAKS[i];
            jump = jm - jp;
            if (jy < jm) break;
            leapJ = leapJ + (jump / 33) * 8 + ((jump % 33) / 4);
            jp = jm;
        }
        n = jy - jp;
        leapJ = leapJ + (n / 33) * 8 + (((n % 33) + 3) / 4);
        if ((jump % 33) == 4 && jump - n == 4) leapJ += 1;
        int leapG = (gy / 4) - (((gy / 100) + 1) * 3 / 4) - 150;
        int march = 20 + leapJ - leapG;
        if (withoutLeap) return new int[]{0, gy, march};
        if (jump - n < 6) n = n - jump + ((jump + 4) / 33) * 33;
        int leap = ((n + 1) % 33 - 1) % 4;
        if (leap == -1) leap = 4;
        return new int[]{leap, gy, march};
    }

    private static long j2d(int jy, int jm, int jd) {
        int[] r = jalCal(jy, true);
        return g2d(r[1], 3, r[2]) + (jm - 1) * 31L - (jm / 7) * (jm - 7L) + jd - 1;
    }

    private static int[] d2j(long jdn) {
        int gy = d2g(jdn)[0];
        int jy = gy - 621;
        int[] r = jalCal(jy, false);
        long jdn1f = g2d(gy, 3, r[2]);
        long k = jdn - jdn1f;
        int jm;
        int jd;
        if (k >= 0) {
            if (k <= 185) {
                jm = 1 + (int) (k / 31);
                jd = (int) (k % 31) + 1;
                return new int[]{jy, jm, jd};
            } else {
                k -= 186;
            }
        } else {
            jy -= 1;
            k += 179;
            if (r[0] == 1) k += 1;
        }
        jm = 7 + (int) (k / 30);
        jd = (int) (k % 30) + 1;
        return new int[]{jy, jm, jd};
    }

    private static long g2d(int gy, int gm, int gd) {
        long d = ((long) (gy + (gm - 8) / 6 + 100100) * 1461) / 4
                + (153L * ((gm + 9) % 12) + 2) / 5
                + gd - 34840408L;
        d = d - (((long) (gy + 100100 + (gm - 8) / 6) / 100) * 3) / 4 + 752;
        return d;
    }

    private static int[] d2g(long jdn) {
        long j = 4 * jdn + 139361631L;
        j = j + (((4 * jdn + 183187720L) / 146097) * 3 / 4) * 4 - 3908;
        long i = ((j % 1461) / 4) * 5 + 308;
        int gd = (int) ((i % 153) / 5) + 1;
        int gm = (int) ((i / 153) % 12) + 1;
        int gy = (int) (j / 1461) - 100100 + (8 - gm) / 6;
        return new int[]{gy, gm, gd};
    }
}
