package com.legends.myapplication

import ai.luciq.library.Luciq
import ai.luciq.survey.Surveys
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/** Manually targeted survey from the Luciq dashboard, shown the first time History is opened. */
private const val HISTORY_SURVEY_TOKEN = "igAwVHB8eQZfXX154ZLG9w"

data class OrderLine(val title: String, val quantity: Int, val unitPrice: Double, val thumbnail: String)

data class Order(val id: Int, val total: Double, val placedAt: Long, val lines: List<OrderLine>)

/** Completed orders, newest first, persisted as JSON in SharedPreferences so they survive restarts. */
object OrderHistory {
    val orders = mutableStateListOf<Order>()
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("orders", Context.MODE_PRIVATE)
        orders.clear()
        orders.addAll(runCatching { decode(prefs.getString("orders", null)) }.getOrDefault(emptyList()))
    }

    fun add(order: Order) {
        orders.add(0, order)
        prefs.edit { putString("orders", encode(orders)) }
    }

    /** Per install: cleared by uninstall or "Clear storage", not tied to a Luciq user identity. */
    var historySurveyShown: Boolean
        get() = prefs.getBoolean("history_survey_shown", false)
        set(value) = prefs.edit { putBoolean("history_survey_shown", value) }

    private fun encode(list: List<Order>) = JSONArray().apply {
        list.forEach { o ->
            val lines = JSONArray()
            o.lines.forEach { l ->
                lines.put(
                    JSONObject().put("title", l.title).put("quantity", l.quantity)
                        .put("unitPrice", l.unitPrice).put("thumbnail", l.thumbnail),
                )
            }
            put(JSONObject().put("id", o.id).put("total", o.total).put("placedAt", o.placedAt).put("lines", lines))
        }
    }.toString()

    private fun decode(json: String?): List<Order> {
        if (json == null) return emptyList()
        val arr = JSONArray(json)
        return List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val lines = o.getJSONArray("lines")
            Order(
                id = o.getInt("id"),
                total = o.getDouble("total"),
                placedAt = o.getLong("placedAt"),
                lines = List(lines.length()) { j ->
                    val l = lines.getJSONObject(j)
                    OrderLine(l.getString("title"), l.getInt("quantity"), l.getDouble("unitPrice"), l.optString("thumbnail"))
                },
            )
        }
    }
}

@Composable
fun HistoryScreen() {
    LaunchedEffect(Unit) {
        // Luciq is only initialised when a token is configured (see TripcartApp).
        if (BuildConfig.LUCIQ_APP_TOKEN.isEmpty()) return@LaunchedEffect
        Luciq.logUserEvent("viewed_history")
        if (!OrderHistory.historySurveyShown) {
            Surveys.showSurvey(HISTORY_SURVEY_TOKEN)
            OrderHistory.historySurveyShown = true
        }
    }

    val orders = OrderHistory.orders
    if (orders.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("No orders yet", style = MaterialTheme.typography.headlineSmall)
            Text("Orders you complete will show up here.", Modifier.padding(top = 8.dp))
            Button({ Store.tab(Screen.Browse) }, Modifier.padding(top = 16.dp)) { Text("Browse items") }
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Order history", style = MaterialTheme.typography.headlineSmall) }
        items(orders, key = { it.placedAt }) { OrderCard(it) }
    }
}

@Composable
private fun OrderCard(order: Order) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Order #${order.id}", style = MaterialTheme.typography.titleMedium)
                Text(money(order.total), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(order.placedAt)),
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            order.lines.forEach { line ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NetImage(line.thumbnail, Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)), line.title)
                    Text(
                        "${line.quantity} × ${line.title}",
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(money(line.unitPrice * line.quantity), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
