package com.barber.app;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

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
        tvIncome.setBackground(Ui.tinted(act, Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6), 0x1F, 10));
        tvIncome.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 10), Ui.dp(act, 6));
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
            String sub = a.service + (a.smsReminder ? (a.smsSent ? "   ✅📩" : "   📩") : "");
            rows.add(new RowAdapter.Row(a.id, a.time, a.customerName, sub, Fmt.price(a.price)));
        }
        adapter.setRows(rows);
        tvIncome.setText("درآمد این روز: " + Fmt.money(db.incomeForDate(iso)));
    }

    private void showActions(final Db.Appointment a) {
        new MaterialAlertDialogBuilder(act)
                .setTitle(a.customerName + " - " + a.time)
                .setItems(new CharSequence[]{"ویرایش", "📩 ارسال پیامک یادآوری همین حالا",
                                "👤 پروفایل مشتری", "حذف"},
                        new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                if (which == 0) {
                                    showDialog(a);
                                } else if (which == 1) {
                                    sendNow(a);
                                } else if (which == 2) {
                                    CustomerProfile.show(act, a.customerId);
                                } else {
                                    confirmDelete(a);
                                }
                            }
                        })
                .show();
    }

    /** ارسال دستی: متن آماده در برنامه‌ی پیامک باز می‌شود و خود کاربر ارسال را تأیید می‌کند. */
    private void sendNow(Db.Appointment a) {
        int[] j = JalaliCalendar.jalaliOfIso(a.date);
        String date = JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(j[0], j[1], j[2])] + " "
                + JalaliCalendar.format(j[0], j[1], j[2]);
        String text = Logic.fillTemplate(AppConfig.getSmsTemplate(act), a.customerName, date, a.time,
                a.service, AppConfig.getSalonName(act));
        Messenger.openSms(act, a.phone, text);
    }

    private void confirmDelete(final Db.Appointment a) {
        new MaterialAlertDialogBuilder(act)
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
        final CheckBox cbSms = v.findViewById(R.id.cb_sms);
        final TextView tvSmsHint = v.findViewById(R.id.tv_sms_hint);
        final TextView tvLoyalty = v.findViewById(R.id.tv_loyalty);

        List<String> names = new ArrayList<>();
        int customerPos = 0;
        for (int i = 0; i < customers.size(); i++) {
            names.add(customers.get(i).name);
            if (existing != null && customers.get(i).id == existing.customerId) customerPos = i;
        }
        spCustomer.setAdapter(Ui.spinnerAdapter(act, names));
        spCustomer.setSelection(customerPos);

        // ---- پیامک یادآوری به مشتری (انتخابی؛ اگر تیک نخورد هیچ پیامکی ارسال نمی‌شود) ----
        cbSms.setText("📩 ارسال پیامک یادآوری به مشتری (" + AppConfig.getSmsLeadHours(act)
                + " ساعت قبل از نوبت)");
        cbSms.setChecked(existing != null ? existing.smsReminder : AppConfig.getSmsDefault(act));
        final Runnable updateSmsHint = new Runnable() {
            @Override
            public void run() {
                if (!cbSms.isChecked()) {
                    tvSmsHint.setText("پیامکی برای این نوبت ارسال نمی‌شود.");
                    return;
                }
                Db.Customer c = customers.get(spCustomer.getSelectedItemPosition());
                if (Logic.isUsablePhone(c.phone)) {
                    tvSmsHint.setText("پیامک به شماره‌ی " + Logic.digitsOnly(c.phone) + " ارسال می‌شود.");
                } else {
                    tvSmsHint.setText("⚠️ برای این مشتری شماره‌ی معتبری ثبت نشده است؛ ابتدا شماره را در تب مشتریان وارد کنید.");
                }
            }
        };
        cbSms.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(final android.widget.CompoundButton b, boolean checked) {
                if (checked && !act.hasSmsPermission()) {
                    act.requestSmsPermission(new MainActivity.PermCallback() {
                        @Override
                        public void onResult(boolean granted) {
                            if (!granted) {
                                b.setChecked(false);
                                Ui.toastLong(act, "بدون مجوز پیامک، ارسال خودکار ممکن نیست");
                            }
                        }
                    });
                }
                updateSmsHint.run();
            }
        });
        // یادآوری باشگاه مشتریان: فقط برای نوبت جدید، وقتی مراجعه‌ی بعدی مشتری جایزه دارد
        final Runnable updateLoyalty = new Runnable() {
            @Override
            public void run() {
                int threshold = AppConfig.getLoyaltyThreshold(act);
                if (existing != null || threshold <= 0) {
                    tvLoyalty.setVisibility(View.GONE);
                    return;
                }
                Db.Customer c = customers.get(spCustomer.getSelectedItemPosition());
                int visits = db.visitCount(c.id, JalaliCalendar.todayGregorianString());
                if (Logic.isRewardVisit(visits, threshold)) {
                    tvLoyalty.setText("🎁 این مراجعه‌ی " + (visits + 1) + "ام مشتری است و جایزه‌دار است");
                    tvLoyalty.setVisibility(View.VISIBLE);
                } else {
                    tvLoyalty.setVisibility(View.GONE);
                }
            }
        };
        spCustomer.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSmsHint.run();
                updateLoyalty.run();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        updateSmsHint.run();

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

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
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

                final boolean sms = cbSms.isChecked();
                if (sms && !Logic.isUsablePhone(customer.phone)) {
                    Ui.toastLong(act, "برای این مشتری شماره‌ی معتبری ثبت نشده؛ شماره را وارد کنید یا تیک پیامک را بردارید");
                    return;
                }
                if (sms && !act.hasSmsPermission()) {
                    Ui.toastLong(act, "مجوز ارسال پیامک داده نشده است؛ تیک پیامک را بردارید یا مجوز را بدهید");
                    return;
                }

                long apptId;
                if (existing == null) {
                    apptId = db.addAppointment(customer.id, iso, time, service, price, sms);
                    if (apptId < 0) {
                        Ui.toastLong(act, "این زمان (" + time + ") قبلاً رزرو شده است!");
                        return;
                    }
                } else {
                    if (!db.updateAppointment(existing.id, customer.id, iso, time, service, price, sms)) {
                        Ui.toastLong(act, "این زمان (" + time + ") قبلاً رزرو شده است!");
                        return;
                    }
                    apptId = existing.id;
                }
                ReminderScheduler.schedule(act, apptId, customer.name, iso, time);
                ReminderScheduler.scheduleSms(act, db.getAppointmentById(apptId));
                AppConfig.setSmsDefault(act, sms);
                dialog.dismiss();
                refresh();
                Ui.toast(act, existing == null
                        ? "نوبت برای " + customer.name + " ساعت " + time + " ثبت شد"
                        : "نوبت ویرایش شد");
            }
        });
    }
}
