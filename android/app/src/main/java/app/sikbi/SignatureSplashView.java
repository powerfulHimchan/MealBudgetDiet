package app.sikbi;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Locally rendered artwork, available before any network request. */
public final class SignatureSplashView extends View {
    static final int TOP = Color.rgb(8, 15, 35);
    static final int MIDDLE = Color.rgb(19, 44, 86);
    static final int BOTTOM = Color.rgb(36, 88, 140);
    private final Paint background = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable mark;

    public SignatureSplashView(Context context) {
        super(context);
        mark = context.getDrawable(R.drawable.signature_mark);
        setContentDescription(context.getString(R.string.appName));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        background.setShader(new LinearGradient(0, 0, width * .15f, height,
                new int[] { TOP, MIDDLE, BOTTOM }, new float[] { 0, .52f, 1 }, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth(), height = getHeight();
        canvas.drawRect(0, 0, width, height, background);
        float density = getResources().getDisplayMetrics().density;
        float fontScale = Math.min(getResources().getConfiguration().fontScale, 1.3f);
        int size = Math.round(Math.min(width * .56f, Math.min(height * .32f, 240 * density)));
        int left = (width - size) / 2;
        int top = Math.round(height * .49f - size / 2f);
        mark.setBounds(left, top, left + size, top + size);
        mark.draw(canvas);
        float brandBaseline = Math.min(height * .89f, height - 60 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(Color.WHITE);
        text.setTextSize(30 * density * fontScale);
        canvas.drawText(getResources().getString(R.string.splash_brand), width / 2f, brandBaseline, text);
        text.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        text.setTextSize(14 * density * fontScale);
        text.setColor(Color.rgb(190, 210, 234));
        canvas.drawText(getResources().getString(R.string.splash_tagline), width / 2f,
                brandBaseline + 27 * density * fontScale, text);
    }
}
