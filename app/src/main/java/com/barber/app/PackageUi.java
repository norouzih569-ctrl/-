package com.barber.app;

import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** انتخاب محصولات انبار برای «پکیج» (مثلاً پکیج داماد) یا برای یک نوبت */
final class PackageUi {

    interface Result {
        void onPicked(List<long[]> items);
    }

    private PackageUi() {
    }

    /** جمع قیمت فروش محصولات {شناسه، تعداد} */
    static double total(Db db, List<long[]> items) {
        double sum = 0;
        for (long[] it : items) {
            Db.Product p = db.getProduct(it[0]);
            if (p != null) sum += p.sellPrice * it[1];
        }
        return sum;
    }

    /** ادغام محصولات پیش‌فرض پکیجِ چند خدمت (تعداد هر محصول جمع می‌شود) */
    static List<long[]> defaultsFor(Activity act, Db db, List<String> serviceNames) {
        List<long[]> out = new ArrayList<>();
        for (String s : serviceNames) {
            for (long[] it : AppConfig.getPackage(act, s)) {
                if (db.getProduct(it[0]) == null) continue; // محصول حذف شده
                boolean found = false;
                for (long[] o : out) {
                    if (o[0] == it[0]) {
                        o[1] += it[1];
                        found = true;
                        break;
                    }
                }
                if (!found) out.add(new long[]{it[0], it[1]});
            }
        }
        return out;
    }

    static String summary(Db db, List<long[]> items) {
        int n = 0;
        for (long[] it : items) if (db.getProduct(it[0]) != null) n++;
        return n == 0 ? "بدون محصول" : n + " محصول  =  " + Fmt.price(total(db, items));
    }

    static void pick(final Activity act, final Db db, String title, List<long[]> current, final Result cb) {
        final List<Db.Product> products = db.getProducts();
        if (products.isEmpty()) {
            Ui.toastLong(act, "هنوز محصولی در انبار ثبت نشده؛ اول از بخش محصولات اضافه کنید");
            return;
        }
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 4));

        final CheckBox[] checks = new CheckBox[products.size()];
        final EditText[] qtys = new EditText[products.size()];
        for (int i = 0; i < products.size(); i++) {
            Db.Product p = products.get(i);
            long q = 1;
            boolean on = false;
            for (long[] it : current) {
                if (it[0] == p.id) {
                    on = true;
                    q = it[1];
                }
            }
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

            CheckBox cb1 = new CheckBox(act);
            cb1.setText(p.name + "\n" + Fmt.price(p.sellPrice) + "  ·  موجودی " + p.stock);
            cb1.setChecked(on);
            checks[i] = cb1;
            row.addView(cb1, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            EditText et = new EditText(act);
            et.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            et.setText(String.valueOf(q));
            et.setGravity(Gravity.CENTER);
            et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
            qtys[i] = et;
            row.addView(et, new LinearLayout.LayoutParams(Ui.dp(act, 56), LinearLayout.LayoutParams.WRAP_CONTENT));
            box.addView(row);
        }

        new MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setMessage("محصولات را تیک بزنید؛ عدد کنار هر محصول «تعداد» است. قیمت = قیمت فروش محصول.")
                .setView(Ui.scroll(box))
                .setPositiveButton("تأیید", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        List<long[]> out = new ArrayList<>();
                        for (int i = 0; i < products.size(); i++) {
                            if (!checks[i].isChecked()) continue;
                            Double q = Fmt.parsePrice(qtys[i].getText().toString());
                            long qq = q == null ? 1 : Math.max(1, Math.round(q));
                            out.add(new long[]{products.get(i).id, qq});
                        }
                        cb.onPicked(out);
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }
}
