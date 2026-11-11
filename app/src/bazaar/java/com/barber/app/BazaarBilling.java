package com.barber.app;

import android.content.Context;

import androidx.activity.ComponentActivity;

import java.util.List;

import ir.cafebazaar.poolakey.Connection;
import ir.cafebazaar.poolakey.ConnectionState;
import ir.cafebazaar.poolakey.Payment;
import ir.cafebazaar.poolakey.config.PaymentConfiguration;
import ir.cafebazaar.poolakey.config.SecurityCheck;
import ir.cafebazaar.poolakey.entity.PurchaseInfo;
import ir.cafebazaar.poolakey.entity.PurchaseState;
import ir.cafebazaar.poolakey.request.PurchaseRequest;
import kotlin.Unit;

/**
 * لایه‌ی پرداخت درون‌برنامه‌ای کافه‌بازار (پولکی) — فقط در نسخه‌ی «bazaar».
 * همه‌ی callbackها روی نخ اصلی صدا زده می‌شوند.
 */
public final class BazaarBilling {

    /** کلید عمومی RSA از پیشخان بازار (برای تأیید صحت پرداخت) */
    private static final String RSA_PUBLIC_KEY =
            "MIHNMA0GCSqGSIb3DQEBAQUAA4G7ADCBtwKBrwDWaTEIjssy3rp2fOfEBh4bOCjgd2VhCow7B4u/df0rO33B7/IksUKvN6h4XlAEiNwM+vjI6O1Vgo1S5aAtrtXnDFdaxwFm1+BijKsO42miNP69iA6ozgemgC/oM2TM5VJGkJ9nCSOUgHBjkR493A1AO1f1MPmFgKS0pBGFtorgkB4KfTjeer2zMqL9BpJ7dyuYJ9u7MYShhlAJ/Qa+DCq9wNFnndOsHCn0+Lm4JPsCAwEAAQ==";

    /** شناسه‌ی محصول‌های اشتراکی که در پیشخان ساخته شده‌اند */
    public static final String SKU_6M = "sub_6m";
    public static final String SKU_1Y = "sub_1y";
    /** اشتراک آزمایشی رایگان ۷ روزه (محصولی که خود بازار با فعال‌کردن «اشتراک آزمایشی» می‌سازد) */
    public static final String SKU_TRIAL = "trial_subscription";

    public interface ConnectListener {
        void onConnected();

        void onFailed(String reason);
    }

    /** نتیجه‌ی پرسش اشتراک‌ها: active = یک اشتراک معتبر (خریداری‌شده و برگشت‌نخورده) وجود دارد */
    public interface SubsListener {
        void onResult(boolean active, String productId, long purchaseTime);

        void onError(String reason);
    }

    public interface BuyListener {
        void onSuccess(String productId);

        void onCancel();

        void onError(String reason);
    }

    public interface TrialListener {
        void onResult(boolean available, int days);

        void onError(String reason);
    }

    private final Payment payment;
    private Connection connection;

    public BazaarBilling(Context ctx) {
        PaymentConfiguration cfg = new PaymentConfiguration(new SecurityCheck.Enable(RSA_PUBLIC_KEY), true);
        payment = new Payment(ctx.getApplicationContext(), cfg);
    }

    public boolean isConnected() {
        return connection != null && connection.getState() == ConnectionState.Connected.INSTANCE;
    }

    /** اتصال به بازار؛ اگر بازار نصب نباشد یا اتصال نشود onFailed صدا زده می‌شود. */
    public void connect(final ConnectListener l) {
        if (isConnected()) {
            l.onConnected();
            return;
        }
        connection = payment.connect(cb -> {
            cb.connectionSucceed(() -> {
                l.onConnected();
                return Unit.INSTANCE;
            });
            cb.connectionFailed(t -> {
                l.onFailed(msg(t));
                return Unit.INSTANCE;
            });
            cb.disconnected(() -> Unit.INSTANCE);
            return Unit.INSTANCE;
        });
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.disconnect();
            } catch (RuntimeException ignored) {
                // قبلاً قطع شده
            }
            connection = null;
        }
    }

    /** فقط اگر وصل هستیم اجرا می‌شود؛ در غیر این صورت اول وصل می‌شود. */
    private void whenConnected(final Runnable run, final ConnectListener onFail) {
        connect(new ConnectListener() {
            @Override
            public void onConnected() {
                run.run();
            }

            @Override
            public void onFailed(String reason) {
                onFail.onFailed(reason);
            }
        });
    }

    /** پرسش اشتراک‌های فعلی کاربر (کاربر باید در بازار وارد شده باشد). */
    public void querySubscriptions(final SubsListener l) {
        whenConnected(() -> payment.getSubscribedProducts(cb -> {
            cb.querySucceed(list -> {
                PurchaseInfo best = null;
                for (PurchaseInfo p : (List<PurchaseInfo>) list) {
                    if (p.getPurchaseState() != PurchaseState.PURCHASED) continue;
                    String id = p.getProductId();
                    boolean paid = SKU_6M.equals(id) || SKU_1Y.equals(id);
                    if (!paid && !SKU_TRIAL.equals(id)) continue;
                    // اشتراک پولی بر آزمایشی مقدم است؛ بین هم‌نوع‌ها جدیدترین
                    if (best == null
                            || (paid && SKU_TRIAL.equals(best.getProductId()))
                            || (paid == !SKU_TRIAL.equals(best.getProductId())
                            && p.getPurchaseTime() > best.getPurchaseTime())) best = p;
                }
                if (best == null) l.onResult(false, null, 0);
                else l.onResult(true, best.getProductId(), best.getPurchaseTime());
                return Unit.INSTANCE;
            });
            cb.queryFailed(t -> {
                l.onError(msg(t));
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        }), new ConnectListener() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onFailed(String reason) {
                l.onError(reason);
            }
        });
    }

    /** باز کردن صفحه‌ی پرداخت بازار برای خرید اشتراک (sku یکی از SKU_6M / SKU_1Y). */
    public void buy(final ComponentActivity act, final String sku, final BuyListener l) {
        whenConnected(() -> payment.subscribeProduct(act.getActivityResultRegistry(),
                new PurchaseRequest(sku, null, null), cb -> {
                    cb.purchaseSucceed(info -> {
                        l.onSuccess(info.getProductId());
                        return Unit.INSTANCE;
                    });
                    cb.purchaseCanceled(() -> {
                        l.onCancel();
                        return Unit.INSTANCE;
                    });
                    cb.purchaseFailed(t -> {
                        l.onError(msg(t));
                        return Unit.INSTANCE;
                    });
                    cb.failedToBeginFlow(t -> {
                        l.onError(msg(t));
                        return Unit.INSTANCE;
                    });
                    cb.purchaseFlowBegan(() -> Unit.INSTANCE);
                    return Unit.INSTANCE;
                }), new ConnectListener() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onFailed(String reason) {
                l.onError(reason);
            }
        });
    }

    /** آیا اشتراک آزمایشی رایگان بازار برای این کاربر در دسترس است و چند روز؟ */
    public void checkTrial(final TrialListener l) {
        whenConnected(() -> payment.checkTrialSubscription(cb -> {
            cb.checkTrialSubscriptionSucceed(info -> {
                l.onResult(info.isAvailable(), info.getTrialPeriodDays());
                return Unit.INSTANCE;
            });
            cb.checkTrialSubscriptionFailed(t -> {
                l.onError(msg(t));
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        }), new ConnectListener() {
            @Override
            public void onConnected() {
            }

            @Override
            public void onFailed(String reason) {
                l.onError(reason);
            }
        });
    }

    private static String msg(Throwable t) {
        String m = t == null ? null : t.getMessage();
        return (m == null || m.isEmpty()) ? "خطای نامشخص" : m;
    }
}
