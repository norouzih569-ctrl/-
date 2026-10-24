package com.barber.app;

import java.util.ArrayList;
import java.util.List;

/** منطق خالص لیست خدمات (بدون وابستگی به اندروید؛ قابل تست) */
public final class ServiceList {

    public static final int MAX_NAME = 30;
    public static final int MAX_COUNT = 40;

    private ServiceList() {
    }

    public static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(n);
        }
        return sb.toString();
    }

    public static List<String> parse(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String s : raw.split("\n")) {
            String t = s.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    /** نام تمیز: فاصله‌های اضافه و خط جدید حذف می‌شود */
    public static String clean(String name) {
        if (name == null) return "";
        return name.replace('\n', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * @param self نام فعلی هنگام ویرایش (یا null هنگام افزودن)
     * @return متن خطا یا null اگر درست است
     */
    public static String validateName(String name, List<String> existing, String self) {
        String n = clean(name);
        if (n.isEmpty()) return "نام خدمت را وارد کنید";
        if (n.length() > MAX_NAME) return "نام خدمت حداکثر " + MAX_NAME + " حرف";
        if (n.indexOf('+') >= 0 || n.indexOf('|') >= 0) return "از + و | در نام استفاده نکنید";
        for (String e : existing) {
            if (e.equals(n) && (self == null || !e.equals(self))) return "این خدمت قبلاً وجود دارد";
        }
        if (self == null && existing.size() >= MAX_COUNT) return "حداکثر " + MAX_COUNT + " خدمت";
        return null;
    }
}
