package com.barber.admin;

import java.util.List;

/** ساخت محتوای سندها (بدون وابستگی به اندروید). تاریخ‌ها و شماره‌ها از بیرون داده می‌شوند. */
public final class Docs {

    private static final String BLANK = "........................";

    private Docs() {
    }

    private static String or(String v) {
        return (v == null || v.trim().isEmpty()) ? BLANK : v.trim();
    }

    private static String durationText(int type) {
        int m = LicenseCodec.monthsForType(type);
        if (m == 6) return "۶ ماه";
        if (m % 12 == 0) return Fa.digits(String.valueOf(m / 12)) + " سال";
        return Fa.digits(String.valueOf(m)) + " ماه";
    }

    private static Doc base(Seller s, String title, String docNo, String date) {
        Doc d = new Doc();
        d.title = title;
        d.docNo = docNo;
        d.dateJalali = date;
        d.sellerName = s.name;
        d.brand = s.brand;
        return d;
    }

    private static String[][] sellerRows(Seller s) {
        return new String[][]{
                {"نام فروشنده", or(s.name)},
                {"کد/شناسه ملی", or(s.nationalId)},
                {"کد اقتصادی", or(s.economicCode)},
                {"تلفن", or(s.phone)},
                {"نشانی", or(s.address)}
        };
    }

    private static String[][] buyerRows(Models.Customer c) {
        return new String[][]{
                {"نام آرایشگاه", or(c.shopName)},
                {"نام مالک", or(c.ownerName)},
                {"کد ملی", or(c.nationalId)},
                {"کد اقتصادی", or(c.economicCode)},
                {"موبایل / تلفن", or(c.mobile.isEmpty() ? c.phone : c.mobile)},
                {"شهر و نشانی", or((c.city.isEmpty() ? "" : c.city + "، ") + c.address)},
                {"کد پستی", or(c.postalCode)}
        };
    }

    private static final String[] PRICE_COLS = {"ردیف", "نوع لایسنس", "مدت", "قیمت", "تخفیف", "قیمت نهایی"};
    private static final float[] PRICE_W = {0.8f, 3f, 1.6f, 2.4f, 1.4f, 2.4f};
    private static final int[] PRICE_A = {Doc.ALIGN_CENTER, Doc.ALIGN_TEXT, Doc.ALIGN_CENTER, Doc.ALIGN_NUM,
            Doc.ALIGN_CENTER, Doc.ALIGN_NUM};

    private static String[][] priceRows(List<Models.PriceRow> prices) {
        String[][] rows = new String[prices.size()][];
        for (int i = 0; i < prices.size(); i++) {
            Models.PriceRow p = prices.get(i);
            long fin = Pricing.finalPrice(p.price, p.discountPercent);
            rows[i] = new String[]{Fa.digits(String.valueOf(i + 1)), Labels.licenseType(p.type),
                    durationText(p.type), Fa.num(p.price),
                    p.discountPercent > 0 ? Fa.percent(p.discountPercent) : "—", Fa.num(fin)};
        }
        return rows;
    }

    // ================= لیست قیمت =================

    public static Doc priceList(Seller s, List<Models.PriceRow> prices, String date) {
        Doc d = base(s, "لیست قیمت لایسنس نرم‌افزار", "", date);
        d.para("قیمت‌ها به تومان است و برای هر دستگاه (یک گوشی) محاسبه می‌شود.");
        d.table(PRICE_COLS, PRICE_W, PRICE_A, priceRows(prices), null);
        d.space(6);
        d.heading("شرایط");
        d.para("• لایسنس فقط روی یک دستگاه فعال می‌شود و بدون نیاز به اینترنت کار می‌کند.");
        d.para("• تمدید لایسنس روی روزهای باقی‌مانده اضافه می‌شود و چیزی از دست نمی‌رود.");
        d.para("• با تمام شدن مدت، برنامه «فقط‌خواندنی» می‌شود؛ اطلاعات و گزارش‌ها همچنان دیده می‌شوند.");
        d.para("• در صورت تعویض گوشی، با «کد جابجایی» بدون افزایش یا کاهش مدت منتقل می‌شود.");
        if (s.vatPercent > 0) d.para("• مالیات بر ارزش افزوده (" + Fa.percent(s.vatPercent) + ") به قیمت‌ها اضافه می‌شود.");
        if (!s.phone.isEmpty()) d.para("• تماس و پشتیبانی: " + Fa.digits(s.phone));
        return d;
    }

    // ================= پیش‌فاکتور =================

    /**
     * @param base      قیمت پایه
     * @param discount  درصد تخفیف
     * @param validDays مدت اعتبار پیش‌فاکتور (روز)
     */
    public static Doc proforma(Seller s, Models.Customer c, int type, long base, double discount,
                               int validDays, String docNo, String date) {
        Doc d = base(s, "پیش‌فاکتور فروش", docNo, date);
        d.heading("مشخصات فروشنده");
        d.kv(sellerRows(s));
        d.heading("مشخصات خریدار");
        d.kv(buyerRows(c));

        long fin = Pricing.finalPrice(base, discount);
        long disc = Math.max(0, base) - fin;
        long vat = s.vatPercent > 0 ? Math.round(fin * s.vatPercent / 100.0) : 0;
        long total = fin + vat;

        d.heading("شرح کالا / خدمت");
        d.table(new String[]{"ردیف", "شرح", "مدت", "قیمت", "تخفیف", "مبلغ"},
                new float[]{0.8f, 4f, 1.6f, 2.4f, 2.2f, 2.4f},
                new int[]{Doc.ALIGN_CENTER, Doc.ALIGN_TEXT, Doc.ALIGN_CENTER, Doc.ALIGN_NUM, Doc.ALIGN_NUM,
                        Doc.ALIGN_NUM},
                new String[][]{{"۱", "حق استفاده از نرم‌افزار مدیریت آرایشگاه (" + Labels.licenseType(type) + "، یک دستگاه)",
                        durationText(type), Fa.num(base), disc > 0 ? Fa.num(disc) : "—", Fa.num(fin)}},
                null);

        String[][] sum;
        if (vat > 0) {
            sum = new String[][]{
                    {"مبلغ پس از تخفیف", Fa.money(fin)},
                    {"مالیات بر ارزش افزوده (" + Fa.percent(s.vatPercent) + ")", Fa.money(vat)},
                    {"مبلغ قابل پرداخت", Fa.money(total)},
                    {"به حروف", Fa.moneyWords(total)}};
        } else {
            sum = new String[][]{
                    {"مبلغ قابل پرداخت", Fa.money(total)},
                    {"به حروف", Fa.moneyWords(total)}};
        }
        d.kv(sum);

        d.heading("شرایط");
        d.para("• این پیش‌فاکتور به مدت " + Fa.digits(String.valueOf(validDays)) + " روز از تاریخ صدور معتبر است.");
        d.para("• کد فعال‌سازی پس از دریافت وجه (یا طبق شرایط اقساط توافق‌شده) تحویل می‌شود.");
        d.para("• لایسنس فقط روی یک دستگاه فعال می‌شود؛ تعویض گوشی با کد جابجایی امکان‌پذیر است.");
        if (!s.bank.isEmpty()) d.para("• اطلاعات پرداخت: " + Fa.digits(s.bank));
        d.sign("مهر و امضای فروشنده" + (s.signer.isEmpty() ? "" : " (" + s.signer + ")"), "تأیید خریدار");
        return d;
    }

    // ================= قرارداد =================

    public static Doc contract(Seller s, Models.Customer c, int type, long base, double discount,
                               List<Models.PriceRow> prices, String docNo, String date) {
        Doc d = base(s, "قرارداد استفاده از نرم‌افزار مدیریت آرایشگاه", docNo, date);
        long fin = Pricing.finalPrice(base, discount);
        String dur = durationText(type);

        d.heading("طرفین قرارداد");
        d.para("فروشنده:");
        d.kv(sellerRows(s));
        d.para("خریدار:");
        d.kv(buyerRows(c));
        d.para("که در این قرارداد به ترتیب «فروشنده» و «خریدار» نامیده می‌شوند.");

        d.clause("ماده ۱ – موضوع قرارداد",
                "واگذاری حق استفاده‌ی غیرانحصاری و غیرقابل انتقال از نرم‌افزار «مدیریت آرایشگاه» (نسخه‌ی اندروید) "
                        + "برای یک دستگاه، به‌صورت لایسنس زمان‌دار (" + Labels.licenseType(type) + ") و بر پایه‌ی شرایط این قرارداد.");
        d.clause("ماده ۲ – مدت قرارداد",
                "مدت اعتبار لایسنس " + dur + " است و از روز فعال‌سازی کد روی دستگاه آغاز می‌شود. "
                        + "کد فعال‌سازی باید حداکثر ۳۰ روز پس از تاریخ صدور وارد شود؛ پس از آن کد باطل است و باید کد جدید گرفته شود. "
                        + "در صورت تمدید، مدت جدید به روزهای باقی‌مانده اضافه می‌شود.");
        d.clause("ماده ۳ – بهای قرارداد و نحوه‌ی پرداخت",
                "قیمت پایه " + Fa.money(base)
                        + (discount > 0 ? "، تخفیف " + Fa.percent(discount) : "")
                        + " و مبلغ نهایی " + Fa.money(fin) + " (" + Fa.moneyWords(fin) + ")"
                        + (s.vatPercent > 0 ? " بدون احتساب مالیات بر ارزش افزوده (" + Fa.percent(s.vatPercent) + ")" : "")
                        + " است. نحوه‌ی پرداخت: نقدی (   )   اقساطی (   )   (توضیح اقساط: ................................................). "
                        + "کد فعال‌سازی پس از دریافت مبلغ یا طبق جدول اقساط توافق‌شده تحویل خریدار می‌شود.");
        d.clause("ماده ۴ – شرایط لایسنس",
                "الف) لایسنس به شناسه‌ی یک دستگاه گره می‌خورد و روی دستگاه دیگر کار نمی‌کند. "
                        + "ب) در صورت تعویض گوشی، فروشنده «کد جابجایی» می‌دهد و تاریخ پایان لایسنس تغییر نمی‌کند. "
                        + "پ) نرم‌افزار بدون اینترنت کار می‌کند و اطلاعات فقط روی دستگاه خریدار ذخیره می‌شود. "
                        + "ت) با پایان مدت، نرم‌افزار «فقط‌خواندنی» می‌شود: مشاهده‌ی اطلاعات و گزارش‌ها آزاد است ولی "
                        + "ثبت، ویرایش و حذف تا تمدید لایسنس متوقف می‌ماند.");
        d.clause("ماده ۵ – تعهدات فروشنده",
                "تحویل کد فعال‌سازی در موعد مقرر، پشتیبانی فنی از طریق تلفن یا پیام"
                        + (s.phone.isEmpty() ? "" : " (" + Fa.digits(s.phone) + ")")
                        + " در ساعات کاری، رفع ایرادهای نرم‌افزاری، و ارائه‌ی نسخه‌های اصلاحی در مدت اعتبار لایسنس در صورت انتشار.");
        d.clause("ماده ۶ – تعهدات خریدار",
                "استفاده از نرم‌افزار تنها برای کسب‌وکار خود؛ خودداری از کپی، فروش، اجاره، واگذاری یا انتشار کد و نسخه‌ی نرم‌افزار، "
                        + "و مهندسی معکوس یا دستکاری سازوکار لایسنس؛ نگهداری از رمز برنامه؛ و تهیه‌ی منظم نسخه‌ی پشتیبان از اطلاعات. "
                        + "از بین رفتن اطلاعات در اثر حذف برنامه، خرابی یا گم شدن گوشی و نبودن پشتیبان بر عهده‌ی خریدار است.");
        d.clause("ماده ۷ – مالکیت فکری و اطلاعات",
                "مالکیت نرم‌افزار و حقوق مرتبط با آن متعلق به فروشنده است و این قرارداد فقط حق استفاده می‌دهد. "
                        + "اطلاعات ثبت‌شده در نرم‌افزار (مشتریان، نوبت‌ها، مبالغ) متعلق به خریدار است و فروشنده به آن‌ها دسترسی ندارد.");
        d.clause("ماده ۸ – محدودیت مسئولیت",
                "نرم‌افزار ابزار کمکی مدیریت است و مسئولیت فروشنده در هر حال محدود به مبلغ دریافتی بابت همین قرارداد است. "
                        + "فروشنده مسئول خسارت‌های غیرمستقیم یا ناشی از استفاده‌ی نادرست خریدار نیست.");
        d.clause("ماده ۹ – استرداد وجه",
                "پس از تحویل کد فعال‌سازی، وجه قابل استرداد نیست؛ مگر آن‌که فعال‌سازی به‌دلیل نقص از سوی فروشنده ممکن نشود "
                        + "که در این صورت فروشنده کد جایگزین می‌دهد یا وجه را بازمی‌گرداند.");
        d.clause("ماده ۱۰ – فسخ",
                "در صورت نقض مواد ۴ و ۶ از سوی خریدار، فروشنده می‌تواند پس از یک اخطار کتبی یا پیامکی ۷ روزه، "
                        + "قرارداد را فسخ کند و از صدور کد جدید یا تمدید خودداری نماید.");
        d.clause("ماده ۱۱ – حل اختلاف",
                "اختلاف‌ها ابتدا از راه مذاکره حل می‌شود و در صورت عدم توافق، به مراجع قضایی صالح در شهر ........................ ارجاع می‌گردد.");
        d.clause("ماده ۱۲ – نسخه‌ها و ابلاغ",
                "این قرارداد در ۱۲ ماده و دو نسخه‌ی هم‌اعتبار تنظیم شده است. نشانی و شماره‌ی تماس طرفین همان است که در ابتدای قرارداد آمده "
                        + "و تغییر آن باید کتباً (یا پیامکی) به طرف مقابل اعلام شود. هرگونه تغییر در قرارداد با توافق کتبی طرفین معتبر است.");

        d.heading("پیوست: لیست قیمت لایسنس‌ها");
        d.table(PRICE_COLS, PRICE_W, PRICE_A, priceRows(prices), null);
        d.sign("فروشنده – مهر و امضا" + (s.signer.isEmpty() ? "" : " (" + s.signer + ")"), "خریدار – امضا");
        return d;
    }
}
