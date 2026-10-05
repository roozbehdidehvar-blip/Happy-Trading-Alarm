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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

enum class AppPage {
    HOME,
    SETTINGS,
    ABOUT,
    CALCULATOR
}

enum class AppTheme {
    SYSTEM,
    LIGHT,
    DARK
}

class MainActivity : ComponentActivity() {

    /*
     * Ringtone controller for TEST SOUND.
     * TEST and STOP always use the same ringtone instance.
     */
    private var testRingtone: Ringtone? = null

    /*
     * Separate ringtone for a real triggered price alarm.
     */
    private var alarmRingtone: Ringtone? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createAllNotificationChannels()
        requestNotificationPermission()

        setContent {
            HappyTradingAlarmApp(
                context = this,

                onAlarmTriggered = { alert, currentPrice ->

                    playSelectedAlarmSound()

                    showPriceAlertNotification(
                        context = this,
                        alert = alert,
                        currentPrice = currentPrice
                    )
                },

                onTestSound = { soundId ->
                    playTestAlarmSound(soundId)
                },

                onStopTestSound = {
                    stopTestAlarmSound()
                }
            )
        }
    }

    private fun requestNotificationPermission() {

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {

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

    /*
     * Start TEST SOUND.
     */
    private fun playTestAlarmSound(
        soundId: String
    ) {

        try {

            stopTestAlarmSound()

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

    /*
     * Stop the exact ringtone used by TEST SOUND.
     */
    private fun stopTestAlarmSound() {

        try {
            testRingtone?.stop()
        } catch (_: Exception) {
        }

        testRingtone = null
    }

    /*
     * Real price-triggered alarm.
     */
    private fun playSelectedAlarmSound() {

        try {

            alarmRingtone?.stop()

            val uri =
                getSelectedAlarmSoundUri(this)

            if (uri != null) {

                alarmRingtone =
                    RingtoneManager.getRingtone(
                        this,
                        uri
                    )

                alarmRingtone?.play()
            }

        } catch (_: Exception) {
        }
    }

    /*
     * Create notification channels.
     */
    private fun createAllNotificationChannels() {

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

            if (
                manager.getNotificationChannel(
                    channelId
                ) == null
            ) {

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

        stopTestAlarmSound()

        try {
            alarmRingtone?.stop()
        } catch (_: Exception) {
        }

        alarmRingtone = null

        super.onDestroy()
    }
}

@Composable
fun HappyTradingAlarmApp(
    context: Context,
    onAlarmTriggered:
        (PriceAlert, Double) -> Unit,
    onTestSound:
        (String) -> Unit,
    onStopTestSound:
        () -> Unit
) {

    var page by remember {
        mutableStateOf(AppPage.HOME)
    }

    var appTheme by remember {
        mutableStateOf(
            loadAppTheme(context)
        )
    }

    val systemDark =
        isSystemInDarkTheme()

    val darkTheme =
        when (appTheme) {

            AppTheme.SYSTEM ->
                systemDark

            AppTheme.LIGHT ->
                false

            AppTheme.DARK ->
                true
        }

    val colors =
        if (darkTheme) {
            darkColorScheme()
        } else {
            lightColorScheme()
        }

    MaterialTheme(
        colorScheme = colors
    ) {

        when (page) {

            AppPage.HOME -> {

                HappyTradingAlarmScreen(
                    context = context,

                    onAlarmTriggered =
                        onAlarmTriggered,

                    onOpenSettings = {
                        page =
                            AppPage.SETTINGS
                    },

                    onOpenCalculator = {
                        page =
                            AppPage.CALCULATOR
                    }
                )
            }

            AppPage.SETTINGS -> {

                SettingsScreen(
                    context = context,

                    currentTheme =
                        appTheme,

                    onThemeChanged = {
                        appTheme = it

                        saveAppTheme(
                            context,
                            it
                        )
                    },

                    onOpenAbout = {
                        page =
                            AppPage.ABOUT
                    },

                    onBack = {
                        page =
                            AppPage.HOME
                    },

                    onTestSound =
                        onTestSound,

                    onStopTestSound =
                        onStopTestSound
                )
            }

            AppPage.ABOUT -> {

                AboutScreen(
                    onBack = {
                        page =
                            AppPage.SETTINGS
                    }
                )
            }

            AppPage.CALCULATOR -> {

                TradingCalculatorScreen(
                    onBack = {
                        page =
                            AppPage.HOME
                    }
                )
            }
        }
    }
}

@Composable
fun HappyTradingAlarmScreen(
    context: Context,
    onAlarmTriggered:
        (PriceAlert, Double) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenCalculator: () -> Unit
) {

    var selectedCoin by remember {
        mutableStateOf(
            "BTC / USDT"
        )
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

    val alerts =
        remember {
            mutableStateListOf<PriceAlert>()
        }

    val coins =
        listOf(
            "BTC / USDT",
            "ETH / USDT",
            "SOL / USDT",
            "XRP / USDT",
            "BNB / USDT",
            "DOGE / USDT"
        )

    /*
     * Load saved alerts.
     */
    LaunchedEffect(Unit) {

        val savedAlerts =
            loadAlerts(context)

        alerts.clear()

        alerts.addAll(
            savedAlerts
        )
    }

    /*
     * Real-time foreground price monitoring.
     */
    LaunchedEffect(selectedCoin) {

        while (true) {

            val result =
                withContext(
                    Dispatchers.IO
                ) {

                    getTabdealPrice(
                        selectedCoin
                    )
                }

            if (result.price != null) {

                val price =
                    result.price

                currentPrice =
                    price

                connectionStatus =
                    "● Connected"

                alerts.forEachIndexed {
                        index,
                        alert ->

                    if (
                        alert.enabled &&
                        !alert.triggered &&
                        alert.symbol ==
                        selectedCoin
                    ) {

                        val reached =
                            if (
                                alert.direction ==
                                "Above"
                            ) {

                                price >=
                                    alert.targetPrice

                            } else {

                                price <=
                                    alert.targetPrice
                            }

                        if (reached) {

                            val triggeredAlert =
                                alert.copy(
                                    triggered =
                                        true,

                                    enabled =
                                        false
                                )

                            alerts[index] =
                                triggeredAlert

                            saveAlerts(
                                context,
                                alerts
                            )

                            onAlarmTriggered(
                                triggeredAlert,
                                price
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

        /*
         * HEADER
         */
        item {

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
                        "HAPPY TRADING ALARM",

                        style =
                            MaterialTheme
                                .typography
                                .headlineSmall,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        "Real-time Crypto Price Alert"
                    )
                }

                OutlinedButton(
                    onClick =
                        onOpenSettings
                ) {

                    Text("⚙")
                }
            }
        }

        /*
         * COIN SELECTOR
         */
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
                        "Cryptocurrency",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            menuExpanded =
                                true
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            selectedCoin
                        )
                    }

                    DropdownMenu(
                        expanded =
                            menuExpanded,

                        onDismissRequest = {
                            menuExpanded =
                                false
                        }
                    ) {

                        coins.forEach {
                            coin ->

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

        /*
         * CURRENT PRICE
         */
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
                        "CURRENT PRICE",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        currentPrice?.let {
                            formatPrice(it)
                        } ?: "--",

                        style =
                            MaterialTheme
                                .typography
                                .headlineMedium,

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        connectionStatus
                    )

                    Text(
                        "Auto refresh: 1 second",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            }
        }

        /*
         * CREATE PRICE ALERT
         *
         * Creating an alert automatically starts
         * background monitoring.
         */
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
                        "CREATE PRICE ALERT",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
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
                        Modifier.height(10.dp)
                    )

                    Button(
                        onClick = {

                            val price =
                                parseNumber(
                                    targetPriceText
                                )

                            if (
                                price != null &&
                                price > 0
                            ) {

                                /*
                                 * Save new alert.
                                 */
                                alerts.add(
                                    PriceAlert(
                                        symbol =
                                            selectedCoin,

                                        targetPrice =
                                            price,

                                        direction =
                                            direction,

                                        enabled =
                                            true,

                                        triggered =
                                            false
                                    )
                                )

                                saveAlerts(
                                    context,
                                    alerts
                                )

                                /*
                                 * IMPORTANT:
                                 * Automatically start background
                                 * monitoring when a new alert is created.
                                 */
                                try {

                                    val intent =
                                        android.content.Intent(
                                            context,
                                            PriceMonitorService::class.java
                                        ).apply {

                                            action =
                                                PriceMonitorService.ACTION_START
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

                                } catch (_: Exception) {
                                }

                                /*
                                 * Clear input.
                                 */
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

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "Background monitoring starts automatically.",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            }
        }

        /*
         * TRADING TOOLS
         */
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
                        "TRADING TOOLS",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick =
                            onOpenCalculator,

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "🧮 PROFIT / LOSS & LIQUIDATION"
                        )
                    }
                }
            }
        }

        /*
         * ACTIVE ALERTS
         */
        item {

            Text(
                "ACTIVE ALERTS",

                style =
                    MaterialTheme
                        .typography
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
                        "No active alerts",

                        modifier =
                            Modifier.padding(16.dp)
                    )
                }
            }

        } else {

            itemsIndexed(
                alerts
            ) { index, alert ->

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
                                    alert.symbol,
                                    fontWeight =
                                        FontWeight.Bold
                                )

                                Text(
                                    "${alert.direction} ${
                                        formatPrice(
                                            alert.targetPrice
                                        )
                                    }"
                                )

                                Text(
                                    when {

                                        alert.triggered ->
                                            "🔔 TRIGGERED"

                                        alert.enabled ->
                                            "Waiting..."

                                        else ->
                                            "Disabled"
                                    }
                                )
                            }

                            Switch(
                                checked =
                                    alert.enabled,

                                onCheckedChange = {

                                    alerts[index] =
                                        alert.copy(
                                            enabled =
                                                it,

                                            triggered =
                                                false
                                        )

                                    saveAlerts(
                                        context,
                                        alerts
                                    )
                                }
                            )
                        }

                        Spacer(
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

                            Text(
                                "DELETE"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    context: Context,
    currentTheme: AppTheme,
    onThemeChanged:
        (AppTheme) -> Unit,
    onOpenAbout: () -> Unit,
    onBack: () -> Unit,
    onTestSound:
        (String) -> Unit,
    onStopTestSound:
        () -> Unit
) {

    var soundMenuExpanded by remember {
        mutableStateOf(false)
    }

    var selectedSoundId by remember {
        mutableStateOf(
            getSavedAlarmSoundId(context)
        )
    }

    val sounds =
        remember {
            getAlarmSounds()
        }

    val selectedSound =
        sounds.firstOrNull {
            it.id == selectedSoundId
        } ?: sounds.first()

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        /*
         * SETTINGS HEADER
         */
        item {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                OutlinedButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "← BACK"
                    )
                }

                Spacer(
                    Modifier.weight(1f)
                )

                Text(
                    "SETTINGS",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        /*
         * ALARM SOUND
         */
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
                        "🔔 ALARM SOUND",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            soundMenuExpanded =
                                true
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            selectedSound.name
                        )
                    }

                    DropdownMenu(
                        expanded =
                            soundMenuExpanded,

                        onDismissRequest = {
                            soundMenuExpanded =
                                false
                        }
                    ) {

                        sounds.forEach {
                            sound ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        sound.name
                                    )
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

                                    onStopTestSound()
                                }
                            )
                        }
                    }

                    Spacer(
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
                                onTestSound(
                                    selectedSoundId
                                )
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                "▶ TEST SOUND"
                            )
                        }

                        OutlinedButton(
                            onClick =
                                onStopTestSound,

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                "■ STOP SOUND"
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Test the selected alarm sound. " +
                            "STOP SOUND immediately stops the test.",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )
                }
            }
        }

        /*
         * APPEARANCE
         */
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
                        "🌓 APPEARANCE",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    ThemeChoice(
                        text =
                            "System Default",

                        selected =
                            currentTheme ==
                                AppTheme.SYSTEM,

                        onClick = {
                            onThemeChanged(
                                AppTheme.SYSTEM
                            )
                        }
                    )

                    ThemeChoice(
                        text =
                            "Light",

                        selected =
                            currentTheme ==
                                AppTheme.LIGHT,

                        onClick = {
                            onThemeChanged(
                                AppTheme.LIGHT
                            )
                        }
                    )

                    ThemeChoice(
                        text =
                            "Dark",

                        selected =
                            currentTheme ==
                                AppTheme.DARK,

                        onClick = {
                            onThemeChanged(
                                AppTheme.DARK
                            )
                        }
                    )
                }
            }
        }

        /*
         * ABOUT
         */
        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Button(
                    onClick =
                        onOpenAbout,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                ) {

                    Text(
                        "ℹ ABOUT & INSTRUCTIONS"
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeChoice(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        RadioButton(
            selected =
                selected,

            onClick =
                onClick
        )

        Text(
            text = text,

            modifier =
                Modifier.padding(
                    start = 4.dp
                )
        )
    }
}

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {

    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp),

        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            OutlinedButton(
                onClick =
                    onBack
            ) {

                Text(
                    "← BACK"
                )
            }
        }

        item {

            Text(
                "ABOUT HAPPY TRADING ALARM",

                style =
                    MaterialTheme
                        .typography
                        .headlineSmall,

                fontWeight =
                    FontWeight.Bold
            )
        }

        /*
         * VERSION
         */
        item {

            Card(
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier =
                        Modifier.padding(16.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        "HAPPY TRADING ALARM",

                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(6.dp)
                    )

                    Text(
                        "Version 1.0.0"
                    )
                }
            }
        }

        /*
         * HOW TO USE
         */
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
                        "HOW TO USE",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Text(
                        "1. Select the cryptocurrency you want to monitor.\n\n" +
                            "2. Enter your target price.\n\n" +
                            "3. Select Above if you want an alert when the price " +
                            "reaches or exceeds your target.\n\n" +
                            "4. Select Below if you want an alert when the price " +
                            "reaches or falls below your target.\n\n" +
                            "5. Press CREATE ALERT.\n\n" +
                            "6. Background monitoring starts automatically when " +
                            "a new alert is created.\n\n" +
                            "7. You can enable or disable individual alerts from " +
                            "the ACTIVE ALERTS section.\n\n" +
                            "8. Alarm sound and appearance settings are available " +
                            "from SETTINGS.\n\n" +
                            "9. TRADING CALCULATOR provides profit/loss and " +
                            "estimated liquidation calculations."
                    )
                }
            }
        }

        /*
         * DEVELOPER
         */
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
                        "DEVELOPER",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Dr.Rouzbeh Didehvar"
                    )

                    Text(
                        "Tell: +989359747249"
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Version 1.0.0"
                    )

                    Spacer(
                        Modifier.height(12.dp)
                    )

                    Text(
                        "تبلیغات پذیرفته می شود."
                    )
                }
            }
        }

        /*
         * IMPORTANT
         */
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
                        "IMPORTANT",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "The liquidation price shown by the calculator " +
                            "is an estimate. Actual liquidation depends on " +
                            "exchange rules, maintenance margin, margin mode, " +
                            "fees and other position conditions."
                    )
                }
            }
        }
    }
}

@Composable
fun TradingCalculatorScreen(
    onBack: () -> Unit
) {

    var entryText by remember {
        mutableStateOf("")
    }

    var exitText by remember {
        mutableStateOf("")
    }

    var capitalText by remember {
        mutableStateOf("")
    }

    var leverageText by remember {
        mutableStateOf("1")
    }

    var calculatorDirection by remember {
        mutableStateOf("Long")
    }

    var resultText by remember {
        mutableStateOf("")
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

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                OutlinedButton(
                    onClick =
                        onBack
                ) {

                    Text(
                        "← BACK"
                    )
                }

                Spacer(
                    Modifier.weight(1f)
                )

                Text(
                    "CALCULATOR",

                    style =
                        MaterialTheme
                            .typography
                            .headlineSmall,

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        /*
         * PROFIT / LOSS
         */
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
                        "🧮 PROFIT / LOSS",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value =
                            entryText,

                        onValueChange = {
                            entryText =
                                it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Entry Price"
                            )
                        },

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value =
                            exitText,

                        onValueChange = {
                            exitText =
                                it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Exit Price"
                            )
                        },

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value =
                            capitalText,

                        onValueChange = {
                            capitalText =
                                it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Margin / Capital"
                            )
                        },

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    OutlinedTextField(
                        value =
                            leverageText,

                        onValueChange = {
                            leverageText =
                                it
                        },

                        modifier =
                            Modifier.fillMaxWidth(),

                        label = {
                            Text(
                                "Leverage"
                            )
                        },

                        singleLine = true
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                calculatorDirection =
                                    "Long"
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                if (
                                    calculatorDirection ==
                                    "Long"
                                ) {
                                    "✓ LONG"
                                } else {
                                    "LONG"
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                calculatorDirection =
                                    "Short"
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                if (
                                    calculatorDirection ==
                                    "Short"
                                ) {
                                    "✓ SHORT"
                                } else {
                                    "SHORT"
                                }
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Button(
                        onClick = {

                            val entry =
                                parseNumber(
                                    entryText
                                )

                            val exit =
                                parseNumber(
                                    exitText
                                )

                            val capital =
                                parseNumber(
                                    capitalText
                                )

                            val leverage =
                                parseNumber(
                                    leverageText
                                )

                            if (
                                entry != null &&
                                exit != null &&
                                entry > 0 &&
                                exit > 0
                            ) {

                                val lev =
                                    if (
                                        leverage != null &&
                                        leverage > 0
                                    ) {
                                        leverage
                                    } else {
                                        1.0
                                    }

                                val pnlPercent =
                                    if (
                                        calculatorDirection ==
                                        "Long"
                                    ) {

                                        (
                                            (exit - entry) /
                                                entry
                                            ) * 100.0

                                    } else {

                                        (
                                            (entry - exit) /
                                                entry
                                            ) * 100.0
                                    }

                                val pnlMoney =
                                    if (
                                        capital != null &&
                                        capital > 0
                                    ) {

                                        capital *
                                            (pnlPercent / 100.0) *
                                            lev

                                    } else {
                                        null
                                    }

                                val roi =
                                    if (
                                        capital != null &&
                                        capital > 0
                                    ) {

                                        pnlPercent * lev

                                    } else {
                                        null
                                    }

                                resultText =
                                    buildString {

                                        append(
                                            "Price P/L: ${
                                                formatDecimal(
                                                    pnlPercent
                                                )
                                            }%\n"
                                        )

                                        if (
                                            pnlMoney != null
                                        ) {

                                            append(
                                                "Estimated P/L: ${
                                                    formatDecimal(
                                                        pnlMoney
                                                    )
                                                }\n"
                                            )
                                        }

                                        if (
                                            roi != null
                                        ) {

                                            append(
                                                "Estimated ROI: ${
                                                    formatDecimal(
                                                        roi
                                                    )
                                                }%"
                                            )
                                        }
                                    }

                            } else {

                                resultText =
                                    "Please enter valid Entry and Exit prices."
                            }
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "CALCULATE P/L"
                        )
                    }

                    if (
                        resultText.isNotEmpty()
                    ) {

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Text(
                            resultText,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }

        /*
         * LIQUIDATION ESTIMATOR
         */
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
                        "⚠ LIQUIDATION ESTIMATOR",
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Text(
                        "Simplified isolated-margin estimate."
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {

                            val entry =
                                parseNumber(
                                    entryText
                                )

                            val leverage =
                                parseNumber(
                                    leverageText
                                )

                            if (
                                entry != null &&
                                entry > 0 &&
                                leverage != null &&
                                leverage > 0
                            ) {

                                val estimated =
                                    if (
                                        calculatorDirection ==
                                        "Long"
                                    ) {

                                        entry *
                                            (
                                                1.0 -
                                                    1.0 /
                                                        leverage
                                            )

                                    } else {

                                        entry *
                                            (
                                                1.0 +
                                                    1.0 /
                                                        leverage
                                            )
                                    }

                                resultText =
                                    "Estimated Liquidation Price: ${
                                        formatPrice(
                                            estimated
                                        )
                                    }\n\n" +
                                    "Approximation only. Actual liquidation " +
                                    "depends on exchange maintenance margin, " +
                                    "fees, margin mode and other rules."

                            } else {

                                resultText =
                                    "Enter a valid Entry Price and Leverage."
                            }
                        },

                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Text(
                            "ESTIMATE LIQUIDATION"
                        )
                    }
                }
            }
        }
    }
}

private const val ALARM_SOUND_PREFS =
    "happy_trading_alarm_prefs"

private const val ALARM_SOUND_KEY =
    "selected_alarm_sound"

private const val THEME_KEY =
    "app_theme"

private const val PREFS_NAME =
    "happy_trading_alarm_prefs"

private const val ALERTS_KEY =
    "saved_alerts"

fun getAlarmSounds(): List<AlarmSound> {

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
}

fun getSavedAlarmSoundId(
    context: Context
): String {

    return context
        .getSharedPreferences(
            ALARM_SOUND_PREFS,
            Context.MODE_PRIVATE
        )
        .getString(
            ALARM_SOUND_KEY,
            "notification"
        )
        ?: "notification"
}

fun saveAlarmSoundId(
    context: Context,
    soundId: String
) {

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
}

fun getSelectedAlarmSoundUri(
    context: Context
): android.net.Uri? {

    val selectedId =
        getSavedAlarmSoundId(
            context
        )

    val sound =
        getAlarmSounds()
            .firstOrNull {
                it.id == selectedId
            }
            ?: getAlarmSounds().first()

    return RingtoneManager.getDefaultUri(
        sound.uriType
    )
}

fun getNotificationChannelId(
    soundId: String
): String {

    return when (soundId) {

        "alarm" ->
            "happy_trading_price_alerts_alarm"

        "ringtone" ->
            "happy_trading_price_alerts_ringtone"

        else ->
            "happy_trading_price_alerts_notification"
    }
}

private var notificationId =
    1000

fun showPriceAlertNotification(
    context: Context,
    alert: PriceAlert,
    currentPrice: Double
) {

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

    val channelId =
        getNotificationChannelId(
            getSavedAlarmSoundId(
                context
            )
        )

    val title =
        "🔔 ${alert.symbol} PRICE ALERT"

    val message =
        "Price reached ${
            formatPrice(
                currentPrice
            )
        } — target ${
            formatPrice(
                alert.targetPrice
            )
        }"

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
                    getSelectedAlarmSoundUri(
                        context
                    )
                )
        }

    builder
        /*
         * Custom Happy Trading Alarm icon.
         */
       .setSmallIcon(
        com.happytradingalarm.R.drawable.ic_happy_trading_alarm
        )
         .setContentTitle(title)
         .setContentText(message)
         .setStyle(
            android.app.Notification.BigTextStyle()
                .bigText(message)
        )
        .setAutoCancel(true)
        .setPriority(
            android.app.Notification.PRIORITY_HIGH
        )

    manager.notify(
        notificationId++,
        builder.build()
    )
}

fun saveAlerts(
    context: Context,
    alerts: List<PriceAlert>
) {

    try {

        val jsonArray =
            JSONArray()

        alerts.forEach { alert ->

            val json =
                JSONObject()

            json.put(
                "symbol",
                alert.symbol
            )

            json.put(
                "targetPrice",
                alert.targetPrice
            )

            json.put(
                "direction",
                alert.direction
            )

            json.put(
                "enabled",
                alert.enabled
            )

            json.put(
                "triggered",
                alert.triggered
            )

            jsonArray.put(
                json
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
}

fun loadAlerts(
    context: Context
): List<PriceAlert> {

    return try {

        val saved =
            context
                .getSharedPreferences(
                    PREFS_NAME,
                    Context.MODE_PRIVATE
                )
                .getString(
                    ALERTS_KEY,
                    null
                )

        if (
            saved.isNullOrEmpty()
        ) {

            return emptyList()
        }

        val array =
            JSONArray(saved)

        val result =
            mutableListOf<PriceAlert>()

        for (
            i in 0 until array.length()
        ) {

            val obj =
                array.getJSONObject(i)

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
}

fun getTabdealPrice(
    symbol: String
): PriceResult {

    return try {

        val cleanSymbol =
            symbol
                .replace(
                    " / ",
                    ""
                )
                .uppercase(
                    Locale.US
                )

        val url =
            URL(
                "https://api1.tabdeal.org" +
                    "/r/api/v1/trades" +
                    "?symbol=$cleanSymbol&limit=1"
            )

        val connection =
            url.openConnection()
                as HttpURLConnection

        try {

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                8000

            connection.readTimeout =
                8000

            val responseCode =
                connection.responseCode

            if (
                responseCode != 200
            ) {

                return PriceResult(
                    price = null,
                    error =
                        "HTTP $responseCode"
                )
            }

            val response =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val trades =
                JSONArray(
                    response
                )

            if (
                trades.length() == 0
            ) {

                return PriceResult(
                    price = null,
                    error =
                        "No trades"
                )
            }

            val latestTrade =
                trades.getJSONObject(0)

            val price =
                latestTrade
                    .getString(
                        "price"
                    )
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

        } finally {

            connection.disconnect()
        }

    } catch (e: Exception) {

        PriceResult(
            price = null,
            error =
                e.message
        )
    }
}

fun formatPrice(
    price: Double
): String {

    val formatter =
        NumberFormat
            .getNumberInstance(
                Locale.US
            )

    formatter.maximumFractionDigits =
        8

    formatter.minimumFractionDigits =
        0

    return formatter.format(
        price
    )
}

fun parseNumber(
    text: String
): Double? {

    return text
        .replace(
            ",",
            ""
        )
        .trim()
        .toDoubleOrNull()
}

fun formatDecimal(
    value: Double
): String {

    return String.format(
        Locale.US,
        "%.2f",
        value
    )
}

fun loadAppTheme(
    context: Context
): AppTheme {

    val value =
        context
            .getSharedPreferences(
                ALARM_SOUND_PREFS,
                Context.MODE_PRIVATE
            )
            .getString(
                THEME_KEY,
                "SYSTEM"
            )

    return when (value) {

        "LIGHT" ->
            AppTheme.LIGHT

        "DARK" ->
            AppTheme.DARK

        else ->
            AppTheme.SYSTEM
    }
}

fun saveAppTheme(
    context: Context,
    theme: AppTheme
) {

    val value =
        when (theme) {

            AppTheme.SYSTEM ->
                "SYSTEM"

            AppTheme.LIGHT ->
                "LIGHT"

            AppTheme.DARK ->
                "DARK"
        }

    context
        .getSharedPreferences(
            ALARM_SOUND_PREFS,
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            THEME_KEY,
            value
        )
        .apply()
}
