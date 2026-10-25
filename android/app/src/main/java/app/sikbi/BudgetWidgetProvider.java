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

    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        render(context, null, context.getString(R.string.widget_loading));
        WidgetUpdateService.enqueue(context);
    }
    @Override public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (REFRESH.equals(intent.getAction())) WidgetUpdateService.enqueue(context);
    }
    @Override public void onDisabled(Context context) {
        context.getSystemService(android.app.job.JobScheduler.class).cancel(WidgetUpdateService.JOB_ID);
        WidgetSecrets.clear(context);
    }
    static boolean installed(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(new ComponentName(context, BudgetWidgetProvider.class)).length > 0;
    }
    static String won(long amount) { return java.text.NumberFormat.getIntegerInstance(java.util.Locale.KOREA).format(amount) + "원"; }

    static RemoteViews views(Context context, JSONObject summary, String message) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.budget_widget);
        views.setTextViewText(R.id.widget_remaining, summary == null ? "—" : won(summary.optLong("remaining")));
        views.setTextViewText(R.id.widget_today, summary == null ? "—" : won(summary.optLong("todaySpent")));
        views.setTextColor(R.id.widget_remaining, summary != null && summary.optLong("remaining") < 0 ? 0xFFDC2626 : 0xFF0B1220);
        String detail = message;
        if (summary != null) {
            JSONObject period = summary.optJSONObject("period");
            detail = period == null ? "" : period.optString("from") + " ~ " + period.optString("to");
        }
        views.setTextViewText(R.id.widget_status, detail);
        views.setViewVisibility(R.id.widget_connect, summary == null ? android.view.View.VISIBLE : android.view.View.GONE);
        Intent home = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://sikbi.app/"));
        Intent record = new Intent(context, LauncherActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("https://sikbi.app/expenses?new=1"));
        Intent setup = new Intent(context, WidgetSetupActivity.class).setAction(Intent.ACTION_VIEW).setData(Uri.parse("sikbi-widget://setup"));
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        views.setOnClickPendingIntent(R.id.widget_brand, PendingIntent.getActivity(context, 1, home, flags));
        views.setOnClickPendingIntent(R.id.widget_record, PendingIntent.getActivity(context, 2, record, flags));
        views.setOnClickPendingIntent(R.id.widget_connect, PendingIntent.getActivity(context, 3, setup, flags));
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, 4,
            new Intent(context, BudgetWidgetProvider.class).setAction(REFRESH), flags));
        return views;
    }
    static void render(Context context, JSONObject summary, String message) {
        AppWidgetManager.getInstance(context).updateAppWidget(new ComponentName(context, BudgetWidgetProvider.class), views(context, summary, message));
    }
}
