package com.legends.myapplication

import android.graphics.Bitmap
import ai.luciq.crash.CrashReporting
import ai.luciq.crash.models.LuciqNonFatalException
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

private const val TAG = "Chaos"

@Composable
fun ChaosScreen() {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Tap a card to trigger it.") }
    var asking by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("Suggest a weekend trip idea in one sentence.") }
    fun run(label: String, block: suspend () -> String) {
        status = "$label…"
        scope.launch {
            status = try {
                "$label: ${block()}"
            } catch (e: Exception) {
                Log.w(TAG, label, e)
                "$label failed: $e"
            }
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Chaos", style = MaterialTheme.typography.headlineSmall)
        Text(status, style = MaterialTheme.typography.bodyMedium)
        ChaosCard("Crash", "A fatal crash.") { throw RuntimeException("Chaos: deliberate unhandled crash") }
        ChaosCard("Handled error", "A caught exception that you report as a non-fatal.") {
            status = try {
                "not-a-number".toInt()
                "unreachable"
            } catch (e: NumberFormatException) {
                Log.e(TAG, "Handled error", e)
                CrashReporting.report(LuciqNonFatalException.Builder(e).build())
                "Handled error: caught, logged and reported $e"
            }
        }
        ChaosCard("Slow call", "GET https://dummyjson.com/products?delay=5000", code = true) {
            run("Slow call") { timed("https://dummyjson.com/products?delay=5000") }
        }
        ChaosCard("Server error", "GET https://dummyjson.com/http/500", code = true) {
            run("Server error") { timed("https://dummyjson.com/http/500") }
        }
        ChaosCard("Freeze", "Block the main thread for 5 seconds, to trigger an app hang or ANR.") {
            Thread.sleep(5_000)
            status = "Freeze: main thread was blocked for 5 s"
        }
        ChaosCard("Heavy list", "A list of 500 images.") { Store.go(Screen.HeavyList) }
        ChaosCard("Memory hog", "Allocate memory until the OS kills the app.") {
            startMemoryHog { mb -> status = "Memory hog: holding $mb MB" }
        }
        ChaosCard("Ask AI", "Send a question to a real LLM API and show the answer.") { asking = true }
    }
    if (asking) AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("Ask AI") },
        text = { OutlinedTextField(question, { question = it }, Modifier.fillMaxWidth(), label = { Text("Question") }) },
        confirmButton = {
            TextButton({
                asking = false
                run("Ask AI") { askAi(question) }
            }, enabled = question.isNotBlank()) { Text("Ask") }
        },
        dismissButton = { TextButton({ asking = false }) { Text("Cancel") } },
    )
}

@Composable
private fun ChaosCard(title: String, description: String, code: Boolean = false, onClick: () -> Unit) =
    Card(onClick, Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, fontFamily = if (code) FontFamily.Monospace else null)
        }
    }

private suspend fun timed(url: String): String {
    val start = System.nanoTime()
    val r = http(url)
    return "HTTP ${r.code} in ${(System.nanoTime() - start) / 1_000_000} ms\n${r.body.take(200)}"
}

private val hog = mutableListOf<Bitmap>() // never released
private var hogStarted = false

private fun startMemoryHog(onProgress: (Int) -> Unit) {
    if (hogStarted) return
    hogStarted = true
    thread(name = "memory-hog") {
        // Bitmap pixels live in native memory on API 26+, outside the Java heap limit, so this grows until
        // the low-memory killer takes the process. On API 24-25 it ends in an OutOfMemoryError instead.
        while (true) {
            hog += Bitmap.createBitmap(2048, 2048, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.RED) }
            onProgress(hog.size * 16)
            Thread.sleep(50)
        }
    }
}

/** Anthropic Messages API; endpoint, key and model come from local.properties via BuildConfig. */
private suspend fun askAi(question: String): String {
    if (BuildConfig.AI_API_KEY.isBlank()) return "set ai.apiKey in local.properties and rebuild."
    val body = JSONObject()
        .put("model", BuildConfig.AI_MODEL)
        .put("max_tokens", 16000)
        .put("output_config", JSONObject().put("effort", "low"))
        .put("fallbacks", "default") // re-run declined requests on Anthropic's recommended fallback model
        .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", question)))
    val r = http(
        BuildConfig.AI_ENDPOINT,
        body.toString(),
        mapOf(
            "x-api-key" to BuildConfig.AI_API_KEY,
            "anthropic-version" to "2023-06-01",
            "anthropic-beta" to "server-side-fallback-2026-07-01",
        ),
    )
    if (r.code != 200) {
        val message = runCatching { JSONObject(r.body).getJSONObject("error").getString("message") }.getOrDefault(r.body.take(300))
        return "HTTP ${r.code}: $message"
    }
    val json = JSONObject(r.body)
    if (json.optString("stop_reason") == "refusal") return "the model declined to answer."
    val content = json.getJSONArray("content")
    return (0 until content.length()).map(content::getJSONObject)
        .filter { it.optString("type") == "text" }
        .joinToString("") { it.getString("text") }
}

@Composable
fun HeavyListScreen() {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(500) { n ->
            NetImage(
                "https://picsum.photos/seed/$n/400/300",
                Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)),
                "Image $n",
            )
        }
    }
}
