package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** کد جابجایی دستگاه: مشتری گوشی عوض کرده؛ تاریخ پایان لایسنس او ثابت می‌ماند. */
public final class TransferDialog {

    private TransferDialog() {
    }

    /** حدس پایان لایسنس از روی آخرین لایسنس زمان‌دار مشتری (فقط پیش‌فرض است؛ باید با گوشی قدیمی چک شود) */
    private static String guessEnd(Db db, long customerId) {
        for (Models.License l : db.listLicenses(customerId)) {
            int days = LicenseCodec.daysForType(l.type);
            if (l.voided || days == 0) continue;
            long end = IssueLogic.epochDayOfIso(l.issueDate) + days - 1;
            long today = IssueLogic.todayEpochDay();
            if (end >= today && end - today <= IssueLogic.MAX_TRANSFER_DAYS) {
                return java.time.LocalDate.ofEpochDay(end).toString();
            }
        }
        return java.time.LocalDate.ofEpochDay(IssueLogic.todayEpochDay() + 365).toString();
    }

    public static void show(final MainActivity act, long customerId) {
        final Db db = Db.get(act);
        final List<Models.Customer> customers = db.listCustomers("");
        if (customers.isEmpty()) {
            Ui.toastLong(act, "اول باید یک مشتری ثبت کنید");
            return;
        }
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_transfer, null);
        final Spinner spCustomer = v.findViewById(R.id.sp_tr_customer);
        final EditText etDevice = v.findViewById(R.id.et_tr_device);
        final EditText etOld = v.findViewById(R.id.et_tr_old_device);
        final EditText etNote = v.findViewById(R.id.et_tr_note);
        final Button btnEnd = v.findViewById(R.id.btn_tr_end);
        final TextView tvInfo = v.findViewById(R.id.tv_tr_info);

        List<String> names = new ArrayList<String>();
        int sel = 0;
        for (int i = 0; i < customers.size(); i++) {
            Models.Customer c = customers.get(i);
            names.add(c.shopName + (c.ownerName.isEmpty() ? "" : " – " + c.ownerName));
            if (c.id == customerId) sel = i;
        }
        spCustomer.setAdapter(Ui.spinnerAdapter(act, names));
        spCustomer.setSelection(sel);

        final String[] end = {guessEnd(db, customers.get(sel).id)};
        final Runnable refreshEnd = new Runnable() {
            @Override
            public void run() {
                long today = IssueLogic.todayEpochDay();
                long e = IssueLogic.epochDayOfIso(end[0]);
                btnEnd.setText("تاریخ پایان لایسنس: " + JalaliCalendar.gregorianStringToJalali(end[0]));
                int extra = IssueLogic.transferExtra(today, e);
                if (extra >= 0) tvInfo.setText(extra + " روز از لایسنس باقی می‌ماند.");
                else if (extra == -1) tvInfo.setText("این تاریخ گذشته است؛ برای لایسنس تمام‌شده باید لایسنس جدید صادر کنید.");
                else tvInfo.setText("بیش از " + IssueLogic.MAX_TRANSFER_DAYS + " روز باقی مانده؛ در کد جابجایی جا نمی‌شود.");
            }
        };
        refreshEnd.run();
        btnEnd.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JalaliPicker.show(act, JalaliCalendar.jalaliOfIso(end[0]), new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int d) {
                        end[0] = JalaliCalendar.jalaliToGregorianString(y, m, d);
                        refreshEnd.run();
                    }
                });
            }
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("کد جابجایی دستگاه")
                .setView(v)
                .setPositiveButton("ساخت کد", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final Models.Customer cust = customers.get(Math.max(0, spCustomer.getSelectedItemPosition()));
                final String device = IssueLogic.parseDevice(etDevice.getText().toString());
                if (device == null) {
                    etDevice.setError("شناسه‌ی گوشی جدید باید ۱۰ حرف/رقم باشد");
                    return;
                }
                String oldRaw = etOld.getText().toString().trim();
                String old = oldRaw.isEmpty() ? "" : IssueLogic.parseDevice(oldRaw);
                if (old == null) {
                    etOld.setError("شناسه‌ی گوشی قبلی نامعتبر است (یا خالی بگذارید)");
                    return;
                }
                long today = IssueLogic.todayEpochDay();
                long endDay = IssueLogic.epochDayOfIso(end[0]);
                int extra = IssueLogic.transferExtra(today, endDay);
                if (extra < 0) {
                    Ui.toastLong(act, extra == -1
                            ? "تاریخ پایان گذشته است"
                            : "بیش از " + IssueLogic.MAX_TRANSFER_DAYS + " روز باقی مانده است");
                    return;
                }
                String code = IssueLogic.makeTransferCode(LicenseKeys.SECRET, device, today, endDay);
                if (code == null) {
                    Ui.toastLong(act, "ساعت یا تاریخ گوشی درست نیست؛ کد ساخته نشد");
                    return;
                }
                String endJalali = JalaliCalendar.gregorianStringToJalali(end[0]);
                StringBuilder notes = new StringBuilder("پایان لایسنس: " + endJalali);
                if (!old.isEmpty()) notes.append(" • از دستگاه ").append(old);
                String extraNote = etNote.getText().toString().trim();
                if (!extraNote.isEmpty()) notes.append(" • ").append(extraNote);

                Models.License l = new Models.License();
                l.customerId = cust.id;
                l.deviceId = device;
                l.type = LicenseCodec.TYPE_TRANSFER;
                l.code = code;
                l.issueDate = JalaliCalendar.todayGregorianString();
                l.endDate = end[0];
                l.notes = notes.toString();
                db.insertLicense(l);
                dialog.dismiss();
                act.refreshAll();
                showResult(act, cust, code, device, endJalali);
            }
        });
    }

    private static void showResult(final MainActivity act, final Models.Customer cust, final String code,
                                   final String device, final String endJalali) {
        final String msg = IssueLogic.transferMessage(code, device, endJalali);
        new MaterialAlertDialogBuilder(act)
                .setTitle("✅ کد جابجایی ساخته شد")
                .setMessage(cust.shopName + "\n\n" + code + "\n\nشناسه‌ی گوشی جدید: " + device
                        + "\nپایان لایسنس (ثابت): " + endJalali)
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
