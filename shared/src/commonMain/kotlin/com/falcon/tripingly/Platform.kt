package com.falcon.tripingly

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform