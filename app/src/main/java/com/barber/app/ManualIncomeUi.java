package com.barber.app;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/**
 * ثبت درآمد یک روز بدون ثبت نوبت (مثلاً روز شلوغ: «امروز ۲ میلیون کار کردم»).
 * کارتخوان / نقد / واریز جدا وارد می‌شود؛ به درآمد روز و ماه و سقف کارتی اضافه می‌شود
 * و در گزارش با عنوان «درآمد دستی» جدا دیده می‌شود.
 */
public final class ManualIncomeUi {

    private ManualIncomeUi() {
    }

    private static EditText field(MainActivity act, LinearLayout box, String label) {
        TextView tv = new TextView(act);
        tv.setText(label);
        tv.setPadding(0, Ui.dp(act, 8), 0, 0);
        box.addView(tv);
        EditText et = new EditText(act);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("۰ = ثبت نمی‌شود");
        et.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        box.addView(et);
        return et;
    }

    public static void show(final MainActivity act) {
        if (!act.canWrite()) return;
        final int[] d = act.selectedDate.clone();
        final String iso = JalaliCalendar.jalaliToGregorianString(d[0], d[1], d[2]);
        final Db db = Db.get(act);

        int pad = Ui.dp(act, 20);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Ui.rtl(box);

        TextView info = new TextView(act);
        double already = db.manualSum(iso, iso);
        info.setText("روز: " + JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(d[0], d[1], d[2])] + " "
                + JalaliCalendar.format(d[0], d[1], d[2])
                + (already > 0 ? "\nدرآمد دستی ثبت‌شده‌ی این روز: " + Fmt.money(already) : "")
                + "\n(روز دیگر را از صفحه‌ی نوبت‌ها انتخاب کنید)");
        box.addView(info);

        final EditText etCard = field(act, box, "💳 مانده‌ی کارتخوان امروز (تومان)");
        final EditText etCash = field(act, box, "💵 نقدی (تومان)");
        final EditText etTransfer = field(act, box, "🏦 واریزی (تومان)");
        TextView tvNote = new TextView(act);
        tvNote.setText("توضیح (اختیاری)");
        tvNote.setPadding(0, Ui.dp(act, 8), 0, 0);
        box.addView(tvNote);
        final EditText etNote = new EditText(act);
        etNote.setSingleLine(true);
        etNote.setHint("مثلاً روز شلوغ، نوبت ثبت نشد");
        box.addView(etNote);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("✍️ ثبت درآمد روز (بدون نوبت)")
                .setView(Ui.scroll(box))
                .setPositiveButton("ثبت", null)
                .setNeutralButton("لیست و حذف", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double card = parse(etCard);
                Double cash = parse(etCash);
                Double tr = parse(etTransfer);
                if (card == null || cash == null || tr == null) {
                    Ui.toast(act, "مبلغ صحیح نیست");
                    return;
                }
                String note = etNote.getText().toString().trim();
                int n = 0;
                double total = 0;
                if (card > 0) { db.addManualIncome(iso, card, Pay.CARD, note); n++; total += card; }
                if (cash > 0) { db.addManualIncome(iso, cash, Pay.CASH, note); n++; total += cash; }
                if (tr > 0) { db.addManualIncome(iso, tr, Pay.TRANSFER, note); n++; total += tr; }
                if (n == 0) {
                    Ui.toast(act, "حداقل یک مبلغ وارد کنید");
                    return;
                }
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, Fmt.money(total) + " به درآمد این روز اضافه شد");
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                showList(act, iso);
            }
        });
    }

    /** خالی = ۰؛ نامعتبر = null */
    private static Double parse(EditText et) {
        String t = et.getText().toString().trim();
        if (t.isEmpty()) return 0.0;
        Double v = Fmt.parsePrice(t);
        return (v == null || v < 0) ? null : v;
    }

    private static void showList(final MainActivity act, final String iso) {
        final Db db = Db.get(act);
        final List<Db.ManualIncome> list = db.getManualIncomeBetween(iso, iso);
        if (list.isEmpty()) {
            Ui.toast(act, "برای این روز درآمد دستی ثبت نشده");
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < items.length; i++) {
            Db.ManualIncome m = list.get(i);
            items[i] = Pay.label(m.method) + "  " + Fmt.money(m.amount)
                    + (m.note == null || m.note.isEmpty() ? "" : "\n" + m.note);
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("درآمد دستی این روز (برای حذف لمس کنید)")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, final int which) {
                        new MaterialAlertDialogBuilder(act)
                                .setTitle("حذف این مورد؟")
                                .setMessage(Fmt.money(list.get(which).amount) + " از درآمد کم می‌شود.")
                                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dd, int w) {
                                        db.deleteManualIncome(list.get(which).id);
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
