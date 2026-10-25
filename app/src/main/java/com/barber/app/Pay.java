package com.barber.app;

/** روش‌های پرداخت و محاسبه‌ی بدهی (منطق خالص؛ قابل تست) */
public final class Pay {

    public static final String CASH = "cash";
    public static final String CARD = "card";
    public static final String TRANSFER = "transfer";

    public static final String[] METHODS = {CASH, CARD, TRANSFER};
    public static final String[] LABELS = {"💵 نقد", "💳 کارت", "🏦 واریز"};

    private Pay() {
    }

    public static String label(String method) {
        if ("pkg".equals(method)) return "📦 پکیج";
        for (int i = 0; i < METHODS.length; i++) {
            if (METHODS[i].equals(method)) return LABELS[i];
        }
        return "—";
    }

    public static String methodAt(int index) {
        return (index >= 0 && index < METHODS.length) ? METHODS[index] : CASH;
    }

    /**
     * بدهی یک نوبت. نوبت قدیمی (tracked=false) یا در انتظار، بدهی ندارد.
     * @param earned مبلغ حساب‌شده‌ی نوبت (final_price یا price)
     */
    public static double debt(boolean tracked, boolean done, double earned, double paid) {
        if (!tracked || !done) return 0;
        double d = earned - paid;
        return d > 0.5 ? d : 0; // کمتر از نیم تومان = تسویه
    }

    /**
     * مبلغ دریافتی را از متن فیلد می‌خواند. خالی = کل مبلغ.
     * @return null اگر نامعتبر باشد (منفی یا بیشتر از کل)
     */
    public static Double resolvePaid(String fieldText, double total) {
        String t = fieldText == null ? "" : fieldText.trim();
        if (t.isEmpty()) return total;
        Double v = Fmt.parsePrice(t);
        if (v == null || v < 0 || v > total) return null;
        return v;
    }
}
