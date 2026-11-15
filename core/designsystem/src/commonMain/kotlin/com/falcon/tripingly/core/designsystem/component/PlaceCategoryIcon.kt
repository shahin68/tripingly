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
import androidx.compose.ui.graphics.vector.ImageVector
import com.falcon.tripingly.core.model.place.PlaceCategory

/** What a kind of place looks like: on its pin, in its card, and where it has no photo. */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.Cafe -> Icons.Default.LocalCafe
        PlaceCategory.Restaurant -> Icons.Default.Restaurant
        PlaceCategory.Bar -> Icons.Default.LocalBar
        PlaceCategory.Attraction -> Icons.Default.Attractions
        PlaceCategory.Museum -> Icons.Default.Museum
        PlaceCategory.Historic -> Icons.Default.Castle
        PlaceCategory.Park -> Icons.Default.Park
        PlaceCategory.Nature -> Icons.Default.Landscape
        PlaceCategory.Landmark -> Icons.Default.AccountBalance
        PlaceCategory.Other -> Icons.Default.Place
        PlaceCategory.Bakery -> Icons.Default.BakeryDining
        PlaceCategory.FastFood -> Icons.Default.LunchDining
        PlaceCategory.Viewpoint -> Icons.Default.Panorama
        PlaceCategory.Beach -> Icons.Default.BeachAccess
        PlaceCategory.Entertainment -> Icons.Default.TheaterComedy
        PlaceCategory.Shopping -> Icons.Default.ShoppingBag
        PlaceCategory.Stay -> Icons.Default.Hotel
        PlaceCategory.Transport -> Icons.Default.Train
        PlaceCategory.Cinema -> Icons.Default.Movie
        PlaceCategory.Nightlife -> Icons.Default.Nightlife
        PlaceCategory.Health -> Icons.Default.MedicalServices
        PlaceCategory.Pharmacy -> Icons.Default.LocalPharmacy
        PlaceCategory.Groceries -> Icons.Default.LocalGroceryStore
        PlaceCategory.Market -> Icons.Default.Storefront
        PlaceCategory.Sports -> Icons.Default.SportsSoccer
        PlaceCategory.Spa -> Icons.Default.Spa
        PlaceCategory.Zoo -> Icons.Default.Pets
        PlaceCategory.Kids -> Icons.Default.Toys
        PlaceCategory.Library -> Icons.Default.LocalLibrary
        PlaceCategory.Camping -> Icons.Default.Festival
        PlaceCategory.Winery -> Icons.Default.WineBar
        PlaceCategory.Parking -> Icons.Default.LocalParking
        PlaceCategory.Fuel -> Icons.Default.LocalGasStation
        PlaceCategory.Money -> Icons.Default.LocalAtm
        PlaceCategory.Toilets -> Icons.Default.Wc
        PlaceCategory.Rental -> Icons.Default.CarRental
        PlaceCategory.PhotoSpot -> Icons.Default.PhotoCamera
        PlaceCategory.Romantic -> Icons.Default.Favorite
    }
