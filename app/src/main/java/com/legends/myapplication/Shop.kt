package com.legends.myapplication

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

@Composable
fun BrowseScreen() {
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        if (Store.products.isNotEmpty()) return@LaunchedEffect
        error = null
        try {
            coroutineScope {
                val categories = async { fetchCategories() }
                val products = async { fetchProducts() }
                Store.categories = categories.await()
                Store.products = products.await()
            }
        } catch (e: Exception) {
            error = e.message ?: e.toString()
        }
    }
    when {
        error != null -> Retry("Couldn't load items: $error") { attempt++ }
        Store.products.isEmpty() -> Loading()
        else -> {
            // Sections come from the server's category list; categories with no items are skipped.
            val sections = Store.categories.mapNotNull { c ->
                Store.products.filter { it.category == c.slug }.takeIf { it.isNotEmpty() }?.let { c to it }
            }
            LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
                sections.forEach { (category, products) ->
                    item(key = category.slug) {
                        Text(category.name, Modifier.padding(16.dp, 12.dp), style = MaterialTheme.typography.titleLarge)
                        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(products, key = { it.id }) { ProductCard(it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductCard(p: Product) {
    Column(Modifier.width(160.dp).clickable { Store.go(Screen.Details(p)) }) {
        NetImage(p.thumbnail, Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)), p.title)
        Text(p.title, Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(money(p.price), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun DetailsScreen(p: Product) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        NetImage(p.images.firstOrNull() ?: p.thumbnail, Modifier.fillMaxWidth().height(300.dp), p.title)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(p.title, style = MaterialTheme.typography.headlineSmall)
            Text("${money(p.price)}  ·  ★ ${p.rating}", style = MaterialTheme.typography.titleMedium)
            Text(p.description, style = MaterialTheme.typography.bodyLarge)
            Button(
                onClick = {
                    Store.track("add_to_cart")
                    Store.cart.add(p)
                    Toast.makeText(context, "Added to cart", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("Add to cart") }
        }
    }
}

@Composable
fun Loading() = Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }

@Composable
fun Retry(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
        Text(message)
        Button(onRetry, Modifier.padding(top = 12.dp)) { Text("Retry") }
    }
}
