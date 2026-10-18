package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** فرم ثبت و ویرایش مشتری. */
public final class CustomerForm {

    private CustomerForm() {
    }

    public static void show(final MainActivity act, final Models.Customer existing) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_customer, null);
        final EditText etShop = v.findViewById(R.id.et_shop);
        final EditText etOwner = v.findViewById(R.id.et_owner);
        final EditText etMobile = v.findViewById(R.id.et_mobile);
        final EditText etPhone = v.findViewById(R.id.et_phone);
        final EditText etCity = v.findViewById(R.id.et_city);
        final EditText etAddress = v.findViewById(R.id.et_address);
        final EditText etNational = v.findViewById(R.id.et_national);
        final EditText etEconomic = v.findViewById(R.id.et_economic);
        final EditText etPostal = v.findViewById(R.id.et_postal);
        final EditText etNotes = v.findViewById(R.id.et_notes);
        final Spinner spVisitor = v.findViewById(R.id.sp_visitor);
        final Button btnDate = v.findViewById(R.id.btn_met_date);

        // ویزیتورها: فعال‌ها + ویزیتور فعلی مشتری (حتی اگر غیرفعال شده)
        final List<Long> visitorIds = new ArrayList<Long>();
        List<String> visitorNames = new ArrayList<String>();
        visitorIds.add(0L);
        visitorNames.add("بدون ویزیتور");
        int selected = 0;
        for (Models.Visitor vis : Db.get(act).listVisitors(false)) {
            boolean current = existing != null && existing.visitorId == vis.id;
            if (!vis.active && !current) continue;
            visitorIds.add(vis.id);
            visitorNames.add(vis.name + " (" + Fmt.percent(vis.percent) + ")" + (vis.active ? "" : " – غیرفعال"));
            if (current) selected = visitorIds.size() - 1;
        }
        spVisitor.setAdapter(Ui.spinnerAdapter(act, visitorNames));
        spVisitor.setSelection(selected);

        final String[] met = {existing != null && !existing.metDate.isEmpty()
                ? existing.metDate : JalaliCalendar.todayGregorianString()};
        btnDate.setText("تاریخ آشنایی: " + JalaliCalendar.gregorianStringToJalali(met[0]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JalaliPicker.show(act, JalaliCalendar.jalaliOfIso(met[0]), new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int y, int m, int d) {
                        met[0] = JalaliCalendar.jalaliToGregorianString(y, m, d);
                        btnDate.setText("تاریخ آشنایی: " + JalaliCalendar.format(y, m, d));
                    }
                });
            }
        });

        if (existing != null) {
            etShop.setText(existing.shopName);
            etOwner.setText(existing.ownerName);
            etMobile.setText(existing.mobile);
            etPhone.setText(existing.phone);
            etCity.setText(existing.city);
            etAddress.setText(existing.address);
            etNational.setText(existing.nationalId);
            etEconomic.setText(existing.economicCode);
            etPostal.setText(existing.postalCode);
            etNotes.setText(existing.notes);
        }

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(existing == null ? "مشتری جدید" : "ویرایش مشتری")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String shop = etShop.getText().toString().trim();
                if (shop.isEmpty()) {
                    etShop.setError("نام آرایشگاه را وارد کنید");
                    return;
                }
                String mobileRaw = etMobile.getText().toString().trim();
                if (!mobileRaw.isEmpty() && !Validate.isValidMobile(mobileRaw)) {
                    etMobile.setError("موبایل باید مثل 09123456789 باشد");
                    return;
                }
                String national = Validate.digits(etNational.getText().toString());
                if (!national.isEmpty() && !Validate.isValidNationalId(national)) {
                    etNational.setError("کد ملی معتبر نیست");
                    return;
                }
                Models.Customer c = existing != null ? existing : new Models.Customer();
                c.shopName = shop;
                c.ownerName = etOwner.getText().toString();
                c.mobile = mobileRaw.isEmpty() ? "" : Validate.normalizeMobile(mobileRaw);
                c.phone = Validate.digits(etPhone.getText().toString());
                c.city = etCity.getText().toString();
                c.address = etAddress.getText().toString();
                c.nationalId = national;
                c.economicCode = Validate.digits(etEconomic.getText().toString());
                c.postalCode = Validate.digits(etPostal.getText().toString());
                c.visitorId = visitorIds.get(Math.max(0, spVisitor.getSelectedItemPosition()));
                c.metDate = met[0];
                c.notes = etNotes.getText().toString();
                Db db = Db.get(act);
                if (existing == null) db.insertCustomer(c);
                else db.updateCustomer(c);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, existing == null ? "مشتری اضافه شد" : "مشتری ویرایش شد");
            }
        });
    }
}
