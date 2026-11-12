package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.DrawableRes
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ==========================================
// 1. DATA MODELS
// ==========================================

enum class FoodCategory(val title: String, val icon: ImageVector) {
    BURGER("Burger", Icons.Default.LunchDining),
    PIZZA("Pizza", Icons.Default.LocalPizza),
    CHICKEN("Chicken", Icons.Default.DinnerDining),
    FRIES("Fries", Icons.Default.Fastfood),
    DRINK("Drink", Icons.Default.LocalDrink)
}

enum class FoodSize(val label: String, val priceMultiplier: Double) {
    REGULAR("Regular", 1.0),
    LARGE("Large", 1.25),
    X_LARGE("X Large", 1.50)
}

data class FoodItem(
    val id: String,
    val name: String,
    val category: FoodCategory,
    val basePrice: Double,
    val rating: Double,
    val reviewsCount: Int,
    val description: String,
    val isSpicy: Boolean = false,
    val isPopular: Boolean = false,
    @DrawableRes val imageRes: Int,
    var isFavorite: Boolean = false
)

data class CustomizationOptions(
    val size: FoodSize = FoodSize.REGULAR,
    val addCheese: Boolean = false,
    val extraPatty: Boolean = false,
    val addBacon: Boolean = false,
    val quantity: Int = 1
) {
    fun calculatePrice(basePrice: Double): Double {
        var unitPrice = basePrice * size.priceMultiplier
        if (addCheese) unitPrice += 0.80
        if (extraPatty) unitPrice += 2.50
        if (addBacon) unitPrice += 1.20
        return unitPrice * quantity
    }
}

data class CartItem(
    val id: String,
    val foodItem: FoodItem,
    val options: CustomizationOptions,
    val totalPrice: Double
)

data class DeliveryAddress(
    val id: String,
    val label: String, // e.g. "Home", "Work", "Other"
    val fullAddress: String,
    val deliveryNote: String = "",
    val isDefault: Boolean = false
)

data class OrderRecord(
    val orderId: String,
    val dateFormatted: String,
    val itemsSummary: String,
    val itemCount: Int,
    val totalAmount: Double,
    val status: String, // "Delivered", "Preparing", "On the way"
    val deliveryAddressLabel: String
)

data class UserProfile(
    val name: String,
    val email: String,
    val phone: String,
    val loyaltyPoints: Int = 350
)

data class PromoBannerConfig(
    val tagText: String = "Limited Time Offer",
    val title: String = "Spicy Burger\nCombo",
    val discountPercent: Int = 20,
    val isEnabled: Boolean = true
)



// ==========================================
// 2. VIEWMODEL & STATE
// ==========================================

class HfcViewModel : ViewModel() {
    var showSplash by mutableStateOf(true)
        private set

    var selectedCategory by mutableStateOf(FoodCategory.BURGER)
    var searchQuery by mutableStateOf("")
    var isRefreshing by mutableStateOf(false)
        private set

    var selectedItemForDetail by mutableStateOf<FoodItem?>(null)
    var activeBottomNav by mutableStateOf(0) // 0: Home, 1: Offers, 2: Orders, 3: Favorite, 4: Profile, 5: Admin
    var isCartOpen by mutableStateOf(false)
    var isProfileOpen by mutableStateOf(false)
    var isAdminOpen by mutableStateOf(false)
    var isAdminAuthenticated by mutableStateOf(false)
    var showOrderSuccessDialog by mutableStateOf(false)

    // Admin Customizable Promo Banner
    var promoBanner by mutableStateOf(PromoBannerConfig())

    // User Profile Information
    var userProfile by mutableStateOf(
        UserProfile(
            name = "Alex Mahmud",
            email = "alex.hfc@fastfood.com",
            phone = "+1 (555) 234-8971",
            loyaltyPoints = 480
        )
    )


    // Saved Delivery Addresses
    val savedAddresses = mutableStateListOf<DeliveryAddress>(
        DeliveryAddress(
            id = "addr_1",
            label = "Home",
            fullAddress = "Apt 4B, 742 Evergreen Terrace, Springfield",
            deliveryNote = "Leave at front door & ring bell",
            isDefault = true
        ),
        DeliveryAddress(
            id = "addr_2",
            label = "Office",
            fullAddress = "Suite 300, 100 Innovation Blvd, Tech Park",
            deliveryNote = "Call when in the reception lobby",
            isDefault = false
        )
    )

    // Past Order History
    val pastOrders = mutableStateListOf<OrderRecord>(
        OrderRecord(
            orderId = "HFC-8921",
            dateFormatted = "Yesterday, 8:45 PM",
            itemsSummary = "1x Spicy Chicken Burger (Large), 1x Crispy Tavern Fries",
            itemCount = 2,
            totalAmount = 12.10,
            status = "Delivered",
            deliveryAddressLabel = "Home"
        ),
        OrderRecord(
            orderId = "HFC-8410",
            dateFormatted = "Nov 10, 2026, 1:20 PM",
            itemsSummary = "1x 8 Pcs Chicken Bucket, 2x Chilled Ice Cola",
            itemCount = 3,
            totalAmount = 19.97,
            status = "Delivered",
            deliveryAddressLabel = "Office"
        )
    )

    // Current detail customization state
    var currentOptions by mutableStateOf(CustomizationOptions())


    val itemsList = mutableStateListOf<FoodItem>(
        FoodItem(
            id = "c1",
            name = "Spicy Chicken Burger",
            category = FoodCategory.BURGER,
            basePrice = 6.49,
            rating = 4.8,
            reviewsCount = 120,
            description = "Crispy spicy chicken patty with fresh lettuce, tomato, onion and our fiery special HFC hot sauce.",
            isSpicy = true,
            isPopular = true,
            imageRes = R.drawable.food_chicken_burger
        ),
        FoodItem(
            id = "c2",
            name = "Cheese Burger",
            category = FoodCategory.BURGER,
            basePrice = 5.49,
            rating = 4.6,
            reviewsCount = 98,
            description = "Juicy flame-grilled beef patty topped with rich melted American cheddar, pickles, and creamy mayo.",
            isSpicy = false,
            isPopular = true,
            imageRes = R.drawable.food_cheese_burger
        ),
        FoodItem(
            id = "c3",
            name = "Double Beef Tower",
            category = FoodCategory.BURGER,
            basePrice = 7.49,
            rating = 4.9,
            reviewsCount = 210,
            description = "Double stacked juicy beef patties dripping in melted cheese, crispy onion rings and smoky BBQ relish.",
            isSpicy = false,
            isPopular = true,
            imageRes = R.drawable.food_burger_hero
        ),
        FoodItem(
            id = "c4",
            name = "8 Pcs Chicken Bucket",
            category = FoodCategory.CHICKEN,
            basePrice = 14.99,
            rating = 4.9,
            reviewsCount = 340,
            description = "Golden crispy fried chicken drumsticks & tenders seasoned with 11 secret HFC herbs and spices.",
            isSpicy = true,
            isPopular = true,
            imageRes = R.drawable.food_chicken_bucket
        ),
        FoodItem(
            id = "c5",
            name = "Artisan Pepperoni Pizza",
            category = FoodCategory.PIZZA,
            basePrice = 8.99,
            rating = 4.7,
            reviewsCount = 85,
            description = "Hand-stretched sourdough pizza with rich marinara, overflowing mozzarella, and crispy pepperoni.",
            isSpicy = false,
            isPopular = false,
            imageRes = R.drawable.food_pizza
        ),
        FoodItem(
            id = "c6",
            name = "Crispy Tavern Fries",
            category = FoodCategory.FRIES,
            basePrice = 3.99,
            rating = 4.7,
            reviewsCount = 145,
            description = "Skin-on thick cut potatoes fried to golden perfection and tossed in sea salt & cracked black pepper.",
            isSpicy = false,
            isPopular = true,
            imageRes = R.drawable.food_fries
        ),
        FoodItem(
            id = "c7",
            name = "Chilled Ice Cola",
            category = FoodCategory.DRINK,
            basePrice = 2.49,
            rating = 4.9,
            reviewsCount = 67,
            description = "Ice-cold sparkling fountain cola served with fresh lime slice and crystal clear ice cubes.",
            isSpicy = false,
            isPopular = false,
            imageRes = R.drawable.food_drink
        )
    )

    val cartItems = mutableStateListOf<CartItem>()

    init {
        // Splash auto dismiss after 1.8 seconds
        dismissSplashAfterDelay()
    }

    private fun dismissSplashAfterDelay() {
        // ViewModel coroutine helper for splash delay
    }

    fun hideSplash() {
        showSplash = false
    }

    fun toggleFavorite(item: FoodItem) {
        val index = itemsList.indexOfFirst { it.id == item.id }
        if (index != -1) {
            val current = itemsList[index]
            itemsList[index] = current.copy(isFavorite = !current.isFavorite)
        }
    }

    fun openItemDetail(item: FoodItem) {
        selectedItemForDetail = item
        currentOptions = CustomizationOptions(
            size = FoodSize.REGULAR,
            addCheese = true,
            extraPatty = false,
            addBacon = false,
            quantity = 1
        )
    }

    fun closeItemDetail() {
        selectedItemForDetail = null
    }

    fun updateDetailOptions(update: (CustomizationOptions) -> CustomizationOptions) {
        currentOptions = update(currentOptions)
    }

    fun addCurrentToCart() {
        val item = selectedItemForDetail ?: return
        val price = currentOptions.calculatePrice(item.basePrice)
        cartItems.add(
            CartItem(
                id = "${item.id}_${System.currentTimeMillis()}",
                foodItem = item,
                options = currentOptions,
                totalPrice = price
            )
        )
        selectedItemForDetail = null
    }

    fun quickAddToCart(item: FoodItem) {
        val defaultOpts = CustomizationOptions()
        cartItems.add(
            CartItem(
                id = "${item.id}_${System.currentTimeMillis()}",
                foodItem = item,
                options = defaultOpts,
                totalPrice = defaultOpts.calculatePrice(item.basePrice)
            )
        )
    }

    fun removeFromCart(cartItem: CartItem) {
        cartItems.remove(cartItem)
    }

    fun triggerRefresh(coroutineScope: kotlinx.coroutines.CoroutineScope) {
        coroutineScope.launch {
            isRefreshing = true
            delay(1400) // simulated network sync
            isRefreshing = false
        }
    }

    fun completeCheckout() {
        if (cartItems.isNotEmpty()) {
            val total = cartItems.sumOf { it.totalPrice }
            val count = cartItems.sumOf { it.options.quantity }
            val summary = cartItems.joinToString(", ") { "${it.options.quantity}x ${it.foodItem.name}" }
            val defaultAddr = savedAddresses.firstOrNull { it.isDefault }?.label ?: "Home"
            val newOrder = OrderRecord(
                orderId = "HFC-${(1000..9999).random()}",
                dateFormatted = "Just now",
                itemsSummary = summary,
                itemCount = count,
                totalAmount = total,
                status = "Preparing",
                deliveryAddressLabel = defaultAddr
            )
            pastOrders.add(0, newOrder)
            userProfile = userProfile.copy(loyaltyPoints = userProfile.loyaltyPoints + (total * 10).toInt())
        }
        cartItems.clear()
        isCartOpen = false
        showOrderSuccessDialog = true
    }

    fun updateProfile(name: String, email: String, phone: String) {
        userProfile = userProfile.copy(name = name, email = email, phone = phone)
    }

    fun addOrUpdateAddress(address: DeliveryAddress) {
        val existingIndex = savedAddresses.indexOfFirst { it.id == address.id }
        if (address.isDefault) {
            for (i in savedAddresses.indices) {
                savedAddresses[i] = savedAddresses[i].copy(isDefault = false)
            }
        }
        if (existingIndex != -1) {
            savedAddresses[existingIndex] = address
        } else {
            savedAddresses.add(address)
        }
    }

    fun removeAddress(id: String) {
        savedAddresses.removeAll { it.id == id }
    }

    fun setDefaultAddress(id: String) {
        for (i in savedAddresses.indices) {
            savedAddresses[i] = savedAddresses[i].copy(isDefault = savedAddresses[i].id == id)
        }
    }

    // ==========================================
    // ADMIN FUNCTIONS (PIN: 192119)
    // ==========================================

    fun loginAdmin(passwordInput: String): Boolean {
        if (passwordInput.trim() == "192119") {
            isAdminAuthenticated = true
            return true
        }
        return false
    }

    fun logoutAdmin() {
        isAdminAuthenticated = false
        isAdminOpen = false
    }

    fun updatePromoBanner(tag: String, title: String, discountPercent: Int, enabled: Boolean) {
        promoBanner = PromoBannerConfig(
            tagText = tag.ifBlank { "Limited Time Offer" },
            title = title.ifBlank { "Spicy Burger\nCombo" },
            discountPercent = discountPercent.coerceIn(0, 99),
            isEnabled = enabled
        )
    }

    fun addFoodItem(
        name: String,
        category: FoodCategory,
        price: Double,
        description: String,
        isSpicy: Boolean
    ) {
        val defaultImg = when (category) {
            FoodCategory.BURGER -> R.drawable.food_chicken_burger
            FoodCategory.CHICKEN -> R.drawable.food_chicken_bucket
            FoodCategory.PIZZA -> R.drawable.food_pizza
            FoodCategory.FRIES -> R.drawable.food_fries
            FoodCategory.DRINK -> R.drawable.food_drink
        }
        val newItem = FoodItem(
            id = "c_${System.currentTimeMillis()}",
            name = name,
            category = category,
            basePrice = price,
            rating = 5.0,
            reviewsCount = 1,
            description = description,
            isSpicy = isSpicy,
            isPopular = true,
            imageRes = defaultImg
        )
        itemsList.add(0, newItem)
    }

    fun updateFoodItem(
        id: String,
        name: String,
        category: FoodCategory,
        price: Double,
        description: String,
        isSpicy: Boolean
    ) {
        val idx = itemsList.indexOfFirst { it.id == id }
        if (idx != -1) {
            val curr = itemsList[idx]
            itemsList[idx] = curr.copy(
                name = name,
                category = category,
                basePrice = price,
                description = description,
                isSpicy = isSpicy
            )
        }
    }

    fun deleteFoodItem(id: String) {
        itemsList.removeAll { it.id == id }
    }
}



// ==========================================
// 3. MAIN ACTIVITY
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HfcTheme {
                val viewModel: HfcViewModel = viewModel()
                HfcAppRoot(viewModel)
            }
        }
    }
}

// ==========================================
// 4. MAIN APP ROOT & NAVIGATION
// ==========================================

@Composable
fun HfcAppRoot(viewModel: HfcViewModel) {
    val coroutineScope = rememberCoroutineScope()

    // Splash screen delay timer
    LaunchedEffect(Unit) {
        delay(1900)
        viewModel.hideSplash()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HfcDarkBackground)
    ) {
        // App Main Shell
        HfcMainScreen(viewModel)

        // Food Detail Sheet / Screen Overlay
        AnimatedVisibility(
            visible = viewModel.selectedItemForDetail != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            viewModel.selectedItemForDetail?.let { food ->
                BackHandler {
                    viewModel.closeItemDetail()
                }
                FoodDetailScreen(
                    foodItem = food,
                    options = viewModel.currentOptions,
                    onOptionsChanged = { viewModel.updateDetailOptions(it) },
                    onClose = { viewModel.closeItemDetail() },
                    onAddToCart = { viewModel.addCurrentToCart() },
                    isFavorite = food.isFavorite,
                    onToggleFavorite = { viewModel.toggleFavorite(food) }
                )
            }
        }

        // Cart Bottom Sheet
        if (viewModel.isCartOpen) {
            BackHandler { viewModel.isCartOpen = false }
            CartBottomSheet(
                cartItems = viewModel.cartItems,
                onClose = { viewModel.isCartOpen = false },
                onRemove = { viewModel.removeFromCart(it) },
                onCheckout = { viewModel.completeCheckout() }
            )
        }

        // User Profile Fullscreen Glassmorphic Overlay
        AnimatedVisibility(
            visible = viewModel.isProfileOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            BackHandler { viewModel.isProfileOpen = false }
            UserProfileScreen(
                userProfile = viewModel.userProfile,
                savedAddresses = viewModel.savedAddresses,
                pastOrders = viewModel.pastOrders,
                onClose = { viewModel.isProfileOpen = false },
                onUpdateProfile = { name, email, phone -> viewModel.updateProfile(name, email, phone) },
                onAddOrUpdateAddress = { viewModel.addOrUpdateAddress(it) },
                onRemoveAddress = { viewModel.removeAddress(it) },
                onSetDefaultAddress = { viewModel.setDefaultAddress(it) },
                onReorder = { order ->
                    val matchedFood = viewModel.itemsList.firstOrNull { order.itemsSummary.contains(it.name, ignoreCase = true) }
                        ?: viewModel.itemsList.first()
                    viewModel.quickAddToCart(matchedFood)
                    viewModel.isProfileOpen = false
                    viewModel.isCartOpen = true
                }
            )
        }

        // Admin Page Overlay (PIN: 192119)
        AnimatedVisibility(
            visible = viewModel.isAdminOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            BackHandler { viewModel.isAdminOpen = false }
            AdminMainView(
                isAuthenticated = viewModel.isAdminAuthenticated,
                onLogin = { pin -> viewModel.loginAdmin(pin) },
                onLogout = { viewModel.logoutAdmin() },
                onClose = { viewModel.isAdminOpen = false },
                promoBanner = viewModel.promoBanner,
                onUpdatePromo = { tag, title, discount, enabled ->
                    viewModel.updatePromoBanner(tag, title, discount, enabled)
                },
                foodItems = viewModel.itemsList,
                onAddFood = { name, cat, price, desc, spicy ->
                    viewModel.addFoodItem(name, cat, price, desc, spicy)
                },
                onUpdateFood = { id, name, cat, price, desc, spicy ->
                    viewModel.updateFoodItem(id, name, cat, price, desc, spicy)
                },
                onDeleteFood = { id -> viewModel.deleteFoodItem(id) }
            )
        }

        // Branded White Splash Screen with 3D HFC Chicken Leg Logo


        AnimatedVisibility(
            visible = viewModel.showSplash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(500)) + scaleOut(targetScale = 1.05f)
        ) {
            HfcSplashScreen(onSkip = { viewModel.hideSplash() })
        }
    }
}

// ==========================================
// 5. BRANDED WHITE SPLASH SCREEN
// ==========================================

@Composable
fun HfcSplashScreen(onSkip: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "splash")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            // 3D HFC Logo with chicken leg
            Image(
                painter = painterResource(id = R.drawable.hfc_splash_logo),
                contentDescription = "HFC Logo",
                modifier = Modifier
                    .size(240.dp)
                    .scale(pulseScale)
                    .shadow(16.dp, shape = CircleShape, spotColor = Color(0x33000000))
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "HFC",
                color = Color(0xFFD32F2F),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )

            Text(
                text = "HOT FRIED CHICKEN",
                color = Color(0xFFE65100),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 4.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Crispy • Juicy • Irresistible",
                color = Color(0xFF757575),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(40.dp))

            CircularProgressIndicator(
                color = Color(0xFFFF6D00),
                strokeWidth = 3.dp,
                modifier = Modifier.size(28.dp)
            )
        }

        // Tap to skip button
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Tap anywhere to skip",
                color = Color(0xFF9E9E9E),
                fontSize = 12.sp
            )
        }
    }
}

// ==========================================
// 6. MAIN FEED SCREEN (Pic 1 Left Side)
// ==========================================

@Composable
fun HfcMainScreen(viewModel: HfcViewModel) {
    val coroutineScope = rememberCoroutineScope()

    // Ambient background lights for glassmorphic depth
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HfcDarkVignetteGradient)
    ) {
        // Ambient orange glow top right
        Box(
            modifier = Modifier
                .size(340.dp)
                .align(Alignment.TopEnd)
                .offset(x = 100.dp, y = (-60).dp)
                .blur(90.dp)
                .background(Brush.radialGradient(listOf(Color(0x35FF6B00), Color.Transparent)))
        )

        // Ambient orange glow mid left
        Box(
            modifier = Modifier
                .size(280.dp)
                .align(Alignment.CenterStart)
                .offset(x = (-80).dp, y = 120.dp)
                .blur(80.dp)
                .background(Brush.radialGradient(listOf(Color(0x22FF8C00), Color.Transparent)))
        )

        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                HfcBottomNavigationBar(
                    selectedTab = viewModel.activeBottomNav,
                    cartCount = viewModel.cartItems.size,
                    onTabSelected = { tab ->
                        viewModel.activeBottomNav = tab
                        if (tab == 2) {
                            viewModel.isCartOpen = true
                        } else if (tab == 4) {
                            viewModel.isProfileOpen = true
                        } else if (tab == 5) {
                            viewModel.isAdminOpen = true
                        }
                    }
                )


            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .statusBarsPadding()
            ) {
                // Header Bar (Hi, Alex + Avatar + Refresh Pull Indicator)
                TopHeaderBar(
                    userName = viewModel.userProfile.name,
                    isRefreshing = viewModel.isRefreshing,
                    onRefresh = { viewModel.triggerRefresh(coroutineScope) },
                    onProfileClick = { viewModel.isProfileOpen = true }
                )


                // Refreshing animated banner (Pic 2 inspired)
                AnimatedVisibility(
                    visible = viewModel.isRefreshing,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    FoodRefreshIndicator()
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Headline
                    item {
                        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Text(
                                text = "Good Food",
                                color = HfcTextWhite,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 38.sp
                            )
                            Text(
                                text = "Good Mood!",
                                color = HfcTextWhite,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 38.sp
                            )
                        }
                    }

                    // Search & Filter Bar
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        SearchBarRow(
                            query = viewModel.searchQuery,
                            onQueryChange = { viewModel.searchQuery = it }
                        )
                    }

                    // Categories Horizontal Row
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        CategorySelectorRow(
                            selected = viewModel.selectedCategory,
                            onSelect = { viewModel.selectedCategory = it }
                        )
                    }

                    // Limited Time Offer Combo Banner
                    item {
                        if (viewModel.promoBanner.isEnabled) {
                            Spacer(modifier = Modifier.height(20.dp))
                            LimitedTimeOfferCard(
                                config = viewModel.promoBanner,
                                onOrderNow = {
                                    val comboItem = viewModel.itemsList.firstOrNull { it.id == "c1" }
                                        ?: viewModel.itemsList.firstOrNull()
                                    if (comboItem != null) viewModel.openItemDetail(comboItem)
                                }
                            )
                        }
                    }


                    // Popular Now Section Header
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Popular Now",
                                color = HfcTextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "View All",
                                color = HfcOrangeLight,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    viewModel.selectedCategory = FoodCategory.BURGER
                                }
                            )
                        }
                    }

                    // Food Items Horizontal / Vertical Grid
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        val filteredItems = viewModel.itemsList.filter {
                            (it.category == viewModel.selectedCategory || viewModel.searchQuery.isNotEmpty()) &&
                                    (viewModel.searchQuery.isEmpty() || it.name.contains(viewModel.searchQuery, ignoreCase = true))
                        }

                        if (filteredItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No delicious items found for this category.",
                                    color = HfcTextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredItems, key = { it.id }) { item ->
                                    PopularFoodCard(
                                        foodItem = item,
                                        onCardClick = { viewModel.openItemDetail(item) },
                                        onAddClick = { viewModel.quickAddToCart(item) },
                                        onFavoriteClick = { viewModel.toggleFavorite(item) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 7. HEADER & REFRESH ANIMATION
// ==========================================

@Composable
fun TopHeaderBar(
    userName: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onProfileClick() }
        ) {
            // Glassmorphic Drawer Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HfcGlassCardBackground)
                    .border(1.dp, HfcGlassBorder, RoundedCornerShape(14.dp))
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = HfcTextWhite,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Hi, ${userName.split(" ").firstOrNull() ?: "Alex"}",
                        color = HfcTextMuted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(text = " 👋", fontSize = 14.sp)
                }
                Text(
                    text = "HFC Fast Food",
                    color = HfcOrangeLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Pull to refresh trigger and Avatar
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(HfcGlassCardBackground)
                    .border(1.dp, HfcGlassBorder, CircleShape)
            ) {
                val rotation by animateFloatAsState(
                    targetValue = if (isRefreshing) 360f else 0f,
                    animationSpec = tween(600),
                    label = "rotate"
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh Menu",
                    tint = HfcOrangeLight,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // User Avatar with glowing border - clickable to open profile
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, HfcOrangePrimary, CircleShape)
                    .clickable { onProfileClick() }
                    .testTag("user_avatar_btn")
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hfc_logo),
                    contentDescription = "User Avatar",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Online dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                        .border(1.5.dp, HfcDarkBackground, CircleShape)
                )
            }
        }
    }
}


@Composable
fun FoodRefreshIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rot"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x3DFF8C00))
            .border(1.dp, HfcOrangePrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.DinnerDining,
                contentDescription = null,
                tint = HfcOrangeLight,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(rotation)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Cooking fresh HFC items...",
                color = HfcTextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ==========================================
// 8. SEARCH & CATEGORY SELECTOR
// ==========================================

@Composable
fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Frosted Glass Search Input
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HfcGlassCardBackground)
                .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = HfcTextMuted,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (query.isEmpty()) {
                    Text(
                        text = "Search your favorite food",
                        color = HfcTextMuted,
                        fontSize = 14.sp
                    )
                }
                TextField(
                    value = query,
                    onValueChange = onQueryChange,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input")
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Filter Action Button
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HfcGlassCardBackground)
                .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
                .clickable { /* Filter settings */ },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = "Filter",
                tint = HfcTextWhite,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun CategorySelectorRow(
    selected: FoodCategory,
    onSelect: (FoodCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(FoodCategory.values()) { cat ->
            val isSelected = cat == selected

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .clickable { onSelect(cat) }
            ) {
                // Glassmorphic Category Icon Box
                Box(
                    modifier = Modifier
                        .size(width = 64.dp, height = 72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            if (isSelected) HfcOrangePrimary.copy(alpha = 0.35f)
                            else HfcGlassCardBackground
                        )
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) HfcOrangePrimary else HfcGlassBorder,
                            shape = RoundedCornerShape(22.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = cat.title,
                            tint = if (isSelected) HfcOrangeLight else HfcTextMuted,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = cat.title,
                            color = if (isSelected) HfcTextWhite else HfcTextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 9. LIMITED TIME OFFER HERO BANNER
// ==========================================

@Composable
fun LimitedTimeOfferCard(
    config: PromoBannerConfig,
    onOrderNow: () -> Unit
) {
    if (!config.isEnabled) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(180.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(28.dp))
            .clickable { onOrderNow() }
    ) {
        // Card Content
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1.1f)) {
                // Tag
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = HfcOrangeLight,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = config.tagText,
                        color = HfcOrangeLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = config.title,
                    color = HfcTextWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 26.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onOrderNow,
                    colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    modifier = Modifier.testTag("order_now_combo_btn")
                ) {
                    Text(
                        text = "Order Now",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Food imagery + discount badge
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.food_burger_hero),
                    contentDescription = "Spicy Burger Combo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                )

                // Customizable % OFF glass badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-6).dp)
                        .clip(CircleShape)
                        .background(HfcDarkSurface.copy(alpha = 0.85f))
                        .border(1.dp, HfcOrangePrimary, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${config.discountPercent}%\nOFF",
                        color = HfcOrangeLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


// ==========================================
// 10. POPULAR FOOD CARD
// ==========================================

@Composable
fun PopularFoodCard(
    foodItem: FoodItem,
    onCardClick: () -> Unit,
    onAddClick: () -> Unit,
    onFavoriteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(175.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(26.dp))
            .clickable { onCardClick() }
            .padding(14.dp)
    ) {
        Column {
            // Top badges (Spicy tag + Favorite heart)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (foodItem.isSpicy) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HfcRedSpicy.copy(alpha = 0.25f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Spicy",
                            color = HfcRedSpicy,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Heart toggle
                Icon(
                    imageVector = if (foodItem.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (foodItem.isFavorite) HfcRedSpicy else HfcTextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onFavoriteClick() }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Food Picture
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .clip(RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = foodItem.imageRes),
                    contentDescription = foodItem.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title
            Text(
                text = foodItem.name,
                color = HfcTextWhite,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Price and Add "+" Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ${"%.2f".format(foodItem.basePrice)}",
                    color = HfcTextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                // Plus Add Button
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(HfcOrangePrimary)
                        .clickable { onAddClick() }
                        .testTag("add_item_${foodItem.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add to Cart",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// 11. FOOD DETAIL & CUSTOMIZATION SCREEN (Pic 1 Right Side)
// ==========================================

@Composable
fun FoodDetailScreen(
    foodItem: FoodItem,
    options: CustomizationOptions,
    onOptionsChanged: ((CustomizationOptions) -> CustomizationOptions) -> Unit,
    onClose: () -> Unit,
    onAddToCart: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit
) {
    val totalPrice = options.calculatePrice(foodItem.basePrice)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HfcDarkBackground)
    ) {
        // Top Hero Food Image
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
        ) {
            Image(
                painter = painterResource(id = foodItem.imageRes),
                contentDescription = foodItem.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Dark gradient overlay for bottom seamless blend
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                HfcDarkBackground
                            )
                        )
                    )
            )

            // Top Action Bar (Back Arrow + Favorite Heart)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HfcDarkSurface.copy(alpha = 0.65f))
                        .border(1.dp, HfcGlassBorder, CircleShape)
                        .clickable { onClose() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = HfcTextWhite,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HfcDarkSurface.copy(alpha = 0.65f))
                        .border(1.dp, HfcGlassBorder, CircleShape)
                        .clickable { onToggleFavorite() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) HfcRedSpicy else HfcTextWhite,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Bottom Frosted Glass Sheet (Scrollable Customizations)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.60f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
                .background(Color(0xE61A1618))
                .border(1.dp, HfcGlassBorder, RoundedCornerShape(topStart = 34.dp, topEnd = 34.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // Spicy badge & nutritional icon
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (foodItem.isSpicy) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(HfcRedSpicy.copy(alpha = 0.25f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Spicy",
                                        color = HfcRedSpicy,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(HfcOrangePrimary.copy(alpha = 0.25f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = foodItem.category.title,
                                        color = HfcOrangeLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Tag,
                                contentDescription = null,
                                tint = HfcTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Food Title & Price
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = foodItem.name,
                                color = HfcTextWhite,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$ ${"%.2f".format(foodItem.basePrice)}",
                                color = HfcOrangeLight,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Rating & Reviews
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = HfcYellowRating,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${foodItem.rating}",
                                color = HfcTextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(${foodItem.reviewsCount})",
                                color = HfcTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }

                    // Description
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = foodItem.description,
                            color = HfcTextMuted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp
                        )
                    }

                    // Customize Section Title
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Customize",
                            color = HfcTextWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Size Selection (Regular, Large, X Large)
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Size",
                                color = HfcTextMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FoodSize.values().forEach { sizeOption ->
                                    val isSelected = options.size == sizeOption
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) HfcOrangePrimary else HfcGlassSubtle
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = if (isSelected) HfcOrangeLight else HfcGlassBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                onOptionsChanged { it.copy(size = sizeOption) }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 7.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = sizeOption.label,
                                            color = if (isSelected) Color.White else HfcTextMuted,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Add-on Switches (Add Cheese, Extra Patty, Add Bacon)
                    item {
                        Spacer(modifier = Modifier.height(14.dp))
                        CustomizerSwitchRow(
                            title = "Add Cheese",
                            price = "+$0.80",
                            checked = options.addCheese,
                            onCheckedChange = { checked ->
                                onOptionsChanged { it.copy(addCheese = checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        CustomizerSwitchRow(
                            title = "Extra Patty",
                            price = "+$2.50",
                            checked = options.extraPatty,
                            onCheckedChange = { checked ->
                                onOptionsChanged { it.copy(extraPatty = checked) }
                            }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        CustomizerSwitchRow(
                            title = "Add Bacon",
                            price = "+$1.20",
                            checked = options.addBacon,
                            onCheckedChange = { checked ->
                                onOptionsChanged { it.copy(addBacon = checked) }
                            }
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Sticky Bottom Bar: Quantity Stepper & Add to Cart Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quantity Stepper
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, RoundedCornerShape(18.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (options.quantity > 1) {
                                    onOptionsChanged { it.copy(quantity = it.quantity - 1) }
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Decrease Quantity",
                                tint = HfcTextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        Text(
                            text = "${options.quantity}",
                            color = HfcTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp)
                        )

                        IconButton(
                            onClick = {
                                onOptionsChanged { it.copy(quantity = it.quantity + 1) }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Increase Quantity",
                                tint = HfcTextWhite,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    // Add to Cart Button
                    Button(
                        onClick = onAddToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("detail_add_to_cart_btn")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Add to Cart",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$ ${"%.2f".format(totalPrice)}",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomizerSwitchRow(
    title: String,
    price: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = HfcTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = price,
                color = HfcOrangeLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HfcOrangePrimary,
                uncheckedThumbColor = HfcTextMuted,
                uncheckedTrackColor = HfcDarkSurface
            )
        )
    }
}

// ==========================================
// 12. CART BOTTOM SHEET & ORDER DIALOG
// ==========================================

@Composable
fun CartBottomSheet(
    cartItems: List<CartItem>,
    onClose: () -> Unit,
    onRemove: (CartItem) -> Unit,
    onCheckout: () -> Unit
) {
    val totalAmount = cartItems.sumOf { it.totalPrice }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { onClose() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.70f)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(HfcDarkSurface)
                .border(1.dp, HfcGlassBorder, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { /* consume clicks */ }
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Food Cart (${cartItems.size})",
                        color = HfcTextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Cart",
                            tint = HfcTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (cartItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.ShoppingBag,
                                contentDescription = null,
                                tint = HfcTextMuted,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your cart is empty!",
                                color = HfcTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Add mouth-watering items from the menu",
                                color = HfcTextMuted,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(cartItems, key = { it.id }) { cartItem ->
                            CartItemRow(cartItem = cartItem, onRemove = { onRemove(cartItem) })
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Subtotal breakdown
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(HfcGlassCardBackground)
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Subtotal", color = HfcTextMuted, fontSize = 14.sp)
                            Text("$ ${"%.2f".format(totalAmount)}", color = HfcTextWhite, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Delivery Fee", color = HfcTextMuted, fontSize = 14.sp)
                            Text("FREE", color = Color(0xFF4CAF50), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider(color = HfcGlassBorder)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", color = HfcTextWhite, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "$ ${"%.2f".format(totalAmount)}",
                                color = HfcOrangeLight,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onCheckout,
                        colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .navigationBarsPadding()
                            .testTag("checkout_button")
                    ) {
                        Text(
                            text = "Checkout ($ ${"%.2f".format(totalAmount)})",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CartItemRow(cartItem: CartItem, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(18.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = painterResource(id = cartItem.foodItem.imageRes),
            contentDescription = cartItem.foodItem.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = cartItem.foodItem.name,
                color = HfcTextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${cartItem.options.size.label} • Qty: ${cartItem.options.quantity}",
                color = HfcTextMuted,
                fontSize = 12.sp
            )
            Text(
                text = "$ ${"%.2f".format(cartItem.totalPrice)}",
                color = HfcOrangeLight,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.DeleteOutline,
                contentDescription = "Remove",
                tint = HfcRedSpicy
            )
        }
    }
}

@Composable
fun OrderSuccessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HfcDarkSurface,
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(54.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Order Placed!",
                    color = HfcTextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        },
        text = {
            Text(
                text = "Thank you for ordering with HFC! Your fresh and crispy meal is being prepared and will arrive hot.",
                color = HfcTextMuted,
                textAlign = TextAlign.Center,
                fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Great, Track Order", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    )
}

// ==========================================
// 13. BOTTOM NAVIGATION BAR
// ==========================================

@Composable
fun HfcBottomNavigationBar(
    selectedTab: Int,
    cartCount: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xD91E181B))
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(32.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Default.Home,
                label = "Home",
                isSelected = selectedTab == 0,
                onClick = { onTabSelected(0) }
            )

            BottomNavItem(
                icon = Icons.Default.LocalOffer,
                label = "Offers",
                isSelected = selectedTab == 1,
                onClick = { onTabSelected(1) }
            )

            // Orders / Cart with badge counter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTabSelected(2) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BadgedBox(
                        badge = {
                            if (cartCount > 0) {
                                Badge(
                                    containerColor = HfcOrangePrimary,
                                    contentColor = Color.White
                                ) {
                                    Text("$cartCount")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingBag,
                            contentDescription = "Orders",
                            tint = if (selectedTab == 2) HfcOrangeLight else HfcTextMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Orders",
                        color = if (selectedTab == 2) HfcOrangeLight else HfcTextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }

            BottomNavItem(
                icon = Icons.Default.Favorite,
                label = "Favorite",
                isSelected = selectedTab == 3,
                onClick = { onTabSelected(3) }
            )

            BottomNavItem(
                icon = Icons.Default.Person,
                label = "Profile",
                isSelected = selectedTab == 4,
                onClick = { onTabSelected(4) }
            )

            BottomNavItem(
                icon = Icons.Default.AdminPanelSettings,
                label = "Admin",
                isSelected = selectedTab == 5,
                onClick = { onTabSelected(5) }
            )
        }
    }
}



@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) HfcOrangeLight else HfcTextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (isSelected) HfcOrangeLight else HfcTextMuted,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// ==========================================
// 14. USER PROFILE SCREEN (GLASSMORPHISM)
// ==========================================

@Composable
fun UserProfileScreen(
    userProfile: UserProfile,
    savedAddresses: List<DeliveryAddress>,
    pastOrders: List<OrderRecord>,
    onClose: () -> Unit,
    onUpdateProfile: (name: String, email: String, phone: String) -> Unit,
    onAddOrUpdateAddress: (DeliveryAddress) -> Unit,
    onRemoveAddress: (String) -> Unit,
    onSetDefaultAddress: (String) -> Unit,
    onReorder: (OrderRecord) -> Unit
) {
    var selectedProfileTab by remember { mutableStateOf(0) } // 0: Account Info, 1: Saved Addresses, 2: Order History
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var addressToEdit by remember { mutableStateOf<DeliveryAddress?>(null) }
    var showAddressDialog by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HfcDarkVignetteGradient)
    ) {
        // Ambient glass light circles
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.TopStart)
                .offset(x = (-60).dp, y = (-40).dp)
                .blur(90.dp)
                .background(Brush.radialGradient(listOf(Color(0x35FF6B00), Color.Transparent)))
        )
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 80.dp, y = 60.dp)
                .blur(80.dp)
                .background(Brush.radialGradient(listOf(Color(0x28FF8C00), Color.Transparent)))
        )

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBackIosNew,
                            contentDescription = "Back",
                            tint = HfcTextWhite,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Text(
                        text = "My Profile",
                        color = HfcTextWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, CircleShape)
                            .clickable { showEditProfileDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = HfcOrangeLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Profile Avatar & Header Card
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, RoundedCornerShape(28.dp))
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, HfcOrangePrimary, CircleShape)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.hfc_logo),
                                    contentDescription = "Avatar",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = userProfile.name,
                                    color = HfcTextWhite,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = userProfile.email,
                                    color = HfcTextMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(HfcOrangePrimary.copy(alpha = 0.22f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Stars,
                                        contentDescription = null,
                                        tint = HfcAmberGlow,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${userProfile.loyaltyPoints} HFC Points",
                                        color = HfcOrangeLight,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Glass Segmented Tab Selector
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val tabs = listOf("Account", "Addresses", "Orders")
                        tabs.forEachIndexed { index, tabName ->
                            val isSelected = selectedProfileTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) HfcOrangePrimary else Color.Transparent
                                    )
                                    .clickable { selectedProfileTab = index }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = tabName,
                                    color = if (isSelected) Color.White else HfcTextMuted,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // TAB 0: ACCOUNT INFORMATION
                if (selectedProfileTab == 0) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Account Details",
                            color = HfcTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        ProfileInfoCard(
                            icon = Icons.Default.Person,
                            label = "Full Name",
                            value = userProfile.name
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ProfileInfoCard(
                            icon = Icons.Default.Email,
                            label = "Email Address",
                            value = userProfile.email
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ProfileInfoCard(
                            icon = Icons.Default.Phone,
                            label = "Phone Number",
                            value = userProfile.phone
                        )

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showEditProfileDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update Information", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // TAB 1: SAVED DELIVERY ADDRESSES
                if (selectedProfileTab == 1) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Saved Addresses (${savedAddresses.size})",
                                color = HfcTextWhite,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(onClick = {
                                addressToEdit = null
                                showAddressDialog = true
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = HfcOrangeLight,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add New", color = HfcOrangeLight, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (savedAddresses.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No addresses saved yet. Tap 'Add New' to add one.",
                                    color = HfcTextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(savedAddresses, key = { it.id }) { address ->
                            AddressCard(
                                address = address,
                                onSetDefault = { onSetDefaultAddress(address.id) },
                                onEdit = {
                                    addressToEdit = address
                                    showAddressDialog = true
                                },
                                onRemove = { onRemoveAddress(address.id) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                // TAB 2: ORDER HISTORY
                if (selectedProfileTab == 2) {
                    item {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Past Orders (${pastOrders.size})",
                            color = HfcTextWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    if (pastOrders.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "You haven't ordered yet. Pick something tasty from the menu!",
                                    color = HfcTextMuted,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(pastOrders, key = { it.orderId }) { order ->
                            PastOrderCard(
                                order = order,
                                onReorder = { onReorder(order) }
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        // Edit Profile Dialog
        if (showEditProfileDialog) {
            EditProfileDialog(
                currentProfile = userProfile,
                onDismiss = { showEditProfileDialog = false },
                onSave = { name, email, phone ->
                    onUpdateProfile(name, email, phone)
                    showEditProfileDialog = false
                }
            )
        }

        // Add / Edit Address Dialog
        if (showAddressDialog) {
            AddressEditDialog(
                initialAddress = addressToEdit,
                onDismiss = { showAddressDialog = false },
                onSave = { address ->
                    onAddOrUpdateAddress(address)
                    showAddressDialog = false
                }
            )
        }
    }
}

@Composable
fun ProfileInfoCard(
    icon: ImageVector,
    label: String,
    value: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HfcOrangePrimary.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = HfcOrangeLight, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(text = label, color = HfcTextMuted, fontSize = 12.sp)
                Text(text = value, color = HfcTextWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun AddressCard(
    address: DeliveryAddress,
    onSetDefault: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HfcGlassCardBackground)
            .border(
                width = if (address.isDefault) 1.5.dp else 1.dp,
                color = if (address.isDefault) HfcOrangePrimary else HfcGlassBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (address.label.equals("Home", ignoreCase = true)) Icons.Default.Home
                        else if (address.label.equals("Office", ignoreCase = true)) Icons.Default.Business
                        else Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = HfcOrangeLight,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = address.label,
                        color = HfcTextWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (address.isDefault) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(HfcOrangePrimary.copy(alpha = 0.28f))
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(text = "DEFAULT", color = HfcOrangeLight, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = HfcTextMuted, modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Remove", tint = HfcRedSpicy, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = address.fullAddress,
                color = HfcTextWhite.copy(alpha = 0.9f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            if (address.deliveryNote.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Note: ${address.deliveryNote}",
                    color = HfcTextMuted,
                    fontSize = 12.sp
                )
            }

            if (!address.isDefault) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Set as default address",
                    color = HfcOrangeLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onSetDefault() }
                )
            }
        }
    }
}

@Composable
fun PastOrderCard(
    order: OrderRecord,
    onReorder: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(22.dp))
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Order #${order.orderId}",
                        color = HfcTextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = order.dateFormatted,
                        color = HfcTextMuted,
                        fontSize = 12.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (order.status == "Delivered") Color(0x334CAF50)
                            else Color(0x33FF9800)
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = order.status,
                        color = if (order.status == "Delivered") Color(0xFF4CAF50) else Color(0xFFFF9800),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = order.itemsSummary,
                color = HfcTextWhite.copy(alpha = 0.85f),
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Divider(color = HfcGlassBorder)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Total (${order.itemCount} items)", color = HfcTextMuted, fontSize = 11.sp)
                    Text(
                        text = "$ ${"%.2f".format(order.totalAmount)}",
                        color = HfcOrangeLight,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onReorder,
                    colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Replay, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reorder", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun EditProfileDialog(
    currentProfile: UserProfile,
    onDismiss: () -> Unit,
    onSave: (name: String, email: String, phone: String) -> Unit
) {
    var name by remember { mutableStateOf(currentProfile.name) }
    var email by remember { mutableStateOf(currentProfile.email) }
    var phone by remember { mutableStateOf(currentProfile.phone) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HfcDarkSurface,
        title = {
            Text(
                text = "Edit Account Information",
                color = HfcTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, email, phone) },
                colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Changes", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HfcTextMuted)
            }
        }
    )
}

@Composable
fun AddressEditDialog(
    initialAddress: DeliveryAddress?,
    onDismiss: () -> Unit,
    onSave: (DeliveryAddress) -> Unit
) {
    var label by remember { mutableStateOf(initialAddress?.label ?: "Home") }
    var fullAddress by remember { mutableStateOf(initialAddress?.fullAddress ?: "") }
    var deliveryNote by remember { mutableStateOf(initialAddress?.deliveryNote ?: "") }
    var isDefault by remember { mutableStateOf(initialAddress?.isDefault ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HfcDarkSurface,
        title = {
            Text(
                text = if (initialAddress == null) "Add Delivery Address" else "Edit Address",
                color = HfcTextWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Quick Label Selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Home", "Office", "Other").forEach { tag ->
                        val isSel = label.equals(tag, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) HfcOrangePrimary else HfcGlassCardBackground)
                                .clickable { label = tag }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = tag,
                                color = if (isSel) Color.White else HfcTextMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = fullAddress,
                    onValueChange = { fullAddress = it },
                    label = { Text("Full Address & Apt/Suite", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deliveryNote,
                    onValueChange = { deliveryNote = it },
                    label = { Text("Delivery Note (optional)", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Set as default", color = HfcTextWhite, fontSize = 13.sp)
                    Switch(
                        checked = isDefault,
                        onCheckedChange = { isDefault = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HfcOrangePrimary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fullAddress.isNotBlank()) {
                        val address = DeliveryAddress(
                            id = initialAddress?.id ?: "addr_${System.currentTimeMillis()}",
                            label = label.ifBlank { "Home" },
                            fullAddress = fullAddress,
                            deliveryNote = deliveryNote,
                            isDefault = isDefault
                        )
                        onSave(address)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Address", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HfcTextMuted)
            }
        }
    )
}

// ==========================================
// 15. ADMIN PAGE & CUSTOMIZER (PIN: 192119)
// ==========================================

@Composable
fun AdminMainView(
    isAuthenticated: Boolean,
    onLogin: (String) -> Boolean,
    onLogout: () -> Unit,
    onClose: () -> Unit,
    promoBanner: PromoBannerConfig,
    onUpdatePromo: (tag: String, title: String, discountPercent: Int, enabled: Boolean) -> Unit,
    foodItems: List<FoodItem>,
    onAddFood: (name: String, category: FoodCategory, price: Double, description: String, isSpicy: Boolean) -> Unit,
    onUpdateFood: (id: String, name: String, category: FoodCategory, price: Double, description: String, isSpicy: Boolean) -> Unit,
    onDeleteFood: (id: String) -> Unit
) {
    if (!isAuthenticated) {
        AdminPinLockView(
            onVerifyPin = onLogin,
            onClose = onClose
        )
    } else {
        AdminDashboardView(
            onLogout = onLogout,
            onClose = onClose,
            promoBanner = promoBanner,
            onUpdatePromo = onUpdatePromo,
            foodItems = foodItems,
            onAddFood = onAddFood,
            onUpdateFood = onUpdateFood,
            onDeleteFood = onDeleteFood
        )
    }
}

@Composable
fun AdminPinLockView(
    onVerifyPin: (String) -> Boolean,
    onClose: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HfcDarkVignetteGradient),
        contentAlignment = Alignment.Center
    ) {
        // Glowing ambient backdrop
        Box(
            modifier = Modifier
                .size(260.dp)
                .blur(80.dp)
                .background(Brush.radialGradient(listOf(Color(0x35FF6B00), Color.Transparent)))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(32.dp))
                .background(Color(0xE61A1618))
                .border(1.dp, HfcGlassBorder, RoundedCornerShape(32.dp))
                .padding(26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(HfcOrangePrimary.copy(alpha = 0.2f))
                    .border(1.dp, HfcOrangePrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Lock",
                    tint = HfcOrangeLight,
                    modifier = Modifier.size(30.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "HFC Admin Portal",
                color = HfcTextWhite,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Enter 6-digit passcode to customize app",
                color = HfcTextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            // PIN Dots Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 6) {
                    val filled = i < pinInput.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) HfcOrangePrimary else HfcGlassCardBackground
                            )
                            .border(1.dp, if (filled) HfcOrangeLight else HfcGlassBorder, CircleShape)
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage ?: "",
                    color = HfcRedSpicy,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Numeric Keypad
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )

            keys.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    row.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(HfcGlassCardBackground)
                                .border(1.dp, HfcGlassBorder, CircleShape)
                                .clickable {
                                    when (key) {
                                        "C" -> {
                                            pinInput = ""
                                            errorMessage = null
                                        }
                                        "⌫" -> {
                                            if (pinInput.isNotEmpty()) {
                                                pinInput = pinInput.dropLast(1)
                                                errorMessage = null
                                            }
                                        }
                                        else -> {
                                            if (pinInput.length < 6) {
                                                val nextPin = pinInput + key
                                                pinInput = nextPin
                                                errorMessage = null
                                                if (nextPin.length == 6) {
                                                    val success = onVerifyPin(nextPin)
                                                    if (!success) {
                                                        errorMessage = "Incorrect Passcode! Hint: 192119"
                                                        pinInput = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = key,
                                color = HfcTextWhite,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(onClick = onClose) {
                Text(text = "Exit Admin", color = HfcTextMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun AdminDashboardView(
    onLogout: () -> Unit,
    onClose: () -> Unit,
    promoBanner: PromoBannerConfig,
    onUpdatePromo: (tag: String, title: String, discountPercent: Int, enabled: Boolean) -> Unit,
    foodItems: List<FoodItem>,
    onAddFood: (name: String, category: FoodCategory, price: Double, description: String, isSpicy: Boolean) -> Unit,
    onUpdateFood: (id: String, name: String, category: FoodCategory, price: Double, description: String, isSpicy: Boolean) -> Unit,
    onDeleteFood: (id: String) -> Unit
) {
    var adminTab by remember { mutableStateOf(0) } // 0: Menu Management, 1: Promo Banner
    var showAddFoodDialog by remember { mutableStateOf(false) }
    var itemToEdit by remember { mutableStateOf<FoodItem?>(null) }

    Scaffold(
        containerColor = HfcDarkBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(HfcGlassCardBackground)
                            .border(1.dp, HfcGlassBorder, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBackIosNew, contentDescription = "Back", tint = HfcTextWhite, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = "HFC Control Station", color = HfcTextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(text = "Admin Mode Active", color = Color(0xFF4CAF50), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                IconButton(
                    onClick = onLogout,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(HfcRedSpicy.copy(alpha = 0.2f))
                ) {
                    Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Logout", tint = HfcRedSpicy)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Summary Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AdminStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Menu Items",
                        value = "${foodItems.size}",
                        icon = Icons.Default.LunchDining
                    )
                    AdminStatCard(
                        modifier = Modifier.weight(1f),
                        title = "Active Promo",
                        value = "${promoBanner.discountPercent}% OFF",
                        icon = Icons.Default.LocalOffer
                    )
                }
            }

            // Tab Selector (Menu Customizer vs Promo Banner)
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HfcGlassCardBackground)
                        .border(1.dp, HfcGlassBorder, RoundedCornerShape(18.dp))
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (adminTab == 0) HfcOrangePrimary else Color.Transparent)
                            .clickable { adminTab = 0 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Manage Food Items",
                            color = if (adminTab == 0) Color.White else HfcTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (adminTab == 1) HfcOrangePrimary else Color.Transparent)
                            .clickable { adminTab = 1 }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Promo Banner",
                            color = if (adminTab == 1) Color.White else HfcTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // TAB 0: FOOD ITEMS LIST & ACTIONS
            if (adminTab == 0) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Menu (${foodItems.size})",
                            color = HfcTextWhite,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Button(
                            onClick = { showAddFoodDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Food", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                items(foodItems, key = { it.id }) { item ->
                    AdminFoodItemCard(
                        item = item,
                        onEdit = { itemToEdit = item },
                        onDelete = { onDeleteFood(item.id) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // TAB 1: PROMO BANNER CUSTOMIZER
            if (adminTab == 1) {
                item {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Customize Home Promo Banner",
                        color = HfcTextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Changes made here will instantly appear on customer feed.",
                        color = HfcTextMuted,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    AdminPromoCustomizer(
                        config = promoBanner,
                        onSave = onUpdatePromo
                    )
                }
            }
        }
    }

    // Add Food Dialog
    if (showAddFoodDialog) {
        FoodItemFormDialog(
            initialItem = null,
            onDismiss = { showAddFoodDialog = false },
            onSave = { name, cat, price, desc, spicy ->
                onAddFood(name, cat, price, desc, spicy)
                showAddFoodDialog = false
            }
        )
    }

    // Edit Food Dialog
    if (itemToEdit != null) {
        FoodItemFormDialog(
            initialItem = itemToEdit,
            onDismiss = { itemToEdit = null },
            onSave = { name, cat, price, desc, spicy ->
                itemToEdit?.let {
                    onUpdateFood(it.id, name, cat, price, desc, spicy)
                }
                itemToEdit = null
            }
        )
    }
}

@Composable
fun AdminStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    icon: ImageVector
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HfcOrangePrimary.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = HfcOrangeLight, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = title, color = HfcTextMuted, fontSize = 11.sp)
                Text(text = value, color = HfcTextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AdminFoodItemCard(
    item: FoodItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = item.imageRes),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(14.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.name,
                        color = HfcTextWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.isSpicy) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(HfcRedSpicy.copy(alpha = 0.25f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(text = "Spicy", color = HfcRedSpicy, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Text(
                    text = "${item.category.title} • $ ${"%.2f".format(item.basePrice)}",
                    color = HfcOrangeLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Row {
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = HfcTextMuted, modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete", tint = HfcRedSpicy, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun AdminPromoCustomizer(
    config: PromoBannerConfig,
    onSave: (tag: String, title: String, discountPercent: Int, enabled: Boolean) -> Unit
) {
    var tag by remember { mutableStateOf(config.tagText) }
    var title by remember { mutableStateOf(config.title) }
    var discount by remember { mutableStateOf(config.discountPercent.toString()) }
    var enabled by remember { mutableStateOf(config.isEnabled) }
    var savedNotice by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(HfcGlassCardBackground)
            .border(1.dp, HfcGlassBorder, RoundedCornerShape(24.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Enable Banner on Home Feed", color = HfcTextWhite, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = enabled,
                onCheckedChange = { enabled = it },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = HfcOrangePrimary
                )
            )
        }

        OutlinedTextField(
            value = tag,
            onValueChange = { tag = it },
            label = { Text("Tag (e.g. Limited Time Offer)", color = HfcTextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HfcTextWhite,
                unfocusedTextColor = HfcTextWhite,
                focusedBorderColor = HfcOrangePrimary,
                unfocusedBorderColor = HfcGlassBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Banner Headline", color = HfcTextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HfcTextWhite,
                unfocusedTextColor = HfcTextWhite,
                focusedBorderColor = HfcOrangePrimary,
                unfocusedBorderColor = HfcGlassBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = discount,
            onValueChange = { discount = it.filter { ch -> ch.isDigit() } },
            label = { Text("Discount Percentage (0-99%)", color = HfcTextMuted) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HfcTextWhite,
                unfocusedTextColor = HfcTextWhite,
                focusedBorderColor = HfcOrangePrimary,
                unfocusedBorderColor = HfcGlassBorder
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                val discountVal = discount.toIntOrNull() ?: 20
                onSave(tag, title, discountVal, enabled)
                savedNotice = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Save & Update App Live", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (savedNotice) {
            Text(
                text = "✓ Banner successfully updated in real time!",
                color = Color(0xFF4CAF50),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun FoodItemFormDialog(
    initialItem: FoodItem?,
    onDismiss: () -> Unit,
    onSave: (name: String, category: FoodCategory, price: Double, description: String, isSpicy: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var category by remember { mutableStateOf(initialItem?.category ?: FoodCategory.BURGER) }
    var priceStr by remember { mutableStateOf(initialItem?.basePrice?.toString() ?: "5.99") }
    var description by remember { mutableStateOf(initialItem?.description ?: "") }
    var isSpicy by remember { mutableStateOf(initialItem?.isSpicy ?: false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HfcDarkSurface,
        title = {
            Text(
                text = if (initialItem == null) "Add New Food Item" else "Edit Food Item",
                color = HfcTextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Item Name", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selector pills
                Text("Category", color = HfcTextMuted, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(FoodCategory.values()) { cat ->
                        val isSel = cat == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) HfcOrangePrimary else HfcGlassCardBackground)
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.title,
                                color = if (isSel) Color.White else HfcTextMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Base Price ($)", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = HfcTextMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = HfcTextWhite,
                        unfocusedTextColor = HfcTextWhite,
                        focusedBorderColor = HfcOrangePrimary,
                        unfocusedBorderColor = HfcGlassBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spicy Flavor Badge", color = HfcTextWhite, fontSize = 13.sp)
                    Switch(
                        checked = isSpicy,
                        onCheckedChange = { isSpicy = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = HfcRedSpicy
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = priceStr.toDoubleOrNull() ?: 5.0
                    if (name.isNotBlank()) {
                        onSave(name, category, price, description, isSpicy)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = HfcOrangePrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Item", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = HfcTextMuted)
            }
        }
    )
}


