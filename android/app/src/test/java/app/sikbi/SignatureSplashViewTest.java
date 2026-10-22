package app.sikbi;

import static org.junit.Assert.*;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;
import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class SignatureSplashViewTest {
    @Test
    public void rendersAdaptiveGradientAndBrandOnPhoneAndTablet() throws Exception {
        for (int[] size : new int[][] { {320, 640}, {390, 844}, {1080, 2400}, {1200, 800} }) {
            SignatureSplashView view = new SignatureSplashView(RuntimeEnvironment.getApplication());
            view.layout(0, 0, size[0], size[1]);
            Bitmap bitmap = Bitmap.createBitmap(size[0], size[1], Bitmap.Config.ARGB_8888);
            view.draw(new Canvas(bitmap));
            int top = bitmap.getPixel(2, 2);
            int bottom = bitmap.getPixel(2, size[1] - 2);
            assertEquals(255, Color.alpha(top));
            assertTrue(Color.blue(bottom) > Color.blue(top) + 70);
            assertTrue(Color.blue(top) < 45);
            int whitePixels = 0;
            for (int y = size[1] / 3; y < size[1] * 2 / 3; y++) {
                for (int x = size[0] / 4; x < size[0] * 3 / 4; x++) {
                    if (Color.red(bitmap.getPixel(x, y)) > 240) whitePixels++;
                }
            }
            assertTrue("Receipt must remain visible at every aspect ratio", whitePixels > 50);
            File directory = new File("build/reports/splash");
            assertTrue(directory.isDirectory() || directory.mkdirs());
            try (FileOutputStream out = new FileOutputStream(new File(directory, "signature-" + size[0] + "x" + size[1] + ".png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
        }
    }

    @Test
    public void unsupportedSplashProviderDoesNotBlockTheRequestedLink() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        SignatureSplashStrategy splash = new SignatureSplashStrategy(activity);
        Uri invitation = Uri.parse("https://sikbi.app/join?code=TEST-CODE");
        TrustedWebActivityIntentBuilder builder = new TrustedWebActivityIntentBuilder(invitation);
        AtomicInteger ready = new AtomicInteger();
        splash.configureTwaBuilder(builder, null, ready::incrementAndGet);
        assertEquals(1, ready.get());
        assertEquals(invitation, builder.getUri());
        splash.destroy();
        activity.finish();
    }

    @Test
    public void manifestKeepsMainEntryAndVerifiedInvitationLinks() throws Exception {
        android.content.Context context = RuntimeEnvironment.getApplication();
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(launch);
        assertEquals(LauncherActivity.class.getName(), launch.getComponent().getClassName());
        Intent link = new Intent(Intent.ACTION_VIEW, Uri.parse("https://sikbi.app/join?code=TEST-CODE"));
        link.addCategory(Intent.CATEGORY_BROWSABLE);
        assertFalse(context.getPackageManager().queryIntentActivities(link, 0).isEmpty());
    }
}
