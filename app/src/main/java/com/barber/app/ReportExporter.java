package com.barber.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** تولید فایل گزارش (PDF / Excel) و ارسال آن با برنامه‌های گوشی (تلگرام، واتساپ، ایمیل، ذخیره در فایل‌ها…). */
public final class ReportExporter {

    private static final String MIME_PDF = "application/pdf";
    private static final String MIME_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final long MAX_AGE_MS = 3L * 24 * 3600 * 1000;

    private ReportExporter() {
    }

    private static File exportDir(Activity act) {
        File dir = new File(act.getCacheDir(), "exports");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        // فایل‌های موقتِ قدیمی را پاک می‌کنیم
        File[] old = dir.listFiles();
        if (old != null) {
            long now = System.currentTimeMillis();
            for (File f : old) {
                if (now - f.lastModified() > MAX_AGE_MS) {
                    //noinspection ResultOfMethodCallIgnored
                    f.delete();
                }
            }
        }
        return dir;
    }

    public static void sharePdf(Activity act, ReportData r) throws IOException {
        File f = new File(exportDir(act), r.fileBaseName() + ".pdf");
        PdfReport.write(act, r, f);
        shareFile(act, f, MIME_PDF, r.title());
    }

    public static void shareExcel(Activity act, ReportData r) throws IOException {
        File f = new File(exportDir(act), r.fileBaseName() + ".xlsx");
        OutputStream out = new FileOutputStream(f);
        try {
            XlsxWriter.write(out, r.toSheets());
        } finally {
            out.close();
        }
        shareFile(act, f, MIME_XLSX, r.title());
    }

    public static void shareText(Activity act, String title, String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, title);
        i.putExtra(Intent.EXTRA_TEXT, text);
        act.startActivity(Intent.createChooser(i, title));
    }

    private static void shareFile(Activity act, File f, String mime, String title) {
        Uri uri = FileProvider.getUriForFile(act, act.getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(mime);
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.putExtra(Intent.EXTRA_SUBJECT, title);
        i.setClipData(ClipData.newRawUri(title, uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        act.startActivity(Intent.createChooser(i, title));
    }
}
