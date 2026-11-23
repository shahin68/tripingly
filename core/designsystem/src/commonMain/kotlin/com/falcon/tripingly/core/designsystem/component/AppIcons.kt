package com.falcon.tripingly.core.designsystem.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.BakeryDining
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.CarRental
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.LunchDining
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Nightlife
import androidx.compose.material.icons.filled.Panorama
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Toys
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Wc
import androidx.compose.material.icons.filled.WineBar
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The app's icon table: the server names an icon by its ID (place kinds, Explore periods in `GET /app-config`), and
 * this is the only place that says what each ID looks like (Shahin, 2026-11-23). An ID keeps its meaning forever and
 * is never reused; an ID the app doesn't know shows the plain Spot icon (1). Custom icons replace these one by one,
 * with nothing else changing.
 */
private val AppIcons: Map<Int, ImageVector> = mapOf(
    1 to Icons.Default.Place, // other
    2 to Icons.Default.PhotoCamera, // photo_spot
    3 to Icons.Default.Favorite, // romantic
    4 to Icons.Default.LocalCafe, // cafe
    5 to Icons.Default.Restaurant, // restaurant
    6 to Icons.Default.LocalBar, // bar
    7 to Icons.Default.BakeryDining, // bakery
    8 to Icons.Default.LunchDining, // fast_food
    9 to Icons.Default.Nightlife, // nightlife
    10 to Icons.Default.WineBar, // winery
    11 to Icons.Default.Attractions, // attraction
    12 to Icons.Default.Panorama, // viewpoint
    13 to Icons.Default.Museum, // museum
    14 to Icons.Default.Castle, // historic
    15 to Icons.Default.AccountBalance, // landmark
    16 to Icons.Default.Pets, // zoo
    17 to Icons.Default.Park, // park
    18 to Icons.Default.Landscape, // nature
    19 to Icons.Default.BeachAccess, // beach
    20 to Icons.Default.TheaterComedy, // entertainment
    21 to Icons.Default.Movie, // cinema
    22 to Icons.Default.SportsSoccer, // sports
    23 to Icons.Default.Spa, // spa
    24 to Icons.Default.Toys, // kids
    25 to Icons.Default.LocalLibrary, // library
    26 to Icons.Default.ShoppingBag, // shopping
    27 to Icons.Default.Storefront, // market
    28 to Icons.Default.LocalGroceryStore, // groceries
    29 to Icons.Default.Hotel, // stay
    30 to Icons.Default.Festival, // camping
    31 to Icons.Default.Train, // transport
    32 to Icons.Default.CarRental, // rental
    33 to Icons.Default.LocalGasStation, // fuel
    34 to Icons.Default.LocalParking, // parking
    35 to Icons.Default.LocalAtm, // money
    36 to Icons.Default.MedicalServices, // health
    37 to Icons.Default.LocalPharmacy, // pharmacy
    38 to Icons.Default.Wc, // toilets
    60 to Icons.Outlined.CalendarMonth, // a time period
    61 to Icons.Outlined.History, // all time
)

/** Every icon ID the app has, to make one painter per icon (map pins). */
val AppIconIds: Set<Int> get() = AppIcons.keys

/** What icon [id] looks like; the Spot icon for an ID the app doesn't know (yet). */
fun appIcon(id: Int?): ImageVector = id?.let(AppIcons::get) ?: AppIcons.getValue(SPOT_ICON)

private const val SPOT_ICON = 1
