package com.barber.app;

import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

/** جداکننده‌ی سه‌رقمی هنگام تایپ مبلغ (مثلاً 1,250,000)؛ مقدار با Fmt.parsePrice خوانده می‌شود. */
final class MoneyWatcher implements TextWatcher {

    private final EditText et;
    private boolean busy;

    private MoneyWatcher(EditText et) {
        this.et = et;
    }

    /** روی یک فیلد */
    static void attach(EditText et) {
        if (et.getTag(R.id.money_watcher_tag) != null) return;
        et.setTag(R.id.money_watcher_tag, Boolean.TRUE);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        // فیلد عددی پیش‌فرض کاما را رد می‌کند؛ باید صریحاً مجاز باشد
        et.setKeyListener(android.text.method.DigitsKeyListener.getInstance(
                "0123456789,\u066C\u060C\u06F0\u06F1\u06F2\u06F3\u06F4\u06F5\u06F6\u06F7\u06F8\u06F9"
                        + "\u0660\u0661\u0662\u0663\u0664\u0665\u0666\u0667\u0668\u0669"));
        et.addTextChangedListener(new MoneyWatcher(et));
        // متنی که از قبل پر شده را هم قالب بده
        CharSequence cur = et.getText();
        if (cur != null && cur.length() > 0) et.setText(cur.toString());
    }

    /** همه‌ی فیلدهای مبلغ داخل یک صفحه/پنجره (بر اساس کلمات راهنمای فیلد) */
    static void attachTree(View root) {
        if (root instanceof EditText) {
            EditText e = (EditText) root;
            if (isMoneyField(e)) attach(e);
        } else if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++) attachTree(g.getChildAt(i));
        }
    }

    private static boolean isMoneyField(EditText e) {
        if ((e.getInputType() & InputType.TYPE_MASK_CLASS) != InputType.TYPE_CLASS_NUMBER) return false;
        if ((e.getInputType() & InputType.TYPE_MASK_VARIATION) == InputType.TYPE_NUMBER_VARIATION_PASSWORD) return false;
        CharSequence h = e.getHint();
        if (h == null) return false;
        String s = h.toString();
        if (s.contains("درصد") || s.contains("%")) return false;
        return s.contains("مبلغ") || s.contains("قیمت") || s.contains("تومان");
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable ed) {
        if (busy) return;
        String raw = ed.toString();
        int cursor = et.getSelectionStart();
        if (cursor < 0 || cursor > raw.length()) cursor = raw.length();
        // تعداد ارقام قبل از مکان‌نما
        int digitsBefore = 0;
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            int d = -1;
            if (c >= '0' && c <= '9') d = c - '0';
            else if (c >= '۰' && c <= '۹') d = c - '۰';
            else if (c >= '٠' && c <= '٩') d = c - '٠';
            if (d >= 0) {
                digits.append((char) ('0' + d));
                if (i < cursor) digitsBefore++;
            }
        }
        // صفرهای ابتدایی بی‌معنی
        while (digits.length() > 1 && digits.charAt(0) == '0') {
            digits.deleteCharAt(0);
            if (digitsBefore > 0) digitsBefore--;
        }
        StringBuilder out = new StringBuilder();
        int n = digits.length();
        int newCursor = 0;
        int seen = 0;
        for (int i = 0; i < n; i++) {
            if (i > 0 && (n - i) % 3 == 0) out.append(',');
            out.append(digits.charAt(i));
            seen++;
            if (seen == digitsBefore) newCursor = out.length();
        }
        if (digitsBefore == 0) newCursor = 0;
        String formatted = out.toString();
        if (formatted.equals(raw)) return;
        busy = true;
        try {
            ed.replace(0, ed.length(), formatted);
            int len = et.getText().length();
            et.setSelection(Math.max(0, Math.min(newCursor, len)));
        } catch (RuntimeException ignored) {
            // هرگز نگذار قالب‌بندی باعث بسته‌شدن برنامه شود
        } finally {
            busy = false;
        }
    }
}
