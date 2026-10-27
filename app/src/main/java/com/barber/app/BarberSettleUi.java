package com.barber.app;

import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * تسویه‌ی آرایشگر درصدی: ثبت پرداخت (کامل یا جزئی)، مانده، رسید پرداخت و خروجی مخصوص خودِ آرایشگر
 * (متن، PDF و اکسل؛ فقط کارکرد و حساب همان آرایشگر).
 */
public final class BarberSettleUi {

    private BarberSettleUi() {
    }

    /** رسید و خروجی ماهِ روز انتخاب‌شده برای یک آرایشگر */
    public static void showSlipForMonth(final MainActivity act, Db.Barber b) {
        int[] d = act.selectedDate;
        ReportData r = ReportsTab.buildReport(act, d[0], d[1]);
        ReportData.Settle target = null;
        for (ReportData.Settle s : r.settlements()) {
            if (s.barberId == b.id) target = s;
        }
        if (target == null) {
            target = new ReportData.Settle();
            target.barberId = b.id;
            target.barber = b.name;
            ReportData.BarberInfo bi = r.info.get(b.id);
            if (bi != null) {
                target.card = bi.card;
                target.prev = bi.prev;
                target.paid = bi.paid;
            }
        }
        showSlip(act, r, target, null);
    }

    /** رسید تسویه با دکمه‌های ارسال (متن / PDF / اکسل) و ثبت پرداخت */
    public static void showSlip(final MainActivity act, final ReportData r, final ReportData.Settle s,
                                final Runnable onChanged) {
        final ReportData one = r.forBarber(s);
        final String slip = r.slipText(s);
        new MaterialAlertDialogBuilder(act)
                .setTitle("رسید تسویه — " + s.barber)
                .setMessage(slip)
                .setPositiveButton("📤 ارسال…", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        chooseFormat(act, one, slip);
                    }
                })
                .setNeutralButton("💵 ثبت پرداخت", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        addPayment(act, s.barberId, s.barber, onChanged);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private static void chooseFormat(final MainActivity act, final ReportData one, final String slip) {
        CharSequence[] items = {"💬 متن (پیام‌رسان)", "📄 فایل PDF", "📊 فایل اکسل"};
        new MaterialAlertDialogBuilder(act)
                .setTitle("ارسال حساب " + one.onlyBarberName)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        try {
                            if (which == 0) ReportExporter.shareText(act, one.title(), slip);
                            else if (which == 1) ReportExporter.sharePdf(act, one);
                            else ReportExporter.shareExcel(act, one);
                        } catch (Exception e) {
                            Ui.toastLong(act, "ساخت یا ارسال فایل ناموفق بود");
                        }
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** ثبت یک پرداخت (کامل یا جزئی) به آرایشگر؛ بعد از ثبت، رسید پرداخت نشان داده می‌شود */
    public static void addPayment(final MainActivity act, final long barberId, final String barberName,
                                  final Runnable onChanged) {
        if (!act.canWrite()) return;
        final Db db = Db.get(act);
        double remaining = db.barberBalanceTotal(barberId);

        int pad = Ui.dp(act, 20);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Ui.rtl(box);
        final EditText etAmount = new EditText(act);
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAmount.setHint("مبلغ پرداخت (تومان)");
        if (remaining > 0.5) etAmount.setText(Fmt.plain(remaining));
        box.addView(etAmount);
        final EditText etNote = new EditText(act);
        etNote.setSingleLine(true);
        etNote.setHint("توضیح (مثلاً کارت به کارت، نقد)");
        box.addView(etNote);
        final int[] date = JalaliCalendar.today();
        final Button btnDate = new Button(act);
        btnDate.setText("تاریخ: " + JalaliCalendar.format(date[0], date[1], date[2]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JalaliPicker.show(act, date, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int d) {
                        date[0] = y;
                        date[1] = m;
                        date[2] = d;
                        btnDate.setText("تاریخ: " + JalaliCalendar.format(y, m, d));
                    }
                });
            }
        });
        box.addView(btnDate);

        new MaterialAlertDialogBuilder(act)
                .setTitle("💵 پرداخت به " + barberName)
                .setMessage("مانده‌ی کل حساب او: " + Fmt.money(remaining)
                        + "\nمبلغ را برای پرداخت جزئی کمتر بنویسید؛ باقی‌مانده در حسابش می‌ماند.")
                .setView(Ui.scroll(box))
                .setPositiveButton("ثبت", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Double amount = Fmt.parsePrice(etAmount.getText().toString());
                        if (amount == null || amount <= 0) {
                            Ui.toast(act, "مبلغ صحیح نیست");
                            return;
                        }
                        String iso = JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]);
                        String note = etNote.getText().toString().trim();
                        db.addBarberPayment(barberId, iso, amount, note);
                        act.refreshAll();
                        if (onChanged != null) onChanged.run();
                        showReceipt(act, barberId, barberName, amount, date.clone(), note);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** رسید پرداخت (متن ساده و سبک) */
    static String receiptText(MainActivity act, long barberId, String barberName, double amount,
                              int[] date, String note) {
        Db db = Db.get(act);
        String card = "";
        for (Db.Barber b : db.getBarbers()) {
            if (b.id == barberId && b.card != null) card = b.card;
        }
        double left = db.barberBalanceTotal(barberId);
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 رسید پرداخت — ").append(AppConfig.getSalonName(act)).append("\n");
        sb.append("آرایشگر: ").append(barberName).append("\n");
        sb.append("تاریخ: ").append(JalaliCalendar.format(date[0], date[1], date[2])).append("\n");
        sb.append("مبلغ پرداخت‌شده: ").append(Fmt.money(amount)).append("\n");
        if (note != null && !note.isEmpty()) sb.append("توضیح: ").append(note).append("\n");
        sb.append(left > 0.5 ? "مانده‌ی حساب: " + Fmt.money(left) : "✅ حساب تسویه شد")
                .append("\n");
        if (!card.isEmpty()) sb.append("شماره کارت/حساب: ").append(card);
        return sb.toString().trim();
    }

    private static void showReceipt(final MainActivity act, long barberId, String barberName, double amount,
                                    int[] date, String note) {
        final String text = receiptText(act, barberId, barberName, amount, date, note);
        new MaterialAlertDialogBuilder(act)
                .setTitle("ثبت شد ✅")
                .setMessage(text)
                .setPositiveButton("📤 ارسال رسید", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        ReportExporter.shareText(act, "رسید پرداخت", text);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** لیست همه‌ی پرداخت‌های یک آرایشگر؛ لمس = حذف (با تأیید) */
    public static void listPayments(final MainActivity act, final Db.Barber b) {
        final Db db = Db.get(act);
        final List<Db.BarberPayment> list = db.getBarberPayments(b.id, "0000-01-01", "9999-12-31");
        if (list.isEmpty()) {
            Ui.toast(act, "هنوز پرداختی به " + b.name + " ثبت نشده");
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < items.length; i++) {
            Db.BarberPayment p = list.get(i);
            items[i] = JalaliCalendar.gregorianStringToJalali(p.date) + "   " + Fmt.money(p.amount)
                    + (p.note == null || p.note.isEmpty() ? "" : "\n" + p.note);
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("پرداخت‌های " + b.name + " (برای حذف لمس کنید)")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, final int which) {
                        new MaterialAlertDialogBuilder(act)
                                .setTitle("حذف این پرداخت؟")
                                .setMessage(Fmt.money(list.get(which).amount) + " دوباره به مانده‌ی حساب اضافه می‌شود.")
                                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dd, int w) {
                                        db.deleteBarberPayment(list.get(which).id);
                                        act.refreshAll();
                                        Ui.toast(act, "حذف شد");
                                    }
                                })
                                .setNegativeButton("انصراف", null)
                                .show();
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }
}
