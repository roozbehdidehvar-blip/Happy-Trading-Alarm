package com.happytradingalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class PriceMonitorService : Service() {

    private val serviceScope =
        CoroutineScope(
            SupervisorJob() + Dispatchers.IO
        )

    private var monitorJob: Job? = null

    override fun onCreate() {
        super.onCreate()

        createServiceNotificationChannel()
        createPriceAlertChannels()

        startForeground(
            SERVICE_NOTIFICATION_ID,
            createServiceNotification()
        )

        startMonitoring()
    }

    private fun startMonitoring() {

        if (monitorJob?.isActive == true) {
            return
        }

        monitorJob =
            serviceScope.launch {

                while (isActive) {

                    try {

                        val alerts =
                            loadAlertsFromService()

                        val symbols =
                            alerts
                                .filter {
                                    it.enabled &&
                                        !it.triggered
                                }
                                .map {
                                    it.symbol
                                }
                                .distinct()

                        for (symbol in symbols) {

                            val result =
                                getTabdealPriceForService(
                                    symbol
                                )

                            if (result.price != null) {

                                checkAlerts(
                                    symbol,
                                    result.price,
                                    alerts
                                )
                            }
                        }

                    } catch (_: Exception) {
                    }

                    delay(1000)
                }
            }
    }

    private suspend fun checkAlerts(
        symbol: String,
        currentPrice: Double,
        alerts: MutableList<PriceAlert>
    ) {

        var changed = false

        alerts.forEachIndexed { index, alert ->

            if (
                alert.symbol == symbol &&
                alert.enabled &&
                !alert.triggered
            ) {

                val reached =
                    if (alert.direction == "Above") {

                        currentPrice >=
                            alert.targetPrice

                    } else {

                        currentPrice <=
                            alert.targetPrice
                    }

                if (reached) {

                    val triggeredAlert =
                        alert.copy(
                            enabled = false,
                            triggered = true
                        )

                    alerts[index] =
                        triggeredAlert

                    changed = true

                    showAlertNotification(
                        triggeredAlert,
                        currentPrice
                    )
                }
            }
        }

        if (changed) {

            saveAlertsFromService(
                alerts
            )
        }
    }

    private fun loadAlertsFromService():
            MutableList<PriceAlert> {

        return try {

            val preferences =
                getSharedPreferences(
                    PREFS_NAME,
                    MODE_PRIVATE
                )

            val saved =
                preferences.getString(
                    ALERTS_KEY,
                    null
                )

            if (saved.isNullOrEmpty()) {
                return mutableListOf()
            }

            val jsonArray =
                JSONArray(saved)

            val result =
                mutableListOf<PriceAlert>()

            for (
                i in 0 until jsonArray.length()
            ) {

                val obj =
                    jsonArray.getJSONObject(i)

                result.add(
                    PriceAlert(
                        symbol =
                            obj.getString(
                                "symbol"
                            ),

                        targetPrice =
                            obj.getDouble(
                                "targetPrice"
                            ),

                        direction =
                            obj.getString(
                                "direction"
                            ),

                        enabled =
                            obj.optBoolean(
                                "enabled",
                                true
                            ),

                        triggered =
                            obj.optBoolean(
                                "triggered",
                                false
                            )
                    )
                )
            }

            result

        } catch (_: Exception) {

            mutableListOf()
        }
    }

    private fun saveAlertsFromService(
        alerts: List<PriceAlert>
    ) {

        try {

            val jsonArray =
                JSONArray()

            alerts.forEach { alert ->

                val obj =
                    JSONObject()

                obj.put(
                    "symbol",
                    alert.symbol
                )

                obj.put(
                    "targetPrice",
                    alert.targetPrice
                )

                obj.put(
                    "direction",
                    alert.direction
                )

                obj.put(
                    "enabled",
                    alert.enabled
                )

                obj.put(
                    "triggered",
                    alert.triggered
                )

                jsonArray.put(obj)
            }

            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )
                .edit()
                .putString(
                    ALERTS_KEY,
                    jsonArray.toString()
                )
                .apply()

        } catch (_: Exception) {
        }
    }

    private fun createServiceNotificationChannel() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {

            val channel =
                NotificationChannel(
                    SERVICE_CHANNEL_ID,
                    "Background Price Monitoring",
                    NotificationManager
                        .IMPORTANCE_LOW
                )

            channel.description =
                "Keeps price monitoring active in the background"

            val manager =
                getSystemService(
                    NotificationManager::class.java
                )

            manager.createNotificationChannel(
                channel
            )
        }
    }

    /*
     * Creates the three price-alert channels.
     *
     * Each channel has its own sound.
     * Android notification channel sounds are persistent,
     * so separate channel IDs are used for each sound.
     */
    private fun createPriceAlertChannels() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        getAlarmSounds().forEach { sound ->

            val channelId =
                getNotificationChannelId(
                    sound.id
                )

            val existingChannel =
                manager.getNotificationChannel(
                    channelId
                )

            if (existingChannel == null) {

                val channel =
                    NotificationChannel(
                        channelId,
                        "Price Alerts — ${sound.name}",
                        NotificationManager
                            .IMPORTANCE_HIGH
                    )

                channel.description =
                    "Happy Trading Alarm price alerts"

                channel.enableVibration(true)

                val soundUri =
                    RingtoneManager.getDefaultUri(
                        sound.uriType
                    )

                channel.setSound(
                    soundUri,
                    AudioAttributes.Builder()
                        .setUsage(
                            AudioAttributes.USAGE_ALARM
                        )
                        .build()
                )

                manager.createNotificationChannel(
                    channel
                )
            }
        }
    }

    private fun createServiceNotification():
            Notification {

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    this,
                    SERVICE_CHANNEL_ID
                )

            } else {

                Notification.Builder(this)
            }

        return builder
            .setSmallIcon(
                android.R.drawable
                    .ic_menu_info_details
            )
            .setContentTitle(
                "Happy Trading Alarm"
            )
            .setContentText(
                "Background price monitoring is active"
            )
            .setOngoing(true)
            .setCategory(
                Notification.CATEGORY_SERVICE
            )
            .build()
    }

    private fun showAlertNotification(
        alert: PriceAlert,
        currentPrice: Double
    ) {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

            if (
                checkSelfPermission(
                    android.Manifest.permission
                        .POST_NOTIFICATIONS
                ) !=
                android.content.pm.PackageManager
                    .PERMISSION_GRANTED
            ) {

                return
            }
        }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        /*
         * Read the sound selected by the user
         * in MainActivity.
         */
        val selectedSoundId =
            getSavedAlarmSoundId(this)

        /*
         * Select the corresponding notification
         * channel.
         */
        val channelId =
            getNotificationChannelId(
                selectedSoundId
            )

        val title =
            "🔔 ${alert.symbol} PRICE ALERT"

        val message =
            "Price reached " +
                formatPriceForService(
                    currentPrice
                ) +
                " — target " +
                formatPriceForService(
                    alert.targetPrice
                )

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {

                Notification.Builder(
                    this,
                    channelId
                )

            } else {

                Notification.Builder(this)
                    .setSound(
                        getSelectedAlarmSoundUri(
                            this
                        )
                    )
            }

        builder
            .setSmallIcon(
                android.R.drawable
                    .ic_dialog_alert
            )
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setPriority(
                Notification.PRIORITY_HIGH
            )

        manager.notify(
            System.currentTimeMillis()
                .toInt(),
            builder.build()
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        if (
            intent?.action ==
            ACTION_STOP
        ) {

            stopMonitoringService()

            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun stopMonitoringService() {

        monitorJob?.cancel()
        monitorJob = null

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    override fun onDestroy() {

        monitorJob?.cancel()

        serviceScope.cancel()

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null
    }

    private fun getTabdealPriceForService(
        symbol: String
    ): PriceResult {

        return try {

            val cleanSymbol =
                symbol
                    .replace(" / ", "")
                    .uppercase()

            val url =
                URL(
                    "https://api1.tabdeal.org" +
                        "/r/api/v1/trades" +
                        "?symbol=" +
                        cleanSymbol +
                        "&limit=1"
                )

            val connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                8000

            connection.readTimeout =
                8000

            val responseCode =
                connection.responseCode

            if (responseCode != 200) {

                connection.disconnect()

                return PriceResult(
                    price = null,
                    error =
                        "HTTP $responseCode"
                )
            }

            val response =
                connection.inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            connection.disconnect()

            val trades =
                JSONArray(response)

            if (
                trades.length() == 0
            ) {

                return PriceResult(
                    price = null,
                    error = "No trades"
                )
            }

            val trade =
                trades.getJSONObject(0)

            val price =
                trade
                    .getString("price")
                    .toDoubleOrNull()

            if (price == null) {

                PriceResult(
                    price = null,
                    error =
                        "Invalid price"
                )

            } else {

                PriceResult(
                    price = price
                )
            }

        } catch (e: Exception) {

            PriceResult(
                price = null,
                error = e.message
            )
        }
    }

    private fun formatPriceForService(
        price: Double
    ): String {

        return if (price >= 1000) {

            String.format(
                java.util.Locale.US,
                "%,.2f",
                price
            )

        } else {

            String.format(
                java.util.Locale.US,
                "%.8f",
                price
            )
        }
    }

    companion object {

        const val ACTION_START =
            "com.happytradingalarm.START_MONITOR"

        const val ACTION_STOP =
            "com.happytradingalarm.STOP_MONITOR"

        const val SERVICE_NOTIFICATION_ID =
            9001

        const val SERVICE_CHANNEL_ID =
            "happy_trading_background"

        private const val PREFS_NAME =
            "happy_trading_alarm_prefs"

        private const val ALERTS_KEY =
            "saved_alerts"
    }
}
