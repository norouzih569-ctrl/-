package com.barber.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** ساخت فایل PDF گزارش ماهانه (A4، راست‌به‌چپ، فونت وزیرمتن). */
public final class PdfReport {

    private static final int W = 595;
    private static final int H = 842;
    private static final int M = 36;
    private static final float ROW_H = 22f;

    private static final int ALIGN_TEXT = 0;   // راست‌چین
    private static final int ALIGN_CENTER = 1;
    private static final int ALIGN_NUM = 2;    // چپ‌چین (اعداد)

    private static final class Col {
        final String title;
        final float weight;
        final int align;

        Col(String title, float weight, int align) {
            this.title = title;
            this.weight = weight;
            this.align = align;
        }
    }

    private final ReportData r;
    private final Typeface regular;
    private final Typeface bold;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PdfDocument doc;
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo = 0;
    private float y;

    private PdfReport(Context ctx, ReportData r) {
        this.r = r;
        Typeface rg;
        Typeface bd;
        try {
            rg = Typeface.createFromAsset(ctx.getAssets(), "fonts/Vazirmatn-Regular.ttf");
            bd = Typeface.createFromAsset(ctx.getAssets(), "fonts/Vazirmatn-Bold.ttf");
        } catch (Exception e) {
            rg = Typeface.DEFAULT;
            bd = Typeface.DEFAULT_BOLD;
        }
        regular = rg;
        bold = bd;
        line.setStrokeWidth(0.8f);
        line.setColor(0xFFB0B7C3);
    }

    public static void write(Context ctx, ReportData r, File out) throws IOException {
        new PdfReport(ctx, r).render(out);
    }

    private void render(File out) throws IOException {
        doc = new PdfDocument();
        try {
            newPage();
            drawHeader();
            drawSummary();

            List<ReportData.Settle> settle = r.settlements();
            if (!settle.isEmpty()) {
                Col[] cols = {new Col("آرایشگر", 3f, ALIGN_TEXT), new Col("تعداد", 1.2f, ALIGN_CENTER),
                        new Col("جمع مبلغ", 2.2f, ALIGN_NUM), new Col("سهم آرایشگر", 2.2f, ALIGN_NUM),
                        new Col("سهم سالن", 2.2f, ALIGN_NUM)};
                List<String[]> rows = new ArrayList<>();
                int cnt = 0;
                double t = 0, b = 0, s = 0;
                for (ReportData.Settle x : settle) {
                    rows.add(new String[]{x.barber, String.valueOf(x.count), ReportData.fmt(x.total),
                            ReportData.fmt(x.barberShare), ReportData.fmt(x.salonShare)});
                    cnt += x.count;
                    t += x.total;
                    b += x.barberShare;
                    s += x.salonShare;
                }
                table("تسویه آرایشگران", cols, rows, new String[]{"جمع", String.valueOf(cnt),
                        ReportData.fmt(t), ReportData.fmt(b), ReportData.fmt(s)});
            }

            Col[] ac = {new Col("تاریخ", 2f, ALIGN_CENTER), new Col("ساعت", 1.2f, ALIGN_CENTER),
                    new Col("مشتری", 3.4f, ALIGN_TEXT), new Col("خدمت", 2.4f, ALIGN_TEXT),
                    new Col("قیمت", 2f, ALIGN_NUM)};
            List<String[]> arows = new ArrayList<>();
            for (ReportData.Appt a : r.appts) {
                arows.add(new String[]{a.date, a.time, a.customer, a.service, ReportData.fmt(a.price)});
            }
            table("نوبت‌ها", ac, arows, arows.isEmpty() ? null
                    : new String[]{"جمع", "", "", "", ReportData.fmt(r.income)});

            Col[] ec = {new Col("تاریخ", 2f, ALIGN_CENTER), new Col("شرح", 5f, ALIGN_TEXT),
                    new Col("مبلغ", 2.4f, ALIGN_NUM)};
            List<String[]> erows = new ArrayList<>();
            for (ReportData.Exp e : r.exps) {
                erows.add(new String[]{e.date, e.desc, ReportData.fmt(e.amount)});
            }
            table("هزینه‌ها", ec, erows, erows.isEmpty() ? null
                    : new String[]{"جمع", "", ReportData.fmt(r.expensesTotal)});

            Col[] sc = {new Col("تاریخ", 2f, ALIGN_CENTER), new Col("آرایشگر", 2.4f, ALIGN_TEXT),
                    new Col("خدمت", 2.2f, ALIGN_TEXT), new Col("قیمت", 1.9f, ALIGN_NUM),
                    new Col("سهم آرایشگر", 1.9f, ALIGN_NUM), new Col("سهم سالن", 1.9f, ALIGN_NUM)};
            List<String[]> srows = new ArrayList<>();
            double tp = 0, tb = 0, ts = 0;
            for (ReportData.Svc v : r.svcs) {
                srows.add(new String[]{v.date, v.barber, v.service, ReportData.fmt(v.price),
                        ReportData.fmt(v.barberShare), ReportData.fmt(v.salonShare)});
                tp += v.price;
                tb += v.barberShare;
                ts += v.salonShare;
            }
            table("سرویس‌های آرایشگران", sc, srows, srows.isEmpty() ? null
                    : new String[]{"جمع", "", "", ReportData.fmt(tp), ReportData.fmt(tb), ReportData.fmt(ts)});

            doc.finishPage(page);
            page = null;
            FileOutputStream fos = new FileOutputStream(out);
            try {
                doc.writeTo(fos);
            } finally {
                fos.close();
            }
        } finally {
            doc.close();
        }
    }

    // ---------------- صفحه و اجزا ----------------

    private void newPage() {
        if (page != null) doc.finishPage(page);
        pageNo++;
        page = doc.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        canvas = page.getCanvas();
        y = M;
        // پاورقی
        setText(false, 8.5f, 0xFF7A8290);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("صفحه " + pageNo + "   •   " + r.salon + "   •   " + r.generatedOn,
                W / 2f, H - 18, text);
        line.setColor(0xFFD5D9E0);
        canvas.drawLine(M, H - 30, W - M, H - 30, line);
    }

    private void setText(boolean b, float size, int color) {
        text.setTypeface(b ? bold : regular);
        text.setTextSize(size);
        text.setColor(color);
    }

    private void drawHeader() {
        setText(true, 20, 0xFF1F3A66);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(r.salon, W / 2f, y + 20, text);
        y += 34;
        setText(true, 14, 0xFF333A47);
        canvas.drawText(r.title(), W / 2f, y + 12, text);
        y += 22;
        setText(false, 9.5f, 0xFF7A8290);
        canvas.drawText("تاریخ تهیه: " + r.generatedOn, W / 2f, y + 10, text);
        y += 22;
    }

    private void drawSummary() {
        String[][] items = {
                {"درآمد نوبت‌ها (" + r.appts.size() + " نوبت)", r.money(r.income)},
                {"سهم سالن از آرایشگران", r.money(r.salonShare)},
                {"هزینه‌ها", r.money(r.expensesTotal)},
                {"مانده", r.money(r.balance)}
        };
        float boxH = items.length * 26f + 12f;
        fill.setColor(0xFFF2F5FB);
        canvas.drawRoundRect(new RectF(M, y, W - M, y + boxH), 8, 8, fill);
        float ry = y + 6;
        for (int i = 0; i < items.length; i++) {
            boolean last = i == items.length - 1;
            setText(last, 12, 0xFF333A47);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(items[i][0], W - M - 12, ry + 18, text);
            int color = !last ? 0xFF1F3A66 : (r.balance >= 0 ? 0xFF2E7D32 : 0xFFC62828);
            setText(true, 12, color);
            text.setTextAlign(Paint.Align.LEFT);
            canvas.drawText(items[i][1], M + 12, ry + 18, text);
            ry += 26;
        }
        y += boxH + 18;
    }

    // ---------------- جدول ----------------

    private float[] widths(Col[] cols) {
        float total = 0;
        for (Col c : cols) total += c.weight;
        float usable = W - 2f * M;
        float[] w = new float[cols.length];
        for (int i = 0; i < cols.length; i++) w[i] = usable * cols[i].weight / total;
        return w;
    }

    private void table(String heading, Col[] cols, List<String[]> rows, String[] totalRow) {
        float[] w = widths(cols);
        // عنوان بخش + حداقل یک ردیف داده باید جا شود
        if (y + 30 + ROW_H * 2 > H - 40) newPage();
        drawHeading(heading, false);
        if (rows.isEmpty()) {
            setText(false, 10.5f, 0xFF7A8290);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("موردی ثبت نشده است.", W - M - 6, y + 14, text);
            y += 30;
            return;
        }
        drawRow(cols, w, null, true, false, false);
        for (int i = 0; i < rows.size(); i++) {
            if (y + ROW_H > H - 44) {
                newPage();
                drawHeading(heading, true);
                drawRow(cols, w, null, true, false, false);
            }
            drawRow(cols, w, rows.get(i), false, i % 2 == 1, false);
        }
        if (totalRow != null) {
            if (y + ROW_H > H - 44) {
                newPage();
                drawHeading(heading, true);
                drawRow(cols, w, null, true, false, false);
            }
            drawRow(cols, w, totalRow, false, false, true);
        }
        y += 18;
    }

    private void drawHeading(String heading, boolean continued) {
        setText(true, 13, 0xFF1F3A66);
        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(heading + (continued ? " (ادامه)" : ""), W - M, y + 14, text);
        y += 24;
    }

    private void drawRow(Col[] cols, float[] w, String[] cells, boolean header, boolean zebra, boolean total) {
        float top = y;
        if (header) {
            fill.setColor(0xFFDDE6F5);
            canvas.drawRect(M, top, W - M, top + ROW_H, fill);
        } else if (total) {
            line.setColor(0xFF7A8290);
            canvas.drawLine(M, top, W - M, top, line);
        } else if (zebra) {
            fill.setColor(0xFFF7F8FB);
            canvas.drawRect(M, top, W - M, top + ROW_H, fill);
        }
        float right = W - M;
        for (int i = 0; i < cols.length; i++) {
            float left = right - w[i];
            String s = header ? cols[i].title : (cells[i] == null ? "" : cells[i]);
            setText(header || total, 10f, 0xFF222831);
            drawCell(s, left, right, top, cols[i].align);
            right = left;
        }
        line.setColor(0xFFE3E6EC);
        canvas.drawLine(M, top + ROW_H, W - M, top + ROW_H, line);
        y += ROW_H;
    }

    private void drawCell(String s, float left, float right, float top, int align) {
        if (s.isEmpty()) return;
        float avail = right - left - 12f;
        if (text.measureText(s) > avail) {
            int n = text.breakText(s, true, avail - text.measureText("…"), null);
            s = s.substring(0, Math.max(0, n)) + "…";
        }
        float base = top + 15f;
        switch (align) {
            case ALIGN_CENTER:
                text.setTextAlign(Paint.Align.CENTER);
                canvas.drawText(s, (left + right) / 2f, base, text);
                break;
            case ALIGN_NUM:
                text.setTextAlign(Paint.Align.LEFT);
                canvas.drawText(s, left + 6f, base, text);
                break;
            default:
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(s, right - 6f, base, text);
        }
    }
}
