package com.barber.admin;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** تب مشتریان: لیست، جستجو، افزودن، پروفایل. */
public class CustomersTab implements Tab {

    private final MainActivity act;
    private final View root;
    private final RowAdapter adapter;
    private final EditText etSearch;
    private List<Models.Customer> data = new ArrayList<Models.Customer>();

    public CustomersTab(MainActivity activity) {
        this.act = activity;
        root = LayoutInflater.from(act).inflate(R.layout.tab_customers, null, false);
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
                CustomerForm.show(act, null);
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    CustomerProfile.show(act, data.get(position).id);
                }
            }
        });
        list.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    showActions(data.get(position));
                    return true;
                }
                return false;
            }
        });
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void refresh() {
        Db db = Db.get(act);
        data = db.listCustomers(etSearch.getText().toString());
        List<RowAdapter.Row> rows = new ArrayList<RowAdapter.Row>();
        for (Models.Customer c : data) {
            StringBuilder sub = new StringBuilder();
            if (!c.ownerName.isEmpty()) sub.append(c.ownerName);
            if (!c.mobile.isEmpty()) sub.append(sub.length() > 0 ? " • " : "").append(c.mobile);
            if (!c.city.isEmpty()) sub.append(sub.length() > 0 ? " • " : "").append(c.city);
            long due = db.customerBalance(c.id).due();
            String trail = due > 0 ? "بدهی " + Fmt.price(due) : "";
            rows.add(new RowAdapter.Row(c.id, "", c.shopName, sub.toString(), trail, true));
        }
        adapter.setRows(rows);
    }

    /** با لمس طولانی: ویرایش و حذف */
    private void showActions(final Models.Customer c) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(c.shopName)
                .setItems(new CharSequence[]{"ویرایش", "حذف"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) CustomerForm.show(act, c);
                        else confirmDelete(c);
                    }
                })
                .show();
    }

    static void confirmDelete(final MainActivity act, final Models.Customer c) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف مشتری")
                .setMessage("مشتری «" + c.shopName + "» حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (Db.get(act).deleteCustomer(c.id)) {
                            act.refreshAll();
                            Ui.toast(act, "مشتری حذف شد");
                        } else {
                            Ui.toastLong(act, "این مشتری لایسنس یا پرداخت ثبت‌شده دارد و حذف نمی‌شود");
                        }
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void confirmDelete(Models.Customer c) {
        confirmDelete(act, c);
    }
}
