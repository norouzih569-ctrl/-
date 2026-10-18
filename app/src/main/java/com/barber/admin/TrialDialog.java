package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** صدور کد آزمایشی (۷، ۱۴ یا ۳۰ روزه) برای یک مشتری و دستگاه او؛ بدون مبلغ و پورسانت. */
public final class TrialDialog {

    private static final int[] DAYS = {7, 14, 30};

    private TrialDialog() {
    }

    /** customerId = 0 یعنی از فهرست انتخاب شود */
    public static void show(final MainActivity act, long customerId) {
        final Db db = Db.get(act);
        final List<Models.Customer> customers = db.listCustomers("");
        if (customers.isEmpty()) {
            Ui.toastLong(act, "اول باید یک مشتری ثبت کنید");
            return;
        }
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_trial, null);
        final Spinner spCustomer = v.findViewById(R.id.sp_trial_customer);
        final Spinner spDays = v.findViewById(R.id.sp_trial_days);
        final EditText etDevice = v.findViewById(R.id.et_trial_device);
        final EditText etNote = v.findViewById(R.id.et_trial_note);

        List<String> names = new ArrayList<String>();
        int sel = 0;
        for (int i = 0; i < customers.size(); i++) {
            Models.Customer c = customers.get(i);
            names.add(c.shopName + (c.ownerName.isEmpty() ? "" : " – " + c.ownerName));
            if (c.id == customerId) sel = i;
        }
        spCustomer.setAdapter(Ui.spinnerAdapter(act, names));
        spCustomer.setSelection(sel);

        List<String> dayNames = new ArrayList<String>();
        for (int d : DAYS) dayNames.add(Fa.digits(String.valueOf(d)) + " روزه");
        spDays.setAdapter(Ui.spinnerAdapter(act, dayNames));

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("صدور کد آزمایشی")
                .setView(v)
                .setPositiveButton("ساخت کد", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Models.Customer cust = customers.get(Math.max(0, spCustomer.getSelectedItemPosition()));
                final int days = DAYS[Math.max(0, Math.min(DAYS.length - 1, spDays.getSelectedItemPosition()))];
                final String device = IssueLogic.parseDevice(etDevice.getText().toString());
                if (device == null) {
                    etDevice.setError("شناسه باید ۱۰ حرف/رقم باشد (مثل K7M2A-9QX4T)");
                    return;
                }
                final long today = IssueLogic.todayEpochDay();
                final String code = IssueLogic.makeTrialCode(LicenseKeys.SECRET, device, days, today);
                if (code == null) {
                    Ui.toastLong(act, "ساعت یا تاریخ گوشی درست نیست؛ کد ساخته نشد");
                    return;
                }
                final int before = db.trialCountForDevice(device);
                // هشدار: همین شناسه قبلاً برای مشتری دیگری صادر شده؟
                boolean otherOwner = false;
                for (Models.License old : db.listLicenses(0)) {
                    if (old.deviceId.equals(device) && old.customerId != cust.id && !old.voided) otherOwner = true;
                }
                String warn = null;
                if (before > 0) {
                    warn = "این دستگاه قبلاً " + Fa.digits(String.valueOf(before))
                            + " بار کد آزمایشی گرفته است. باز هم صادر شود؟";
                } else if (otherOwner) {
                    warn = "این شناسه‌ی دستگاه قبلاً برای مشتری دیگری کد گرفته است. باز هم صادر شود؟";
                }
                final Runnable doIssue = new Runnable() {
                    @Override
                    public void run() {
                        String endIso = java.time.LocalDate.ofEpochDay(IssueLogic.trialEndDay(today, days)).toString();
                        String endJalali = JalaliCalendar.gregorianStringToJalali(endIso);
                        Models.License l = new Models.License();
                        l.customerId = cust.id;
                        l.deviceId = device;
                        l.type = LicenseCodec.TYPE_TRIAL;
                        l.code = code;
                        l.issueDate = JalaliCalendar.todayGregorianString();
                        l.endDate = endIso;
                        String note = "آزمایشی " + days + " روزه تا " + endJalali;
                        String extra = etNote.getText().toString().trim();
                        l.notes = extra.isEmpty() ? note : note + " • " + extra;
                        db.insertLicense(l);
                        dialog.dismiss();
                        act.refreshAll();
                        showResult(act, cust, code, device, days, endJalali);
                    }
                };
                if (warn == null) {
                    doIssue.run();
                } else {
                    new MaterialAlertDialogBuilder(act)
                            .setTitle("هشدار")
                            .setMessage(warn)
                            .setPositiveButton("صدور", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int which) {
                                    doIssue.run();
                                }
                            })
                            .setNegativeButton("انصراف", null)
                            .show();
                }
            }
        });
    }

    private static void showResult(final MainActivity act, final Models.Customer cust, final String code,
                                   final String device, final int days, final String endJalali) {
        final String msg = IssueLogic.trialMessage(code, device, days, endJalali);
        new MaterialAlertDialogBuilder(act)
                .setTitle("✅ کد آزمایشی ساخته شد")
                .setMessage(cust.shopName + "\n\n" + code + "\n\nشناسه‌ی دستگاه: " + device
                        + "\nپایان دوره‌ی آزمایشی: " + endJalali)
                .setPositiveButton("ارسال", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        IssueDialog.sendChooser(act, cust, msg);
                    }
                })
                .setNeutralButton("کپی کد", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Share.copy(act, "license", code);
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }
}
