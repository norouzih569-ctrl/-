package com.barber.app;

import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** صفحه‌ی یادآورهای پرداخت: اجاره، قسط، خرید مواد/کالا */
public final class ReminderUi {

    private ReminderUi() {
    }

    public static void show(final MainActivity act) {
        final LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 8));
        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle("⏰ یادآور پرداخت‌ها")
                .setView(scroll)
                .setPositiveButton("بستن", null)
                .setNeutralButton("+ یادآور جدید", null)
                .create();
        final Runnable[] rebuild = {null};
        rebuild[0] = new Runnable() {
            @Override
            public void run() {
                box.removeAllViews();
                List<Db.Reminder> list = Db.get(act).getReminders();
                TextView note = new TextView(act);
                note.setText(list.isEmpty()
                        ? "هنوز یادآوری ثبت نشده. مثلاً «اجاره‌ی مغازه» با تکرار ماهانه، «قسط ماشین ریش‌تراش» یا «خرید مواد»."
                        : "روی هر مورد بزنید: «پرداخت شد» مبلغ را در هزینه‌ها ثبت می‌کند.");
                note.setTextSize(12);
                note.setPadding(0, 0, 0, Ui.dp(act, 8));
                box.addView(note);
                String today = JalaliCalendar.todayGregorianString();
                for (final Db.Reminder r : list) {
                    TextView row = new TextView(act);
                    long days = ReminderMath.daysLeft(r.due, today);
                    row.setText(r.title + (r.monthly ? "  🔁" : "")
                            + "\n" + JalaliCalendar.gregorianStringToJalali(r.due)
                            + "  •  " + ReminderMath.describe(days)
                            + (r.amount > 0 ? "  •  " + Fmt.price(r.amount) : ""));
                    row.setTextSize(14);
                    row.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            actions(act, r, rebuild[0]);
                        }
                    });
                    box.addView(row);
                }
            }
        };
        rebuild[0].run();
        d.show();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit(act, null, rebuild[0]);
            }
        });
    }

    private static void actions(final MainActivity act, final Db.Reminder r, final Runnable done) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(r.title)
                .setItems(new CharSequence[]{"✅ پرداخت شد (ثبت در هزینه‌ها)", "✏️ ویرایش", "حذف"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface x, int which) {
                                if (which == 0) pay(act, r, done);
                                else if (which == 1) edit(act, r, done);
                                else confirmDelete(act, r, done);
                            }
                        })
                .show();
    }

    private static void pay(final MainActivity act, final Db.Reminder r, final Runnable done) {
        if (!act.canWrite()) return;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 4));
        TextView info = new TextView(act);
        info.setText(r.monthly
                ? "مبلغ در هزینه‌ها ثبت می‌شود و یادآور به ماه بعد می‌رود."
                : "مبلغ در هزینه‌ها ثبت می‌شود و این یادآور حذف می‌شود.");
        info.setTextSize(13);
        box.addView(info);
        final EditText et = new EditText(act);
        et.setHint("مبلغ پرداختی (" + AppConfig.CURRENCY + ")");
        et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (r.amount > 0) et.setText(Fmt.plain(r.amount));
        box.addView(et);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle("پرداخت – " + r.title)
                .setView(box)
                .setPositiveButton("ثبت", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Double a = Fmt.parsePrice(et.getText().toString());
                if (a == null || a <= 0) {
                    et.setError("مبلغ را وارد کنید");
                    return;
                }
                Db db = Db.get(act);
                long exp = db.payReminder(r.id, a);
                d.dismiss();
                if (exp < 0) {
                    Ui.toast(act, "ثبت نشد");
                    return;
                }
                Db.Reminder after = db.getReminder(r.id);
                if (after != null) ReminderScheduler.schedulePay(act, after);
                else ReminderScheduler.cancelPay(act, r.id);
                done.run();
                act.refreshAll();
                Ui.toast(act, "در هزینه‌ها ثبت شد");
            }
        });
    }

    private static void confirmDelete(final MainActivity act, final Db.Reminder r, final Runnable done) {
        if (!act.canWrite()) return;
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف «" + r.title + "»؟")
                .setMessage("فقط یادآور حذف می‌شود؛ هزینه‌های قبلاً ثبت‌شده می‌مانند.")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface x, int w) {
                        Db.get(act).deleteReminder(r.id);
                        ReminderScheduler.cancelPay(act, r.id);
                        done.run();
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private static void edit(final MainActivity act, final Db.Reminder r, final Runnable done) {
        if (!act.canWrite()) return;
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 4));
        final EditText etTitle = new EditText(act);
        etTitle.setHint("عنوان (مثلاً اجاره‌ی مغازه)");
        final EditText etAmount = new EditText(act);
        etAmount.setHint("مبلغ (" + AppConfig.CURRENCY + ") — اختیاری");
        etAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final EditText etLead = new EditText(act);
        etLead.setHint("چند روز قبل خبر بدهد؟ (۰ تا ۳۰)");
        etLead.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        final CheckBox cbMonthly = new CheckBox(act);
        cbMonthly.setText("هر ماه تکرار شود");
        final int[] date = JalaliCalendar.today();
        if (r != null) {
            etTitle.setText(r.title);
            if (r.amount > 0) etAmount.setText(Fmt.plain(r.amount));
            etLead.setText(String.valueOf(r.leadDays));
            cbMonthly.setChecked(r.monthly);
            int[] j = JalaliCalendar.jalaliOfIso(r.due);
            date[0] = j[0];
            date[1] = j[1];
            date[2] = j[2];
        } else {
            etLead.setText("2");
            cbMonthly.setChecked(true);
        }
        final Button btnDate = new Button(act, null, android.R.attr.buttonBarButtonStyle);
        btnDate.setAllCaps(false);
        btnDate.setText("سررسید: " + JalaliCalendar.format(date[0], date[1], date[2]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JalaliPicker.show(act, date, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int dd) {
                        date[0] = y;
                        date[1] = m;
                        date[2] = dd;
                        btnDate.setText("سررسید: " + JalaliCalendar.format(y, m, dd));
                    }
                });
            }
        });
        box.addView(etTitle);
        box.addView(etAmount);
        box.addView(btnDate);
        box.addView(cbMonthly);
        box.addView(etLead);
        final AlertDialog d = new MaterialAlertDialogBuilder(act)
                .setTitle(r == null ? "یادآور جدید" : "ویرایش یادآور")
                .setView(box)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String title = ServiceList.clean(etTitle.getText().toString());
                if (title.isEmpty()) {
                    etTitle.setError("عنوان را وارد کنید");
                    return;
                }
                double amount = 0;
                if (!etAmount.getText().toString().trim().isEmpty()) {
                    Double a = Fmt.parsePrice(etAmount.getText().toString());
                    if (a == null) {
                        etAmount.setError("مبلغ صحیح نیست");
                        return;
                    }
                    amount = a;
                }
                Double lead = Fmt.parsePrice(etLead.getText().toString());
                if (lead == null || lead > 30) {
                    etLead.setError("۰ تا ۳۰");
                    return;
                }
                String iso = JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]);
                Db db = Db.get(act);
                long id;
                if (r == null) {
                    id = db.addReminder(title, amount, iso, cbMonthly.isChecked(), date[2], (int) Math.round(lead));
                } else {
                    id = r.id;
                    db.updateReminder(id, title, amount, iso, cbMonthly.isChecked(), date[2], (int) Math.round(lead));
                }
                Db.Reminder saved = db.getReminder(id);
                if (saved != null) ReminderScheduler.schedulePay(act, saved);
                d.dismiss();
                done.run();
            }
        });
    }

    /** روزی یک‌بار هنگام باز کردن برنامه، یادآورهای گذشته/نزدیک را نشان می‌دهد */
    public static void showDueOncePerDay(final MainActivity act) {
        android.content.SharedPreferences sp = act.getSharedPreferences("pay_reminders", 0);
        String today = JalaliCalendar.todayGregorianString();
        if (today.equals(sp.getString("shown", ""))) return;
        List<Db.Reminder> due = Db.get(act).dueReminders();
        if (due.isEmpty()) return;
        sp.edit().putString("shown", today).apply();
        StringBuilder sb = new StringBuilder();
        for (Db.Reminder r : due) {
            sb.append("• ").append(r.title);
            if (r.amount > 0) sb.append(" — ").append(Fmt.price(r.amount));
            sb.append("  (").append(ReminderMath.describe(ReminderMath.daysLeft(r.due, today))).append(")\n");
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("⏰ پرداخت‌های نزدیک")
                .setMessage(sb.toString().trim())
                .setPositiveButton("بازکردن یادآورها", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface x, int w) {
                        show(act);
                    }
                })
                .setNegativeButton("بعداً", null)
                .show();
    }
}
