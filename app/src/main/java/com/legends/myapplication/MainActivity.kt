package com.legends.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import ai.luciq.compose.LuciqScreen
import com.legends.myapplication.ui.theme.MyApplicationTheme

sealed interface Screen {
    data object Browse : Screen
    data class Details(val product: Product) : Screen
    data object Cart : Screen // checkout A, step 1
    data object YourDetails : Screen // checkout A, step 2
    data object Payment : Screen // checkout A, step 3
    data object SinglePage : Screen // checkout B
    data class Confirmation(val orderId: Int, val total: Double) : Screen
    data object Chaos : Screen
    data object HeavyList : Screen
    data object Settings : Screen
}

// ponytail: process-wide state, lost on process death; move to a ViewModel + SavedState if that matters.
object Store {
    val stack = mutableStateListOf<Screen>(Screen.Browse)
    val cart = mutableStateListOf<Product>() // one entry per unit
    var categories by mutableStateOf<List<Category>>(emptyList())
    var products by mutableStateOf<List<Product>>(emptyList())
    var serverCart by mutableStateOf<ServerCart?>(null)
    var name by mutableStateOf("")
    var email by mutableStateOf("")
    var phone by mutableStateOf("")

    /** Feature flag: "A" = three-step checkout, "B" = single-page checkout. */
    var checkoutVariant by mutableStateOf("A")
        private set
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences("flags", Context.MODE_PRIVATE)
        checkoutVariant = prefs.getString("checkout_variant", "A")!!
    }

    fun setVariant(v: String) {
        checkoutVariant = v
        prefs.edit { putString("checkout_variant", v) }
    }

    fun cartLines(): Map<Product, Int> = cart.groupingBy { it }.eachCount()
    fun go(s: Screen) = stack.add(s)
    fun tab(s: Screen) { stack.clear(); stack.add(s) }
    fun back() { stack.removeAt(stack.lastIndex) }
    fun startCheckout() = tab(if (checkoutVariant == "B") Screen.SinglePage else Screen.Cart)
    fun finishOrder(total: Double) {
        val id = serverCart?.id ?: 0
        cart.clear()
        serverCart = null
        tab(Screen.Confirmation(id, total))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MyApplicationTheme { App() } }
    }
}

// Material icon paths, inlined to avoid the material-icons dependency.
private fun icon(path: String, mirror: Boolean = false) =
    ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f, autoMirror = mirror)
        .addPath(PathParser().parsePathString(path).toNodes(), fill = SolidColor(Color.Black)).build()

private val BackIcon = icon("M20,11H7.83l5.59,-5.59L12,4l-8,8 8,8 1.41,-1.41L7.83,13H20v-2z", mirror = true)
private val HomeIcon = icon("M10,20v-6h4v6h5v-8h3L12,3 2,12h3v8z")
private val CartIcon = icon("M7,18c-1.1,0 -1.99,0.9 -1.99,2S5.9,22 7,22s2,-0.9 2,-2 -0.9,-2 -2,-2zM1,2v2h2l3.6,7.59 -1.35,2.45c-0.16,0.28 -0.25,0.61 -0.25,0.96 0,1.1 0.9,2 2,2h12v-2L7.42,15c-0.14,0 -0.25,-0.11 -0.25,-0.25l0.03,-0.12 0.9,-1.63h7.45c0.75,0 1.41,-0.41 1.75,-1.03l3.58,-6.49c0.08,-0.14 0.12,-0.31 0.12,-0.48 0,-0.55 -0.45,-1 -1,-1L5.21,4l-0.94,-2L1,2zM17,18c-1.1,0 -1.99,0.9 -1.99,2s0.89,2 1.99,2 2,-0.9 2,-2 -0.9,-2 -2,-2z")
private val ChaosIcon = icon("M1,21h22L12,2 1,21zM13,18h-2v-2h2v2zM13,14h-2v-4h2v4z")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun App() {
    val screen = Store.stack.last()
    BackHandler(Store.stack.size > 1) { Store.back() }
    Scaffold(
        topBar = {
            TopAppBar(
                // Long-press the title to open the hidden settings screen.
                title = { Text("Tripcart", Modifier.combinedClickable(onClick = {}, onLongClick = { Store.go(Screen.Settings) })) },
                navigationIcon = {
                    if (Store.stack.size > 1) IconButton(onClick = Store::back) {
                        Icon(BackIcon, "Back")
                    }
                },
            )
        },
        bottomBar = {
            val root = Store.stack.first()
            NavigationBar {
                NavigationBarItem(
                    selected = root == Screen.Browse,
                    onClick = { Store.tab(Screen.Browse) },
                    icon = { Icon(HomeIcon, null) },
                    label = { Text("Browse") },
                )
                NavigationBarItem(
                    selected = root == Screen.Cart || root == Screen.SinglePage,
                    onClick = Store::startCheckout,
                    icon = {
                        BadgedBox(badge = { if (Store.cart.isNotEmpty()) Badge { Text("${Store.cart.size}") } }) {
                            Icon(CartIcon, null)
                        }
                    },
                    label = { Text("Cart") },
                )
                NavigationBarItem(
                    selected = root == Screen.Chaos,
                    onClick = { Store.tab(Screen.Chaos) },
                    icon = { Icon(ChaosIcon, null) },
                    label = { Text("Chaos") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // Custom navigation, so each screen is named for Luciq by hand.
            val name = screen::class.simpleName.orEmpty()
            key(name) { LuciqScreen(screenName = name) { ScreenContent(screen) } }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen) {
            when (screen) {
                Screen.Browse -> BrowseScreen()
                is Screen.Details -> DetailsScreen(screen.product)
                Screen.Cart -> CartScreen()
                Screen.YourDetails -> YourDetailsScreen()
                Screen.Payment -> PaymentScreen()
                Screen.SinglePage -> SinglePageCheckout()
                is Screen.Confirmation -> ConfirmationScreen(screen)
                Screen.Chaos -> ChaosScreen()
                Screen.HeavyList -> HeavyListScreen()
                Screen.Settings -> SettingsScreen()
            }
}

@Composable
fun SettingsScreen() {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Text("Checkout variant", style = MaterialTheme.typography.titleMedium)
        listOf("A" to "A: three-step checkout", "B" to "B: single-page checkout").forEach { (v, label) ->
            Row(
                Modifier.fillMaxWidth().selectable(Store.checkoutVariant == v, onClick = { Store.setVariant(v) }).padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(Store.checkoutVariant == v, onClick = null)
                Text(label, Modifier.padding(start = 12.dp))
            }
        }
        Text("Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", style = MaterialTheme.typography.bodySmall)
    }
}
