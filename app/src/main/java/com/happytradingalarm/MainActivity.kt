package com.happytradingalarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

data class PriceAlert(
val symbol: String,
val targetPrice: Double,
val direction: String,
var enabled: Boolean = true,
var triggered: Boolean = false
)

data class PriceResult(
val price: Double?,
val error: String? = null
)

data class AlarmSound(
val id: String,
val name: String,
val uriType: Int
)

class MainActivity : ComponentActivity() {

```
private var testRingtone: Ringtone? = null

private val notificationPermissionLauncher =
    registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    createAllNotificationChannels()
    requestNotificationPermission()

    setContent {
        MaterialTheme {
            HappyTradingAlarmScreen(
                context = this,
                onAlarmTriggered = { alert, currentPrice ->

                    playSelectedAlarmSound()

                    showPriceAlertNotification(
                        context = this,
                        alert = alert,
                        currentPrice = currentPrice
                    )
                }
            )
        }
    }
}

private fun requestNotificationPermission() {

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

        if (
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }
}

private fun playSelectedAlarmSound() {

    try {
        testRingtone?.stop()

        val uri =
            getSelectedAlarmSoundUri(this)

        if (uri != null) {

            testRingtone =
                RingtoneManager.getRingtone(
                    this,
                    uri
                )

            testRingtone?.play()
        }

    } catch (_: Exception) {
    }
}

private fun createAllNotificationChannels() {

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        return
    }

    val manager =
        getSystemService(
            NotificationManager::class.java
        )

    val sounds =
        getAlarmSounds()

    sounds.forEach { sound ->

        val channelId =
            getNotificationChannelId(sound.id)

        val existingChannel =
            manager.getNotificationChannel(channelId)

        if (existingChannel == null) {

            val channel =
                NotificationChannel(
                    channelId,
                    "Price Alerts — ${sound.name}",
                    NotificationManager.IMPORTANCE_HIGH
                )

            channel.description =
                "Happy Trading Alarm price alerts"

            channel.enableVibration(true)

            val uri =
                RingtoneManager.getDefaultUri(
                    sound.uriType
                )

            channel.setSound(
                uri,
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

override fun onDestroy() {

    testRingtone?.stop()
    testRingtone = null

    super.onDestroy()
}
```

}

@Composable
fun HappyTradingAlarmScreen(
context: Context,
onAlarmTriggered: (
PriceAlert,
Double
) -> Unit
) {

```
var selectedCoin by remember {
    mutableStateOf("BTC / USDT")
}

var menuExpanded by remember {
    mutableStateOf(false)
}

var targetPriceText by remember {
    mutableStateOf("")
}

var direction by remember {
    mutableStateOf("Above")
}

var currentPrice by remember {
    mutableStateOf<Double?>(null)
}

var connectionStatus by remember {
    mutableStateOf("Connecting...")
}

var backgroundMonitoring by remember {
    mutableStateOf(false)
}

var soundMenuExpanded by remember {
    mutableStateOf(false)
}

var selectedSoundId by remember {
    mutableStateOf(
        getSavedAlarmSoundId(context)
    )
}

val alerts = remember {
    mutableStateListOf<PriceAlert>()
}

val coins = listOf(
    "BTC / USDT",
    "ETH / USDT",
    "SOL / USDT",
    "XRP / USDT",
    "BNB / USDT",
    "DOGE / USDT"
)

val alarmSounds =
    remember {
        getAlarmSounds()
    }

val selectedSound =
    alarmSounds.firstOrNull {
        it.id == selectedSoundId
    } ?: alarmSounds.first()

LaunchedEffect(Unit) {

    val savedAlerts =
        loadAlerts(context)

    alerts.clear()
    alerts.addAll(savedAlerts)
}

LaunchedEffect(selectedCoin) {

    while (true) {

        val result =
            withContext(Dispatchers.IO) {
                getTabdealPrice(selectedCoin)
            }

        if (result.price != null) {

            currentPrice =
                result.price

            connectionStatus =
                "● Connected"

            alerts.forEachIndexed { index, alert ->

                if (
                    alert.enabled &&
                    !alert.triggered &&
                    alert.symbol == selectedCoin
                ) {

                    val reached =
                        if (alert.direction == "Above") {

                            result.price >=
                                alert.targetPrice

                        } else {

                            result.price <=
                                alert.targetPrice
                        }

                    if (reached) {

                        val triggeredAlert =
                            alert.copy(
                                triggered = true,
                                enabled = false
                            )

                        alerts[index] =
                            triggeredAlert

                        saveAlerts(
                            context,
                            alerts
                        )

                        onAlarmTriggered(
                            triggeredAlert,
                            result.price
                        )
                    }
                }
            }

        } else {

            connectionStatus =
                "● Connection failed"
        }

        delay(1000)
    }
}

LazyColumn(
    modifier =
        Modifier
            .fillMaxSize()
            .padding(20.dp),

    verticalArrangement =
        Arrangement.spacedBy(12.dp)
) {

    item {

        Text(
            text =
                "HAPPY TRADING ALARM",

            style =
                MaterialTheme.typography
                    .headlineSmall,

            fontWeight =
                FontWeight.Bold
        )

        Text(
            text =
                "Real-time Crypto Price Alert",

            style =
                MaterialTheme.typography
                    .bodyMedium
        )
    }

    item {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "Cryptocurrency",

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {
                        menuExpanded = true
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(selectedCoin)
                }

                DropdownMenu(
                    expanded =
                        menuExpanded,

                    onDismissRequest = {
                        menuExpanded = false
                    }
                ) {

                    coins.forEach { coin ->

                        DropdownMenuItem(
                            text = {
                                Text(coin)
                            },

                            onClick = {

                                selectedCoin =
                                    coin

                                menuExpanded =
                                    false

                                currentPrice =
                                    null

                                connectionStatus =
                                    "Connecting..."
                            }
                        )
                    }
                }
            }
        }
    }

    item {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text =
                        "CURRENT PRICE",

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        if (
                            currentPrice != null
                        ) {

                            formatPrice(
                                currentPrice!!
                            )

                        } else {

                            "--"
                        },

                    style =
                        MaterialTheme.typography
                            .headlineMedium,

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(connectionStatus)

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        "Auto refresh: 1 second",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }

    item {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "ALARM SOUND",

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {
                        soundMenuExpanded = true
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "🔔 ${selectedSound.name}"
                    )
                }

                DropdownMenu(
                    expanded =
                        soundMenuExpanded,

                    onDismissRequest = {
                        soundMenuExpanded = false
                    }
                ) {

                    alarmSounds.forEach { sound ->

                        DropdownMenuItem(
                            text = {
                                Text(sound.name)
                            },

                            onClick = {

                                selectedSoundId =
                                    sound.id

                                saveAlarmSoundId(
                                    context,
                                    sound.id
                                )

                                soundMenuExpanded =
                                    false
                            }
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                OutlinedButton(
                    onClick = {

                        playAlarmSoundForTest(
                            context,
                            selectedSoundId
                        )
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "▶ TEST SOUND"
                    )
                }

                Spacer(
                    modifier =
                        Modifier.height(6.dp)
                )

                Text(
                    text =
                        "Selected sound: ${selectedSound.name}",

                    style =
                        MaterialTheme.typography
                            .bodySmall
                )
            }
        }
    }

    item {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "BACKGROUND MONITORING",

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        if (backgroundMonitoring)
                            "● Monitoring Active"
                        else
                            "○ Monitoring Off"
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Button(
                    onClick = {

                        if (!backgroundMonitoring) {

                            val intent =
                                android.content.Intent(
                                    context,
                                    PriceMonitorService::class.java
                                ).apply {
                                    action =
                                        PriceMonitorService
                                            .ACTION_START
                                }

                            if (
                                Build.VERSION.SDK_INT >=
                                Build.VERSION_CODES.O
                            ) {

                                context.startForegroundService(
                                    intent
                                )

                            } else {

                                context.startService(
                                    intent
                                )
                            }

                            backgroundMonitoring =
                                true

                        } else {

                            val intent =
                                android.content.Intent(
                                    context,
                                    PriceMonitorService::class.java
                                ).apply {
                                    action =
                                        PriceMonitorService
                                            .ACTION_STOP
                                }

                            context.startService(intent)

                            backgroundMonitoring =
                                false
                        }
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        if (backgroundMonitoring)
                            "STOP BACKGROUND MONITORING"
                        else
                            "START BACKGROUND MONITORING"
                    )
                }
            }
        }
    }

    item {

        Card(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.padding(16.dp)
            ) {

                Text(
                    text =
                        "CREATE PRICE ALERT",

                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                OutlinedTextField(
                    value =
                        targetPriceText,

                    onValueChange = {
                        targetPriceText =
                            it
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    label = {
                        Text("Target Price")
                    },

                    singleLine = true
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    OutlinedButton(
                        onClick = {
                            direction =
                                "Above"
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            if (
                                direction ==
                                "Above"
                            ) {
                                "✓ Above"
                            } else {
                                "Above"
                            }
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            direction =
                                "Below"
                        },

                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            if (
                                direction ==
                                "Below"
                            ) {
                                "✓ Below"
                            } else {
                                "Below"
                            }
                        )
                    }
                }

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Button(
                    onClick = {

                        val price =
                            targetPriceText
                                .replace(",", "")
                                .trim()
                                .toDoubleOrNull()

                        if (
                            price != null &&
                            price > 0
                        ) {

                            val newAlert =
                                PriceAlert(
                                    symbol =
                                        selectedCoin,

                                    targetPrice =
                                        price,

                                    direction =
                                        direction
                                )

                            alerts.add(
                                newAlert
                            )

                            saveAlerts(
                                context,
                                alerts
                            )

                            targetPriceText =
                                ""
                        }
                    },

                    modifier =
                        Modifier.fillMaxWidth()
                ) {

                    Text(
                        "🔔 CREATE ALERT"
                    )
                }
            }
        }
    }

    item {

        Text(
            text =
                "ACTIVE ALERTS",

            style =
                MaterialTheme.typography
                    .titleLarge,

            fontWeight =
                FontWeight.Bold
        )
    }

    if (alerts.isEmpty()) {

        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "No active alerts",

                    modifier =
                        Modifier.padding(16.dp)
                )
            }
        }

    } else {

        itemsIndexed(alerts) {
            index,
            alert ->

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp)
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column(
                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                text =
                                    alert.symbol,

                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                text =
                                    "${alert.direction} " +
                                    formatPrice(
                                        alert.targetPrice
                                    )
                            )

                            if (
                                alert.triggered
                            ) {

                                Text(
                                    text =
                                        "🔔 TRIGGERED",

                                    fontWeight =
                                        FontWeight.Bold
                                )

                            } else if (
                                alert.enabled
                            ) {

                                Text(
                                    text =
                                        "Waiting..."
                                )

                            } else {

                                Text(
                                    text =
                                        "Disabled"
                                )
                            }
                        }

                        Switch(
                            checked =
                                alert.enabled,

                            onCheckedChange = {

                                val updatedAlert =
                                    alert.copy(
                                        enabled =
                                            it,

                                        triggered =
                                            false
                                    )

                                alerts[index] =
                                    updatedAlert

                                saveAlerts(
                                    context,
                                    alerts
                                )
                            }
                        )
                    }

                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {

                            alerts.removeAt(
                                index
                            )

                            saveAlerts(
                                context,
                                alerts
                            )
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text("DELETE")
                    }
                }
            }
        }
    }
}
```

}

/*

* ---
* ALARM SOUND
* ---

*/

private const val ALARM_SOUND_PREFS =
"happy_trading_alarm_prefs"

private const val ALARM_SOUND_KEY =
"selected_alarm_sound"

fun getAlarmSounds(): List<AlarmSound> {

```
return listOf(

    AlarmSound(
        id = "notification",
        name = "Default Notification",
        uriType =
            RingtoneManager.TYPE_NOTIFICATION
    ),

    AlarmSound(
        id = "alarm",
        name = "Default Alarm",
        uriType =
            RingtoneManager.TYPE_ALARM
    ),

    AlarmSound(
        id = "ringtone",
        name = "Default Ringtone",
        uriType =
            RingtoneManager.TYPE_RINGTONE
    )
)
```

}

fun getSavedAlarmSoundId(
context: Context
): String {

```
return context
    .getSharedPreferences(
        ALARM_SOUND_PREFS,
        Context.MODE_PRIVATE
    )
    .getString(
        ALARM_SOUND_KEY,
        "notification"
    ) ?: "notification"
```

}

fun saveAlarmSoundId(
context: Context,
soundId: String
) {

```
context
    .getSharedPreferences(
        ALARM_SOUND_PREFS,
        Context.MODE_PRIVATE
    )
    .edit()
    .putString(
        ALARM_SOUND_KEY,
        soundId
    )
    .apply()
```

}

fun getSelectedAlarmSoundUri(
context: Context
): android.net.Uri? {

```
val selectedId =
    getSavedAlarmSoundId(context)

val sound =
    getAlarmSounds()
        .firstOrNull {
            it.id == selectedId
        }
        ?: getAlarmSounds().first()

return RingtoneManager.getDefaultUri(
    sound.uriType
)
```

}

fun playAlarmSoundForTest(
context: Context,
soundId: String
) {

```
try {

    val sound =
        getAlarmSounds()
            .firstOrNull {
                it.id == soundId
            }
            ?: getAlarmSounds().first()

    val uri =
        RingtoneManager.getDefaultUri(
            sound.uriType
        )

    val ringtone =
        RingtoneManager.getRingtone(
            context,
            uri
        )

    ringtone?.play()

} catch (_: Exception) {
}
```

}

fun getNotificationChannelId(
soundId: String
): String {

```
return when (soundId) {

    "alarm" ->
        "happy_trading_price_alerts_alarm"

    "ringtone" ->
        "happy_trading_price_alerts_ringtone"

    else ->
        "happy_trading_price_alerts_notification"
}
```

}

/*

* ---
* NOTIFICATION
* ---

*/

private var notificationId = 1000

fun showPriceAlertNotification(
context: Context,
alert: PriceAlert,
currentPrice: Double
) {

```
if (
    Build.VERSION.SDK_INT >=
    Build.VERSION_CODES.TIRAMISU
) {

    if (
        context.checkSelfPermission(
            Manifest.permission.POST_NOTIFICATIONS
        ) !=
        PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
}

val manager =
    context.getSystemService(
        Context.NOTIFICATION_SERVICE
    ) as NotificationManager

val selectedSoundId =
    getSavedAlarmSoundId(context)

val channelId =
    getNotificationChannelId(
        selectedSoundId
    )

val title =
    "🔔 ${alert.symbol} PRICE ALERT"

val message =
    "Price reached " +
        formatPrice(currentPrice) +
        " — target " +
        formatPrice(alert.targetPrice)

val builder =
    if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.O
    ) {

        android.app.Notification.Builder(
            context,
            channelId
        )

    } else {

        android.app.Notification.Builder(
            context
        )
            .setSound(
                getSelectedAlarmSoundUri(context)
            )
    }

builder
    .setSmallIcon(
        android.R.drawable.ic_dialog_alert
    )
    .setContentTitle(title)
    .setContentText(message)
    .setStyle(
        android.app.Notification.BigTextStyle()
            .bigText(message)
    )
    .setAutoCancel(true)
    .setPriority(
        android.app.Notification
            .PRIORITY_HIGH
    )

manager.notify(
    notificationId++,
    builder.build()
)
```

}

/*

* ---
* PERSISTENT ALERT STORAGE
* ---

*/

private const val PREFS_NAME =
"happy_trading_alarm_prefs"

private const val ALERTS_KEY =
"saved_alerts"

fun saveAlerts(
context: Context,
alerts: List<PriceAlert>
) {

```
try {

    val jsonArray =
        JSONArray()

    alerts.forEach { alert ->

        val jsonObject =
            JSONObject()

        jsonObject.put(
            "symbol",
            alert.symbol
        )

        jsonObject.put(
            "targetPrice",
            alert.targetPrice
        )

        jsonObject.put(
            "direction",
            alert.direction
        )

        jsonObject.put(
            "enabled",
            alert.enabled
        )

        jsonObject.put(
            "triggered",
            alert.triggered
        )

        jsonArray.put(
            jsonObject
        )
    }

    context
        .getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            ALERTS_KEY,
            jsonArray.toString()
        )
        .apply()

} catch (_: Exception) {
}
```

}

fun loadAlerts(
context: Context
): List<PriceAlert> {

```
return try {

    val preferences =
        context.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    val saved =
        preferences.getString(
            ALERTS_KEY,
            null
        )

    if (saved.isNullOrEmpty()) {
        return emptyList()
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

    emptyList()
}
```

}

/*

* ---
* TABDEAL API
* ---

*/

fun getTabdealPrice(
symbol: String
): PriceResult {

```
return try {

    val cleanSymbol =
        symbol
            .replace(" / ", "")
            .uppercase()

    val url =
        URL(
            "https://api1.tabdeal.org" +
                "/r/api/v1/trades" +
                "?symbol=$cleanSymbol&limit=1"
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

    val latestTrade =
        trades.getJSONObject(0)

    val price =
        latestTrade
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
```

}

/*

* ---
* PRICE FORMATTER
* ---

*/

fun formatPrice(
price: Double
): String {

```
val formatter =
    NumberFormat.getNumberInstance(
        Locale.US
    )

formatter.maximumFractionDigits =
    8

formatter.minimumFractionDigits =
    0

return formatter.format(price)
```

}
