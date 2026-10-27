package com.barber.app;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** افزودن مشتری از مخاطبین گوشی: لیست تیک‌دار با جستجو؛ شماره‌های تکراری یا موجود نمایش داده نمی‌شوند. */
public final class ContactsImport {

    private ContactsImport() {
    }

    private static final class Item {
        String name;
        String phone;
        boolean checked;
    }

    /** شماره را به شکل ۰۹۱۲… یکدست می‌کند (۹۸+ و ۰۰۹۸ به ۰ تبدیل می‌شود) */
    static String normalize(String raw) {
        String d = Logic.digitsOnly(raw);
        if (d.startsWith("0098")) d = "0" + d.substring(4);
        else if (d.startsWith("98") && d.length() >= 12) d = "0" + d.substring(2);
        else if (d.length() == 10 && d.startsWith("9")) d = "0" + d;
        return d;
    }

    public static void start(MainActivity act) {
        if (!act.canWrite()) return;
        if (act.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            showPicker(act);
        } else {
            act.requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, MainActivity.REQ_CONTACTS);
        }
    }

    private static List<Item> load(MainActivity act) {
        Set<String> have = new HashSet<>();
        for (Db.Customer c : Db.get(act).getCustomers()) {
            have.add(normalize(c.phone));
        }
        List<Item> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = act.getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " COLLATE LOCALIZED ASC");
            if (c != null) {
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    String num = normalize(c.getString(1));
                    if (num.length() < 10 || have.contains(num)) continue;
                    have.add(num); // تکراری‌های داخل خود مخاطبین هم یک بار می‌آید
                    Item it = new Item();
                    it.name = (name == null || name.trim().isEmpty()) ? num : name.trim();
                    it.phone = num;
                    out.add(it);
                }
            }
        } catch (Exception ignored) {
            // دسترسی یا خواندن مخاطبین شکست خورد؛ لیست خالی نمایش داده می‌شود
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    public static void showPicker(final MainActivity act) {
        final List<Item> all = load(act);
        if (all.isEmpty()) {
            Ui.toastLong(act, "مخاطب تازه‌ای برای افزودن پیدا نشد");
            return;
        }
        final List<Item> shown = new ArrayList<>(all);

        int pad = Ui.dp(act, 16);
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);
        Ui.rtl(box);

        final EditText search = new EditText(act);
        search.setHint("جستجوی نام یا شماره");
        search.setSingleLine(true);
        box.addView(search);

        final CheckBox cbAll = new CheckBox(act);
        cbAll.setText("انتخاب همه‌ی نمایش‌داده‌شده‌ها");
        box.addView(cbAll);

        final TextView count = new TextView(act);
        box.addView(count);

        final ListView lv = new ListView(act);
        lv.setDivider(null);
        box.addView(lv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(act, 320)));

        final BaseAdapter ad = new BaseAdapter() {
            @Override
            public int getCount() {
                return shown.size();
            }

            @Override
            public Object getItem(int i) {
                return shown.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View convert, ViewGroup parent) {
                CheckBox cb = convert instanceof CheckBox ? (CheckBox) convert : new CheckBox(act);
                Item it = shown.get(i);
                cb.setFocusable(false);
                cb.setClickable(false);
                cb.setText(it.name + "\n" + it.phone);
                cb.setChecked(it.checked);
                return cb;
            }
        };
        lv.setAdapter(ad);

        final Runnable updateCount = new Runnable() {
            @Override
            public void run() {
                int n = 0;
                for (Item it : all) if (it.checked) n++;
                count.setText("انتخاب‌شده: " + n + " از " + all.size());
            }
        };
        updateCount.run();

        lv.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> p, View v, int pos, long id) {
                Item it = shown.get(pos);
                it.checked = !it.checked;
                ad.notifyDataSetChanged();
                updateCount.run();
            }
        });
        cbAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = cbAll.isChecked();
                for (Item it : shown) it.checked = on;
                ad.notifyDataSetChanged();
                updateCount.run();
            }
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase();
                String qd = Logic.digitsOnly(q);
                shown.clear();
                for (Item it : all) {
                    boolean ok = q.isEmpty() || it.name.toLowerCase().contains(q)
                            || (!qd.isEmpty() && it.phone.contains(qd));
                    if (ok) shown.add(it);
                }
                cbAll.setChecked(false);
                ad.notifyDataSetChanged();
            }
        });

        new MaterialAlertDialogBuilder(act)
                .setTitle("📇 انتخاب مشتری از مخاطبین")
                .setView(box)
                .setPositiveButton("افزودن انتخاب‌شده‌ها", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Db db = Db.get(act);
                        int added = 0;
                        for (Item it : all) {
                            if (!it.checked) continue;
                            if (db.addCustomer(it.name, it.phone) >= 0) added++;
                        }
                        act.refreshAll();
                        Ui.toast(act, added == 0 ? "مشتری‌ای انتخاب نشد" : added + " مشتری اضافه شد");
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
