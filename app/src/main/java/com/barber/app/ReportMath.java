package com.barber.app;

/** محاسبات خالص نمودار و مقایسه‌ی گزارش (قابل تست) */
public final class ReportMath {
    private ReportMath() {
    }

    /** جمع مبلغ هر روز ماه؛ days[i] شماره‌ی روز (۱ تا monthLen) */
    public static double[] dailyTotals(int[] days, double[] amounts, int monthLen) {
        double[] out = new double[monthLen];
        for (int i = 0; i < days.length; i++) {
            int d = days[i];
            if (d >= 1 && d <= monthLen) out[d - 1] += amounts[i];
        }
        return out;
    }

    /** درصد تغییر نسبت به ماه قبل؛ null اگر ماه قبل صفر بود (مقایسه معنی ندارد) */
    public static Double changePercent(double cur, double prev) {
        if (prev <= 0) return null;
        return (cur - prev) * 100.0 / prev;
    }

    /** اندیس بیشترین مقدار؛ -1 اگر همه صفر */
    public static int maxIndex(double[] v) {
        int best = -1;
        double m = 0;
        for (int i = 0; i < v.length; i++) {
            if (v[i] > m) {
                m = v[i];
                best = i;
            }
        }
        return best;
    }

    public static String describeChange(double cur, double prev) {
        Double p = changePercent(cur, prev);
        if (p == null) return cur > 0 ? "ماه قبل درآمدی ثبت نشده بود" : "";
        long r = Math.round(Math.abs(p));
        if (r == 0) return "تقریباً برابر با ماه قبل";
        return (p > 0 ? "▲ " : "▼ ") + r + "٪ " + (p > 0 ? "بیشتر" : "کمتر") + " از ماه قبل";
    }
}
