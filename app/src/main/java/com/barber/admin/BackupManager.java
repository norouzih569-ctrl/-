package com.barber.admin;

import android.net.Uri;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;

/** پشتیبان‌گیری رمزدار و بازیابی. فایل پشتیبان فقط با رمزی که شما می‌گذارید باز می‌شود. */
public final class BackupManager {

    private BackupManager() {
    }

    private static EditText pass(MainActivity act, LinearLayout box, String hint) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        e.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(act, 8);
        box.addView(e, lp);
        return e;
    }

    private static LinearLayout box(MainActivity act, String message) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        box.setPadding(Ui.dp(act, 20), Ui.dp(act, 12), Ui.dp(act, 20), Ui.dp(act, 4));
        TextView t = new TextView(act);
        t.setText(message);
        t.setTextSize(13);
        box.addView(t);
        return box;
    }

    // ================= پشتیبان‌گیری =================

    public static void backup(final MainActivity act) {
        LinearLayout box = box(act, "فایل پشتیبان با این رمز رمزگذاری می‌شود. "
                + "⚠️ اگر رمز را فراموش کنید، هیچ راهی برای بازکردن آن نیست.");
        final EditText p1 = pass(act, box, "رمز پشتیبان (حداقل " + BackupCrypto.MIN_PASSWORD + " نویسه)");
        final EditText p2 = pass(act, box, "تکرار رمز");
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("پشتیبان‌گیری امن")
                .setView(box)
                .setPositiveButton("ساخت پشتیبان", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String a = p1.getText().toString();
                String b = p2.getText().toString();
                if (a.length() < BackupCrypto.MIN_PASSWORD) {
                    p1.setError("رمز خیلی کوتاه است");
                    return;
                }
                if (!a.equals(b)) {
                    p2.setError("دو رمز یکی نیست");
                    return;
                }
                dialog.dismiss();
                doBackup(act, a.toCharArray());
            }
        });
    }

    private static void doBackup(final MainActivity act, final char[] password) {
        try {
            File dbFile = Db.get(act).databaseFileForBackup();
            byte[] plain = readAll(new FileInputStream(dbFile));
            byte[] enc = BackupCrypto.encrypt(plain, password);
            File dir = new File(act.getCacheDir(), "exports");
            if (!dir.exists()) dir.mkdirs();
            int[] j = JalaliCalendar.today();
            final File out = new File(dir, String.format(java.util.Locale.US, "license_admin_%04d%02d%02d.lbk",
                    j[0], j[1], j[2]));
            FileOutputStream fos = new FileOutputStream(out);
            try {
                fos.write(enc);
            } finally {
                fos.close();
            }
            new MaterialAlertDialogBuilder(act)
                    .setTitle("✅ پشتیبان ساخته شد")
                    .setMessage("فایل را جایی امن ذخیره کنید (تلگرام، ایمیل، گوگل‌درایو، …). "
                            + "برای بازکردن آن همین رمز لازم است.")
                    .setPositiveButton("ذخیره / ارسال", new android.content.DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(android.content.DialogInterface d, int which) {
                            Share.file(act, out, "application/octet-stream");
                        }
                    })
                    .setNegativeButton("بستن", null)
                    .show();
        } catch (Exception e) {
            Ui.toastLong(act, "پشتیبان‌گیری انجام نشد: " + e.getMessage());
        }
    }

    // ================= بازیابی =================

    /** بعد از انتخاب فایل توسط کاربر صدا زده می‌شود */
    public static void restoreFrom(final MainActivity act, final Uri uri) {
        if (uri == null) return;
        final byte[] data;
        try {
            InputStream in = act.getContentResolver().openInputStream(uri);
            if (in == null) throw new java.io.IOException("فایل باز نشد");
            data = readAll(in);
        } catch (Exception e) {
            Ui.toastLong(act, "خواندن فایل انجام نشد");
            return;
        }
        LinearLayout box = box(act, "⚠️ با بازیابی، همه‌ی اطلاعات فعلی این برنامه با اطلاعات فایل پشتیبان جایگزین می‌شود. "
                + "اگر لازم است، اول از وضعیت فعلی پشتیبان بگیرید.");
        final EditText p = pass(act, box, "رمز پشتیبان");
        final AlertDialog dialog = new MaterialAlertDialogBuilder(act)
                .setTitle("بازیابی از پشتیبان")
                .setView(box)
                .setPositiveButton("بازیابی", null)
                .setNegativeButton("انصراف", null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pw = p.getText().toString();
                if (pw.isEmpty()) {
                    p.setError("رمز را وارد کنید");
                    return;
                }
                try {
                    byte[] plain = BackupCrypto.decrypt(data, pw.toCharArray());
                    applyRestore(act, plain);
                    dialog.dismiss();
                } catch (BackupCrypto.WrongPasswordException e) {
                    p.setError("رمز اشتباه است یا فایل خراب شده");
                } catch (BackupCrypto.NotBackupException e) {
                    dialog.dismiss();
                    Ui.toastLong(act, "این فایل، پشتیبان این برنامه نیست");
                } catch (Exception e) {
                    dialog.dismiss();
                    Ui.toastLong(act, "بازیابی انجام نشد: " + e.getMessage());
                }
            }
        });
    }

    private static void applyRestore(MainActivity act, byte[] plain) throws Exception {
        // قبل از جایگزینی، مطمئن می‌شویم فایل یک پایگاه داده‌ی سالم SQLite با جدول‌های برنامه است
        File tmp = new File(act.getCacheDir(), "restore_check.db");
        FileOutputStream fos = new FileOutputStream(tmp);
        try {
            fos.write(plain);
        } finally {
            fos.close();
        }
        android.database.sqlite.SQLiteDatabase chk = null;
        try {
            chk = android.database.sqlite.SQLiteDatabase.openDatabase(tmp.getPath(), null,
                    android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
            android.database.Cursor c = chk.rawQuery(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name IN ('customers','visitors','licenses','payments','payouts','prices','settings')",
                    null);
            try {
                if (!c.moveToFirst() || c.getInt(0) != 7) throw new IllegalStateException("ساختار فایل پشتیبان درست نیست");
            } finally {
                c.close();
            }
        } finally {
            if (chk != null) chk.close();
        }

        File target = Db.get(act).databaseFileForBackup();
        Db.closeAndReset();
        File wal = new File(target.getPath() + "-wal");
        File shm = new File(target.getPath() + "-shm");
        File journal = new File(target.getPath() + "-journal");
        if (wal.exists()) wal.delete();
        if (shm.exists()) shm.delete();
        if (journal.exists()) journal.delete();
        FileOutputStream out = new FileOutputStream(target);
        try {
            out.write(plain);
        } finally {
            out.close();
        }
        tmp.delete();
        Db.get(act).getWritableDatabase();   // باز کردن دوباره (و ارتقای ساختار در صورت نیاز)
        act.refreshAll();
        Ui.toastLong(act, "بازیابی انجام شد");
    }

    private static byte[] readAll(InputStream in) throws java.io.IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
            return bos.toByteArray();
        } finally {
            in.close();
        }
    }
}
