package com.barber.app;

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

/** تب هزینه‌ها. */
public class ExpensesTab implements Refreshable {

    private static final int LIMIT = 100;

    private final MainActivity act;
    private final RowAdapter adapter;
    private List<Db.Expense> data = new ArrayList<>();

    public ExpensesTab(MainActivity activity, View root) {
        this.act = activity;
        ListView list = root.findViewById(R.id.list_expenses);
        TextView empty = root.findViewById(R.id.empty_expenses);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        root.findViewById(R.id.fab_add_expense).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog();
            }
        });
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    confirmDelete(data.get(position));
                }
            }
        });
    }

    @Override
    public void refresh() {
        data = Db.get(act).getRecentExpenses(LIMIT);
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Expense e : data) {
            rows.add(new RowAdapter.Row(e.id, JalaliCalendar.gregorianStringToJalali(e.date),
                    e.description, "", Fmt.price(e.amount)));
        }
        adapter.setRows(rows);
    }

    private void confirmDelete(final Db.Expense e) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("حذف هزینه")
                .setMessage("هزینه «" + e.description + "» (" + Fmt.money(e.amount) + ") حذف شود؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deleteExpense(e.id);
                        act.refreshAll();
                        Ui.toast(act, "هزینه حذف شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    private void showDialog() {
        View v = LayoutInflater.from(act).inflate(R.layout.dialog_expense, null);
        final Button btnDate = v.findViewById(R.id.btn_expense_date);
        final EditText etDesc = v.findViewById(R.id.et_expense_desc);
        final EditText etAmount = v.findViewById(R.id.et_expense_amount);

        final int[] date = act.selectedDate.clone();
        btnDate.setText("تاریخ: " + JalaliCalendar.format(date[0], date[1], date[2]));
        btnDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                JalaliPicker.show(act, date, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int year, int month, int day) {
                        date[0] = year;
                        date[1] = month;
                        date[2] = day;
                        btnDate.setText("تاریخ: " + JalaliCalendar.format(year, month, day));
                    }
                });
            }
        });

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("ثبت هزینه")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String desc = etDesc.getText().toString().trim();
                if (desc.isEmpty()) {
                    etDesc.setError("توضیحات هزینه را وارد کنید");
                    return;
                }
                Double amount = Fmt.parsePrice(etAmount.getText().toString());
                if (amount == null) {
                    etAmount.setError("مبلغ صحیح نیست");
                    return;
                }
                String iso = JalaliCalendar.jalaliToGregorianString(date[0], date[1], date[2]);
                Db.get(act).addExpense(iso, desc, amount);
                dialog.dismiss();
                act.refreshAll();
                Ui.toast(act, "هزینه «" + desc + "» ثبت شد");
            }
        });
    }
}
