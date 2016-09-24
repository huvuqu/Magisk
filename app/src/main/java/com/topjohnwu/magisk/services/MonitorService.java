package com.topjohnwu.magisk.services;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.topjohnwu.magisk.R;
import com.topjohnwu.magisk.WelcomeActivity;
import com.topjohnwu.magisk.utils.Logger;
import com.topjohnwu.magisk.utils.PrefHelper;
import com.topjohnwu.magisk.utils.Utils;

import java.util.Set;

public class MonitorService extends AccessibilityService {
    private static final String TAG = "MonitorService";
    private Boolean disableroot;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();

        //Configure these here for compatibility with API 13 and below.
        AccessibilityServiceInfo config = new AccessibilityServiceInfo();
        config.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        config.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        disableroot = false;
        if (Build.VERSION.SDK_INT >= 16)
            //Just in case this helps
            config.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;

        setServiceInfo(config);

    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d("Magisk", "MonitorService: Service created");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("Magisk", "MonitorService: Service destroyed");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            ComponentName componentName = new ComponentName(
                    event.getPackageName().toString(),
                    event.getClassName().toString()
            );

            ActivityInfo activityInfo = tryGetActivity(componentName);
            boolean isActivity = activityInfo != null;
            if (isActivity) {
                Logger.dh("MonitorService: CurrentActivity: " + componentName.getPackageName());
                String mPackage = componentName.getPackageName();
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
                if (Utils.autoToggleEnabled(getApplicationContext())) {
                    Set<String> setBlackList = prefs.getStringSet("auto_blacklist", null);

                    if (setBlackList != null) {
                        disableroot = setBlackList.contains(mPackage);
                        ForceRoot(!disableroot);
                        String appFriendly = getAppName(mPackage);
                        ShowNotification(disableroot, appFriendly);
                    }
                }
            }
        }
    }

    private String getAppName(String packageName) {
        PackageManager pkManager = getPackageManager();
        ApplicationInfo appInfo;
        String appName;
        try {
            appInfo = pkManager.getApplicationInfo(packageName, 0);
            appName = (String) ((appInfo != null) ? pkManager.getApplicationLabel(appInfo) : "???");
            return appName;
        } catch (final PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private ActivityInfo tryGetActivity(ComponentName componentName) {
        try {
            return getPackageManager().getActivityInfo(componentName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void ForceRoot(Boolean rootToggle) {

        String rootString = rootToggle ? "on" : "off";
        if (Utils.rootEnabled() != rootToggle) {
            Logger.dh("MonitorService: toggling root " + rootString);
            Utils.toggleRoot(rootToggle,getApplicationContext());
            if (Utils.rootEnabled() != rootToggle) {
                Utils.toggleRoot(rootToggle,getApplicationContext());
                Logger.dh("MonitorService: FORCING to " + rootString);
            }

        }
    }

    private void ForceEnableRoot() {
        Log.d("Magisk", "MonitorService: ForceEnable called.");
        Utils.toggleRoot(true,getApplicationContext());
        if (!Utils.rootEnabled()) {
            Utils.toggleRoot(true,getApplicationContext());
        }
    }

    private void ShowNotification(boolean rootAction, String packageName) {
        NotificationManager mNotifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder mBuilder;
        if (!PrefHelper.CheckBool("hide_root_notification", getApplicationContext())) {
            if (rootAction) {

                Intent intent = new Intent(getApplication(), WelcomeActivity.class);
                intent.putExtra("relaunch", "relaunch");
                String rootMessage;
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        getApplicationContext(),
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);
                if (packageName.equals("")) {
                    rootMessage = "Root has been disabled";
                } else {
                    rootMessage = "Root has been disabled for " + packageName;
                }
                mBuilder =
                        new NotificationCompat.Builder(getApplicationContext())
                                .setSmallIcon(disableroot ? R.drawable.ic_stat_notification_autoroot_off : R.drawable.ic_stat_notification_autoroot_on)
                                .setContentIntent(pendingIntent)
                                .setContentTitle(getApplicationContext().getString(R.string.auto_toggle) + " status changed")
                                .setContentText(rootMessage);
                int mNotificationId = 1;
                mNotifyMgr.notify(mNotificationId, mBuilder.build());
            } else {
                mNotifyMgr.cancelAll();
            }
        }

    }

    @Override
    public void onInterrupt() {
    }
}