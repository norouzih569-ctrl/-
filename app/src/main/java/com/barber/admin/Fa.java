package com.barber.admin;

/** ارقام فارسی و مبلغ به حروف (بدون وابستگی به اندروید). */
public final class Fa {

    private Fa() {
    }

    /** ارقام انگلیسی ← فارسی */
    public static String digits(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c >= '0' && c <= '9' ? (char) ('۰' + (c - '0')) : c);
        }
        return sb.toString();
    }

    /** مثل ۲۰,۰۰۰,۰۰۰ */
    public static String num(long v) {
        return digits(String.format(java.util.Locale.US, "%,d", v));
    }

    /** مثل «۲۰,۰۰۰,۰۰۰ تومان» */
    public static String money(long v) {
        return num(v) + " تومان";
    }

    /** درصد، مثل ۱۵٪ یا ۱۲٫۵٪ */
    public static String percent(double p) {
        String t = (p == Math.rint(p))
                ? String.format(java.util.Locale.US, "%d", (long) p)
                : String.format(java.util.Locale.US, "%.1f", p).replace('.', '٫');
        return digits(t) + "٪";
    }

    private static final String[] ONES = {"", "یک", "دو", "سه", "چهار", "پنج", "شش", "هفت", "هشت", "نه", "ده",
            "یازده", "دوازده", "سیزده", "چهارده", "پانزده", "شانزده", "هفده", "هجده", "نوزده"};
    private static final String[] TENS = {"", "", "بیست", "سی", "چهل", "پنجاه", "شصت", "هفتاد", "هشتاد", "نود"};
    private static final String[] HUNDREDS = {"", "صد", "دویست", "سیصد", "چهارصد", "پانصد", "ششصد", "هفتصد",
            "هشتصد", "نهصد"};
    private static final String[] SCALES = {"", "هزار", "میلیون", "میلیارد", "هزار میلیارد"};

    private static String below1000(int n) {
        StringBuilder sb = new StringBuilder();
        int h = n / 100;
        int r = n % 100;
        if (h > 0) sb.append(HUNDREDS[h]);
        if (r > 0) {
            if (sb.length() > 0) sb.append(" و ");
            if (r < 20) {
                sb.append(ONES[r]);
            } else {
                sb.append(TENS[r / 10]);
                if (r % 10 > 0) sb.append(" و ").append(ONES[r % 10]);
            }
        }
        return sb.toString();
    }

    /** عدد به حروف فارسی؛ مثلاً ۱۷۰۰۰۰۰۰ ← «هفده میلیون» (تا هزار میلیارد) */
    public static String words(long n) {
        if (n == 0) return "صفر";
        if (n < 0) return "منفی " + words(-n);
        StringBuilder sb = new StringBuilder();
        int scale = 0;
        java.util.ArrayList<String> parts = new java.util.ArrayList<String>();
        long rest = n;
        while (rest > 0 && scale < SCALES.length) {
            int chunk = (int) (rest % 1000);
            rest /= 1000;
            if (chunk > 0) {
                String w = below1000(chunk);
                parts.add(0, SCALES[scale].isEmpty() ? w : w + " " + SCALES[scale]);
            }
            scale++;
        }
        if (rest > 0) return digits(String.valueOf(n)); // خارج از محدوده
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(" و ");
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    public static String moneyWords(long v) {
        return words(v) + " تومان";
    }
}
