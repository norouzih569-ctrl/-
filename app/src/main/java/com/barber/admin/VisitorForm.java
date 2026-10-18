package com.barber.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** فرم ثبت و ویرایش ویزیتور (درصد هر ویزیتور جدا و قابل تغییر است). */
public final class VisitorForm {

    private VisitorForm() {
    }

    public static void show(final MainActivity act, final Models.Visitor existing) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_visitor, null);
        final EditText etName = v.findViewById(R.id.et_vis_name);
        final EditText etMobile = v.findViewById(R.id.et_vis_mobile);
        final EditText etPercent = v.findViewById(R.id.et_vis_percent);
        final EditText etNotes = v.findViewById(R.id.et_vis_notes);
        final CheckBox cbActive = v.findViewById(R.id.cb_vis_active);
        if (existing != null) {
            etName.setText(existing.name);
            etMobile.setText(existing.mobile);
            etPercent.setText(Fmt.percent(existing.percent).replace("%", ""));
            etNotes.setText(existing.notes);
            cbActive.setChecked(existing.active);
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(existing == null ? "ویزیتور جدید" : "ویرایش ویزیتور")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString().trim();
                if (name.isEmpty()) {
                    etName.setError("نام را وارد کنید");
                    return;
                }
                String mobileRaw = etMobile.getText().toString().trim();
                if (!mobileRaw.isEmpty() && !Validate.isValidMobile(mobileRaw)) {
                    etMobile.setError("موبایل باید مثل 09123456789 باشد");
                    return;
                }
                Double pct = Fmt.parsePrice(etPercent.getText().toString());
                if (pct == null || pct > 100) {
                    etPercent.setError("درصد را بین 0 تا 100 وارد کنید");
                    return;
                }
                Models.Visitor x = existing != null ? existing : new Models.Visitor();
                x.name = name;
                x.mobile = mobileRaw.isEmpty() ? "" : Validate.normalizeMobile(mobileRaw);
                x.percent = pct;
                x.active = cbActive.isChecked();
                x.notes = etNotes.getText().toString();
                Db db = Db.get(act);
                if (existing == null) db.insertVisitor(x);
                else db.updateVisitor(x);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, existing == null ? "ویزیتور اضافه شد" : "ویزیتور ویرایش شد");
            }
        });
    }
}
