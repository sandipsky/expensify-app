package com.example.expensify.designsystem.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Help
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreditCard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalShipping
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RequestQuote
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Icons the app's own chrome uses, in one place so screens never reach into
 * the icon library directly.
 */
object AppIcons {
    val Back = Icons.AutoMirrored.Rounded.ArrowBack
    val Close = Icons.Rounded.Close
    val Add = Icons.Rounded.Add
    val More = Icons.Rounded.MoreVert
    val Edit = Icons.Rounded.Edit
    val Delete = Icons.Rounded.Delete
    val Archive = Icons.Rounded.Archive
    val Restore = Icons.Rounded.Unarchive
    val Reconcile = Icons.Rounded.Balance
    val ExpandMore = Icons.Rounded.ExpandMore
    val ExpandLess = Icons.Rounded.ExpandLess
    val Visible = Icons.Rounded.Visibility
    val Hidden = Icons.Rounded.VisibilityOff
    val Logout = Icons.Rounded.Logout
    val Home = Icons.Rounded.Home
    val Transactions = Icons.Rounded.ReceiptLong
    val Accounts = Icons.Rounded.AccountBalanceWallet
    val Budgets = Icons.Rounded.PieChart
    val Reports = Icons.Rounded.BarChart
    val Recurring = Icons.Rounded.EventRepeat
    val Categories = Icons.Rounded.Category
    val Settings = Icons.Rounded.Settings
    val Transfer = Icons.Rounded.SwapHoriz
    val Unknown = Icons.AutoMirrored.Rounded.Help
}

/**
 * Firestore stores lumen-ui icon names (`cart`, `train`, `wallet_alt`, …),
 * which the web app renders. The names stay as they are so both clients
 * agree; this maps each one to a Material icon, with a fallback for any
 * name a newer web build might add.
 */
object IconMap {
    private val icons: Map<String, ImageVector> = mapOf(
        // Category icons, in the order category.domain.ts lists them.
        "category" to Icons.Rounded.Category,
        "cart" to Icons.Rounded.ShoppingCart,
        "bag" to Icons.Rounded.ShoppingBag,
        "checkroom" to Icons.Rounded.Checkroom,
        "home" to Icons.Rounded.Home,
        "building" to Icons.Rounded.Apartment,
        "bolt" to Icons.Rounded.Bolt,
        "wifi" to Icons.Rounded.Wifi,
        "call" to Icons.Rounded.Call,
        "car" to Icons.Rounded.DirectionsCar,
        "fuel" to Icons.Rounded.LocalGasStation,
        "train" to Icons.Rounded.Train,
        "truck" to Icons.Rounded.LocalShipping,
        "flight" to Icons.Rounded.Flight,
        "location" to Icons.Rounded.Place,
        "hospital" to Icons.Rounded.LocalHospital,
        "fitness" to Icons.Rounded.FitnessCenter,
        "heart" to Icons.Rounded.Favorite,
        "family" to Icons.Rounded.FamilyRestroom,
        "users" to Icons.Rounded.Group,
        "education" to Icons.Rounded.School,
        "notebook" to Icons.AutoMirrored.Rounded.MenuBook,
        "music" to Icons.Rounded.MusicNote,
        "controller" to Icons.Rounded.SportsEsports,
        "image" to Icons.Rounded.Image,
        "star" to Icons.Rounded.Star,
        "calendar" to Icons.Rounded.CalendarMonth,
        "event_repeat" to Icons.Rounded.EventRepeat,
        "refresh" to Icons.Rounded.Refresh,
        "receipt" to Icons.Rounded.Receipt,
        "file" to Icons.Rounded.Description,
        "shield" to Icons.Rounded.Shield,
        "lock" to Icons.Rounded.Lock,
        "work" to Icons.Rounded.Work,
        "factory" to Icons.Rounded.Factory,
        "money" to Icons.Rounded.Payments,
        "moneys" to Icons.Rounded.RequestQuote,
        "card" to Icons.Rounded.CreditCard,
        "bank" to Icons.Rounded.AccountBalance,
        "savings" to Icons.Rounded.Savings,
        "wallet" to Icons.Rounded.AccountBalanceWallet,
        "bar_chart" to Icons.Rounded.BarChart,
        "pie_chart" to Icons.Rounded.PieChart,
        "hand" to Icons.Rounded.VolunteerActivism,
        "configuration" to Icons.Rounded.Settings,
        "box" to Icons.Rounded.Inventory2,
        "undo" to Icons.AutoMirrored.Rounded.Undo,
        "add" to Icons.Rounded.Add,
        // Account type icons not in the category set.
        "wallet_alt" to Icons.Rounded.Wallet,
        // Drawn by the app for transfers and deleted categories.
        "help" to AppIcons.Unknown,
    )

    fun of(name: String?): ImageVector = icons[name] ?: Icons.Rounded.Category
}
