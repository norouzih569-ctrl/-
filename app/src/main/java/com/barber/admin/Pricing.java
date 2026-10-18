package com.barber.admin;

/** محاسبه‌ی قیمت، تخفیف و پورسانت (بدون وابستگی به اندروید). مبلغ‌ها به تومان. */
public final class Pricing {

    private Pricing() {
    }

    /** درصد را به بازه‌ی ۰ تا ۱۰۰ می‌رساند. */
    public static double clampPercent(double p) {
        if (Double.isNaN(p) || p < 0) return 0;
        return Math.min(p, 100);
    }

    /** مبلغ نهایی پس از تخفیف (گرد به نزدیک‌ترین تومان). مثال: ۲۰,۰۰۰,۰۰۰ با ۱۵٪ ← ۱۷,۰۰۰,۰۰۰ */
    public static long finalPrice(long base, double discountPercent) {
        if (base <= 0) return 0;
        double d = clampPercent(discountPercent);
        return Math.round(base * (100.0 - d) / 100.0);
    }

    /** مبلغ تخفیف */
    public static long discountAmount(long base, double discountPercent) {
        return Math.max(0, base) - finalPrice(base, discountPercent);
    }

    /** پورسانت ویزیتور؛ طبق تصمیم، روی مبلغ بعد از تخفیف حساب می‌شود. */
    public static long commission(long finalPrice, double commissionPercent) {
        if (finalPrice <= 0) return 0;
        return Math.round(finalPrice * clampPercent(commissionPercent) / 100.0);
    }
}
