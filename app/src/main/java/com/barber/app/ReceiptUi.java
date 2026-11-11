package com.barber.app;

import java.util.List;

/** رسید ساده‌ی متنی نوبت برای مشتری (قابل ارسال در بله، ایتا، واتساپ، پیامک...). */
final class ReceiptUi {

    private ReceiptUi() {
    }

    static String text(MainActivity act, Db.Appointment a) {
        Db db = Db.get(act);
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 رسید — ").append(AppConfig.getSalonName(act)).append("\n");
        sb.append("مشتری: ").append(a.customerName).append("\n");
        sb.append("تاریخ: ").append(JalaliCalendar.gregorianStringToJalali(a.date))
                .append("   ساعت: ").append(a.time).append("\n");
        sb.append("خدمات: ").append(a.service).append("\n");
        List<long[]> prods = db.getApptProducts(a.id);
        double prodTotal = 0;
        StringBuilder pl = new StringBuilder();
        for (long[] it : prods) {
            Db.Product p = db.getProduct(it[0]);
            if (p == null) continue;
            double line = p.sellPrice * it[1];
            prodTotal += line;
            pl.append("  • ").append(p.name).append(" × ").append(it[1])
                    .append(" = ").append(Fmt.price(line)).append("\n");
        }
        if (pl.length() > 0) sb.append("محصولات:\n").append(pl);
        double total = a.billed();
        if (prodTotal > 0 && total - prodTotal >= 0) {
            sb.append("اجرت خدمات: ").append(Fmt.money(total - prodTotal)).append("\n");
        }
        sb.append("جمع کل: ").append(Fmt.money(total)).append("\n");
        if (a.done) {
            double paid = a.tracked ? a.paid : total;
            sb.append("دریافتی: ").append(Fmt.money(paid)).append("\n");
            double debt = a.debt();
            sb.append(debt > 0.5 ? "مانده: " + Fmt.money(debt) : "✅ تسویه شد").append("\n");
        } else {
            sb.append("وضعیت: در انتظار انجام\n");
        }
        sb.append("\nاز اعتماد شما سپاسگزاریم 🌹 منتظر دیدار دوباره‌ی شما هستیم.");
        return sb.toString();
    }

    static void share(final MainActivity act, final Db.Appointment a) {
        final String t = text(act, a);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                .setTitle("🧾 رسید مشتری")
                .setMessage(t)
                .setPositiveButton("📤 ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        ReportExporter.shareText(act, "رسید " + a.customerName, t);
                    }
                })
                .setNeutralButton("📩 پیامک", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Messenger.openSms(act, a.phone, t);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }
}
