package com.barber.app;

import android.content.Intent;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** پروفایل مشتری: آمار مراجعه، باشگاه مشتریان، یادداشت و سابقه‌ی نوبت‌ها. */
public final class CustomerProfile {

    private static final int HISTORY_LIMIT = 10;

    private CustomerProfile() {
    }

    private static View statRow(MainActivity act, String label, String value) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 5));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(act);
        v.setText(value);
        v.setGravity(Gravity.END);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        return row;
    }

    private static TextView heading(MainActivity act, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(Ui.themeColor(act, R.attr.barberAccent, 0xFF1F5FBF));
        t.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 4));
        return t;
    }

    private static Button smallButton(MainActivity act, String text, View.OnClickListener l) {
        Button b = new Button(act, null, android.R.attr.borderlessButtonStyle);
        b.setText(text);
        b.setOnClickListener(l);
        b.setAllCaps(false);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    public static void show(final MainActivity act, final long customerId) {
        final Db db = Db.get(act);
        final Db.Customer c = db.getCustomer(customerId);
        if (c == null) return;

        List<Db.Appointment> all = db.getCustomerAppointments(customerId); // جدید به قدیم
        String[] dates = new String[all.size()];
        String[] times = new String[all.size()];
        double[] prices = new double[all.size()];
        for (int i = 0; i < all.size(); i++) {
            dates[i] = all.get(i).date;
            times[i] = all.get(i).time;
            prices[i] = all.get(i).price;
        }
        Logic.Stats st = Logic.stats(dates, times, prices, JalaliCalendar.todayGregorianString());
        Db.Appointment next = st.nextIndex >= 0 ? all.get(st.nextIndex) : null;
        int threshold = AppConfig.getLoyaltyThreshold(act);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        TextView phone = new TextView(act);
        boolean hasPhone = c.phone != null && !c.phone.trim().isEmpty();
        phone.setText(hasPhone ? c.phone : "شماره‌ای ثبت نشده است");
        phone.setTextSize(16);
        box.addView(phone);

        if (hasPhone) {
            LinearLayout actions = new LinearLayout(act);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(smallButton(act, "📞 تماس", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.trim())));
                }
            }));
            final String body;
            if (next != null) {
                int[] j = JalaliCalendar.jalaliOfIso(next.date);
                String date = JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(j[0], j[1], j[2])] + " "
                        + JalaliCalendar.format(j[0], j[1], j[2]);
                body = Logic.fillTemplate(AppConfig.getSmsTemplate(act), c.name, date, next.time,
                        next.service, AppConfig.getSalonName(act));
            } else {
                body = "";
            }
            actions.addView(smallButton(act, "📩 پیامک", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Messenger.openSms(act, c.phone, body);
                }
            }));
            box.addView(actions);
        }

        box.addView(heading(act, "آمار مراجعه"));
        box.addView(statRow(act, "تعداد مراجعه", String.valueOf(st.visits)));
        box.addView(statRow(act, "آخرین مراجعه",
                st.lastVisit == null ? "—" : JalaliCalendar.gregorianStringToJalali(st.lastVisit)));
        box.addView(statRow(act, "مجموع پرداختی", Fmt.money(st.spent)));
        box.addView(statRow(act, "میانگین هر مراجعه", st.visits > 0 ? Fmt.money(st.average()) : "—"));
        if (next != null) {
            box.addView(statRow(act, "نوبت بعدی",
                    JalaliCalendar.gregorianStringToJalali(next.date) + "  " + next.time));
        }

        if (threshold > 0) {
            box.addView(heading(act, "باشگاه مشتریان"));
            int left = Logic.visitsUntilReward(st.visits, threshold);
            TextView t = new TextView(act);
            t.setText(left == 1
                    ? "🎁 مراجعه‌ی بعدی این مشتری جایزه دارد! (هر " + threshold + " مراجعه)"
                    : (left - 1) + " مراجعه‌ی دیگر تا مراجعه‌ی جایزه‌دار (هر " + threshold + " مراجعه)");
            box.addView(t);
        }

        box.addView(heading(act, "یادداشت"));
        final EditText notes = new EditText(act);
        notes.setHint("مثلاً: نمره‌ی ماشین، حساسیت، سلیقه‌ی مشتری…");
        notes.setMinLines(2);
        notes.setGravity(Gravity.TOP | Gravity.START);
        notes.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (c.notes != null) notes.setText(c.notes);
        box.addView(notes);

        box.addView(heading(act, "سابقه‌ی نوبت‌ها"));
        if (all.isEmpty()) {
            TextView t = new TextView(act);
            t.setText("هنوز نوبتی ثبت نشده است");
            box.addView(t);
        }
        for (int i = 0; i < all.size() && i < HISTORY_LIMIT; i++) {
            Db.Appointment a = all.get(i);
            TextView t = new TextView(act);
            t.setText(JalaliCalendar.gregorianStringToJalali(a.date) + "  •  " + a.time + "  •  "
                    + a.service + "  •  " + Fmt.price(a.price));
            t.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
            box.addView(t);
        }
        if (all.size() > HISTORY_LIMIT) {
            TextView t = new TextView(act);
            t.setText("… و " + (all.size() - HISTORY_LIMIT) + " نوبت قدیمی‌تر");
            t.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
            box.addView(t);
        }

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);

        new MaterialAlertDialogBuilder(act)
                .setTitle(c.name)
                .setView(scroll)
                .setPositiveButton("ذخیره‌ی یادداشت", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        db.updateCustomerNotes(customerId, notes.getText().toString().trim());
                        Ui.toast(act, "یادداشت ذخیره شد");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }
}
