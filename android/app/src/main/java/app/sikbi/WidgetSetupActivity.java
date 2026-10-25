package app.sikbi;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;

/** Only the on-device verifier can redeem a web approval; callback URLs carry no credentials. */
public class WidgetSetupActivity extends Activity {
    private TextView status;
    private Button connect;
    private boolean claiming;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding * 2, padding, padding); layout.setBackgroundColor(0xFFEEF2F9);
        TextView title = new TextView(this); title.setText(R.string.widget_setup_title); title.setTextSize(26); title.setTextColor(0xFF0B1220);
        status = new TextView(this); status.setText(R.string.widget_setup_description); status.setTextSize(16); status.setTextColor(Color.DKGRAY); status.setPadding(0, padding, 0, padding);
        connect = new Button(this); connect.setText(R.string.widget_connect); connect.setOnClickListener(v -> begin());
        layout.addView(title); layout.addView(status); layout.addView(connect); setContentView(layout);
        handle(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handle(intent); }
    private void begin() {
        try {
            WidgetUpdateService.disconnect(this);
            String verifier = WidgetSecrets.newVerifier();
            WidgetSecrets.write(this, new JSONObject().put("verifier", verifier).put("started", System.currentTimeMillis()));
            String challenge = WidgetSecrets.challenge(verifier);
            startActivity(new Intent(this, LauncherActivity.class).setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://sikbi.app/settings/widget?challenge=" + challenge)));
        } catch (Exception error) { status.setText(R.string.widget_setup_failed); }
    }
    private void handle(Intent intent) {
        Uri uri = intent.getData();
        if (uri == null || !"sikbi-widget".equals(uri.getScheme()) || !"connected".equals(uri.getHost()) || claiming) return;
        JSONObject saved = WidgetSecrets.read(this);
        String verifier = saved.optString("verifier");
        try {
            long age = System.currentTimeMillis() - saved.optLong("started");
            if (verifier.isEmpty() || age < 0 || age > 10 * 60 * 1000 || !WidgetSecrets.challenge(verifier).equals(uri.getQueryParameter("challenge"))) {
                status.setText(R.string.widget_setup_failed); return;
            }
        } catch (Exception error) { status.setText(R.string.widget_setup_failed); return; }
        claiming = true; connect.setEnabled(false); status.setText(R.string.widget_loading);
        new Thread(() -> {
            try {
                String token = WidgetApi.call("claim", "", new JSONObject().put("verifier", verifier)).getString("token");
                synchronized (WidgetUpdateService.LOCK) {
                    if (!verifier.equals(WidgetSecrets.read(this).optString("verifier"))) throw new IllegalStateException("Connection changed");
                    WidgetSecrets.write(this, new JSONObject().put("token", token));
                }
                WidgetUpdateService.enqueue(this);
                runOnUiThread(() -> { status.setText(R.string.widget_setup_done); connect.setVisibility(android.view.View.GONE); });
            } catch (Exception error) {
                runOnUiThread(() -> { claiming = false; connect.setEnabled(true); status.setText(R.string.widget_setup_failed); });
            }
        }, "sikbi-widget-connect").start();
    }
}
