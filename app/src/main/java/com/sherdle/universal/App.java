package com.sherdle.universal;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.RequestConfiguration;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.installations.FirebaseInstallations;
import com.onesignal.Continue;
import com.onesignal.notifications.INotificationClickEvent;
import com.onesignal.notifications.INotificationClickListener;
import com.sherdle.universal.providers.wordpress.PostItem;
import com.sherdle.universal.providers.wordpress.api.WordpressGetTaskInfo;
import com.sherdle.universal.providers.wordpress.api.providers.JetPackProvider;
import com.sherdle.universal.providers.wordpress.api.providers.RestApiProvider;
import com.sherdle.universal.providers.wordpress.ui.WordpressDetailActivity;
import com.sherdle.universal.util.Log;

import com.onesignal.OneSignal;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static com.sherdle.universal.Config.NOTIFICATION_BASEURL;

/**
 * This file is part of the Universal template
 * For license information, please check the LICENSE
 * file in the root of this project
 * @author Sherdle
 * Copyright 2021
 */
public class App extends MultiDexApplication {

    private static App mInstance;
    private int wpInterstitialCount = 0;
    private int rssInterstitialCount = 0;
    private FirebaseAnalytics mFirebaseAnalytics;
    private InterstitialAd interstitialAd;

    public static App getAppInstance() {
        return mInstance;
    }

    public int getRssInterstitialCount() {
        return rssInterstitialCount;
    }

    public void setRssInterstitialCount(int rssInterstitialCount) {
        this.rssInterstitialCount = rssInterstitialCount;
    }

    public int getWpInterstitialCount() {
        return wpInterstitialCount;
    }

    public void setWpInterstitialCount(int wpInterstitialCount) {
        this.wpInterstitialCount = wpInterstitialCount;
    }

    public InterstitialAd getInterstitialAd() {
        return interstitialAd;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mInstance = this;
        FirebaseApp.initializeApp(this);
        if (Config.FIREBASE_ANALYTICS) {
            mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            FirebaseInstallations.getInstance().getId()
                    .addOnCompleteListener(new OnCompleteListener<String>() {
                        @Override
                        public void onComplete(@NonNull Task<String> task) {
                            if (task.isSuccessful()) {
                                String installationId = task.getResult();
                                FirebaseCrashlytics.getInstance().setUserId(installationId);

                                // Log
                                Log.d("Firebase", "Token: " + installationId);
                            } else {
                                Log.e("Installations", "Unable to get Installation ID");
                            }
                        }
                    });
        }

        //OneSignal Push
        if (!TextUtils.isEmpty(getString(R.string.onesignal_app_id))) {
            // OneSignal Initialization
            OneSignal.initWithContext(this, getString(R.string.onesignal_app_id));

            OneSignal.getNotifications().addClickListener(new NotificationHandler());

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                OneSignal.getNotifications().requestPermission(true, Continue.with(r -> {
                    if (r.isSuccess()) {
                        if (r.getData()) {
                            OneSignal.getUser().getPushSubscription().optIn();
                        }
                    }
                    else {
                        Log.e("ONESIGNAL", r.getThrowable().toString());
                    }
                }));
            }

            System.out.println("UserId: " + OneSignal.getUser().getPushSubscription().getId());
        }

        if (!TextUtils.isEmpty(getString(R.string.admob_interstitial_id)) || !TextUtils.isEmpty(getString(R.string.admob_banner_id))){
            MobileAds.initialize(this);
            List<String> testDeviceIds = new ArrayList<>();
            testDeviceIds.add(AdRequest.DEVICE_ID_EMULATOR);
            testDeviceIds.add("02DC0787CED2F9571E337DB96097298B");
            RequestConfiguration configuration =
                    new RequestConfiguration.Builder().setTestDeviceIds(testDeviceIds).build();
            MobileAds.setRequestConfiguration(configuration);
        }

        // Load initial interstitial ad
        loadInterstitialAd();
    }

    public void loadInterstitialAd() {
        AdRequest adRequestInter = new AdRequest.Builder().build();
        InterstitialAd.load(this, getResources().getString(R.string.admob_interstitial_id), adRequestInter, new InterstitialAdLoadCallback() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                App.this.interstitialAd = interstitialAd;
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                interstitialAd = null;
            }
        });
    }

    // This fires when a notification is opened by tapping on it or one is received while the app is running.
    private class NotificationHandler implements INotificationClickListener {
        @Override
        public void onClick(@NotNull INotificationClickEvent result) {

            try {
                JSONObject data = result.getNotification().getAdditionalData();

                String webViewUrl = (data != null) ? data.optString("url", null) : null;
                String browserUrl = result.getNotification().getLaunchURL();

                if (browserUrl != null || webViewUrl != null) {
                    if (NOTIFICATION_BASEURL.length() > 0 && browserUrl != null) {
                        openWordPressPost(browserUrl);
                    } else {
                        String urlToOpen = (browserUrl != null) ? browserUrl : webViewUrl;
                        boolean openExternally = webViewUrl == null;
                        HolderActivity.startWebViewActivity(App.this, urlToOpen, openExternally, false, null, Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_NEW_TASK);
                    }
                } else {
                    Intent mainIntent;
                    mainIntent = new Intent(App.this, MainActivity.class);
                    mainIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | FLAG_ACTIVITY_NEW_TASK);
                    startActivity(mainIntent);
                }

            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    private void openWordPressPost(String postUrl){
        //Toast to indicate that posts are loading, a loading screen would be nicer.
        Toast.makeText(getApplicationContext(), R.string.loading, Toast.LENGTH_SHORT).show();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                WordpressGetTaskInfo info = new WordpressGetTaskInfo(null, null, NOTIFICATION_BASEURL, false);

                //By default, we'll check the most recent posts and see if the notified url is used by one of these posts
                String requestUrl = info.provider.getRecentPosts(info) + 1;

                //However, if the notification url potentially contains the slug (last path contains -), we query by slug
                String potentialPostSlug = postUrl.split("/")[postUrl.split("/").length - 1];
                if (potentialPostSlug.contains("-")) {
                    //TODO Dirty, integrate this into the provider interface (get post by slug)
                    if (info.provider instanceof RestApiProvider) {
                        requestUrl = NOTIFICATION_BASEURL + "posts/?_embed=1&slug=" + potentialPostSlug ;
                    } else if (info.provider instanceof JetPackProvider) {
                        requestUrl = "https://public-api.wordpress.com/rest/v1.1/sites/" + NOTIFICATION_BASEURL + "/posts/slug:" + potentialPostSlug;
                    }
                }

                ArrayList<PostItem> posts = info.provider.parsePostsFromUrl(info,
                        requestUrl);
                for (PostItem post : posts) {
                    if (post.getUrl().equals(postUrl)) {
                        Intent intent = new Intent(getApplicationContext(), WordpressDetailActivity.class);
                        intent.putExtra(WordpressDetailActivity.EXTRA_POSTITEM, post);
                        intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
                        intent.putExtra(WordpressDetailActivity.EXTRA_API_BASE, NOTIFICATION_BASEURL);

                        startActivity(intent);
                        return;
                    }
                }
            }
        };
        AsyncTask.execute(runnable);
    }
}
