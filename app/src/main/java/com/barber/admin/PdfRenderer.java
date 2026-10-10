package com.barber.admin;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

/**
 * تبدیل Doc به PDF (A4، راست‌به‌چپ، فونت وزیرمتن).
 * اگر فایل‌های logo.png و stamp.png در پوشه‌ی files برنامه باشند، لوگو در سربرگ و مهر کنار امضای فروشنده چاپ می‌شود.
 */
public final class PdfRenderer {

    private static final int W = 595;
    private static final int H = 842;
    private static final int M = 40;
    private static final String RLM = "‏";
    private static final int ACCENT = 0xFF0F766E;
    private static final int INK = 0xFF222831;
    private static final int MUTED = 0xFF7A8290;

    private final Doc doc;
    private final Context ctx;
    private final Typeface regular;
    private final Typeface bold;
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);

    private PdfDocument pdf;
    private PdfDocument.Page page;
    private Canvas canvas;
    private int pageNo = 0;
    private float y;

    private PdfRenderer(Context ctx, Doc doc) {
        this.ctx = ctx;
        this.doc = doc;
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

    public static void write(Context ctx, Doc doc, File out) throws IOException {
        new PdfRenderer(ctx, doc).render(out);
    }

    private void render(File out) throws IOException {
        pdf = new PdfDocument();
        try {
            newPage();
            drawHeader();
            for (Doc.Block b : doc.blocks) drawBlock(b);
            pdf.finishPage(page);
            page = null;
            FileOutputStream fos = new FileOutputStream(out);
            try {
                pdf.writeTo(fos);
            } finally {
                fos.close();
            }
        } finally {
            pdf.close();
        }
    }

    // ---------------- صفحه ----------------

    private void newPage() {
        if (page != null) pdf.finishPage(page);
        pageNo++;
        page = pdf.startPage(new PdfDocument.PageInfo.Builder(W, H, pageNo).create());
        canvas = page.getCanvas();
        y = M;
        setText(false, 8.5f, MUTED);
        text.setTextAlign(Paint.Align.CENTER);
        String foot = "صفحه " + Fa.digits(String.valueOf(pageNo)) + "   •   " + doc.brand
                + (doc.docNo.isEmpty() ? "" : "   •   " + doc.docNo);
        canvas.drawText(RLM + foot, W / 2f, H - 18, text);
        line.setColor(0xFFD5D9E0);
        canvas.drawLine(M, H - 30, W - M, H - 30, line);
    }

    private void ensure(float h) {
        if (y + h > H - 44) newPage();
    }

    private void setText(boolean b, float size, int color) {
        text.setTypeface(b ? bold : regular);
        text.setTextSize(size);
        text.setColor(color);
    }

    private Bitmap loadImage(String name) {
        try {
            File f = new File(ctx.getFilesDir(), name);
            if (!f.exists()) return null;
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    private void drawImageFit(Bitmap bmp, float right, float top, float maxW, float maxH) {
        if (bmp == null || bmp.getWidth() == 0 || bmp.getHeight() == 0) return;
        float s = Math.min(maxW / bmp.getWidth(), maxH / bmp.getHeight());
        float w = bmp.getWidth() * s;
        float h = bmp.getHeight() * s;
        canvas.drawBitmap(bmp, null, new RectF(right - w, top, right, top + h), null);
    }

    private void drawHeader() {
        Bitmap logo = loadImage("logo.png");
        if (logo != null) drawImageFit(logo, W - M, y, 60, 60);
        setText(true, 19, ACCENT);
        text.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(RLM + (doc.sellerName.isEmpty() ? doc.brand : doc.sellerName), W / 2f, y + 20, text);
        y += 32;
        setText(false, 10, MUTED);
        if (!doc.sellerName.isEmpty()) {
            canvas.drawText(RLM + doc.brand, W / 2f, y + 10, text);
            y += 18;
        }
        setText(true, 15, INK);
        canvas.drawText(RLM + doc.title, W / 2f, y + 18, text);
        y += 30;
        setText(false, 10, MUTED);
        String meta = "تاریخ: " + doc.dateJalali + (doc.docNo.isEmpty() ? "" : "     شماره: " + doc.docNo);
        canvas.drawText(RLM + meta, W / 2f, y + 10, text);
        y += 22;
        if (logo != null) y = Math.max(y, M + 66);
        canvas.drawLine(M, y, W - M, y, line);
        y += 12;
    }

    // ---------------- متن و شکستن خط ----------------

    private List<String> wrap(String s, float maxW) {
        List<String> out = new ArrayList<String>();
        String[] paras = (s == null ? "" : s).split("\n", -1);
        for (String p : paras) {
            String[] words = p.split(" ");
            StringBuilder cur = new StringBuilder();
            for (String w : words) {
                String candidate = cur.length() == 0 ? w : cur + " " + w;
                if (cur.length() > 0 && text.measureText(RLM + candidate) > maxW) {
                    out.add(cur.toString());
                    cur = new StringBuilder(w);
                } else {
                    cur = new StringBuilder(candidate);
                }
            }
            out.add(cur.toString());
        }
        return out;
    }

    /** چند خط متن راست‌چین؛ y را جلو می‌برد (با رد شدن خودکار به صفحه‌ی بعد) */
    private void drawWrapped(String s, float xRight, float maxW, float lineH) {
        for (String ln : wrap(s, maxW)) {
            ensure(lineH);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(RLM + ln, xRight, y + lineH - 5, text);
            y += lineH;
        }
    }

    // ---------------- بلوک‌ها ----------------

    private void drawBlock(Doc.Block b) {
        float full = W - 2f * M;
        switch (b.type) {
            case Doc.H:
                ensure(40);
                y += 8;
                setText(true, 13, ACCENT);
                text.setTextAlign(Paint.Align.RIGHT);
                canvas.drawText(RLM + b.a, W - M, y + 14, text);
                y += 20;
                line.setColor(ACCENT);
                canvas.drawLine(W - M - 60, y, W - M, y, line);
                line.setColor(0xFFB0B7C3);
                y += 8;
                break;
            case Doc.P:
                setText(false, 11, INK);
                drawWrapped(b.a, W - M, full, 18);
                y += 2;
                break;
            case Doc.NOTE:
                setText(false, 9, MUTED);
                drawWrapped(b.a, W - M, full, 14);
                break;
            case Doc.CLAUSE:
                ensure(60);
                y += 6;
                setText(true, 11.5f, INK);
                drawWrapped(b.a, W - M, full, 19);
                setText(false, 10.5f, INK);
                drawWrapped(b.b, W - M, full, 17.5f);
                break;
            case Doc.SPACE:
                y += b.h;
                break;
            case Doc.KV:
                drawKv(b);
                break;
            case Doc.TABLE:
                drawTable(b);
                break;
            case Doc.SIGN:
                drawSign(b);
                break;
            default:
                break;
        }
    }

    private void drawKv(Doc.Block b) {
        float full = W - 2f * M;
        float labelW = full * 0.28f;
        float valueRight = W - M - labelW - 8;
        float valueW = full - labelW - 16;
        for (int i = 0; i < b.rows.length; i++) {
            setText(false, 10.5f, INK);
            List<String> lines = wrap(b.rows[i][1], valueW);
            float rowH = Math.max(22f, lines.size() * 16f + 8f);
            ensure(rowH);
            if (i % 2 == 0) {
                fill.setColor(0xFFF2F5FB);
                canvas.drawRect(M, y, W - M, y + rowH, fill);
            }
            setText(true, 10.5f, MUTED);
            text.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(RLM + b.rows[i][0], W - M - 8, y + 15, text);
            setText(false, 10.5f, INK);
            float ly = y + 15;
            for (String ln : lines) {
                canvas.drawText(RLM + ln, valueRight, ly, text);
                ly += 16;
            }
            y += rowH;
        }
        y += 6;
    }

    private void drawTable(Doc.Block b) {
        float full = W - 2f * M;
        float sum = 0;
        for (float w : b.weights) sum += w;
        float[] cw = new float[b.weights.length];
        for (int i = 0; i < cw.length; i++) cw[i] = full * b.weights[i] / sum;

        drawRow(b, b.cols, cw, true, false);
        for (String[] r : b.rows) drawRow(b, r, cw, false, false);
        if (b.footer != null) drawRow(b, b.footer, cw, false, true);
        y += 8;
    }

    private void drawRow(Doc.Block b, String[] cells, float[] cw, boolean header, boolean footer) {
        setText(header || footer, 10f, header ? 0xFFFFFFFF : INK);
        float rowH = 24f;
        List<List<String>> wrapped = new ArrayList<List<String>>();
        for (int i = 0; i < cells.length; i++) {
            List<String> l = wrap(cells[i], cw[i] - 10);
            wrapped.add(l);
            rowH = Math.max(rowH, l.size() * 15f + 9f);
        }
        ensure(rowH);
        if (header) {
            fill.setColor(ACCENT);
            canvas.drawRect(M, y, W - M, y + rowH, fill);
        } else if (footer) {
            fill.setColor(0xFFE6ECF5);
            canvas.drawRect(M, y, W - M, y + rowH, fill);
        }
        float right = W - M;
        for (int i = 0; i < cells.length; i++) {
            float left = right - cw[i];
            int align = header ? Doc.ALIGN_CENTER : b.aligns[i];
            float ly = y + 16;
            for (String ln : wrapped.get(i)) {
                if (align == Doc.ALIGN_CENTER) {
                    text.setTextAlign(Paint.Align.CENTER);
                    canvas.drawText(RLM + ln, (left + right) / 2f, ly, text);
                } else if (align == Doc.ALIGN_NUM) {
                    text.setTextAlign(Paint.Align.LEFT);
                    canvas.drawText(RLM + ln, left + 6, ly, text);
                } else {
                    text.setTextAlign(Paint.Align.RIGHT);
                    canvas.drawText(RLM + ln, right - 6, ly, text);
                }
                ly += 15;
            }
            right = left;
        }
        line.setColor(0xFFD5D9E0);
        canvas.drawLine(M, y + rowH, W - M, y + rowH, line);
        y += rowH;
    }

    private void drawSign(Doc.Block b) {
        float boxH = 110f;
        ensure(boxH + 20);
        y += 14;
        int n = b.labels.length;
        float gap = 16f;
        float bw = (W - 2f * M - gap * (n - 1)) / n;
        float right = W - M;
        Bitmap stamp = loadImage("stamp.png");
        for (int i = 0; i < n; i++) {
            float left = right - bw;
            fill.setColor(0xFFF8F9FB);
            canvas.drawRoundRect(new RectF(left, y, right, y + boxH), 8, 8, fill);
            line.setColor(0xFFB0B7C3);
            canvas.drawLine(left, y, right, y, line);
            setText(true, 10.5f, MUTED);
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(RLM + b.labels[i], (left + right) / 2f, y + 18, text);
            if (i == 0 && stamp != null) drawImageFit(stamp, right - 14, y + 26, bw - 28, boxH - 36);
            right = left - gap;
        }
        y += boxH + 8;
    }
}
