package com.barber.admin;

import java.util.ArrayList;
import java.util.List;

/** مدل ساده‌ی سند (پیش‌فاکتور/قرارداد/لیست قیمت) که بعداً به PDF تبدیل می‌شود. */
public final class Doc {

    public static final int H = 0;      // عنوان بخش
    public static final int P = 1;      // پاراگراف
    public static final int CLAUSE = 2; // ماده: a = عنوان، b = متن
    public static final int KV = 3;     // جدول دوستونه‌ی مشخصات: rows
    public static final int TABLE = 4;  // جدول: cols, rows, footer
    public static final int SIGN = 5;   // محل امضا و مهر: labels
    public static final int SPACE = 6;
    public static final int NOTE = 7;   // یادداشت کوچک و کم‌رنگ

    public static final int ALIGN_TEXT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_NUM = 2;

    public static final class Block {
        public int type;
        public String a = "";
        public String b = "";
        public String[][] rows;      // KV: [برچسب، مقدار]؛ TABLE: ردیف‌ها
        public String[] cols;        // عنوان ستون‌ها
        public float[] weights;
        public int[] aligns;
        public String[] footer;      // ردیف جمع
        public String[] labels;      // امضاها
        public float h;
    }

    public String title = "";
    public String docNo = "";
    public String dateJalali = "";
    public String sellerName = "";
    public String brand = "";
    public final List<Block> blocks = new ArrayList<Block>();

    public Doc heading(String t) {
        Block x = new Block();
        x.type = H;
        x.a = t;
        blocks.add(x);
        return this;
    }

    public Doc para(String t) {
        Block x = new Block();
        x.type = P;
        x.a = t;
        blocks.add(x);
        return this;
    }

    public Doc note(String t) {
        Block x = new Block();
        x.type = NOTE;
        x.a = t;
        blocks.add(x);
        return this;
    }

    public Doc clause(String title, String text) {
        Block x = new Block();
        x.type = CLAUSE;
        x.a = title;
        x.b = text;
        blocks.add(x);
        return this;
    }

    public Doc kv(String[][] rows) {
        Block x = new Block();
        x.type = KV;
        x.rows = rows;
        blocks.add(x);
        return this;
    }

    public Doc table(String[] cols, float[] weights, int[] aligns, String[][] rows, String[] footer) {
        Block x = new Block();
        x.type = TABLE;
        x.cols = cols;
        x.weights = weights;
        x.aligns = aligns;
        x.rows = rows;
        x.footer = footer;
        blocks.add(x);
        return this;
    }

    public Doc sign(String... labels) {
        Block x = new Block();
        x.type = SIGN;
        x.labels = labels;
        blocks.add(x);
        return this;
    }

    public Doc space(float h) {
        Block x = new Block();
        x.type = SPACE;
        x.h = h;
        blocks.add(x);
        return this;
    }

    /** همه‌ی متن سند (برای تست و بازبینی) */
    public String dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(title).append(" | ").append(docNo).append(" | ").append(dateJalali).append('\n');
        for (Block x : blocks) {
            switch (x.type) {
                case H:
                    sb.append("\n## ").append(x.a).append('\n');
                    break;
                case P:
                case NOTE:
                    sb.append(x.a).append('\n');
                    break;
                case CLAUSE:
                    sb.append("\n").append(x.a).append(": ").append(x.b).append('\n');
                    break;
                case KV:
                    for (String[] r : x.rows) sb.append("  ").append(r[0]).append(" = ").append(r[1]).append('\n');
                    break;
                case TABLE:
                    sb.append("  | ").append(join(x.cols)).append('\n');
                    for (String[] r : x.rows) sb.append("  | ").append(join(r)).append('\n');
                    if (x.footer != null) sb.append("  | ").append(join(x.footer)).append('\n');
                    break;
                case SIGN:
                    sb.append("  [امضا] ").append(join(x.labels)).append('\n');
                    break;
                default:
                    break;
            }
        }
        return sb.toString();
    }

    private static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) sb.append(i > 0 ? " | " : "").append(a[i]);
        return sb.toString();
    }
}
