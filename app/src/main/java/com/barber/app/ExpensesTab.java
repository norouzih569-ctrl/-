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

    /** ماه نمایش‌داده‌شده {سال، ماه} (شمسی) */
    private final int[] month;
    private final TextView tvMonth;

    private final MainActivity act;
    private final RowAdapter adapter;
    private List<Db.Expense> data = new ArrayList<>();
    private final GridMode.Host grid;

    public ExpensesTab(MainActivity activity, View root) {
        this.act = activity;
        ListView list = root.findViewById(R.id.list_expenses);
        TextView empty = root.findViewById(R.id.empty_expenses);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);
        grid = new GridMode.Host(act, list);
        int[] today = JalaliCalendar.today();
        month = new int[]{today[0], today[1]};
        tvMonth = root.findViewById(R.id.tv_exp_month);
        root.findViewById(R.id.btn_exp_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(-1);
            }
        });
        root.findViewById(R.id.btn_exp_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftMonth(1);
            }
        });
        tvMonth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final int[] d = {month[0], month[1], 1};
                JalaliPicker.show(act, d, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int year, int m, int day) {
                        month[0] = year;
                        month[1] = m;
                        refresh();
                    }
                });
            }
        });

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

    private void shiftMonth(int delta) {
        int m = month[1] + delta;
        int y = month[0];
        if (m > 12) {
            m = 1;
            y++;
        } else if (m < 1) {
            m = 12;
            y--;
        }
        month[0] = y;
        month[1] = m;
        refresh();
    }

    @Override
    public void refresh() {
        String[] range = JalaliCalendar.monthRangeIso(month[0], month[1]);
        data = new ArrayList<>(Db.get(act).getExpensesBetween(range[0], range[1]));
        java.util.Collections.reverse(data); // تازه‌ترین بالا
        double monthSum = 0;
        for (Db.Expense e : data) monthSum += e.amount;
        tvMonth.setText(JalaliCalendar.MONTH_NAMES[month[1] - 1] + " " + month[0]
                + "   •   جمع: " + Fmt.money(monthSum));
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Expense e : data) {
            rows.add(new RowAdapter.Row(e.id, JalaliCalendar.gregorianStringToJalali(e.date),
                    e.description, "", Fmt.price(e.amount)));
        }
        adapter.setRows(rows);
        List<Object[]> trows = new ArrayList<>();
        double sum = 0;
        for (Db.Expense e : data) {
            trows.add(new Object[]{JalaliCalendar.gregorianStringToJalali(e.date), e.description, e.amount});
            sum += e.amount;
        }
        grid.render(new String[]{"تاریخ", "شرح", "مبلغ"}, trows, new Object[]{"جمع", "", sum},
                new TableUi.OnRow() {
                    @Override
                    public void onRow(int i) {
                        if (i >= 0 && i < data.size()) confirmDelete(data.get(i));
                    }
                }, true);
    }

    private void confirmDelete(final Db.Expense e) {
        if (!act.canWrite()) return;
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
        if (!act.canWrite()) return;
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
                .setView(Ui.scroll(v))
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
