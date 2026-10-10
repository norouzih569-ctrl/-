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
import java.util.List;

/** پروفایل مشتری: مشخصات، تماس، حساب (بدهی)، لایسنس‌ها و پرداخت‌ها. */
public final class CustomerProfile {

    private CustomerProfile() {
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

    private static void addInfo(LinearLayout box, MainActivity act, String label, String value) {
        if (value != null && !value.trim().isEmpty()) box.addView(infoRow(act, label, value));
    }

    public static void show(final MainActivity act, final long customerId) {
        final Db db = Db.get(act);
        final Models.Customer c = db.getCustomer(customerId);
        if (c == null) return;

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        final AlertDialog[] holder = new AlertDialog[1];
        com.google.android.material.button.MaterialButton issue = new com.google.android.material.button.MaterialButton(
                act, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        issue.setText("🔑 صدور کد برای این مشتری");
        issue.setAllCaps(false);
        issue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                IssueDialog.chooser(act, customerId);
            }
        });
        box.addView(issue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        com.google.android.material.button.MaterialButton docBtn = new com.google.android.material.button.MaterialButton(
                act, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        docBtn.setText("📄 پیش‌فاکتور / قرارداد");
        docBtn.setAllCaps(false);
        docBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (holder[0] != null) holder[0].dismiss();
                DocMaker.forCustomer(act, customerId);
            }
        });
        box.addView(docBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // دکمه‌های تماس
        final String contactNumber = !c.mobile.isEmpty() ? c.mobile : c.phone;
        if (!contactNumber.isEmpty()) {
            LinearLayout actions = new LinearLayout(act);
            actions.setOrientation(LinearLayout.HORIZONTAL);
            actions.addView(smallButton(act, "📞 تماس", new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Contact.dial(act, contactNumber);
                }
            }));
            if (!c.mobile.isEmpty()) {
                actions.addView(smallButton(act, "📩 پیامک", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Contact.sms(act, c.mobile, "");
                    }
                }));
                actions.addView(smallButton(act, "💬 واتساپ", new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Contact.whatsapp(act, c.mobile, "");
                    }
                }));
            }
            box.addView(actions);
        }

        // مشخصات
        box.addView(heading(act, "مشخصات"));
        addInfo(box, act, "مالک", c.ownerName);
        addInfo(box, act, "موبایل", c.mobile);
        addInfo(box, act, "تلفن ثابت", c.phone);
        addInfo(box, act, "شهر", c.city);
        addInfo(box, act, "آدرس", c.address);
        addInfo(box, act, "کد ملی", c.nationalId);
        addInfo(box, act, "کد اقتصادی", c.economicCode);
        addInfo(box, act, "کد پستی", c.postalCode);
        if (c.visitorId > 0) {
            Models.Visitor vis = db.getVisitor(c.visitorId);
            if (vis != null) addInfo(box, act, "ویزیتور معرف", vis.name + " (" + Fmt.percent(vis.percent) + ")");
        }
        if (!c.metDate.isEmpty()) addInfo(box, act, "تاریخ آشنایی", JalaliCalendar.gregorianStringToJalali(c.metDate));
        addInfo(box, act, "یادداشت", c.notes);

        // حساب
        Models.Balance bal = db.customerBalance(customerId);
        box.addView(heading(act, "حساب مشتری"));
        box.addView(infoRow(act, "مجموع لایسنس‌ها", Fmt.money(bal.total)));
        box.addView(infoRow(act, "دریافت‌شده", Fmt.money(bal.paid)));
        box.addView(infoRow(act, bal.due() >= 0 ? "باقی‌مانده (بدهی)" : "پیش‌پرداخت (بستانکار)",
                Fmt.money(Math.abs(bal.due()))));

        // لایسنس‌ها
        final List<Models.License> licenses = db.listLicenses(customerId);
        box.addView(heading(act, "لایسنس‌ها (" + licenses.size() + ")"));
        if (licenses.isEmpty()) {
            TextView t = new TextView(act);
            t.setText("هنوز لایسنسی برای این مشتری صادر نشده است");
            t.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
            box.addView(t);
        }
        for (Models.License l : licenses) {
            TextView t = new TextView(act);
            String price = l.type == LicenseCodec.TYPE_TRANSFER ? "" : "  •  " + Fmt.price(l.finalPrice);
            t.setText(JalaliCalendar.gregorianStringToJalali(l.issueDate) + "  •  "
                    + Labels.licenseType(l.type) + price + (l.voided ? "  ⛔ باطل" : ""));
            t.setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3));
            box.addView(t);
        }

        // پرداخت‌ها
        final List<Models.Payment> payments = db.listPayments(customerId);
        box.addView(heading(act, "پرداخت‌ها (" + payments.size() + ")"));
        if (payments.isEmpty()) {
            TextView t = new TextView(act);
            t.setText("پرداختی ثبت نشده است");
            t.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
            box.addView(t);
        }
        for (final Models.Payment p : payments) {
            TextView t = new TextView(act);
            String extra = p.method.isEmpty() ? "" : "  •  " + p.method;
            t.setText(JalaliCalendar.gregorianStringToJalali(p.payDate) + "  •  " + Fmt.money(p.amount) + extra
                    + (p.note.isEmpty() ? "" : "\n" + p.note));
            t.setPadding(0, Ui.dp(act, 5), 0, Ui.dp(act, 5));
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDeletePayment(act, p, holder[0]);
                }
            });
            box.addView(t);
        }
        if (!payments.isEmpty()) {
            TextView hint = new TextView(act);
            hint.setText("برای حذف یک پرداخت، روی آن بزنید");
            hint.setTextSize(11.5f);
            hint.setTextColor(Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888));
            box.addView(hint);
        }

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);

        holder[0] = new MaterialAlertDialogBuilder(act)
                .setTitle(c.shopName)
                .setView(scroll)
                .setPositiveButton("ثبت پرداخت", null)
                .setNeutralButton("ویرایش", null)
                .setNegativeButton("بستن", null)
                .create();
        final AlertDialog dialog = holder[0];
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                showPaymentDialog(act, c, licenses);
            }
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                CustomerForm.show(act, c);
            }
        });
    }

    private static void confirmDeletePayment(final MainActivity act, final Models.Payment p, final AlertDialog profile) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف پرداخت")
                .setMessage("پرداخت " + Fmt.money(p.amount) + " مورخ "
                        + JalaliCalendar.gregorianStringToJalali(p.payDate) + " حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deletePayment(p.id);
                        if (profile != null) profile.dismiss();
                        act.refreshAll();
                        show(act, p.customerId);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** ثبت پرداخت (قسط) مشتری؛ می‌تواند به یک لایسنس مشخص وصل شود. */
    static void showPaymentDialog(final MainActivity act, final Models.Customer c, List<Models.License> licenses) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_payment, null);
        final EditText etAmount = v.findViewById(R.id.et_pay_amount);
        final EditText etNote = v.findViewById(R.id.et_pay_note);
        final Button btnDate = v.findViewById(R.id.btn_pay_date);
        final Spinner spMethod = v.findViewById(R.id.sp_pay_method);
        final Spinner spLicense = v.findViewById(R.id.sp_pay_license);

        List<String> methods = new ArrayList<String>();
        for (String m : Labels.PAY_METHODS) methods.add(m);
        spMethod.setAdapter(Ui.spinnerAdapter(act, methods));

        final List<Long> licenseIds = new ArrayList<Long>();
        List<String> licenseNames = new ArrayList<String>();
        licenseIds.add(0L);
        licenseNames.add("عمومی (بدون لایسنس مشخص)");
        for (Models.License l : licenses) {
            if (l.voided || l.type == LicenseCodec.TYPE_TRANSFER) continue;
            licenseIds.add(l.id);
            licenseNames.add(JalaliCalendar.gregorianStringToJalali(l.issueDate) + " – "
                    + Labels.licenseType(l.type) + " – " + Fmt.price(l.finalPrice));
        }
        spLicense.setAdapter(Ui.spinnerAdapter(act, licenseNames));
        // پیش‌فرض: جدیدترین لایسنس معتبر
        if (licenseIds.size() > 1) spLicense.setSelection(1);

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
                .setTitle("ثبت پرداخت – " + c.shopName)
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        show(act, c.id);
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
                Models.Payment p = new Models.Payment();
                p.customerId = c.id;
                p.licenseId = licenseIds.get(Math.max(0, spLicense.getSelectedItemPosition()));
                p.amount = Math.round(amount);
                p.payDate = date[0];
                p.method = Labels.PAY_METHODS[Math.max(0, spMethod.getSelectedItemPosition())];
                p.note = etNote.getText().toString();
                Db.get(act).addPayment(p);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, "پرداخت ثبت شد");
                show(act, c.id);
            }
        });
    }
}
