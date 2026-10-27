package com.barber.app;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** دعوت مشتریان قدیمی: مشتری‌هایی که چند هفته نیامده‌اند، با پیام آماده و یک لمس. */
final class WinbackUi {

    private WinbackUi() {
    }

    private static final int[] DAY_OPTIONS = {21, 30, 45, 60, 90};

    private static final class Row {
        Db.Customer c;
        int days;
        String lastIso;
    }

    static int daysSince(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso);
            long diff = System.currentTimeMillis() - d.getTime();
            return (int) (diff / 86400000L);
        } catch (Exception e) {
            return 0;
        }
    }

    static void show(final MainActivity act) {
        final Db db = Db.get(act);
        final int minDays = AppConfig.getWinbackDays(act);
        Map<Long, String> last = db.lastVisits();
        Set<Long> upcoming = db.customersWithUpcoming(JalaliCalendar.todayGregorianString());
        final List<Row> rows = new ArrayList<>();
        for (Db.Customer c : db.getCustomers()) {
            String iso = last.get(c.id);
            if (iso == null || upcoming.contains(c.id)) continue; // هرگز نیامده یا نوبت دارد
            int dd = daysSince(iso);
            if (dd < minDays) continue;
            Row r = new Row();
            r.c = c;
            r.days = dd;
            r.lastIso = iso;
            rows.add(r);
        }
        java.util.Collections.sort(rows, new java.util.Comparator<Row>() {
            @Override
            public int compare(Row a, Row b) {
                return b.days - a.days;
            }
        });

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 4));

        TextView info = new TextView(act);
        info.setText("مشتری‌هایی که بیشتر از " + minDays + " روز نیامده‌اند و نوبت آینده ندارند: "
                + rows.size() + " نفر\nروی هر نفر بزنید تا پیام دعوت را بفرستید.");
        info.setTextSize(13);
        info.setPadding(0, 0, 0, Ui.dp(act, 8));
        box.addView(info);

        final AlertDialog[] holder = new AlertDialog[1];
        if (rows.isEmpty()) {
            TextView none = new TextView(act);
            none.setText("فعلاً کسی نیست 🎉");
            none.setPadding(0, Ui.dp(act, 12), 0, Ui.dp(act, 12));
            box.addView(none);
        }
        for (final Row r : rows) {
            TextView t = new TextView(act);
            t.setText(r.c.name + "\nآخرین مراجعه: " + JalaliCalendar.gregorianStringToJalali(r.lastIso)
                    + "  (" + r.days + " روز پیش)");
            t.setTextSize(14);
            t.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 10));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendDialog(act, r.c);
                }
            });
            box.addView(t);
        }

        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(act)
                .setTitle("💌 دعوت مشتریان قدیمی")
                .setView(Ui.scroll(box))
                .setPositiveButton("تنظیم مدت و متن", null)
                .setNegativeButton("بستن", null);
        holder[0] = b.create();
        holder[0].show();
        holder[0].getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                holder[0].dismiss();
                settings(act);
            }
        });
    }

    private static String message(MainActivity act, Db.Customer c) {
        return AppConfig.getWinbackText(act)
                .replace("{نام}", c.name)
                .replace("{سالن}", AppConfig.getSalonName(act));
    }

    private static void sendDialog(final MainActivity act, final Db.Customer c) {
        final String text = message(act, c);
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(act)
                .setTitle("پیام برای " + c.name)
                .setMessage(text)
                .setPositiveButton("📤 ارسال (بله، واتساپ...)", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        ReportExporter.shareText(act, "دعوت", text);
                    }
                })
                .setNegativeButton("بستن", null);
        if (Logic.isUsablePhone(c.phone)) {
            b.setNeutralButton("📩 پیامک", new android.content.DialogInterface.OnClickListener() {
                @Override
                public void onClick(android.content.DialogInterface d, int w) {
                    Messenger.openSms(act, c.phone, text);
                }
            });
        }
        b.show();
    }

    private static void settings(final MainActivity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 4));

        final int[] days = {AppConfig.getWinbackDays(act)};
        final Button btnDays = new Button(act);
        btnDays.setText("مشتری‌هایی که بیشتر از " + days[0] + " روز نیامده‌اند (برای تغییر لمس کنید)");
        btnDays.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int idx = 0;
                for (int i = 0; i < DAY_OPTIONS.length; i++) if (DAY_OPTIONS[i] == days[0]) idx = i;
                days[0] = DAY_OPTIONS[(idx + 1) % DAY_OPTIONS.length];
                btnDays.setText("مشتری‌هایی که بیشتر از " + days[0] + " روز نیامده‌اند (برای تغییر لمس کنید)");
            }
        });
        box.addView(btnDays);

        TextView hint = new TextView(act);
        hint.setText("متن پیام؛ {نام} با نام مشتری و {سالن} با نام آرایشگاه جایگزین می‌شود:");
        hint.setTextSize(12);
        hint.setPadding(0, Ui.dp(act, 10), 0, 0);
        box.addView(hint);
        final EditText et = new EditText(act);
        et.setText(AppConfig.getWinbackText(act));
        et.setMinLines(3);
        et.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        et.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        box.addView(et);

        new MaterialAlertDialogBuilder(act)
                .setTitle("تنظیم دعوت مشتریان")
                .setView(Ui.scroll(box))
                .setPositiveButton("ذخیره", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        String t = et.getText().toString().trim();
                        AppConfig.setWinbackText(act, t.isEmpty() ? AppConfig.DEFAULT_WINBACK : t);
                        AppConfig.setWinbackDays(act, days[0]);
                        show(act);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
