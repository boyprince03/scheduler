package stevedaydream.scheduler.presentation.schedule.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import stevedaydream.scheduler.MainActivity
import stevedaydream.scheduler.R

class SchedulerMessagingService : FirebaseMessagingService() {

    private val TAG = "FCM_Service"

    /**
     * 當 FCM 註冊 Token 更新時呼叫。
     * 這個 Token 是裝置的唯一識別碼，用於發送推播。
     * 您應將此 Token 發送到您的後端伺服器，以便後端可以向特定裝置發送訊息。
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Refreshed token: $token")

        // TODO: 將此 Token 發送到您的應用程式伺服器
        // sendTokenToServer(token)
    }

    /**
     * 當應用程式在前景時，收到 FCM 訊息時呼叫。
     * 如果應用程式在背景或已關閉，通知訊息會由系統匣處理。
     * 資料訊息 (Data Message) 則不論前景或背景都會在此處處理。
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d(TAG, "From: ${remoteMessage.from}")

        // 檢查訊息是否包含資料承載 (Data Payload)
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "Message data payload: " + remoteMessage.data)
            // 在這裡可以根據 data payload 的內容執行特定操作，例如同步資料
        }

        // 檢查訊息是否包含通知承載 (Notification Payload)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            // 顯示通知到系統通知列
            sendNotification(it.title, it.body)
        }
    }

    /**
     * 建立並顯示一個簡單的系統通知。
     * @param title 通知標題
     * @param messageBody 通知內容
     */
    private fun sendNotification(title: String?, messageBody: String?) {
        val channelId = "SCHEDULER_CHANNEL_ID"
        val notificationId = System.currentTimeMillis().toInt()

        // 建立點擊通知後要開啟的 Intent
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        // 建立通知
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // 請確認您有這個圖示資源
            .setContentTitle(title ?: "新通知")
            .setContentText(messageBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent) // 設定點擊行為
            .setAutoCancel(true) // 點擊後自動關閉通知

        // 對於 Android 8.0 (API 26) 以上版本，必須建立 Notification Channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "排班通知"
            val descriptionText = "接收排班相關的通知"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance).apply {
                description = descriptionText
            }
            // 向系統註冊 Channel
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        // 顯示通知
        with(NotificationManagerCompat.from(this)) {
            // 檢查 Android 13 (API 33) 的通知權限
            if (ActivityCompat.checkSelfPermission(
                    this@SchedulerMessagingService,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // 在實際應用中，您應該在 Activity/Fragment 中請求此權限，
                // Service 中無法直接跳出權限請求對話框。
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
                return
            }
            notify(notificationId, builder.build())
        }
    }
}
