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
    private final Button btnWeek;
    private boolean weekMode = false;
    /** آیتم‌های لیست فعلی: Db.Appointment یا int[] (تاریخ شمسیِ سرتیتر روز در نمای هفتگی) */
    private List<Object> items = new ArrayList<>();

    public AppointmentsTab(MainActivity activity, View root) {
        this.act = activity;
        tvDate = root.findViewById(R.id.tv_date);
        tvIncome = root.findViewById(R.id.tv_income);
        tvIncome.setBackground(Ui.tinted(act, Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6), 0x1F, 10));
        tvIncome.setPadding(Ui.dp(act, 10), Ui.dp(act, 6), Ui.dp(act, 10), Ui.dp(act, 6));
        etSearch = root.findViewById(R.id.et_search);
        btnWeek = root.findViewById(R.id.btn_week);
        btnWeek.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                weekMode = !weekMode;
                refresh();
            }
        });
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
                if (position < 0 || position >= items.size()) return;
                Object o = items.get(position);
                if (o instanceof Db.Appointment) {
                    showActions((Db.Appointment) o);
                } else if (o instanceof int[]) {
                    act.selectedDate = (int[]) o; // لمس سرتیتر روز → نمای همان روز
                    weekMode = false;
                    refresh();
                }
            }
        });
    }

    private void shiftDay(int delta) {
        int[] d = act.selectedDate;
        act.selectedDate = JalaliCalendar.addDays(d[0], d[1], d[2], weekMode ? delta * 7 : delta);
        refresh();
    }

    private String isoSelected() {
        int[] d = act.selectedDate;
        return JalaliCalendar.jalaliToGregorianString(d[0], d[1], d[2]);
    }

    private static String describe(Db.Appointment a) {
        return a.service + "  •  تا " + a.endTime()
                + (a.smsReminder ? (a.smsSent ? "   ✅📩" : "   📩") : "");
    }

    @Override
    public void refresh() {
        btnWeek.setText(weekMode ? "روز" : "هفته");
        if (weekMode) {
            refreshWeek();
        } else {
            refreshDay();
        }
    }

    private void refreshDay() {
        int[] d = act.selectedDate;
        String dayName = JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(d[0], d[1], d[2])];
        tvDate.setText(dayName + "  " + JalaliCalendar.format(d[0], d[1], d[2]));

        String iso = isoSelected();
        Db db = Db.get(act);
        List<Db.Appointment> data = db.getAppointments(iso, etSearch.getText().toString());
        items = new ArrayList<>();
        List<RowAdapter.Row> rows = new ArrayList<>();
        for (Db.Appointment a : data) {
            items.add(a);
            rows.add(new RowAdapter.Row(a.id, a.time, a.customerName, describe(a), Fmt.price(a.price)));
        }
        adapter.setRows(rows);
        tvIncome.setText("درآمد این روز: " + Fmt.money(db.incomeForDate(iso)));
    }

    /** عنوان کوتاه هفته، مثلاً «10 مهر – 16 مهر 1405» */
    static String weekTitle(int[] s, int[] e) {
        String a = s[2] + " " + JalaliCalendar.MONTH_NAMES[s[1] - 1];
        String b = e[2] + " " + JalaliCalendar.MONTH_NAMES[e[1] - 1];
        if (s[0] == e[0]) return a + " – " + b + " " + e[0];
        return a + " " + s[0] + " – " + b + " " + e[0];
    }

    /** نمای هفتگی: هفته از شنبه تا جمعه؛ برای هر روز یک سرتیتر و زیرش نوبت‌های همان روز */
    private void refreshWeek() {
        int[] d = act.selectedDate;
        int dow = JalaliCalendar.dayOfWeek(d[0], d[1], d[2]);
        int[] start = JalaliCalendar.addDays(d[0], d[1], d[2], -dow);
        int[] end = JalaliCalendar.addDays(start[0], start[1], start[2], 6);
        tvDate.setText(weekTitle(start, end));

        Db db = Db.get(act);
        String q = etSearch.getText().toString().trim().toLowerCase();
        List<Db.Appointment> all = db.getAppointmentsBetween(
                JalaliCalendar.jalaliToGregorianString(start[0], start[1], start[2]),
                JalaliCalendar.jalaliToGregorianString(end[0], end[1], end[2]));

        items = new ArrayList<>();
        List<RowAdapter.Row> rows = new ArrayList<>();
        double weekTotal = 0;
        for (int i = 0; i < 7; i++) {
            int[] day = JalaliCalendar.addDays(start[0], start[1], start[2], i);
            String iso = JalaliCalendar.jalaliToGregorianString(day[0], day[1], day[2]);
            List<Db.Appointment> dayList = new ArrayList<>();
            double income = 0;
            int count = 0;
            for (Db.Appointment a : all) {
                if (!iso.equals(a.date)) continue;
                income += a.price;
                count++;
                if (q.isEmpty() || (a.customerName != null && a.customerName.toLowerCase().contains(q))) {
                    dayList.add(a);
                }
            }
            weekTotal += income;
            items.add(day);
            rows.add(new RowAdapter.Row(-1, "", JalaliCalendar.DAY_NAMES[i] + "  " + day[2] + " "
                    + JalaliCalendar.MONTH_NAMES[day[1] - 1],
                    count == 0 ? "بدون نوبت" : count + " نوبت",
                    income > 0 ? Fmt.price(income) : "", false, true));
            for (Db.Appointment a : dayList) {
                items.add(a);
                rows.add(new RowAdapter.Row(a.id, a.time, a.customerName, describe(a), Fmt.price(a.price)));
            }
        }
        adapter.setRows(rows);
        tvIncome.setText("درآمد این هفته: " + Fmt.money(weekTotal));
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

    private static List<String> pickedNames(List<String> all, boolean[] sel) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < all.size() && i < sel.length; i++) {
            if (sel[i]) out.add(all.get(i));
        }
        return out;
    }

    private int sumDurations(List<String> names) {
        int sum = 0;
        for (String n : names) sum += AppConfig.getDuration(act, n);
        return sum <= 0 ? 30 : sum;
    }

    private static int intOf(EditText et, int def) {
        Double v = Fmt.parsePrice(et.getText().toString());
        return v == null ? def : (int) Math.round(v);
    }

    private double sumDefaults(List<String> names) {
        double sum = 0;
        for (String n : names) sum += AppConfig.getPrice(act, n);
        return sum;
    }

    /** پنجره افزودن (existing == null) یا ویرایش نوبت */
    private void showDialog(final Db.Appointment existing) {
        // روزی که پنجره روی آن کار می‌کند: برای ویرایش، روز خودِ نوبت (در نمای هفتگی ممکن است با روز انتخاب‌شده فرق کند)
        final String dialogIso = existing != null ? existing.date : isoSelected();
        final int[] sd = existing != null ? JalaliCalendar.jalaliOfIso(existing.date) : act.selectedDate;
        final Db db = Db.get(act);
        final List<Db.Customer> customers = db.getCustomers();
        if (customers.isEmpty()) {
            Ui.toastLong(act, "ابتدا از تب «مشتریان» یک مشتری اضافه کنید");
            return;
        }

        View v = LayoutInflater.from(act).inflate(R.layout.dialog_appointment, null);
        final Spinner spCustomer = v.findViewById(R.id.sp_customer);
        final Button btnTime = v.findViewById(R.id.btn_time);
        final EditText etDuration = v.findViewById(R.id.et_duration);
        final TextView tvTimeHint = v.findViewById(R.id.tv_time_hint);
        final Button btnServices = v.findViewById(R.id.btn_services);
        final TextView tvServicesSum = v.findViewById(R.id.tv_services_sum);
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

        final Runnable[] onDurationChanged = {null};

        // ---- خدمت‌ها: یک یا چند مورد؛ مبلغ کل = جمع قیمت خدمت‌ها (قابل ویرایش) ----
        final List<String> serviceList = new ArrayList<>();
        for (String sName : AppConfig.SERVICES) serviceList.add(sName);
        List<String> initial = new ArrayList<>();
        if (existing != null) initial.addAll(Logic.splitServices(existing.service));
        if (initial.isEmpty()) initial.add(AppConfig.SERVICES[0]);
        for (String n : initial) {
            if (!serviceList.contains(n)) serviceList.add(n); // خدمت قدیمی که در لیست نیست
        }
        final boolean[] selected = new boolean[serviceList.size()];
        for (String n : initial) selected[serviceList.indexOf(n)] = true;

        final Runnable refreshServices = new Runnable() {
            @Override
            public void run() {
                List<String> names = pickedNames(serviceList, selected);
                btnServices.setText(names.isEmpty() ? "انتخاب خدمت‌ها" : Logic.joinServices(names));
                StringBuilder sb = new StringBuilder();
                for (String n : names) {
                    if (sb.length() > 0) sb.append("  +  ");
                    sb.append(n).append(" ").append(Fmt.price(AppConfig.getPrice(act, n)));
                }
                if (names.size() > 1) sb.append("  =  ").append(Fmt.price(sumDefaults(names)));
                tvServicesSum.setText(sb.toString());
            }
        };
        refreshServices.run();

        if (existing != null) {
            etPrice.setText(Fmt.plain(existing.price));
            etDuration.setText(String.valueOf(existing.duration));
        } else {
            List<String> firstPick = pickedNames(serviceList, selected);
            etPrice.setText(Fmt.plain(sumDefaults(firstPick)));
            etDuration.setText(String.valueOf(sumDurations(firstPick)));
        }

        btnServices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CharSequence[] labels = new CharSequence[serviceList.size()];
                for (int i = 0; i < labels.length; i++) {
                    labels[i] = serviceList.get(i) + "   —   " + Fmt.price(AppConfig.getPrice(act, serviceList.get(i)));
                }
                final boolean[] working = selected.clone();
                new MaterialAlertDialogBuilder(act)
                        .setTitle("انتخاب خدمت‌ها")
                        .setMultiChoiceItems(labels, working, new android.content.DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which, boolean checked) {
                                working[which] = checked;
                            }
                        })
                        .setPositiveButton("تأیید", new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int w) {
                                List<String> names = pickedNames(serviceList, working);
                                if (names.isEmpty()) {
                                    Ui.toastLong(act, "حداقل یک خدمت باید انتخاب شود؛ تغییری اعمال نشد");
                                    return;
                                }
                                System.arraycopy(working, 0, selected, 0, selected.length);
                                etPrice.setText(Fmt.plain(sumDefaults(names)));
                                etDuration.setText(String.valueOf(sumDurations(names)));
                                refreshServices.run();
                                if (onDurationChanged[0] != null) onDurationChanged[0].run();
                            }
                        })
                        .setNegativeButton("انصراف", null)
                        .show();
            }
        });

        // ---- ساعت شروع: ساعتی / نیم‌ساعتی / آزاد (هر دقیقه) + تشخیص تداخل با نوبت‌های دیگر ----
        final int mode = AppConfig.getTimeMode(act);
        final int workStart = AppConfig.getWorkStartHour(act) * 60;
        final int workEnd = AppConfig.getWorkEndHour(act) * 60;
        final long exceptId = existing == null ? -1 : existing.id;
        final List<Db.Appointment> others = new ArrayList<>();
        for (Db.Appointment x : db.getAppointments(dialogIso, null)) {
            if (x.id != exceptId) others.add(x);
        }
        final int[] busyStart = new int[others.size()];
        final int[] busyDur = new int[others.size()];
        for (int i = 0; i < others.size(); i++) {
            busyStart[i] = Logic.toMinutes(others.get(i).time);
            busyDur[i] = others.get(i).duration;
        }

        final int[] startMin = {-1};
        if (existing != null) startMin[0] = Logic.toMinutes(existing.time);
        if (startMin[0] < 0) {
            int free = Logic.firstFreeStart(workStart, workEnd, Math.max(5, intOf(etDuration, 30)),
                    mode, busyStart, busyDur);
            startMin[0] = free >= 0 ? free : workStart;
        }
        final int[] hintTarget = {-1};

        final Runnable updateTimeHint = new Runnable() {
            @Override
            public void run() {
                int dur = Math.max(5, intOf(etDuration, 30));
                int s0 = startMin[0];
                btnTime.setText(Logic.fromMinutes(s0) + "  (تا " + Logic.fromMinutes(s0 + dur) + ")");
                int ci = Logic.conflictIndex(s0, dur, busyStart, busyDur);
                int free = Logic.firstFreeStart(workStart, workEnd, dur, mode, busyStart, busyDur);
                if (ci >= 0) {
                    Db.Appointment c = others.get(ci);
                    String base = "⚠️ با نوبت «" + c.customerName + "» (" + c.time + " تا " + c.endTime() + ") تداخل دارد";
                    if (free >= 0) {
                        tvTimeHint.setText(base + " — پیشنهاد: " + Logic.fromMinutes(free) + " (لمس کنید)");
                        hintTarget[0] = free;
                    } else {
                        tvTimeHint.setText(base + " — در ساعت کاری جای خالی نمانده");
                        hintTarget[0] = -1;
                    }
                } else {
                    tvTimeHint.setText("✅ این بازه آزاد است");
                    hintTarget[0] = -1;
                }
            }
        };
        onDurationChanged[0] = updateTimeHint;
        etDuration.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s1, int st, int c, int a1) {
            }

            @Override
            public void onTextChanged(CharSequence s1, int st, int b1, int c) {
            }

            @Override
            public void afterTextChanged(Editable s1) {
                updateTimeHint.run();
            }
        });
        tvTimeHint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (hintTarget[0] >= 0) {
                    startMin[0] = hintTarget[0];
                    updateTimeHint.run();
                }
            }
        });
        btnTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TimeChooser.show(act, startMin[0], mode, new TimeChooser.OnPicked() {
                    @Override
                    public void onPicked(int minutes) {
                        startMin[0] = minutes;
                        updateTimeHint.run();
                    }
                });
            }
        });
        updateTimeHint.run();

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle((existing == null ? "نوبت جدید" : "ویرایش نوبت") + " – "
                        + JalaliCalendar.DAY_NAMES[JalaliCalendar.dayOfWeek(sd[0], sd[1], sd[2])] + " "
                        + JalaliCalendar.format(sd[0], sd[1], sd[2]))
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
                String time = Logic.fromMinutes(startMin[0]);
                int duration = intOf(etDuration, 30);
                if (duration < 5) {
                    etDuration.setError("مدت باید حداقل ۵ دقیقه باشد");
                    return;
                }
                List<String> pickedList = pickedNames(serviceList, selected);
                if (pickedList.isEmpty()) {
                    Ui.toastLong(act, "حداقل یک خدمت انتخاب کنید");
                    return;
                }
                String service = Logic.joinServices(pickedList);
                String iso = dialogIso;

                final boolean sms = cbSms.isChecked();
                if (sms && !Logic.isUsablePhone(customer.phone)) {
                    Ui.toastLong(act, "برای این مشتری شماره‌ی معتبری ثبت نشده؛ شماره را وارد کنید یا تیک پیامک را بردارید");
                    return;
                }
                if (sms && !act.hasSmsPermission()) {
                    Ui.toastLong(act, "مجوز ارسال پیامک داده نشده است؛ تیک پیامک را بردارید یا مجوز را بدهید");
                    return;
                }

                Db.Appointment cf = db.findConflict(iso, time, duration, existing == null ? -1 : existing.id);
                if (cf != null) {
                    Ui.toastLong(act, "این زمان با نوبت «" + cf.customerName + "» (" + cf.time + " تا "
                            + cf.endTime() + ") تداخل دارد");
                    return;
                }

                long apptId;
                if (existing == null) {
                    apptId = db.addAppointment(customer.id, iso, time, service, price, sms, duration);
                    if (apptId < 0) {
                        Ui.toastLong(act, "این زمان (" + time + ") قبلاً رزرو شده است!");
                        return;
                    }
                } else {
                    if (!db.updateAppointment(existing.id, customer.id, iso, time, service, price, sms, duration)) {
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
