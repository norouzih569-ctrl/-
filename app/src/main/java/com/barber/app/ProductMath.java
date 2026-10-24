package com.barber.app;

/** محاسبات خالص فروش محصول (قابل تست) */
public final class ProductMath {
    private ProductMath() {
    }

    /** سود هر واحد */
    public static double unitProfit(double sell, double buy) {
        return sell - buy;
    }

    /** درصد سود نسبت به قیمت خرید؛ اگر خرید صفر باشد 0 */
    public static double markupPercent(double sell, double buy) {
        return buy <= 0 ? 0 : (sell - buy) * 100.0 / buy;
    }

    public static double saleProfit(int qty, double unitPrice, double unitCost) {
        return qty * (unitPrice - unitCost);
    }

    /** null اگر درست است، وگرنه پیام خطا */
    public static String validateSale(int qty, int stock) {
        if (qty <= 0) return "تعداد باید حداقل ۱ باشد";
        if (qty > stock) return "موجودی کافی نیست (موجودی: " + stock + ")";
        return null;
    }
}
