package com.github.catvod.qrcode;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.github.catvod.spider.Init;
import com.github.catvod.utils.NotifyToast;

public class QrDialog {

    private static volatile Dialog sActiveDialog;

    public interface Poller {
        /**
         * Return:
         * "SUCCESS" -> authenticated
         * "SCANED" -> scanned on phone, waiting confirmation
         * "WAITING" -> not scanned yet
         * "EXPIRED" -> expired
         */
        String check();
        void onSuccess();
    }

    public static void dismiss() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            dismissInternal(false);
        } else {
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    dismissInternal(false);
                }
            });
        }
    }

    private static void dismissInternal(boolean suppressListener) {
        try {
            if (sActiveDialog != null && sActiveDialog.isShowing()) {
                if (suppressListener) {
                    sActiveDialog.setOnDismissListener(null);
                }
                sActiveDialog.dismiss();
            }
        } catch (Throwable ignored) {}
        sActiveDialog = null;
    }

    public static void show(final String title, final String subtitle, final Bitmap qrBitmap, final String initialStatus, final Poller poller) {
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                try {
                    dismissInternal(true);
                    Activity act = Init.getActivity();
                    if (act == null || act.isFinishing()) {
                        NotifyToast.show(title + "\n" + subtitle);
                        return;
                    }

                    final Dialog dialog = new Dialog(act);
                    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
                    Window window = dialog.getWindow();
                    if (window != null) {
                        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                        window.setDimAmount(0.75f);
                    }

                    LinearLayout root = new LinearLayout(act);
                    root.setOrientation(LinearLayout.VERTICAL);
                    root.setGravity(Gravity.CENTER_HORIZONTAL);

                    GradientDrawable bg = new GradientDrawable();
                    bg.setColor(0xFF1B1E23); // Deep modern dark
                    bg.setCornerRadius(dp(act, 18));
                    bg.setStroke(dp(act, 2), 0xFF3B82F6); // Soft blue border
                    root.setBackground(bg);
                    int padH = dp(act, 36);
                    int padV = dp(act, 28);
                    root.setPadding(padH, padV, padH, padV);

                    // 1. Title
                    TextView titleTv = new TextView(act);
                    titleTv.setText(title);
                    titleTv.setTextColor(0xFFFFFFFF);
                    titleTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    titleTv.setTypeface(Typeface.DEFAULT_BOLD);
                    titleTv.setGravity(Gravity.CENTER);
                    root.addView(titleTv);

                    // 2. Subtitle
                    TextView subTv = new TextView(act);
                    subTv.setText(subtitle);
                    subTv.setTextColor(0xFF9CA3AF);
                    subTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    subTv.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    subLp.topMargin = dp(act, 6);
                    subLp.bottomMargin = dp(act, 16);
                    root.addView(subTv, subLp);

                    // 3. QR Container (white card background for high contrast scanning)
                    FrameLayout qrFrame = new FrameLayout(act);
                    GradientDrawable whiteCard = new GradientDrawable();
                    whiteCard.setColor(0xFFFFFFFF);
                    whiteCard.setCornerRadius(dp(act, 12));
                    qrFrame.setBackground(whiteCard);
                    int framePad = dp(act, 10);
                    qrFrame.setPadding(framePad, framePad, framePad, framePad);

                    ImageView qrIv = new ImageView(act);
                    int qrSize = dp(act, 240);
                    FrameLayout.LayoutParams qrLp = new FrameLayout.LayoutParams(qrSize, qrSize);
                    qrIv.setLayoutParams(qrLp);
                    qrIv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    if (qrBitmap != null) {
                        qrIv.setImageBitmap(qrBitmap);
                    }
                    qrFrame.addView(qrIv);
                    root.addView(qrFrame);

                    // 4. Status Text
                    final TextView statusTv = new TextView(act);
                    statusTv.setText(initialStatus != null ? initialStatus : "⏳ 正在等待手机扫码...");
                    statusTv.setTextColor(0xFFFBBF24); // Amber
                    statusTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    statusTv.setTypeface(Typeface.DEFAULT_BOLD);
                    statusTv.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams statLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    statLp.topMargin = dp(act, 16);
                    root.addView(statusTv, statLp);

                    // 5. Bottom Hint
                    TextView hintTv = new TextView(act);
                    hintTv.setText("手机确认后将自动关闭窗口并生效\n按遥控器【返回键】关闭");
                    hintTv.setTextColor(0xFF6B7280);
                    hintTv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                    hintTv.setGravity(Gravity.CENTER);
                    LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    hintLp.topMargin = dp(act, 12);
                    root.addView(hintTv, hintLp);

                    dialog.setContentView(root);
                    dialog.setCancelable(true);
                    dialog.setCanceledOnTouchOutside(false);

                    final boolean[] isRunning = new boolean[]{true};

                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface d) {
                            isRunning[0] = false;
                            sActiveDialog = null;
                            try {
                                Class<?> refreshClz = Class.forName("com.fongmi.android.tv.event.RefreshEvent");
                                refreshClz.getMethod("home").invoke(null);
                                refreshClz.getMethod("category").invoke(null);
                            } catch (Throwable ignored) {}
                        }
                    });

                    dialog.show();
                    sActiveDialog = dialog;

                    // Background Poller Thread
                    if (poller != null) {
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                int attempts = 0;
                                while (isRunning[0] && attempts < 150) { // Poll for up to ~4 minutes
                                    try {
                                        Thread.sleep(1500);
                                    } catch (InterruptedException e) {
                                        break;
                                    }
                                    if (!isRunning[0]) break;

                                    attempts++;
                                    try {
                                        final String res = poller.check();
                                        if ("SUCCESS".equals(res)) {
                                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        statusTv.setText("🎉 授权成功！正在保存配置...");
                                                        statusTv.setTextColor(0xFF10B981); // Emerald green
                                                    } catch (Throwable ignored) {}
                                                }
                                            });
                                            poller.onSuccess();
                                            try {
                                                Thread.sleep(1200);
                                            } catch (Exception ignored) {}
                                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        if (dialog.isShowing()) dialog.dismiss();
                                                    } catch (Throwable ignored) {}
                                                }
                                            });
                                            break;
                                        } else if ("SCANED".equals(res)) {
                                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        statusTv.setText("📱 手机已扫码！请在手机端点击【确认登录】");
                                                        statusTv.setTextColor(0xFF3B82F6);
                                                    } catch (Throwable ignored) {}
                                                }
                                            });
                                        } else if ("EXPIRED".equals(res)) {
                                            new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                @Override
                                                public void run() {
                                                    try {
                                                        statusTv.setText("⚠️ 二维码已失效，请按返回键重新打开");
                                                        statusTv.setTextColor(0xFFEF4444);
                                                    } catch (Throwable ignored) {}
                                                }
                                            });
                                            break;
                                        }
                                    } catch (Throwable t) {
                                        t.printStackTrace();
                                    }
                                }
                            }
                        }).start();
                    }

                } catch (Throwable t) {
                    t.printStackTrace();
                    NotifyToast.show("打开配置窗口异常: " + t.getMessage());
                }
            }
        });
    }

    private static int dp(Activity act, int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.getResources().getDisplayMetrics());
    }
}
