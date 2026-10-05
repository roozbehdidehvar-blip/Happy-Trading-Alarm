package com.happytradingalarm

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {

    private var toneGenerator: ToneGenerator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        toneGenerator = ToneGenerator(
            AudioManager.STREAM_ALARM,
            100
        )

        setContent {
            MaterialTheme {
                HappyTradingAlarmScreen(
                    onAlarmTriggered = {
                        playAlarmSound()
                    }
                )
            }
        }
    }

    private fun playAlarmSound() {
        toneGenerator?.startTone(
            ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,
            1200
        )
    }

    override fun onDestroy() {
        toneGenerator?.release()
        toneGenerator = null
        super.onDestroy()
    }
}

@Composable
fun HappyTradingAlarmScreen(
    onAlarmTriggered: () -> Unit
) {

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

    /*
     * Price engine
     *
     * Every 5 seconds:
     * 1. Get real price from Tabdeal
     * 2. Update screen
     * 3. Check active alerts
     */

    LaunchedEffect(selectedCoin) {

        while (true) {

            val result = withContext(Dispatchers.IO) {
                getTabdealPrice(selectedCoin)
            }

            if (result.price != null) {

                currentPrice = result.price
                connectionStatus = "● Connected"

                /*
                 * Check alerts belonging to current coin.
                 */

                alerts.forEachIndexed { index, alert ->

                    if (
                        alert.enabled &&
                        !alert.triggered &&
                        alert.symbol == selectedCoin
                    ) {

                        val reached =
                            if (alert.direction == "Above") {

                                result.price >= alert.targetPrice

                            } else {

                                result.price <= alert.targetPrice
                            }

                        if (reached) {

                            alerts[index] =
                                alert.copy(
                                    triggered = true,
                                    enabled = false
                                )

                            onAlarmTriggered()
                        }
                    }
                }

            } else {

                connectionStatus =
                    "● Connection failed"
            }

            delay(5000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {

            Text(
                text = "HAPPY TRADING ALARM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Real-time Crypto Price Alert",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Cryptocurrency",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            menuExpanded = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text(selectedCoin)
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
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

                                    selectedCoin = coin
                                    menuExpanded = false
                                    currentPrice = null
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
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "CURRENT PRICE",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text =
                            if (currentPrice != null) {
                                formatPrice(currentPrice!!)
                            } else {
                                "--"
                            },
                        style =
                            MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(6.dp)
                    )

                    Text(connectionStatus)

                    Spacer(
                        modifier = Modifier.height(4.dp)
                    )

                    Text(
                        text = "Auto refresh: 5 seconds",
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        item {

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "CREATE PRICE ALERT",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    OutlinedTextField(
                        value = targetPriceText,
                        onValueChange = {
                            targetPriceText = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Target Price")
                        },
                        singleLine = true
                    )

                    Spacer(
                        modifier = Modifier.height(10.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                direction = "Above"
                            },
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                if (direction == "Above")
                                    "✓ Above"
                                else
                                    "Above"
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                direction = "Below"
                            },
                            modifier = Modifier.weight(1f)
                        ) {

                            Text(
                                if (direction == "Below")
                                    "✓ Below"
                                else
                                    "Below"
                            )
                        }
                    }

                    Spacer(
                        modifier = Modifier.height(10.dp)
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

                                alerts.add(
                                    PriceAlert(
                                        symbol = selectedCoin,
                                        targetPrice = price,
                                        direction = direction
                                    )
                                )

                                targetPriceText = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {

                        Text("🔔 CREATE ALERT")
                    }
                }
            }
        }

        item {

            Text(
                text = "ACTIVE ALERTS",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        if (alerts.isEmpty()) {

            item {

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "No active alerts",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

        } else {

            itemsIndexed(alerts) { index, alert ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    text = alert.symbol,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text =
                                        "${alert.direction} " +
                                        formatPrice(
                                            alert.targetPrice
                                        )
                                )

                                if (alert.triggered) {

                                    Text(
                                        text = "🔔 TRIGGERED",
                                        fontWeight =
                                            FontWeight.Bold
                                    )

                                } else {

                                    Text(
                                        text = "Waiting..."
                                    )
                                }
                            }

                            Switch(
                                checked = alert.enabled,
                                onCheckedChange = {

                                    alerts[index] =
                                        alert.copy(
                                            enabled = it,
                                            triggered = false
                                        )
                                }
                            )
                        }

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        OutlinedButton(
                            onClick = {
                                alerts.removeAt(index)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text("DELETE")
                        }
                    }
                }
            }
        }
    }
}

fun getTabdealPrice(
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
                        "?symbol=$cleanSymbol&limit=1"
            )

        val connection =
            url.openConnection()
                    as HttpURLConnection

        connection.requestMethod = "GET"
        connection.connectTimeout = 8000
        connection.readTimeout = 8000

        val responseCode =
            connection.responseCode

        if (responseCode != 200) {

            connection.disconnect()

            return PriceResult(
                price = null,
                error = "HTTP $responseCode"
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

        if (trades.length() == 0) {

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
                error = "Invalid price"
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

fun formatPrice(
    price: Double
): String {

    val formatter =
        NumberFormat.getNumberInstance(
            Locale.US
        )

    formatter.maximumFractionDigits = 8
    formatter.minimumFractionDigits = 0

    return formatter.format(price)
}
