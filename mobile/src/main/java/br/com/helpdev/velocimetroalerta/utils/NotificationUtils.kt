package br.com.helpdev.velocimetroalerta.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.text.TextUtils

class NotificationUtils private constructor() {

    init {
        throw RuntimeException("No NotificationUtils!")
    }

    companion object {

        private val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"
        val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"
        val DEFAULT_CHANNEL_ID = "NotificationUtils.DEFAULT_CHANNEL_ID"

        fun createNotification(context: Context,
                               title: String,
                               message: String,
                               pi: PendingIntent): NotificationCompat.Builder {
            return createNotification(context, DEFAULT_CHANNEL_ID, title, message, pi)
        }

        fun createNotification(context: Context,
                               channelId: String,
                               title: String,
                               message: String,
                               pi: PendingIntent): NotificationCompat.Builder {

            val builder = NotificationCompat.Builder(context, channelId)
            builder.setDefaults(Notification.DEFAULT_ALL)
            builder.setContentTitle(title)
            builder.setContentText(message)
            builder.setContentIntent(pi)
            return builder
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        fun createNotificationChannel(context: Context, channelId: String, title: String, importance: Int) {
            val notificationChannel = NotificationChannel(channelId, title,
                    importance)
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager?.createNotificationChannel(notificationChannel)
        }

        fun notify(context: Context, notificationBuilder: NotificationCompat.Builder, id: Int) {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.notify(id, notificationBuilder.build())
        }

        fun cancel(context: Context, id: Int) {
            val notificationManagerCompat = NotificationManagerCompat.from(context)
            notificationManagerCompat.cancel(id)
        }


        /**
         * Is Notification Service Enabled.
         * Verifies if the notification listener service is enabled.
         * Got it from: https://github.com/kpbird/NotificationListenerService-Example/blob/master/NLSExample/src/main/java/com/kpbird/nlsexample/NLService.java
         *
         * @return True if eanbled, false otherwise.
         */
        fun isNotificationServiceEnabled(context: Context): Boolean {
            val pkgName = context.packageName
            val flat = Settings.Secure.getString(context.contentResolver,
                    ENABLED_NOTIFICATION_LISTENERS)
            if (!TextUtils.isEmpty(flat)) {
                val names = flat.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (name in names) {
                    val cn = ComponentName.unflattenFromString(name)
                    if (cn != null) {
                        if (TextUtils.equals(pkgName, cn.packageName)) {
                            return true
                        }
                    }
                }
            }
            return false
        }
    }

}
