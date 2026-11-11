package com.barber.app;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

/** صفحه‌ی «لایسنس و فعال‌سازی»: وضعیت، شناسه‌ی دستگاه، زدن کد و سابقه‌ی فعال‌سازی‌ها. */
public final class LicenseDialog {

    private LicenseDialog() {
    }

    private static TextView text(Context c, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(t.getTypeface(), Typeface.BOLD);
        if (color != 0) t.setTextColor(color);
        return t;
    }

    private static TextView heading(Context c, String s) {
        TextView t = text(c, s, 15, true, Ui.themeColor(c, R.attr.barberAccent, 0xFF1E6BE6));
        t.setPadding(0, Ui.dp(c, 16), 0, Ui.dp(c, 4));
        return t;
    }

    private static View row(Context c, String label, String value) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4));
        TextView l = text(c, label, 14, false, Ui.themeColor(c, android.R.attr.textColorSecondary, 0xFF888888));
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView v = text(c, value, 14, true, 0);
        v.setGravity(Gravity.END);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        return r;
    }

    /** onChanged بعد از فعال‌سازی موفق اجرا می‌شود (برای به‌روزکردن نوار هشدار و صفحه‌ها). */
    public static void show(final MainActivity act, final Runnable onChanged) {
        final LicenseLogic.State st = LicenseManager.state(act);
        final String deviceId = LicenseManager.deviceId(act);
        final int red = 0xFFEF5350;
        final int green = 0xFF43A047;
        final int accent = Ui.themeColor(act, R.attr.barberAccent, 0xFF1E6BE6);

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), Ui.dp(act, 8));

        // ---------------- وضعیت ----------------
        String title;
        int titleColor;
        switch (st.status) {
            case ACTIVE:
                if (LicenseManager.lastType(act) == LicenseCodec.TYPE_TRIAL) {
                    title = "⏳ دوره‌ی آزمایشی";
                    titleColor = accent;
                } else {
                    title = "✅ لایسنس فعال است";
                    titleColor = green;
                }
                break;
            case NONE:
                title = "🔑 برنامه هنوز فعال نشده";
                titleColor = accent;
                break;
            case TAMPERED:
                title = "⚠️ ساعت گوشی نادرست است";
                titleColor = red;
                break;
            default:
                title = "⛔ لایسنس تمام شده است";
                titleColor = red;
        }
        box.addView(text(act, title, 18, true, titleColor));

        switch (st.status) {
            case ACTIVE:
                box.addView(row(act, "نوع", LicenseManager.typeText(LicenseManager.lastType(act))));
                box.addView(row(act, "تاریخ شروع", LicenseManager.dateText(LicenseManager.licStart(act))));
                box.addView(row(act, "تاریخ پایان", LicenseManager.dateText(LicenseManager.licEnd(act))));
                box.addView(row(act, "روزهای باقی‌مانده",
                        st.daysLeft == 0 ? "امروز آخرین روز است" : st.daysLeft + " روز"));
                break;
            case NONE:
                box.addView(text(act, "برای شروع، «شناسه‌ی دستگاه» را (از پایین همین صفحه) به فروشنده بدهید "
                        + "و کد آزمایشی یا کد فعال‌سازی بگیرید؛ سپس کد را همین‌جا وارد کنید.", 13, false, 0));
                break;
            case TAMPERED:
                box.addView(text(act, "ساعت یا تاریخ گوشی از آخرین استفاده‌ی برنامه عقب‌تر است. "
                        + "تاریخ را درست کنید؛ تا آن موقع برنامه فقط‌خواندنی است.", 13, false, 0));
                break;
            default:
                if (LicenseManager.licEnd(act) > 0) {
                    box.addView(row(act, "پایان لایسنس قبلی", LicenseManager.dateText(LicenseManager.licEnd(act))));
                }
                box.addView(text(act, "اطلاعات و گزارش‌های شما حفظ شده و قابل مشاهده است. "
                        + "برای ثبت نوبت و تغییر اطلاعات، لایسنس را فعال یا تمدید کنید.", 13, false, 0));
        }

        // ---------------- شناسه‌ی دستگاه ----------------
        box.addView(heading(act, "شناسه‌ی این دستگاه"));
        box.addView(text(act, "برای دریافت کد، این شناسه را به فروشنده بدهید. کد فقط روی همین گوشی کار می‌کند.",
                12, false, Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF888888)));
        TextView idView = text(act, deviceId, 24, true, accent);
        idView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        idView.setGravity(Gravity.CENTER);
        idView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        idView.setLetterSpacing(0.08f);
        idView.setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 6));
        idView.setBackground(Ui.tinted(act, accent, 0x18, 12));
        LinearLayout.LayoutParams idLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        idLp.topMargin = Ui.dp(act, 8);
        box.addView(idView, idLp);

        LinearLayout idButtons = new LinearLayout(act);
        idButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = new Button(act, null, android.R.attr.borderlessButtonStyle);
        copy.setText("📋 کپی");
        copy.setAllCaps(false);
        copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ClipboardManager cm = (ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("device-id", deviceId));
                Ui.toast(act, "شناسه کپی شد");
            }
        });
        Button share = new Button(act, null, android.R.attr.borderlessButtonStyle);
        share.setText("📤 ارسال");
        share.setAllCaps(false);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, "شناسه‌ی دستگاه برای لایسنس مدیریت آرایشگاه:\n" + deviceId);
                act.startActivity(Intent.createChooser(i, "ارسال شناسه"));
            }
        });
        idButtons.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        idButtons.addView(share, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        box.addView(idButtons);

        // ---------------- کد فعال‌سازی ----------------
        box.addView(heading(act, "کد فعال‌سازی"));
        final EditText et = new EditText(act);
        et.setHint("مثل K7M2-9QX4-TH5R");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        et.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        et.setGravity(Gravity.CENTER);
        et.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        et.setTextSize(18);
        et.setMaxLines(1);
        box.addView(et);
        final TextView msg = text(act, "", 13, true, red);
        msg.setPadding(0, Ui.dp(act, 6), 0, 0);
        box.addView(msg);

        // ---------------- سابقه ----------------
        List<LicenseCore.History> hist = LicenseManager.history(act);
        if (!hist.isEmpty()) {
            box.addView(heading(act, "سابقه‌ی فعال‌سازی‌ها"));
            for (LicenseCore.History h : hist) {
                box.addView(row(act, LicenseManager.dateText(h.onDay) + "  •  " + LicenseManager.typeText(h.type),
                        "تا " + LicenseManager.dateText(h.end)));
            }
        }

        ScrollView scroll = new ScrollView(act);
        scroll.addView(box);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("🔑 لایسنس و فعال‌سازی")
                .setView(scroll)
                .setPositiveButton("فعال‌سازی", null)
                .setNegativeButton("بستن", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String code = et.getText().toString();
                if (code.trim().isEmpty()) {
                    msg.setTextColor(red);
                    msg.setText("کد را وارد کنید.");
                    return;
                }
                LicenseCore.Outcome o = LicenseManager.activate(act, code);
                if (!o.ok) {
                    msg.setTextColor(red);
                    msg.setText(o.message);
                    return;
                }
                dialog.dismiss();
                Ui.toastLong(act, "✅ لایسنس فعال شد؛ اعتبار تا " + LicenseManager.dateText(o.end));
                if (onChanged != null) onChanged.run();
            }
        });
    }
}
