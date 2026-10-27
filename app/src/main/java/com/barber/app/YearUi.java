package com.barber.app;

import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** حساب آخر سال: جمع درآمد، سهم سالن، هزینه‌ها و مانده‌ی سال، به‌اضافه‌ی وام‌های دریافتی (جدا از درآمد). */
public final class YearUi {

    private YearUi() {
    }

    private static String[] yearRange(int y) {
        return new String[]{JalaliCalendar.monthRangeIso(y, 1)[0], JalaliCalendar.monthRangeIso(y, 12)[1]};
    }

    /** متن خلاصه‌ی یک سال */
    static String summary(Db db, int year) {
        String[] r = yearRange(year);
        double income = db.incomeBetween(r[0], r[1]);
        double salon = db.salonShareBetween(r[0], r[1]);
        double exp = db.expensesBetweenSum(r[0], r[1]);
        double balance = income + salon - exp;
        double prod = db.productProfitBetween(r[0], r[1]);
        double loans = 0;
        for (Db.Loan l : db.getLoansBetween(r[0], r[1])) loans += l.amount;
        StringBuilder sb = new StringBuilder();
        sb.append("📅 حساب سال ").append(year).append("\n\n");
        sb.append("💰 درآمد (نوبت‌ها + درآمد دستی): ").append(Fmt.money(income)).append("\n");
        sb.append("✂️ سهم سالن از آرایشگران درصدی: ").append(Fmt.money(salon)).append("\n");
        sb.append("🧾 هزینه‌ها: ").append(Fmt.money(exp)).append("\n");
        sb.append("📊 مانده‌ی سال: ").append(Fmt.money(balance)).append("\n");
        sb.append("🛍 سود فروش محصولات (جدا): ").append(Fmt.money(prod)).append("\n\n");
        sb.append("🏦 وام‌های دریافتی: ").append(Fmt.money(loans)).append("\n");
        sb.append("✅ مانده‌ی سال با احتساب وام: ").append(Fmt.money(balance + loans));
        return sb.toString();
    }

    public static void show(final MainActivity act) {
        final Db db = Db.get(act);
        final int thisYear = JalaliCalendar.today()[0];
        final List<Integer> years = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int y = thisYear - 4; y <= thisYear + 1; y++) {
            years.add(y);
            labels.add(String.valueOf(y));
        }
        int pad = Ui.dp(act, 20);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Ui.rtl(box);

        final Spinner sp = new Spinner(act);
        sp.setAdapter(Ui.spinnerAdapter(act, labels));
        sp.setSelection(years.indexOf(thisYear));
        box.addView(sp);
        final TextView tv = new TextView(act);
        tv.setTextSize(15);
        tv.setLineSpacing(0, 1.25f);
        tv.setPadding(0, Ui.dp(act, 10), 0, 0);
        box.addView(tv);
        final Button share = new Button(act);
        share.setText("📤 ارسال این حساب");
        box.addView(share);

        final Runnable reload = new Runnable() {
            @Override
            public void run() {
                tv.setText(summary(db, years.get(sp.getSelectedItemPosition())));
            }
        };
        reload.run();
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                reload.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) {
            }
        });
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, AppConfig.getSalonName(act) + "\n" + tv.getText());
                act.startActivity(Intent.createChooser(i, "ارسال حساب سال"));
            }
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("📅 حساب آخر سال")
                .setView(Ui.scroll(box))
                .setPositiveButton("➕ ثبت وام", null)
                .setNeutralButton("لیست وام‌ها", null)
                .setNegativeButton("بستن", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                addLoan(act, years.get(sp.getSelectedItemPosition()));
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                listLoans(act, years.get(sp.getSelectedItemPosition()));
            }
        });
    }

    private static void addLoan(final MainActivity act, final int year) {
        if (!act.canWrite()) {
            return;
        }
        int pad = Ui.dp(act, 20);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Ui.rtl(box);
        final EditText etAmount = new EditText(act);
        etAmount.setInputType(InputType.TYPE_CLASS_NUMBER);
        etAmount.setHint("مبلغ وام (تومان)");
        box.addView(etAmount);
        final EditText etNote = new EditText(act);
        etNote.setSingleLine(true);
        etNote.setHint("توضیح (مثلاً وام بانک ...)");
        box.addView(etNote);
        final int[] date = act.selectedDate.clone();
        // تاریخ پیش‌فرض: روز انتخاب‌شده؛ اگر در سال دیگری بود، ۱ فروردین همان سال
        if (date[0] != year) {
            date[0] = year;
            date[1] = 1;
            date[2] = 1;
        }
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
                .setTitle("🏦 ثبت وام دریافتی")
                .setMessage("وام درآمد حساب نمی‌شود؛ فقط در «حساب آخر سال» جدا نشان داده و به مانده‌ی سال اضافه می‌شود.")
                .setView(Ui.scroll(box))
                .setPositiveButton("ثبت", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Double amount = Fmt.parsePrice(etAmount.getText().toString());
                        if (amount == null || amount <= 0) {
                            Ui.toast(act, "مبلغ صحیح نیست");
                            return;
                        }
                        Db.get(act).addLoan(JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]),
                                amount, etNote.getText().toString().trim());
                        Ui.toast(act, Fmt.money(amount) + " وام ثبت شد");
                        show(act);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private static void listLoans(final MainActivity act, final int year) {
        final Db db = Db.get(act);
        String[] r = yearRange(year);
        final List<Db.Loan> list = db.getLoansBetween(r[0], r[1]);
        if (list.isEmpty()) {
            Ui.toast(act, "برای سال " + year + " وامی ثبت نشده");
            show(act);
            return;
        }
        CharSequence[] items = new CharSequence[list.size()];
        for (int i = 0; i < items.length; i++) {
            Db.Loan l = list.get(i);
            items[i] = JalaliCalendar.gregorianStringToJalali(l.date) + "   " + Fmt.money(l.amount)
                    + (l.note == null || l.note.isEmpty() ? "" : "\n" + l.note);
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("وام‌های سال " + year + " (برای حذف لمس کنید)")
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, final int which) {
                        new MaterialAlertDialogBuilder(act)
                                .setTitle("حذف این وام؟")
                                .setMessage(Fmt.money(list.get(which).amount))
                                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dd, int w) {
                                        db.deleteLoan(list.get(which).id);
                                        Ui.toast(act, "حذف شد");
                                        show(act);
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
