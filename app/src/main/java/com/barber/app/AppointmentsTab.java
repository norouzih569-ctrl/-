package com.barber.app;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import java.util.ArrayList;
import java.util.List;

/** تب نوبت‌ها: نمایش نوبت‌های یک روز، جستجو، افزودن، ویرایش و حذف. */
public class AppointmentsTab implements Refreshable {

    private final MainActivity act;
    private final TextView tvDate;
    private final TextView tvIncome;
    private final EditText etSearch;
    private final RowAdapter adapter;
    private List<Db.Appointment> data = new ArrayList<>();

    public AppointmentsTab(MainActivity activity, View root) {
        this.act = activity;
        tvDate = root.findViewById(R.id.tv_date);
        tvIncome = root.findViewById(R.id.tv_income);
        etSearch = root.findViewById(R.id.et_search);
        ListView list = root.findViewById(R.id.list_appointments);
        TextView empty = root.findViewById(R.id.empty_appointments);
        adapter = new RowAdapter(act);
        list.setAdapter(adapter);
        list.setEmptyView(empty);

        root.findViewById(R.id.btn_prev_day).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftDay(-1);
            }
        });
        root.findViewById(R.id.btn_next_day).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shiftDay(1);
            }
        });
        root.findViewById(R.id.btn_today).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                act.selectedDate = JalaliCalendar.today();
                refresh();
            }
        });
        tvDate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                JalaliPicker.show(act, act.selectedDate, new JalaliPicker.OnPicked() {
                    @Override
                    public void onPicked(int year, int month, int day) {
                        act.selectedDate = new int[]{year, month, day};
                        refresh();
                    }
                });
            }
        });
        root.findViewById(R.id.fab_add_appointment).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDialog(null);
            }
        });
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
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < data.size()) {
                    showActions(data.get(position));
                }
            }
        });
    }

    private void shiftDay(int delta) {
        int[] d = act.selectedDate;
        act.selectedDate = JalaliCalendar.addDays(d[0], d[1], d[2], delta);
        refresh();
    }

    private String isoSelected() {
        int[] d = act.selectedDate;
        return JalaliCalendar.jalaliToGregorianString(d[0], d[1], d[2]);
    }

    @Override
    public void refresh() {
        int[] d = act.selectedDate;
        String dayName = JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(d[0], d[1], d[2])];
        tvDate.setText(dayName + "  " + JalaliCalendar.format(d[0], d[1], d[2]));

        String iso = isoSelected();
        Db db = Db.get(act);
        data = db.getAppointments(iso, etSearch.getText().toString());
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Appointment a : data) {
            rows.add(new RowAdapter.Row(a.id, a.time, a.customerName, a.service, Fmt.price(a.price)));
        }
        adapter.setRows(rows);
        tvIncome.setText("درآمد این روز: " + Fmt.money(db.incomeForDate(iso)));
    }

    private void showActions(final Db.Appointment a) {
        new AlertDialog.Builder(act)
                .setTitle(a.customerName + " - " + a.time)
                .setItems(new CharSequence[]{"ویرایش", "حذف"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) {
                            showDialog(a);
                        } else {
                            confirmDelete(a);
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final Db.Appointment a) {
        new AlertDialog.Builder(act)
                .setTitle("حذف نوبت")
                .setMessage("آیا از حذف نوبت " + a.customerName + " ساعت " + a.time + " مطمئن هستید؟")
                .setPositiveButton("حذف", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Db.get(act).deleteAppointment(a.id);
                        ReminderScheduler.cancel(act, a.id);
                        refresh();
                        Ui.toast(act, "نوبت حذف شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** پنجره افزودن (existing == null) یا ویرایش نوبت */
    private void showDialog(final Db.Appointment existing) {
        final Db db = Db.get(act);
        final List<Db.Customer> customers = db.getCustomers();
        if (customers.isEmpty()) {
            Ui.toastLong(act, "ابتدا از تب «مشتریان» یک مشتری اضافه کنید");
            return;
        }

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_appointment, null);
        final Spinner spCustomer = v.findViewById(R.id.sp_customer);
        final Spinner spTime = v.findViewById(R.id.sp_time);
        final Spinner spService = v.findViewById(R.id.sp_service);
        final EditText etPrice = v.findViewById(R.id.et_price);

        List<String> names = new ArrayList<>();
        int customerPos = 0;
        for (int i = 0; i < customers.size(); i++) {
            names.add(customers.get(i).name);
            if (existing != null && customers.get(i).id == existing.customerId) customerPos = i;
        }
        spCustomer.setAdapter(Ui.spinnerAdapter(act, names));
        spCustomer.setSelection(customerPos);

        final List<String> times = new ArrayList<>();
        for (String t : AppConfig.TIME_SLOTS) times.add(t);
        int timePos = 0;
        if (existing != null) {
            int idx = times.indexOf(existing.time);
            if (idx < 0) {
                times.add(existing.time);
                idx = times.size() - 1;
            }
            timePos = idx;
        }
        spTime.setAdapter(Ui.spinnerAdapter(act, times));
        spTime.setSelection(timePos);

        List<String> services = new ArrayList<>();
        for (String s : AppConfig.SERVICES) services.add(s);
        int servicePos = 0;
        if (existing != null) {
            int idx = services.indexOf(existing.service);
            if (idx < 0) {
                services.add(existing.service);
                idx = services.size() - 1;
            }
            servicePos = idx;
        }
        final List<String> serviceList = services;
        spService.setAdapter(Ui.spinnerAdapter(act, serviceList));
        spService.setSelection(servicePos);

        if (existing != null) {
            etPrice.setText(Fmt.plain(existing.price));
        } else {
            etPrice.setText(Fmt.plain(AppConfig.getPrice(act, serviceList.get(0))));
        }

        final int[] lastServicePos = {servicePos};
        spService.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == lastServicePos[0]) return;
                lastServicePos[0] = position;
                etPrice.setText(Fmt.plain(AppConfig.getPrice(act, serviceList.get(position))));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        final AlertDialog dialog = new AlertDialog.Builder(act)
                .setTitle(existing == null ? "نوبت جدید" : "ویرایش نوبت")
                .setView(v)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();

        Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Double price = Fmt.parsePrice(etPrice.getText().toString());
                if (price == null) {
                    etPrice.setError("قیمت صحیح نیست");
                    return;
                }
                Db.Customer customer = customers.get(spCustomer.getSelectedItemPosition());
                String time = times.get(spTime.getSelectedItemPosition());
                String service = serviceList.get(spService.getSelectedItemPosition());
                String iso = isoSelected();

                long apptId;
                if (existing == null) {
                    apptId = db.addAppointment(customer.id, iso, time, service, price);
                    if (apptId < 0) {
                        Ui.toastLong(act, "این زمان (" + time + ") قبلاً رزرو شده است!");
                        return;
                    }
                } else {
                    if (!db.updateAppointment(existing.id, customer.id, iso, time, service, price)) {
                        Ui.toastLong(act, "این زمان (" + time + ") قبلاً رزرو شده است!");
                        return;
                    }
                    apptId = existing.id;
                }
                ReminderScheduler.schedule(act, apptId, customer.name, iso, time);
                dialog.dismiss();
                refresh();
                Ui.toast(act, existing == null
                        ? "نوبت برای " + customer.name + " ساعت " + time + " ثبت شد"
                        : "نوبت ویرایش شد");
            }
        });
    }
}
