package com.happytradingalarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

data class PriceAlert(
    val symbol: String,
    val targetPrice: String,
    val direction: String,
    var enabled: Boolean = true
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                HappyTradingAlarmScreen()
            }
        }
    }
}

@Composable
fun HappyTradingAlarmScreen() {

    var selectedCoin by remember { mutableStateOf("BTC / USDT") }
    var menuExpanded by remember { mutableStateOf(false) }

    var currentPrice by remember { mutableStateOf("--") }

    var status by remember {
        mutableStateOf("Starting connection test...")
    }

    var diagnostic by remember {
        mutableStateOf("Waiting...")
    }

    var lastUpdate by remember {
        mutableStateOf("--")
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

    LaunchedEffect(selectedCoin) {

        while (true) {

            val result = withContext(Dispatchers.IO) {
                testTabdeal(selectedCoin)
            }

            currentPrice = result.price
            status = result.status
            diagnostic = result.details
            lastUpdate = result.time

            delay(10000)
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
                text = "Tabdeal Connection Diagnostic",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))

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
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Current Price",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = currentPrice,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = status,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Last update: $lastUpdate"
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
                        text = "Diagnostic Result",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = diagnostic
                    )
                }
            }
        }

        item {

            Button(
                onClick = {
                    status = "Testing..."
                    diagnostic = "Running connection test..."
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("TEST CONNECTION")
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
                        text = "API Endpoints Tested",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text("1. /ping")
                    Text("2. /exchangeInfo")
                    Text("3. /trades")
                }
            }
        }
    }
}

data class TabdealResult(
    val price: String,
    val status: String,
    val details: String,
    val time: String
)

fun testTabdeal(symbol: String): TabdealResult {

    val cleanSymbol = symbol
        .replace(" / ", "")
        .uppercase()

    val baseUrl = "https://api1.tabdeal.org"

    return try {

        // -------------------------------------------------
        // TEST 1: PING
        // -------------------------------------------------

        val pingUrl =
            URL("$baseUrl/r/api/v1/ping")

        val pingConnection =
            pingUrl.openConnection() as HttpURLConnection

        pingConnection.requestMethod = "GET"
        pingConnection.connectTimeout = 8000
        pingConnection.readTimeout = 8000

        val pingCode = pingConnection.responseCode

        pingConnection.disconnect()

        if (pingCode != 200) {

            return TabdealResult(
                price = "--",
                status = "❌ PING FAILED",
                details = "HTTP $pingCode from /ping",
                time = nowTime()
            )
        }

        // -------------------------------------------------
        // TEST 2: EXCHANGE INFO
        // -------------------------------------------------

        val infoUrl =
            URL("$baseUrl/r/api/v1/exchangeInfo?symbol=$cleanSymbol")

        val infoConnection =
            infoUrl.openConnection() as HttpURLConnection

        infoConnection.requestMethod = "GET"
        infoConnection.connectTimeout = 8000
        infoConnection.readTimeout = 8000

        val infoCode = infoConnection.responseCode

        val infoResponse =
            infoConnection.inputStream
                .bufferedReader()
                .use { it.readText() }

        infoConnection.disconnect()

        if (infoCode != 200) {

            return TabdealResult(
                price = "--",
                status = "❌ EXCHANGE INFO FAILED",
                details = "HTTP $infoCode\n$infoResponse",
                time = nowTime()
            )
        }

        if (infoResponse.isBlank()) {

            return TabdealResult(
                price = "--",
                status = "❌ EMPTY RESPONSE",
                details = "exchangeInfo returned empty data",
                time = nowTime()
            )
        }

        // -------------------------------------------------
        // TEST 3: TRADES
        // -------------------------------------------------

        val tradesUrl =
            URL(
                "$baseUrl/r/api/v1/trades" +
                        "?symbol=$cleanSymbol&limit=1"
            )

        val tradesConnection =
            tradesUrl.openConnection() as HttpURLConnection

        tradesConnection.requestMethod = "GET"
        tradesConnection.connectTimeout = 8000
        tradesConnection.readTimeout = 8000

        val tradesCode = tradesConnection.responseCode

        val tradesResponse =
            tradesConnection.inputStream
                .bufferedReader()
                .use { it.readText() }

        tradesConnection.disconnect()

        if (tradesCode != 200) {

            return TabdealResult(
                price = "--",
                status = "❌ TRADES FAILED",
                details = "HTTP $tradesCode\n$tradesResponse",
                time = nowTime()
            )
        }

        if (tradesResponse.isBlank()) {

            return TabdealResult(
                price = "--",
                status = "❌ EMPTY TRADES",
                details = "trades endpoint returned empty data",
                time = nowTime()
            )
        }

        val trades =
            JSONArray(tradesResponse)

        if (trades.length() == 0) {

            return TabdealResult(
                price = "--",
                status = "❌ NO TRADES",
                details = "No trades returned for $cleanSymbol",
                time = nowTime()
            )
        }

        val latestTrade =
            trades.getJSONObject(0)

        val price =
            latestTrade.getString("price")

        TabdealResult(
            price = price,
            status = "✅ CONNECTED",
            details =
                "Tabdeal API is reachable.\n" +
                "Symbol: $cleanSymbol\n" +
                "Ping: OK\n" +
                "ExchangeInfo: OK\n" +
                "Trades: OK",
            time = nowTime()
        )

    } catch (e: java.net.UnknownHostException) {

        TabdealResult(
            price = "--",
            status = "❌ DNS ERROR",
            details =
                "Cannot resolve api1.tabdeal.org\n\n" +
                e.message,
            time = nowTime()
        )

    } catch (e: java.net.SocketTimeoutException) {

        TabdealResult(
            price = "--",
            status = "❌ TIMEOUT",
            details =
                "Connection timed out.\n\n" +
                e.message,
            time = nowTime()
        )

    } catch (e: javax.net.ssl.SSLException) {

        TabdealResult(
            price = "--",
            status = "❌ SSL ERROR",
            details =
                "SSL/TLS connection failed.\n\n" +
                e.message,
            time = nowTime()
        )

    } catch (e: Exception) {

        TabdealResult(
            price = "--",
            status = "❌ CONNECTION ERROR",
            details =
                "${e.javaClass.simpleName}\n\n" +
                (e.message ?: "Unknown error"),
            time = nowTime()
        )
    }
}

fun nowTime(): String {

    val formatter =
        java.text.SimpleDateFormat(
            "HH:mm:ss",
            java.util.Locale.getDefault()
        )

    return formatter.format(
        java.util.Date()
    )
}
