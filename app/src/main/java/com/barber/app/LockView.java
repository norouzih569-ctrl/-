package com.barber.app;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** صفحه‌ی قفل تمام‌صفحه با صفحه‌کلید عددی و (اختیاری) اثر انگشت. روی محتوای برنامه قرار می‌گیرد. */
public class LockView extends FrameLayout {

    public interface Listener {
        void onUnlocked();
    }

    private final Activity act;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final StringBuilder entered = new StringBuilder();
    private final int pinLength;

    private TextView dots;
    private TextView message;
    private Button bioButton;
    private final java.util.List<Button> keys = new java.util.ArrayList<>();
    private CancellationSignal bioSignal;
    private boolean unlocked = false;

    private final Runnable countdown = new Runnable() {
        @Override
        public void run() {
            int left = LockManager.lockRemainingSeconds(act);
            if (left > 0) {
                message.setText("تلاش بیش از حد. " + left + " ثانیه صبر کنید");
                setKeysEnabled(false);
                handler.postDelayed(this, 500);
            } else {
                message.setText("");
                setKeysEnabled(true);
            }
        }
    };

    public LockView(Activity activity, Listener listener) {
        super(activity);
        this.act = activity;
        this.listener = listener;
        this.pinLength = LockManager.getPinLength(activity);
        build();
    }

    private int dp(float v) {
        return Ui.dp(act, v);
    }

    private void build() {
        final int bg = Ui.themeColor(act, android.R.attr.colorBackground, 0xFF121212);
        final int text = Ui.themeColor(act, android.R.attr.textColorPrimary, 0xFFFFFFFF);
        final int sub = Ui.themeColor(act, android.R.attr.textColorSecondary, 0xFF999999);
        final int accent = Ui.themeColor(act, R.attr.barberAccent, 0xFF1F5FBF);
        final int surface = Ui.themeColor(act, R.attr.barberSurface, 0xFF2A2A2A);

        setBackgroundColor(bg | 0xFF000000);
        setClickable(true);
        setFocusable(true);
        setLayoutDirection(View.LAYOUT_DIRECTION_LTR); // صفحه‌کلید عددی همیشه چپ‌به‌راست

        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(24), dp(24), dp(24), dp(24));
        addView(col, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        TextView icon = new TextView(act);
        icon.setText("🔒");
        icon.setTextSize(40);
        icon.setGravity(Gravity.CENTER);
        col.addView(icon);

        TextView title = new TextView(act);
        title.setText("مدیریت آرایشگاه");
        title.setTextSize(20);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(text);
        title.setGravity(Gravity.CENTER);
        col.addView(title);

        TextView subtitle = new TextView(act);
        subtitle.setText("رمز عبور را وارد کنید");
        subtitle.setTextColor(sub);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, dp(4), 0, dp(16));
        col.addView(subtitle);

        dots = new TextView(act);
        dots.setTextSize(28);
        dots.setTextColor(accent);
        dots.setGravity(Gravity.CENTER);
        dots.setLetterSpacing(0.3f);
        col.addView(dots);

        message = new TextView(act);
        message.setTextColor(0xFFEF5350);
        message.setGravity(Gravity.CENTER);
        message.setMinHeight(dp(28));
        message.setPadding(0, dp(6), 0, dp(10));
        col.addView(message);

        String[][] rows = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {"BIO", "0", "DEL"}};
        for (String[] r : rows) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER);
            for (final String k : r) {
                Button b = makeKey(k, text, surface);
                row.addView(b);
                if (k.equals("BIO")) {
                    bioButton = b;
                } else {
                    keys.add(b);
                }
            }
            col.addView(row);
        }

        boolean bio = LockManager.isBiometricEnabled(act);
        bioButton.setVisibility(bio ? View.VISIBLE : View.INVISIBLE);
        bioButton.setText("👆");
        bioButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                startBiometric();
            }
        });

        updateDots();
        if (LockManager.lockRemainingSeconds(act) > 0) handler.post(countdown);
        if (bio) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!unlocked && isAttachedToWindow() && LockManager.lockRemainingSeconds(act) == 0) {
                        startBiometric();
                    }
                }
            }, 400);
        }
    }

    private Button makeKey(final String k, int textColor, int surface) {
        Button b = new Button(act);
        int size = dp(76);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
        lp.setMargins(dp(8), dp(6), dp(8), dp(6));
        b.setLayoutParams(lp);
        b.setAllCaps(false);
        b.setTextSize(24);
        b.setTextColor(textColor);
        b.setPadding(0, 0, 0, 0);
        b.setStateListAnimator(null);
        if (k.equals("BIO")) {
            b.setBackgroundColor(Color.TRANSPARENT);
        } else if (k.equals("DEL")) {
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setText("⌫");
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (entered.length() > 0) {
                        entered.deleteCharAt(entered.length() - 1);
                        updateDots();
                    }
                }
            });
        } else {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(surface);
            b.setBackground(g);
            b.setText(k);
            b.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDigit(k);
                }
            });
        }
        return b;
    }

    private void setKeysEnabled(boolean on) {
        for (Button b : keys) b.setEnabled(on);
    }

    private void updateDots() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pinLength; i++) {
            sb.append(i < entered.length() ? "●" : "○");
        }
        dots.setText(sb.toString());
    }

    private void onDigit(String d) {
        if (unlocked || LockManager.lockRemainingSeconds(act) > 0) return;
        if (entered.length() >= pinLength) return;
        entered.append(d);
        message.setText("");
        updateDots();
        if (entered.length() == pinLength) {
            verify();
        }
    }

    private void verify() {
        String pin = entered.toString();
        entered.setLength(0);
        if (LockManager.checkPin(act, pin)) {
            LockManager.resetFailures(act);
            finishUnlock();
            return;
        }
        int secs = LockManager.recordFailure(act);
        updateDots();
        TranslateAnimation shake = new TranslateAnimation(0, dp(14), 0, 0);
        shake.setDuration(70);
        shake.setRepeatCount(5);
        shake.setRepeatMode(Animation.REVERSE);
        dots.startAnimation(shake);
        if (secs > 0) {
            handler.removeCallbacks(countdown);
            handler.post(countdown);
        } else {
            int left = 5 - LockManager.getFailures(act);
            message.setText(left > 0
                    ? "رمز اشتباه است (" + left + " تلاش تا قفل موقت)"
                    : "رمز اشتباه است");
        }
    }

    private void finishUnlock() {
        if (unlocked) return;
        unlocked = true;
        cancelBiometric();
        handler.removeCallbacksAndMessages(null);
        listener.onUnlocked();
    }

    // ---------------- اثر انگشت ----------------

    private void startBiometric() {
        if (Build.VERSION.SDK_INT < 29 || unlocked) return;
        if (LockManager.lockRemainingSeconds(act) > 0) return;
        cancelBiometric();
        try {
            BiometricPrompt.Builder b = new BiometricPrompt.Builder(act)
                    .setTitle("باز کردن قفل برنامه")
                    .setSubtitle("اثر انگشت یا چهره‌ی خود را تأیید کنید")
                    .setNegativeButton("استفاده از رمز", act.getMainExecutor(),
                            new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                }
                            });
            if (Build.VERSION.SDK_INT >= 30) {
                b.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager
                        .Authenticators.BIOMETRIC_WEAK);
            }
            BiometricPrompt prompt = b.build();
            bioSignal = new CancellationSignal();
            prompt.authenticate(bioSignal, act.getMainExecutor(),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            LockManager.resetFailures(act);
                            finishUnlock();
                        }
                    });
        } catch (Exception e) {
            message.setText("اثر انگشت در دسترس نیست؛ رمز را وارد کنید");
        }
    }

    private void cancelBiometric() {
        if (bioSignal != null) {
            try {
                bioSignal.cancel();
            } catch (Exception ignored) {
                // مهم نیست
            }
            bioSignal = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelBiometric();
        handler.removeCallbacksAndMessages(null);
    }
}
