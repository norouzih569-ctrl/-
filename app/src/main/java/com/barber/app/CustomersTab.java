package com.barber.app;

import android.content.Intent;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** تب مشتریان. */
public class CustomersTab implements Refreshable {

    private final MainActivity act;
    private final RowAdapter adapter;
    private final EditText etSearch;
    private List<Db.Customer> data = new ArrayList<>();

    public CustomersTab(MainActivity activity, View root) {
        this.act = activity;
        etSearch = root.findViewById(R.id.et_customer_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        ListView list = root.findViewById(R.id.list_customers);
        TextView empty = root.findViewById(R.id.empty_customers);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        root.findViewById(R.id.fab_add_customer).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(null);
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    showActions(data.get(position));
                }
            }
        });
    }

    @Override
    public void refresh() {
        Db db = Db.get(act);
        java.util.Map<Long, Integer> visits = db.visitCounts(JalaliCalendar.todayGregorianString());
        String q = etSearch.getText().toString().trim().toLowerCase();
        String qDigits = Logic.digitsOnly(q);
        data = new ArrayList<>();
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Customer c : db.getCustomers()) {
            if (!q.isEmpty()) {
                boolean byName = c.name != null && c.name.toLowerCase().contains(q);
                boolean byPhone = !qDigits.isEmpty() && Logic.digitsOnly(c.phone).contains(qDigits);
                if (!byName && !byPhone) continue;
            }
            data.add(c);
            Integer n = visits.get(c.id);
            String trail = (n == null || n == 0) ? "" : n + " مراجعه";
            rows.add(new RowAdapter.Row(c.id, "", c.name, c.phone, trail, true));
        }
        adapter.setRows(rows);
    }

    private void showActions(final Db.Customer c) {
        final boolean hasPhone = c.phone != null && !c.phone.trim().isEmpty();
        CharSequence[] items = hasPhone
                ? new CharSequence[]{"👤 پروفایل و سابقه", "ویرایش", "تماس", "حذف"}
                : new CharSequence[]{"👤 پروفایل و سابقه", "ویرایش", "حذف"};
        new MaterialAlertDialogBuilder(act)
                .setTitle(c.name)
                .setItems(items, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            CustomerProfile.show(act, c.id);
                        } else if (which == 1) {
                            showDialog(c);
                        } else if (hasPhone && which == 2) {
                            Intent i = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + c.phone.trim()));
                            act.startActivity(i);
                        } else {
                            confirmDelete(c);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final Db.Customer c) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف مشتری")
                .setMessage("آیا از حذف مشتری «" + c.name + "» مطمئن هستید؟\nنوبت‌های این مشتری هم حذف می‌شود.")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        List<Long> removed = Db.get(act).deleteCustomer(c.id);
                        for (Long id : removed) ReminderScheduler.cancel(act, id);
                        act.refreshAll();
                        Ui.toast(act, "مشتری حذف شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showDialog(final Db.Customer existing) {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_customer, null);
        final EditText etName = v.findViewById(R.id.et_customer_name);
        final EditText etPhone = v.findViewById(R.id.et_customer_phone);
        if (existing != null) {
            etName.setText(existing.name);
            etPhone.setText(existing.phone);
        }
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(existing == null ? "مشتری جدید" : "ویرایش مشتری")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String name = etName.getText().toString().trim();
                String phone = Fmt.normalizeDigits(etPhone.getText().toString()).trim();
                if (name.isEmpty()) {
                    etName.setError("نام را وارد کنید");
                    return;
                }
                Db db = Db.get(act);
                if (existing == null) {
                    db.addCustomer(name, phone);
                } else {
                    db.updateCustomer(existing.id, name, phone);
                }
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, existing == null ? "مشتری اضافه شد" : "مشتری ویرایش شد");
            }
        });
    }
}
