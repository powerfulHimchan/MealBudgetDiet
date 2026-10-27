package app.sikbi;

import static org.junit.Assert.*;
import android.app.Activity;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.RemoteViews;
import java.io.File;
import java.io.FileOutputStream;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BudgetWidgetTest {
    @Test public void showsOverspendAndTodayWithoutLeakingOtherFields() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JSONObject summary = new JSONObject("{\"remaining\":-12500,\"todaySpent\":32000,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}");
        View view = BudgetWidgetProvider.views(activity, summary, "").apply(activity, new FrameLayout(activity));
        assertEquals("-12,500원", ((TextView)view.findViewById(R.id.widget_remaining)).getText().toString());
        assertEquals("32,000원", ((TextView)view.findViewById(R.id.widget_today)).getText().toString());
        assertEquals(View.GONE, view.findViewById(R.id.widget_connect).getVisibility());
        assertEquals(0xFFFDA4AF, ((TextView)view.findViewById(R.id.widget_remaining)).getCurrentTextColor());
        assertEquals("10.01 – 10.31", ((TextView)view.findViewById(R.id.widget_period)).getText().toString());
        render(view, "connected", 340, 160);
    }
    @Test public void logoutAndNetworkFailureEraseBothAmountsAndKeepRecordButton() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JSONObject data = new JSONObject("{\"remaining\":115000,\"todaySpent\":0}");
        View view = BudgetWidgetProvider.views(activity, data, "").apply(activity, new FrameLayout(activity));
        BudgetWidgetProvider.views(activity, null, activity.getString(R.string.widget_offline)).reapply(activity, view);
        assertEquals("—", ((TextView)view.findViewById(R.id.widget_remaining)).getText().toString());
        assertEquals("—", ((TextView)view.findViewById(R.id.widget_today)).getText().toString());
        assertEquals(View.VISIBLE, view.findViewById(R.id.widget_connect).getVisibility());
        view.findViewById(R.id.widget_record).performClick();
        android.content.Intent launched = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals("https://sikbi.app/expenses?new=1", launched.getDataString());
        render(view, "offline", 280, 160);
        BudgetWidgetProvider.views(activity, null, activity.getString(R.string.widget_login)).reapply(activity, view);
        render(view, "disconnected", 340, 160);
    }
    @Test public void callbackChallengeUsesSha256AndCannotBeGuessedFromUrl() throws Exception {
        String verifier = WidgetSecrets.newVerifier();
        assertEquals(43, verifier.length());
        assertTrue(WidgetSecrets.challenge(verifier).matches("[a-f0-9]{64}"));
        assertNotEquals(WidgetSecrets.challenge(verifier), WidgetSecrets.challenge(WidgetSecrets.newVerifier()));
        assertNotEquals(verifier, WidgetSecrets.challenge(verifier));
    }
    @Test public void bothSizesOpenDashboardFromAmountsLabelsAndGroupsAndKeepRecordSeparate() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JSONObject data = new JSONObject("{\"remaining\":315000,\"todaySpent\":18500,\"period\":{\"from\":\"2026-10-01\",\"to\":\"2026-10-31\"}}");
        for (int layout : new int[] { R.layout.budget_widget, R.layout.budget_widget_compact }) {
            View view = BudgetWidgetProvider.views(activity, data, "", layout).apply(activity, new FrameLayout(activity));
            for (int id : new int[] { R.id.widget_remaining_group, R.id.widget_remaining_label, R.id.widget_remaining,
                    R.id.widget_today_group, R.id.widget_today_label, R.id.widget_today }) {
                assertTrue(view.findViewById(id).performClick());
                android.content.Intent launched = org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity();
                assertEquals(LauncherActivity.class.getName(), launched.getComponent().getClassName());
                assertEquals("https://sikbi.app/", launched.getDataString());
            }
            view.findViewById(R.id.widget_record).performClick();
            assertEquals("https://sikbi.app/expenses?new=1", org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getDataString());
            assertEquals(0xFFFFFFFF, ((TextView)view.findViewById(R.id.widget_remaining)).getCurrentTextColor());
            render(view, layout == R.layout.budget_widget ? "navy-4x2" : "navy-4x1", 340, layout == R.layout.budget_widget ? 170 : 72);
            render(view, layout == R.layout.budget_widget ? "navy-4x2-narrow" : "navy-4x1-narrow", 280, layout == R.layout.budget_widget ? 140 : 64);
        }
    }
    @Test public void compactHidesOldMoneyOnFailureAndRestoresItAfterRefresh() throws Exception {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        JSONObject data = new JSONObject("{\"remaining\":315000,\"todaySpent\":18500}");
        View view = BudgetWidgetProvider.views(activity, data, "", R.layout.budget_widget_compact).apply(activity, new FrameLayout(activity));
        BudgetWidgetProvider.views(activity, null, activity.getString(R.string.widget_offline), R.layout.budget_widget_compact).reapply(activity, view);
        assertEquals("—", ((TextView)view.findViewById(R.id.widget_remaining)).getText().toString());
        assertEquals("—", ((TextView)view.findViewById(R.id.widget_today)).getText().toString());
        assertEquals(View.GONE, view.findViewById(R.id.widget_metrics).getVisibility());
        assertEquals(View.VISIBLE, view.findViewById(R.id.widget_unavailable).getVisibility());
        view.findViewById(R.id.widget_connect).performClick();
        assertEquals(WidgetSetupActivity.class.getName(), org.robolectric.Shadows.shadowOf(activity).getNextStartedActivity().getComponent().getClassName());
        render(view, "navy-4x1-offline", 340, 72);
        BudgetWidgetProvider.views(activity, data, "", R.layout.budget_widget_compact).reapply(activity, view);
        assertEquals(View.VISIBLE, view.findViewById(R.id.widget_metrics).getVisibility());
        assertEquals(View.GONE, view.findViewById(R.id.widget_unavailable).getVisibility());
        assertEquals("315,000원", ((TextView)view.findViewById(R.id.widget_remaining)).getText().toString());
    }
    @Test public void sharedRefreshUpdatesEveryInstalledSize() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        android.appwidget.AppWidgetManager manager = android.appwidget.AppWidgetManager.getInstance(context);
        org.robolectric.shadows.ShadowAppWidgetManager shadow = org.robolectric.Shadows.shadowOf(manager);
        int regular = shadow.createWidget(BudgetWidgetProvider.class, R.layout.budget_widget);
        int compact = shadow.createWidget(CompactBudgetWidgetProvider.class, R.layout.budget_widget_compact);
        BudgetWidgetProvider.render(context, new JSONObject("{\"remaining\":315000,\"todaySpent\":18500}"), "");
        for (int id : new int[] {regular, compact}) {
            assertEquals("315,000원", ((TextView)shadow.getViewFor(id).findViewById(R.id.widget_remaining)).getText().toString());
            assertEquals("18,500원", ((TextView)shadow.getViewFor(id).findViewById(R.id.widget_today)).getText().toString());
        }
        BudgetWidgetProvider.render(context, null, context.getString(R.string.widget_login));
        for (int id : new int[] {regular, compact}) assertEquals("—", ((TextView)shadow.getViewFor(id).findViewById(R.id.widget_remaining)).getText().toString());
    }
    @Test public void removingRegularSizeKeepsConnectionWhileCompactIsInstalled() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        org.robolectric.Shadows.shadowOf(android.appwidget.AppWidgetManager.getInstance(context))
            .createWidget(CompactBudgetWidgetProvider.class, R.layout.budget_widget_compact);
        File credentials = new File(context.getNoBackupFilesDir(), "widget-auth");
        try (FileOutputStream out = new FileOutputStream(credentials)) { out.write(1); }
        assertTrue(BudgetWidgetProvider.installed(context));
        new BudgetWidgetProvider().onDisabled(context);
        assertTrue(credentials.isFile());
        credentials.delete();
    }
    private void render(View view, String name, int width, int height) throws Exception {
        float density = view.getResources().getDisplayMetrics().density;
        int w = Math.round(width * density), h = Math.round(height * density);
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, w, h);
        assertTrue(view.findViewById(R.id.widget_record).getBottom() > 0);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); view.draw(new Canvas(bitmap));
        File directory = new File("build/reports/widget"); directory.mkdirs();
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
    }
}
