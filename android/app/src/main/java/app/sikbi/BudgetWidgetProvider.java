package app.sikbi;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class BudgetWidgetProvider extends AppWidgetProvider {
    static final String REFRESH = "app.sikbi.widget.REFRESH";
    private static final Class<?>[] PROVIDERS = { BudgetWidgetProvider.class, CompactBudgetWidgetProvider.class };

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, null, context.getString(R.string.widget_loading));
        WidgetUpdateService.enqueue(context);
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (REFRESH.equals(intent.getAction())) WidgetUpdateService.enqueue(context);
    }
    @Override public void onDisabled(Context context) {
        // The two sizes share a connection: removing one size must not disconnect the other.
        if (installed(context)) return;
        context.getSystemService(android.app.job.JobScheduler.class).cancel(WidgetUpdateService.JOB_ID);
        WidgetSecrets.clear(context);
    }
    static boolean installed(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> provider : PROVIDERS) {
            if (manager.getAppWidgetIds(new ComponentName(context, provider)).length > 0) return true;
        }
        return false;
    }
    static String won(long amount) { return java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(amount) + "원"; }

    static RemoteViews views(Context context, JSONObject summary, String message) {
        return views(context, summary, message, R.layout.budget_widget);
    }

    static RemoteViews views(Context context, JSONObject summary, String message, int layout) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layout);
        views.setTextViewText(R.id.widget_remaining, summary == null ? "—" : won(summary.optLong("remaining")));
        views.setTextViewText(R.id.widget_today, summary == null ? "—" : won(summary.optLong("todaySpent")));
        views.setTextColor(R.id.widget_remaining, summary != null && summary.optLong("remaining") < 0 ? 0xFFFDA4AF : 0xFFFFFFFF);
        views.setTextViewText(R.id.widget_status, message);
        views.setViewVisibility(R.id.widget_status, summary == null ? android.view.View.VISIBLE : android.view.View.GONE);
        if (layout == R.layout.budget_widget) {
            JSONObject period = summary == null ? null : summary.optJSONObject("period");
            String dates = period == null ? "" : shortDate(period.optString("from")) + " – " + shortDate(period.optString("to"));
            views.setTextViewText(R.id.widget_period, dates);
            views.setViewVisibility(R.id.widget_period, period == null ? android.view.View.GONE : android.view.View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.widget_metrics, summary == null ? android.view.View.GONE : android.view.View.VISIBLE);
            views.setViewVisibility(R.id.widget_unavailable, summary == null ? android.view.View.VISIBLE : android.view.View.GONE);
        }
        views.setViewVisibility(R.id.widget_connect, summary == null ? android.view.View.VISIBLE : android.view.View.GONE);
        Intent home = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://sikbi.app/"));
        Intent record = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://sikbi.app/expenses?new=1"));
        Intent setup = new Intent(context, WidgetSetupActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("sikbi-widget://setup"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent dashboard = PendingIntent.getActivity(context, 1, home, flags);
        for (int id : new int[] { R.id.widget_brand, R.id.widget_remaining_group, R.id.widget_remaining_label,
                R.id.widget_remaining, R.id.widget_today_group, R.id.widget_today_label, R.id.widget_today }) {
            views.setOnClickPendingIntent(id, dashboard);
        }
        views.setOnClickPendingIntent(R.id.widget_record, PendingIntent.getActivity(context, 2, record, flags));
        views.setOnClickPendingIntent(R.id.widget_connect, PendingIntent.getActivity(context, 3, setup, flags));
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 4,
            new Intent(context, BudgetWidgetProvider.class).setAction(REFRESH), flags));
        return views;
    }
    static void render(Context context, JSONObject summary, String message) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> provider : PROVIDERS) {
            int[] ids = manager.getAppWidgetIds(new ComponentName(context, provider));
            if (ids.length == 0) continue;
            int layout = provider == CompactBudgetWidgetProvider.class ? R.layout.budget_widget_compact : R.layout.budget_widget;
            manager.updateAppWidget(ids, views(context, summary, message, layout));
        }
    }
    private static String shortDate(String date) {
        return date.matches("\\d{4}-\\d{2}-\\d{2}") ? date.substring(5).replace('-', '.') : date;
    }
}
