package com.legends.myapplication

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class HttpResult(val code: Int, val body: String)

/** Plain HttpURLConnection; POSTs JSON when [body] is set. Throws IOException on network failure. */
suspend fun http(url: String, body: String? = null, headers: Map<String, String> = emptyMap()): HttpResult =
    runInterruptible(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            headers.forEach(c::setRequestProperty)
            if (body != null) {
                c.requestMethod = "POST"
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = c.responseCode
            val stream = if (code < 400) c.inputStream else c.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            c.disconnect()
        }
    }

data class Category(val slug: String, val name: String)

data class Product(
    val id: Int,
    val title: String,
    val description: String,
    val price: Double,
    val rating: Double,
    val category: String,
    val thumbnail: String,
    val images: List<String>,
)

data class ServerCart(val id: Int, val total: Double, val discountedTotal: Double, val totalQuantity: Int)

private fun JSONArray.objects() = List(length()) { getJSONObject(it) }

private fun ok(r: HttpResult) = r.also { if (it.code !in 200..299) error("HTTP ${it.code}") }

suspend fun fetchCategories(): List<Category> =
    JSONArray(ok(http("https://dummyjson.com/products/categories")).body).objects()
        .map { Category(it.getString("slug"), it.getString("name")) }

suspend fun fetchProducts(): List<Product> =
    JSONObject(ok(http("https://dummyjson.com/products?limit=30")).body).getJSONArray("products").objects().map {
        val images = it.optJSONArray("images")
        Product(
            id = it.getInt("id"),
            title = it.getString("title"),
            description = it.getString("description"),
            price = it.getDouble("price"),
            rating = it.optDouble("rating", 0.0),
            category = it.getString("category"),
            thumbnail = it.getString("thumbnail"),
            images = if (images == null) emptyList() else List(images.length()) { i -> images.getString(i) },
        )
    }

suspend fun postCart(items: Map<Product, Int>): ServerCart {
    val products = JSONArray()
    items.forEach { (p, qty) -> products.put(JSONObject().put("id", p.id).put("quantity", qty)) }
    val json = JSONObject(ok(http("https://dummyjson.com/carts/add", JSONObject().put("userId", 1).put("products", products).toString())).body)
    return ServerCart(json.getInt("id"), json.getDouble("total"), json.getDouble("discountedTotal"), json.getInt("totalQuantity"))
}

fun money(v: Double) = "$" + "%.2f".format(v)

// ponytail: memory-only cache, no disk cache; add one if repeat launches need offline images.
private val imageCache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt()) {
    override fun sizeOf(key: String, value: Bitmap) = value.byteCount
}

@Composable
fun NetImage(url: String, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val bitmap by produceState(imageCache.get(url), url) {
        if (value == null) value = runCatching {
            runInterruptible(Dispatchers.IO) {
                val c = URL(url).openConnection().apply { connectTimeout = 15_000; readTimeout = 30_000 }
                c.getInputStream().use(BitmapFactory::decodeStream)
            }
        }.getOrNull()?.also { imageCache.put(url, it) }
    }
    val b = bitmap
    if (b == null) Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    else Image(b.asImageBitmap(), contentDescription, modifier, contentScale = ContentScale.Crop)
}
