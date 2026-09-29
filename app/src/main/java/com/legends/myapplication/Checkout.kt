package com.legends.myapplication

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Patterns
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import ai.luciq.compose.luciqPrivate
import org.json.JSONObject

fun detailsValid() = Store.name.isNotBlank() &&
    Patterns.EMAIL_ADDRESS.matcher(Store.email).matches() &&
    Patterns.PHONE.matcher(Store.phone).matches()

@Composable
private fun Page(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        content()
    }
}

@Composable
private fun EmptyCart() = Page("Your cart is empty") {
    Button({ Store.tab(Screen.Browse) }) { Text("Browse items") }
}

/** Cart lines plus the server-side cart created via POST /carts/add. */
@Composable
private fun CartSummary() {
    val lines = Store.cartLines()
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(lines, attempt) {
        Store.serverCart = null
        error = null
        Store.serverCart = try {
            postCart(lines)
        } catch (e: Exception) {
            error = e.message ?: e.toString()
            null
        }
    }
    lines.forEach { (p, qty) ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("$qty × ${p.title}", Modifier.weight(1f))
            Text(money(p.price * qty))
        }
    }
    HorizontalDivider()
    val cart = Store.serverCart
    when {
        error != null -> Row {
            Text("Couldn't create cart: $error", Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
            TextButton({ attempt++ }) { Text("Retry") }
        }
        cart == null -> CircularProgressIndicator()
        else -> {
            Text("Cart #${cart.id} · ${cart.totalQuantity} items · subtotal ${money(cart.total)}")
            Text("Total ${money(cart.discountedTotal)}", style = MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun DetailsForm() {
    Field("Full name", Store.name, KeyboardType.Text, valid = Store.name.isNotBlank()) { Store.name = it }
    Field("Email", Store.email, KeyboardType.Email, Patterns.EMAIL_ADDRESS.matcher(Store.email).matches()) { Store.email = it }
    Field("Phone", Store.phone, KeyboardType.Phone, Patterns.PHONE.matcher(Store.phone).matches()) { Store.phone = it }
}

@Composable
private fun Field(label: String, value: String, type: KeyboardType, valid: Boolean, onChange: (String) -> Unit) {
    val showError = value.isNotEmpty() && !valid
    OutlinedTextField(
        value, onChange,
        Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = showError,
        supportingText = if (showError) ({ Text("Enter a valid ${label.lowercase()}") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = type),
    )
}

@Composable
fun CartScreen() {
    if (Store.cart.isEmpty()) return EmptyCart()
    Page("Step 1 of 3 · Cart") {
        CartSummary()
        Button({ Store.go(Screen.YourDetails) }, Modifier.fillMaxWidth(), enabled = Store.serverCart != null) { Text("Continue") }
    }
}

@Composable
fun YourDetailsScreen() = Page("Step 2 of 3 · Your details") {
    DetailsForm()
    Button({ Store.go(Screen.Payment) }, Modifier.fillMaxWidth(), enabled = detailsValid()) { Text("Continue to payment") }
}

@Composable
fun PaymentScreen() {
    val cart = Store.serverCart ?: return EmptyCart()
    Column(Modifier.fillMaxSize()) {
        Text("Step 3 of 3 · Payment", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall)
        PaymentWebView(cart.discountedTotal, Modifier.fillMaxSize())
    }
}

@Composable
fun SinglePageCheckout() {
    if (Store.cart.isEmpty()) return EmptyCart()
    Page("Checkout") {
        CartSummary()
        Text("Your details", style = MaterialTheme.typography.titleMedium)
        DetailsForm()
        Text("Payment", style = MaterialTheme.typography.titleMedium)
        val cart = Store.serverCart
        if (cart != null && detailsValid()) PaymentWebView(cart.discountedTotal, Modifier.fillMaxWidth().height(440.dp))
        else Text("Fill in your details to continue to payment.", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun ConfirmationScreen(s: Screen.Confirmation) = Page("Booking confirmed") {
    Text("Order #${s.orderId}")
    Text("Paid ${money(s.total)}", style = MaterialTheme.typography.titleLarge)
    Text("A receipt is on its way to ${Store.email}.")
    Button({ Store.tab(Screen.Browse) }, Modifier.fillMaxWidth()) { Text("Back to browse") }
}

/** Exposed to the bundled payment page as `TripcartBridge`. JS calls arrive on a background thread. */
class PaymentBridge(private val checkoutJson: String, private val onPaid: () -> Unit) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun checkout() = checkoutJson

    @JavascriptInterface
    fun paid() {
        main.post(onPaid)
    }
}

/** Stands in for a partner's hosted payment page: bundled HTML that calls dummyjson itself on submit. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PaymentWebView(total: Double, modifier: Modifier) {
    AndroidView(
        modifier = modifier.luciqPrivate(), // card details: black box in screenshots and replays
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                val checkout = JSONObject().put("amount", total).put("email", Store.email).toString()
                addJavascriptInterface(PaymentBridge(checkout) { Store.finishOrder(total) }, "TripcartBridge")
                val html = context.assets.open("payment.html").bufferedReader().use { it.readText() }
                // An https base URL gives the page a real origin, so its fetch() is a normal CORS request.
                loadDataWithBaseURL("https://appassets.androidplatform.net/pay", html, "text/html", "utf-8", null)
            }
        },
        onRelease = WebView::destroy,
    )
}
