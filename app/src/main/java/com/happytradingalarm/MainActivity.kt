package com.happytradingalarm

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    var targetPrice by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("Above") }

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
                text = "Crypto Price Alert",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth()
            ) {

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {

                    Text(
                        text = "Select Cryptocurrency",
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
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "Current Price",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$0.00",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Waiting for live price...",
                        style = MaterialTheme.typography.bodySmall
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
                        text = "Create Price Alert",
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = targetPrice,
                        onValueChange = {
                            targetPrice = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Target Price")
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {

                        OutlinedButton(
                            onClick = {
                                direction = "Above"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Above")
                        }

                        OutlinedButton(
                            onClick = {
                                direction = "Below"
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Below")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {

                            if (targetPrice.isNotBlank()) {

                                alerts.add(
                                    PriceAlert(
                                        symbol = selectedCoin,
                                        targetPrice = targetPrice,
                                        direction = direction
                                    )
                                )

                                targetPrice = ""
                            }

                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔  CREATE ALERT")
                    }
                }
            }
        }

        item {

            Text(
                text = "Active Alerts",
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

            items(alerts) { alert ->

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                            ) {

                                Text(
                                    text = alert.symbol,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "${alert.direction} ${alert.targetPrice}"
                                )
                            }

                            Switch(
                                checked = alert.enabled,
                                onCheckedChange = {
                                    alert.enabled = it
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
