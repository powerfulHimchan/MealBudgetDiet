package app.sikbi;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.customtabs.TrustedWebUtils;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;
import androidx.browser.trusted.splashscreens.SplashScreenParamKey;
import androidx.browser.trusted.splashscreens.SplashScreenVersion;
import com.google.androidbrowserhelper.trusted.splashscreens.SplashScreenStrategy;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Transfers the same responsive native artwork to Chrome until web content is ready. */
final class SignatureSplashStrategy implements SplashScreenStrategy {
    private final Activity activity;
    private final SignatureSplashView view;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean ready = new AtomicBoolean();
    private String provider;
    private boolean supported;
    private boolean destroyed;

    SignatureSplashStrategy(Activity activity) {
        this.activity = activity;
        view = new SignatureSplashView(activity);
        activity.getWindow().setStatusBarColor(SignatureSplashView.TOP);
        activity.getWindow().setNavigationBarColor(SignatureSplashView.BOTTOM);
        activity.setContentView(view);
    }

    @Override
    public void onTwaLaunchInitiated(String provider, TrustedWebActivityIntentBuilder builder) {
        this.provider = provider;
        supported = TrustedWebUtils.areSplashScreensSupported(activity, provider, SplashScreenVersion.V1);
    }

    @Override
    public void configureTwaBuilder(TrustedWebActivityIntentBuilder builder, CustomTabsSession session,
            Runnable onReady) {
        if (!supported) {
            onReady.run();
            return;
        }
        // Provider/file failures must not leave users stuck on the intro.
        Runnable finish = () -> {
            if (!destroyed && !activity.isFinishing() && ready.compareAndSet(false, true)) onReady.run();
        };
        handler.postDelayed(finish, 2000);
        if (view.getWidth() > 0 && view.getHeight() > 0) {
            transfer(builder, session, finish);
        } else {
            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                        int oldL, int oldT, int oldR, int oldB) {
                    if (v.getWidth() == 0 || v.getHeight() == 0) return;
                    v.removeOnLayoutChangeListener(this);
                    if (!destroyed && !ready.get()) transfer(builder, session, finish);
                }
            });
        }
    }

    private void transfer(TrustedWebActivityIntentBuilder builder, CustomTabsSession session, Runnable finish) {
        // Bound bitmap memory on high resolution devices, preserving layout proportions.
        float scale = Math.min(1f, 1920f / Math.max(view.getWidth(), view.getHeight()));
        Bitmap bitmap = Bitmap.createBitmap(Math.max(1, Math.round(view.getWidth() * scale)),
                Math.max(1, Math.round(view.getHeight() * scale)), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(scale, scale);
        view.draw(canvas);
        worker.execute(() -> {
            boolean transferred = false;
            try {
                File directory = new File(activity.getFilesDir(), "twa_splash");
                if (!directory.isDirectory() && !directory.mkdirs()) throw new java.io.IOException("Splash directory");
                File file = new File(directory, "signature.png");
                try (FileOutputStream output = new FileOutputStream(file)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new java.io.IOException("Splash bitmap");
                }
                if (!Thread.currentThread().isInterrupted()) {
                    transferred = TrustedWebUtils.transferSplashImage(activity, file,
                            activity.getString(R.string.providerAuthority), provider, session);
                }
            } catch (Exception ignored) {
                // Browser-helper's normal fallback still opens the original URL.
            } finally {
                bitmap.recycle();
            }
            final boolean success = transferred;
            handler.post(() -> {
                if (destroyed || ready.get()) return;
                if (success) {
                    Bundle params = new Bundle();
                    params.putString(SplashScreenParamKey.KEY_VERSION, SplashScreenVersion.V1);
                    params.putInt(SplashScreenParamKey.KEY_BACKGROUND_COLOR, SignatureSplashView.TOP);
                    params.putInt(SplashScreenParamKey.KEY_SCALE_TYPE, ImageView.ScaleType.CENTER_CROP.ordinal());
                    params.putInt(SplashScreenParamKey.KEY_FADE_OUT_DURATION_MS, 200);
                    builder.setSplashScreenParams(params);
                }
                finish.run();
            });
        });
    }

    void destroy() {
        destroyed = true;
        ready.set(true);
        handler.removeCallbacksAndMessages(null);
        worker.shutdownNow();
    }
}
