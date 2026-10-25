package app.sikbi;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsService;
import androidx.browser.customtabs.CustomTabsSession;

/** Origin-validated TWA messages contain only refresh/logout commands, never credentials. */
final class WidgetBridge extends CustomTabsCallback {
    private static final Uri ORIGIN = Uri.parse("https://sikbi.app");
    private final Context context;
    private final CustomTabsCallback delegate;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private CustomTabsSession session;
    private boolean validated, loaded, channel, acknowledged;
    WidgetBridge(Context context, CustomTabsCallback delegate) { this.context = context.getApplicationContext(); this.delegate = delegate; }
    void bind(CustomTabsSession session) {
        this.session = session;
        session.validateRelationship(CustomTabsService.RELATION_USE_AS_ORIGIN, ORIGIN, null);
    }
    private void requestChannel() {
        if (validated && loaded && session != null) session.requestPostMessageChannel(ORIGIN, ORIGIN, new Bundle());
    }
    @Override public void onRelationshipValidationResult(int relation, Uri origin, boolean result, Bundle extras) {
        if (delegate != null) delegate.onRelationshipValidationResult(relation, origin, result, extras);
        if (relation == CustomTabsService.RELATION_USE_AS_ORIGIN && ORIGIN.equals(origin)) { validated = result; requestChannel(); }
    }
    @Override public void onNavigationEvent(int event, Bundle extras) {
        if (delegate != null) delegate.onNavigationEvent(event, extras);
        if (event == NAVIGATION_STARTED) { loaded = false; channel = false; acknowledged = false; handler.removeCallbacksAndMessages(null); }
        if (event == NAVIGATION_FINISHED) { loaded = true; requestChannel(); }
        if (event == TAB_HIDDEN) WidgetUpdateService.enqueue(context);
    }
    @Override public void onMessageChannelReady(Bundle extras) {
        if (delegate != null) delegate.onMessageChannelReady(extras);
        channel = true; hello(); handler.postDelayed(this::hello, 1000); handler.postDelayed(this::hello, 3000);
    }
    private void hello() { if (validated && loaded && channel && !acknowledged && session != null) session.postMessage("sikbi:ready", null); }
    @Override public void onPostMessage(String message, Bundle extras) {
        if (delegate != null) delegate.onPostMessage(message, extras);
        if (!validated || !loaded || !channel) return;
        acknowledged = true;
        if ("sikbi:logout".equals(message) && !WidgetSecrets.token(context).isEmpty()) WidgetUpdateService.disconnect(context);
        else if ("sikbi:refresh".equals(message)) WidgetUpdateService.enqueue(context);
    }
    @Override public void extraCallback(String name, Bundle extras) { if (delegate != null) delegate.extraCallback(name, extras); }
    @Override public Bundle extraCallbackWithResult(String name, Bundle extras) { return delegate == null ? null : delegate.extraCallbackWithResult(name, extras); }
    void destroy() { handler.removeCallbacksAndMessages(null); session = null; channel = false; }
}
