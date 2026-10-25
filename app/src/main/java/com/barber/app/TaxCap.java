package com.barber.app;

/** منطق خالص سقف سالانه‌ی درآمد کارتی/واریزی (قابل تست) */
public final class TaxCap {
    public static final int OK = 0, WARN = 1, OVER = 2;

    private TaxCap() {
    }

    /** cap <= 0 یعنی غیرفعال */
    public static int level(double received, double cap, int warnPercent) {
        if (cap <= 0) return OK;
        if (received >= cap) return OVER;
        if (received >= cap * warnPercent / 100.0) return WARN;
        return OK;
    }

    public static double percentUsed(double received, double cap) {
        return cap <= 0 ? 0 : received * 100.0 / cap;
    }
}
