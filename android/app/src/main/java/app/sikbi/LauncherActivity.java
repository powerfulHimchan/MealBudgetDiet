/*
 * Copyright 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.sikbi;

import android.os.Bundle;
import androidx.browser.customtabs.CustomTabsCallback;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.browser.trusted.TrustedWebActivityIntentBuilder;
import com.google.androidbrowserhelper.trusted.SessionStore;
import com.google.androidbrowserhelper.trusted.SharedPreferencesTokenStore;
import com.google.androidbrowserhelper.trusted.TwaLauncher;
import com.google.androidbrowserhelper.trusted.splashscreens.SplashScreenStrategy;

/** Preserves the browser helper's URL, login, notification and back-stack handling. */
public class LauncherActivity extends com.google.androidbrowserhelper.trusted.LauncherActivity {
    private SignatureSplashStrategy signatureSplash;
    private WidgetBridge widgetBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WidgetUpdateService.enqueue(this);
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            getSplashScreen().setOnExitAnimationListener(splash -> splash.remove());
        }
    }

    @Override
    protected TwaLauncher createTwaLauncher() {
        return new TwaLauncher(this, null, SessionStore.makeSessionId(getTaskId()),
                new SharedPreferencesTokenStore(this)) {
            @Override
            public void launch(TrustedWebActivityIntentBuilder builder, CustomTabsCallback callback,
                    SplashScreenStrategy unused, Runnable completion, FallbackStrategy fallback) {
                if (isTaskRoot()) signatureSplash = new SignatureSplashStrategy(LauncherActivity.this);
                widgetBridge = new WidgetBridge(LauncherActivity.this, callback);
                SplashScreenStrategy strategy = new SplashScreenStrategy() {
                    @Override public void onTwaLaunchInitiated(String provider, TrustedWebActivityIntentBuilder b) {
                        if (signatureSplash != null) signatureSplash.onTwaLaunchInitiated(provider, b);
                    }
                    @Override public void configureTwaBuilder(TrustedWebActivityIntentBuilder b, CustomTabsSession session, Runnable ready) {
                        widgetBridge.bind(session);
                        if (signatureSplash != null) signatureSplash.configureTwaBuilder(b, session, ready);
                        else ready.run();
                    }
                };
                super.launch(builder, widgetBridge, strategy, completion, fallback);
            }
        };
    }

    @Override
    protected void onDestroy() {
        if (signatureSplash != null) signatureSplash.destroy();
        if (widgetBridge != null) widgetBridge.destroy();
        super.onDestroy();
    }
}
