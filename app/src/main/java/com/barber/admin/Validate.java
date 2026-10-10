package com.barber.admin;

/** اعتبارسنجی و قالب‌بندی شماره‌ها و شناسه‌ها (بدون وابستگی به اندروید). */
public final class Validate {

    private Validate() {
    }

    /** فقط ارقام انگلیسی؛ ارقام فارسی/عربی تبدیل و بقیه حذف می‌شود. */
    public static String digits(String s) {
        String n = Fmt.normalizeDigits(s == null ? "" : s);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            char c = n.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
        }
        return sb.toString();
    }

    /** موبایل ایران: ۰۹ و ۱۱ رقم، یا +98/0098 با ۹ و ۱۰ رقم */
    public static String normalizeMobile(String raw) {
        String d = digits(raw);
        if (d.startsWith("0098")) d = "0" + d.substring(4);
        else if (d.startsWith("98") && d.length() == 12) d = "0" + d.substring(2);
        else if (d.length() == 10 && d.startsWith("9")) d = "0" + d;
        return d;
    }

    public static boolean isValidMobile(String raw) {
        String m = normalizeMobile(raw);
        return m.length() == 11 && m.startsWith("09");
    }

    /** شماره‌ی بین‌المللی برای لینک واتساپ (۹۸۹۱۲…)؛ اگر معتبر نباشد رشته‌ی خالی */
    public static String whatsappNumber(String raw) {
        String m = normalizeMobile(raw);
        if (m.length() == 11 && m.startsWith("09")) return "98" + m.substring(1);
        return "";
    }

    /** کد ملی ۱۰ رقمی با رقم کنترل */
    public static boolean isValidNationalId(String raw) {
        String d = digits(raw);
        if (d.length() != 10) return false;
        boolean same = true;
        for (int i = 1; i < 10; i++) if (d.charAt(i) != d.charAt(0)) same = false;
        if (same) return false;
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (d.charAt(i) - '0') * (10 - i);
        int r = sum % 11;
        int check = d.charAt(9) - '0';
        return r < 2 ? check == r : check == 11 - r;
    }

    /** نمایش کوتاه آدرس/متن در یک خط */
    public static String oneLine(String s, int max) {
        String t = s == null ? "" : s.replace('\n', ' ').trim();
        return t.length() <= max ? t : t.substring(0, max) + "…";
    }
}
