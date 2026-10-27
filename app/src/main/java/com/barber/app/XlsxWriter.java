package com.barber.app;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * نویسنده‌ی ساده‌ی فایل اکسل (.xlsx) بدون هیچ کتابخانه‌ی خارجی.
 * برگه‌ها راست‌به‌چپ هستند؛ متن‌ها به‌صورت inline و اعداد با جداکننده‌ی هزارگان ذخیره می‌شوند.
 */
public final class XlsxWriter {

    private XlsxWriter() {
    }

    /** سلول پررنگ */
    public static final class Bold {
        final Object value;

        Bold(Object value) {
            this.value = value;
        }
    }

    public static Bold bold(Object v) {
        return new Bold(v);
    }

    public static final class Sheet {
        public final String name;
        public final List<Object[]> rows = new ArrayList<>();
        public double[] widths;
        /** اندیس ردیف عنوان ستون‌ها (۰ = ردیف اول) یا -1 */
        public int headerRow = -1;

        public Sheet(String name) {
            this.name = name;
        }

        public Sheet add(Object... cells) {
            rows.add(cells);
            return this;
        }
    }

    // استایل‌ها: 0 معمولی، 1 عنوان ستون، 2 عدد، 3 متن پررنگ، 4 عدد پررنگ
    private static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
            + "<fonts count=\"2\">"
            + "<font><sz val=\"11\"/><name val=\"Tahoma\"/></font>"
            + "<font><b/><sz val=\"11\"/><name val=\"Tahoma\"/></font>"
            + "</fonts>"
            + "<fills count=\"3\">"
            + "<fill><patternFill patternType=\"none\"/></fill>"
            + "<fill><patternFill patternType=\"gray125\"/></fill>"
            + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FFDDE6F5\"/><bgColor indexed=\"64\"/></patternFill></fill>"
            + "</fills>"
            + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
            + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
            + "<cellXfs count=\"5\">"
            + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\" applyAlignment=\"1\"><alignment horizontal=\"center\"/></xf>"
            + "<xf numFmtId=\"3\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\"/>"
            + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyFont=\"1\"/>"
            + "<xf numFmtId=\"3\" fontId=\"1\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyNumberFormat=\"1\" applyFont=\"1\"/>"
            + "</cellXfs>"
            + "<cellStyles count=\"1\"><cellStyle name=\"Normal\" xfId=\"0\" builtinId=\"0\"/></cellStyles>"
            + "</styleSheet>";

    public static void write(OutputStream out, List<Sheet> sheets) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(out);
        List<String> names = uniqueNames(sheets);

        StringBuilder ct = new StringBuilder();
        ct.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        ct.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        ct.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        ct.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        ct.append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>");
        ct.append("<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>");
        for (int i = 0; i < sheets.size(); i++) {
            ct.append("<Override PartName=\"/xl/worksheets/sheet").append(i + 1)
                    .append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>");
        }
        ct.append("</Types>");
        put(zip, "[Content_Types].xml", ct.toString());

        put(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>");

        StringBuilder wb = new StringBuilder();
        wb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        wb.append("<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" ")
                .append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels = new StringBuilder();
        rels.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        rels.append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        for (int i = 0; i < sheets.size(); i++) {
            wb.append("<sheet name=\"").append(esc(names.get(i))).append("\" sheetId=\"").append(i + 1)
                    .append("\" r:id=\"rId").append(i + 1).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(i + 1)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet")
                    .append(i + 1).append(".xml\"/>");
        }
        wb.append("</sheets></workbook>");
        rels.append("<Relationship Id=\"rId").append(sheets.size() + 1)
                .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>");
        rels.append("</Relationships>");
        put(zip, "xl/workbook.xml", wb.toString());
        put(zip, "xl/_rels/workbook.xml.rels", rels.toString());
        put(zip, "xl/styles.xml", STYLES);

        for (int i = 0; i < sheets.size(); i++) {
            put(zip, "xl/worksheets/sheet" + (i + 1) + ".xml", sheetXml(sheets.get(i)));
        }
        zip.finish();
        zip.flush();
    }

    private static String sheetXml(Sheet sh) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetViews><sheetView rightToLeft=\"1\" workbookViewId=\"0\"/></sheetViews>");
        if (sh.widths != null && sh.widths.length > 0) {
            sb.append("<cols>");
            for (int i = 0; i < sh.widths.length; i++) {
                sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
                        .append("\" width=\"").append(sh.widths[i]).append("\" customWidth=\"1\"/>");
            }
            sb.append("</cols>");
        }
        sb.append("<sheetData>");
        for (int r = 0; r < sh.rows.size(); r++) {
            Object[] row = sh.rows.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            if (row != null) {
                for (int c = 0; c < row.length; c++) {
                    appendCell(sb, r, c, row[c], r == sh.headerRow);
                }
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendCell(StringBuilder sb, int r, int c, Object raw, boolean header) {
        if (raw == null) return;
        boolean bold = false;
        Object v = raw;
        if (raw instanceof Bold) {
            bold = true;
            v = ((Bold) raw).value;
            if (v == null) return;
        }
        String ref = colName(c) + (r + 1);
        if (v instanceof Number) {
            double d = ((Number) v).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) return;
            int style = header ? 1 : (bold ? 4 : 2);
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\"><v>")
                    .append(new BigDecimal(d).setScale(2, java.math.RoundingMode.HALF_UP)
                            .stripTrailingZeros().toPlainString())
                    .append("</v></c>");
        } else {
            String s = clean(String.valueOf(v));
            int style = header ? 1 : (bold ? 3 : 0);
            sb.append("<c r=\"").append(ref).append("\" s=\"").append(style).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(esc(s)).append("</t></is></c>");
        }
    }

    /** A, B, ..., Z, AA, AB, ... */
    static String colName(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /** نام برگه‌ها: بدون نویسه‌های ممنوع، حداکثر ۳۱ حرف و یکتا */
    private static List<String> uniqueNames(List<Sheet> sheets) {
        List<String> out = new ArrayList<>();
        Set<String> used = new HashSet<>();
        for (int i = 0; i < sheets.size(); i++) {
            String n = sheets.get(i).name == null ? "" : sheets.get(i).name;
            n = n.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
            if (n.isEmpty()) n = "Sheet" + (i + 1);
            if (n.length() > 31) n = n.substring(0, 31);
            String base = n;
            int k = 2;
            while (used.contains(n.toLowerCase())) {
                String suffix = " (" + k++ + ")";
                n = base.substring(0, Math.min(base.length(), 31 - suffix.length())) + suffix;
            }
            used.add(n.toLowerCase());
            out.add(n);
        }
        return out;
    }

    /** حذف نویسه‌های کنترلی غیرمجاز در XML */
    private static String clean(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '\t' || ch == '\n' || ch == '\r' || ch >= 0x20) sb.append(ch);
        }
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
