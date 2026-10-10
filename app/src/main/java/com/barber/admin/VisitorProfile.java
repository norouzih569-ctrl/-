package com.barber.admin;

import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** پروفایل ویزیتور: درصد، مانده‌ی پورسانت، وضعیت پرداخت هر لایسنس، مشتریان و تسویه‌ها. */
public final class VisitorProfile {

    private VisitorProfile() {
    }

    private static View infoRow(MainActivity act, String label, String value) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4));
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = new TextView(act);
        v.setText(value);
        v.setGravity(Gravity.END);
        v.setTypeface(v.getTypeface(), android.graphics.Typeface.BOLD);
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f));
        return row;
    }

    private static TextView heading(MainActivity act, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(15);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setTextColor(Ui.themeColor(act, R.attr.barberAccent, 0xFF0F766E));
        t.setPadding(0, Ui.dp(act, 14), 0, Ui.dp(act, 4));
        return t;
    }

    private static TextView note(MainActivity act, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
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

    public static void show(final MainActivity act, final long visitorId) {
        final Db db = Db.get(act);
        final Models.Visitor vis = db.getVisitor(visitorId);
        if (vis == null) return;

        final Commission.Result res = db.visitorCommissionStatus(visitorId);
        long total = 0;
        long paid = 0;
        for (Commission.Item it : res.items) {
            total += it.commission;
            paid += it.paid;
        }
        final long due = total - paid;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        if (!vis.mobile.isEmpty()) {
            LinearLayout actions = new LinearLayout(act);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(smallButton(act, "📞 تماس", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Contact.dial(act, vis.mobile);
                }
            }));
            actions.addView(smallButton(act, "📩 پیامک", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Contact.sms(act, vis.mobile, "");
                }
            }));
            actions.addView(smallButton(act, "💬 واتساپ", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Contact.whatsapp(act, vis.mobile, "");
                }
            }));
            box.addView(actions);
        }

        box.addView(heading(act, "مشخصات"));
        box.addView(infoRow(act, "درصد پورسانت", Fmt.percent(vis.percent)));
        if (!vis.mobile.isEmpty()) box.addView(infoRow(act, "موبایل", vis.mobile));
        box.addView(infoRow(act, "وضعیت", vis.active ? "فعال" : "غیرفعال"));
        if (!vis.notes.trim().isEmpty()) box.addView(infoRow(act, "یادداشت", vis.notes));

        box.addView(heading(act, "حساب پورسانت"));
        box.addView(infoRow(act, "کل پورسانت", Fmt.money(total)));
        box.addView(infoRow(act, "پرداخت‌شده", Fmt.money(paid)));
        box.addView(infoRow(act, due > 0 ? "❌ پرداخت‌نشده" : "✅ تسویه کامل", Fmt.money(Math.max(0, due))));
        if (res.credit > 0) box.addView(infoRow(act, "پرداخت اضافه (بستانکار)", Fmt.money(res.credit)));

        // وضعیت پرداخت هر لایسنس
        final Map<Long, Models.License> licById = new HashMap<Long, Models.License>();
        for (Models.License l : db.listCommissionLicenses(visitorId)) licById.put(l.id, l);
        box.addView(heading(act, "وضعیت پورسانت هر لایسنس (" + res.items.size() + ")"));
        if (res.items.isEmpty()) {
            box.addView(note(act, "هنوز لایسنس پورسانت‌داری از طریق این ویزیتور فروخته نشده است"));
        }
        final List<Commission.Item> unpaidItems = new ArrayList<Commission.Item>();
        for (int i = res.items.size() - 1; i >= 0; i--) {       // جدید به قدیم
            Commission.Item it = res.items.get(i);
            Models.License l = licById.get(it.licenseId);
            if (l == null) continue;
            Models.Customer c = db.getCustomer(l.customerId);
            TextView t = new TextView(act);
            String line = (c != null ? c.shopName : "؟") + " • "
                    + JalaliCalendar.gregorianStringToJalali(l.issueDate) + "\n"
                    + Labels.licenseType(l.type) + " • " + Fmt.percent(l.commissionPercent) + " از "
                    + Fmt.price(l.finalPrice) + " = " + Fmt.money(it.commission) + "\n"
                    + Commission.statusLabel(it.status());
            if (it.status() == Commission.PARTIAL) line += " (" + Fmt.price(it.paid) + " از " + Fmt.price(it.commission) + ")";
            t.setText(line);
            t.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 5));
            box.addView(t);
            if (it.status() != Commission.PAID) unpaidItems.add(it);
        }

        // مشتریان معرفی‌شده
        List<Models.Customer> custs = db.listCustomersByVisitor(visitorId);
        box.addView(heading(act, "مشتریان معرفی‌شده (" + custs.size() + ")"));
        if (custs.isEmpty()) box.addView(note(act, "مشتری‌ای ثبت نشده است"));
        for (Models.Customer c : custs) {
            TextView t = new TextView(act);
            t.setText(c.shopName + (c.city.isEmpty() ? "" : " • " + c.city));
            t.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
            box.addView(t);
        }

        // تسویه‌ها
        final List<Models.Payout> payouts = db.listPayouts(visitorId);
        box.addView(heading(act, "پرداخت‌های انجام‌شده (" + payouts.size() + ")"));
        if (payouts.isEmpty()) box.addView(note(act, "هنوز پرداختی ثبت نشده است"));
        final AlertDialog[] holder = new AlertDialog[1];
        for (final Models.Payout p : payouts) {
            TextView t = new TextView(act);
            String target = "";
            if (p.licenseId > 0) {
                Models.License l = db.getLicense(p.licenseId);
                Models.Customer c = l != null ? db.getCustomer(l.customerId) : null;
                if (c != null) target = " • بابت " + c.shopName;
            }
            t.setText(JalaliCalendar.gregorianStringToJalali(p.payDate) + " • " + Fmt.money(p.amount) + target
                    + (p.note.isEmpty() ? "" : "\n" + p.note));
            t.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 5));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeletePayout(act, p, holder[0]);
                }
            });
            box.addView(t);
        }
        if (!payouts.isEmpty()) {
            TextView hint = note(act, "برای حذف یک پرداخت، روی آن بزنید");
            hint.setTextSize(11.5f);
            box.addView(hint);
        }

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);

        holder[0] = new MaterialAlertDialogBuilder(act)
                .setTitle(vis.name)
                .setView(scroll)
                .setPositiveButton("ثبت پرداخت پورسانت", null)
                .setNeutralButton("ویرایش", null)
                .setNegativeButton("بستن", null)
                .create();
        final AlertDialog dialog = holder[0];
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                showPayoutDialog(act, vis, due, unpaidItems, licById);
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                VisitorForm.show(act, vis);
            }
        });
    }

    private static void confirmDeletePayout(final MainActivity act, final Models.Payout p, final AlertDialog profile) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف پرداخت")
                .setMessage("پرداخت " + Fmt.money(p.amount) + " مورخ "
                        + JalaliCalendar.gregorianStringToJalali(p.payDate) + " حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deletePayout(p.id);
                        if (profile != null) profile.dismiss();
                        act.refreshAll();
                        show(act, p.visitorId);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** ثبت پرداخت پورسانت؛ مبلغ پیش‌فرض = کل مانده (تسویه‌ی کامل) یا مانده‌ی لایسنس انتخاب‌شده. */
    private static void showPayoutDialog(final MainActivity act, final Models.Visitor vis, final long totalDue,
                                         List<Commission.Item> unpaid, Map<Long, Models.License> licById) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_payout, null);
        final EditText etAmount = v.findViewById(R.id.et_payout_amount);
        final EditText etNote = v.findViewById(R.id.et_payout_note);
        final Button btnDate = v.findViewById(R.id.btn_payout_date);
        final Spinner spLicense = v.findViewById(R.id.sp_payout_license);
        TextView tvDue = v.findViewById(R.id.tv_payout_due);
        tvDue.setText("پرداخت‌نشده‌ی کل: " + Fmt.money(Math.max(0, totalDue)));

        final List<Long> licenseIds = new ArrayList<Long>();
        final List<Long> licenseDue = new ArrayList<Long>();
        List<String> names = new ArrayList<String>();
        licenseIds.add(0L);
        licenseDue.add(Math.max(0, totalDue));
        names.add("عمومی (قدیمی‌ترین لایسنس‌های پرداخت‌نشده تسویه می‌شود)");
        Db db = Db.get(act);
        for (int i = unpaid.size() - 1; i >= 0; i--) {
            Commission.Item it = unpaid.get(i);
            Models.License l = licById.get(it.licenseId);
            if (l == null) continue;
            Models.Customer c = db.getCustomer(l.customerId);
            licenseIds.add(l.id);
            licenseDue.add(it.due());
            names.add((c != null ? c.shopName : "؟") + " – " + Fmt.price(it.due()) + " مانده");
        }
        spLicense.setAdapter(Ui.spinnerAdapter(act, names));
        etAmount.setText(totalDue > 0 ? Fmt.plain(totalDue) : "");
        spLicense.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                long d = licenseDue.get(position);
                etAmount.setText(d > 0 ? Fmt.plain(d) : "");
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        final String[] date = {JalaliCalendar.todayGregorianString()};
        btnDate.setText("تاریخ پرداخت: " + JalaliCalendar.gregorianStringToJalali(date[0]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JalaliPicker.show(act, JalaliCalendar.jalaliOfIso(date[0]), new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int d) {
                        date[0] = JalaliCalendar.jalaliToGregorianString(y, m, d);
                        btnDate.setText("تاریخ پرداخت: " + JalaliCalendar.format(y, m, d));
                    }
                });
            }
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("پرداخت پورسانت – " + vis.name)
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        show(act, vis.id);
                    }
                })
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Double amount = Fmt.parsePrice(etAmount.getText().toString());
                if (amount == null || amount < 1) {
                    etAmount.setError("مبلغ را وارد کنید");
                    return;
                }
                Models.Payout p = new Models.Payout();
                p.visitorId = vis.id;
                p.licenseId = licenseIds.get(Math.max(0, spLicense.getSelectedItemPosition()));
                p.amount = Math.round(amount);
                p.payDate = date[0];
                p.note = etNote.getText().toString();
                Db.get(act).addPayout(p);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, "پرداخت ثبت شد");
                show(act, vis.id);
            }
        });
    }
}
