package com.barber.admin;

import java.util.Locale;

/** توابع کمکی قالب‌بندی و تبدیل اعداد. */
public final class Fmt {

    private Fmt() {
    }

    /** قیمت با جداکننده هزارگان، مثلاً 250,000 */
    public static String price(double v) {
        return String.format(Locale.US, "%,d", (long) v);
    }

    /** قیمت با واحد پول */
    public static String money(double v) {
        return price(v) + " " + AppConfig.CURRENCY;
    }

    /** درصد: اگر عدد صحیح بود بدون اعشار، در غیر این صورت با یک رقم اعشار */
    public static String percent(double p) {
        if (p == Math.rint(p)) {
            return String.format(Locale.US, "%d%%", (long) p);
        }
        return String.format(Locale.US, "%.1f%%", p);
    }

    /** عدد بدون جداکننده (برای پر کردن EditText) */
    public static String plain(double v) {
        return String.valueOf((long) v);
    }

    /** تبدیل ارقام فارسی/عربی به انگلیسی و حذف جداکننده‌ها */
    public static String normalizeDigits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u06F0' && c <= '\u06F9') {
                sb.append((char) ('0' + (c - '\u06F0')));
            } else if (c >= '\u0660' && c <= '\u0669') {
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c == '\u066B') {
                sb.append('.');
            } else if (c == '\u066C' || c == '\u060C' || c == ',' || c == ' ') {
                // جداکننده هزارگان: حذف
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** تبدیل متن قیمت به عدد؛ در صورت نامعتبر بودن null برمی‌گرداند. */
    public static Double parsePrice(String s) {
        String t = normalizeDigits(s).trim();
        if (t.isEmpty()) return null;
        try {
            double v = Double.parseDouble(t);
            if (Double.isNaN(v) || Double.isInfinite(v) || v < 0) return null;
            return v;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
