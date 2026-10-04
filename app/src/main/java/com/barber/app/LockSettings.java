package com.barber.app;

import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/** پنجره‌های تنظیم قفل برنامه: فعال‌سازی، تغییر رمز، اثر انگشت، زمان قفل خودکار و غیرفعال‌سازی. */
public final class LockSettings {

    private LockSettings() {
    }

    private static EditText pinField(MainActivity act, String hint) {
        EditText et = new EditText(act);
        et.setHint(hint);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        et.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(Logic.PIN_MAX)});
        et.setMaxLines(1);
        return et;
    }

    private static LinearLayout box(MainActivity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 8));
        return box;
    }

    private static TextView note(MainActivity act, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(13);
        t.setPadding(0, 0, 0, Ui.dp(act, 8));
        return t;
    }

    public static void show(final MainActivity act) {
        if (!LockManager.isEnabled(act)) {
            showEnable(act);
            return;
        }
        final boolean bioAvail = LockManager.biometricAvailable(act);
        final boolean bioOn = LockManager.isBiometricEnabled(act);

        final List<String> labels = new ArrayList<>();
        final List<Integer> codes = new ArrayList<>();
        labels.add("🔑 تغییر رمز");
        codes.add(0);
        if (bioAvail) {
            labels.add(bioOn ? "👆 خاموش کردن اثر انگشت/چهره" : "👆 فعال کردن اثر انگشت/چهره");
            codes.add(1);
        }
        labels.add("⏱ قفل خودکار بعد از: "
                + LockManager.timeoutLabel(LockManager.getTimeoutSeconds(act)));
        codes.add(2);
        labels.add("🔓 غیرفعال کردن قفل");
        codes.add(3);

        new MaterialAlertDialogBuilder(act)
                .setTitle("🔒 قفل برنامه (فعال)")
                .setItems(labels.toArray(new CharSequence[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        switch (codes.get(which)) {
                            case 0:
                                askCurrentPin(act, "برای تغییر رمز، رمز فعلی را وارد کنید", new Runnable() {
                                    @Override
                                    public void run() {
                                        showSetPin(act, "رمز جدید", null);
                                    }
                                });
                                break;
                            case 1:
                                toggleBiometric(act, !bioOn);
                                break;
                            case 2:
                                showTimeoutChooser(act);
                                break;
                            default:
                                askCurrentPin(act, "برای غیرفعال کردن قفل، رمز را وارد کنید", new Runnable() {
                                    @Override
                                    public void run() {
                                        LockManager.disable(act);
                                        act.applySecureFlag();
                                        Ui.toast(act, "قفل برنامه غیرفعال شد");
                                    }
                                });
                        }
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    private static void showEnable(final MainActivity act) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("🔒 قفل برنامه")
                .setMessage("با فعال کردن قفل، هر بار که برنامه را باز می‌کنید (یا بعد از مدتی که از آن بیرون بوده‌اید) "
                        + "باید رمز یا اثر انگشت را وارد کنید. همچنین عکس‌گرفتن از صفحه و نمایش برنامه در لیست اخیر مخفی می‌شود.\n\n"
                        + "⚠️ اگر رمز را فراموش کنید راه بازیابی وجود ندارد و فقط با پاک کردن اطلاعات برنامه باز می‌شود "
                        + "(که دیتابیس را هم پاک می‌کند). پیشنهاد می‌شود قبلش از منوی بالا «پشتیبان‌گیری» کنید.")
                .setPositiveButton("ادامه", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        showSetPin(act, "تعیین رمز", new Runnable() {
                            @Override
                            public void run() {
                                if (LockManager.biometricAvailable(act)) offerBiometric(act);
                            }
                        });
                    }
                })
                .setNegativeButton("انصراف", null)
                .show();
    }

    /** دو بار گرفتن رمز جدید و ذخیره؛ onDone بعد از ذخیره‌ی موفق اجرا می‌شود. */
    private static void showSetPin(final MainActivity act, String title, final Runnable onDone) {
        LinearLayout box = box(act);
        box.addView(note(act, "یک رمز ۴ تا ۸ رقمی انتخاب کنید (فقط عدد)."));
        final EditText p1 = pinField(act, "رمز جدید");
        final EditText p2 = pinField(act, "تکرار رمز");
        box.addView(p1);
        box.addView(p2);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("ذخیره", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String a = Logic.normalizePin(p1.getText().toString());
                if (a == null) {
                    p1.setError("رمز باید فقط عدد و ۴ تا ۸ رقم باشد");
                    return;
                }
                String b = Logic.normalizePin(p2.getText().toString());
                if (b == null || !a.equals(b)) {
                    p2.setError("تکرار رمز با رمز اول یکی نیست");
                    return;
                }
                boolean wasEnabled = LockManager.isEnabled(act);
                boolean bio = LockManager.isBiometricEnabled(act);
                LockManager.setPin(act, a);
                LockManager.setBiometricEnabled(act, wasEnabled && bio);
                act.markSessionUnlocked();
                act.applySecureFlag();
                dialog.dismiss();
                Ui.toast(act, wasEnabled ? "رمز تغییر کرد" : "قفل برنامه فعال شد");
                if (onDone != null) onDone.run();
            }
        });
    }

    private static void offerBiometric(final MainActivity act) {
        new MaterialAlertDialogBuilder(act)
                .setTitle("اثر انگشت / چهره")
                .setMessage("می‌خواهید علاوه بر رمز، با اثر انگشت یا چهره هم بتوانید قفل را باز کنید؟")
                .setPositiveButton("بله، فعال کن", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        toggleBiometric(act, true);
                    }
                })
                .setNegativeButton("فقط رمز", null)
                .show();
    }

    private static void toggleBiometric(MainActivity act, boolean on) {
        LockManager.setBiometricEnabled(act, on);
        Ui.toast(act, on ? "اثر انگشت/چهره فعال شد" : "اثر انگشت/چهره خاموش شد");
    }

    private static void showTimeoutChooser(final MainActivity act) {
        int cur = LockManager.getTimeoutSeconds(act);
        int sel = 1;
        for (int i = 0; i < LockManager.TIMEOUTS.length; i++) {
            if (LockManager.TIMEOUTS[i] == cur) sel = i;
        }
        new MaterialAlertDialogBuilder(act)
                .setTitle("قفل خودکار بعد از بیرون رفتن از برنامه")
                .setSingleChoiceItems(LockManager.TIMEOUT_LABELS, sel, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        LockManager.setTimeoutSeconds(act, LockManager.TIMEOUTS[which]);
                        dialog.dismiss();
                        Ui.toast(act, "ذخیره شد");
                    }
                })
                .setNegativeButton("بستن", null)
                .show();
    }

    /** از کاربر رمز فعلی را می‌گیرد و فقط در صورت درست بودن onOk را اجرا می‌کند. */
    private static void askCurrentPin(final MainActivity act, String message, final Runnable onOk) {
        LinearLayout box = box(act);
        box.addView(note(act, message));
        final EditText et = pinField(act, "رمز فعلی");
        box.addView(et);

        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("تأیید رمز")
                .setView(box)
                .setPositiveButton("تأیید", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int wait = LockManager.lockRemainingSeconds(act);
                if (wait > 0) {
                    et.setError("تلاش بیش از حد؛ " + wait + " ثانیه صبر کنید");
                    return;
                }
                String pin = Logic.normalizePin(et.getText().toString());
                if (pin != null && LockManager.checkPin(act, pin)) {
                    LockManager.resetFailures(act);
                    dialog.dismiss();
                    onOk.run();
                } else {
                    LockManager.recordFailure(act);
                    et.setError("رمز اشتباه است");
                }
            }
        });
    }
}
