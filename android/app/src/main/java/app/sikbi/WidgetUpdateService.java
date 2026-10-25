package app.sikbi;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Bounded network work survives the widget receiver lifecycle without a foreground service. */
public class WidgetUpdateService extends JobService {
    static final int JOB_ID = 51701;
    static final Object LOCK = new Object();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    static void enqueue(Context context) {
        if (!BudgetWidgetProvider.installed(context)) return;
        // The queued job is coalesced by ID. No network constraint so offline refresh hides old amounts.
        context.getSystemService(JobScheduler.class).schedule(new JobInfo.Builder(JOB_ID,
            new ComponentName(context, WidgetUpdateService.class)).setOverrideDeadline(1000).build());
    }
    static void disconnect(Context context) {
        synchronized (LOCK) {
            WidgetSecrets.clear(context);
            BudgetWidgetProvider.render(context, null, context.getString(R.string.widget_login));
        }
    }
    static void update(Context context) {
        String token = WidgetSecrets.token(context);
        if (token.isEmpty()) {
            BudgetWidgetProvider.render(context, null, context.getString(R.string.widget_login)); return;
        }
        try {
            org.json.JSONObject data = WidgetApi.call("summary", token, null);
            // A logout or account switch while the request was in flight must not re-publish old data.
            synchronized (LOCK) {
                if (token.equals(WidgetSecrets.token(context))) BudgetWidgetProvider.render(context, data, "");
            }
        } catch (Exception error) {
            synchronized (LOCK) {
                if (!token.equals(WidgetSecrets.token(context))) return;
                if (error instanceof WidgetApi.Failure && (((WidgetApi.Failure) error).status == 401 || ((WidgetApi.Failure) error).status == 403)) {
                    WidgetSecrets.clear(context);
                    BudgetWidgetProvider.render(context, null, context.getString(R.string.widget_login));
                } else BudgetWidgetProvider.render(context, null, context.getString(R.string.widget_offline));
            }
        }
    }
    @Override public boolean onStartJob(JobParameters params) {
        executor.execute(() -> { update(this); jobFinished(params, false); }); return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
