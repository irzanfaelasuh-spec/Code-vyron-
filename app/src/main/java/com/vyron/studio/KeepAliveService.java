package com.vyron.studio;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "vyron_runtime";
    private static final int NOTIFICATION_ID = 7401;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(com.vyron.studio.R.drawable.ic_vyron)
                .setContentTitle("VYRON Code")
                .setContentText("Background workspace mode is active")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "VYRON Runtime", NotificationManager.IMPORTANCE_LOW));
        }
    }
}
