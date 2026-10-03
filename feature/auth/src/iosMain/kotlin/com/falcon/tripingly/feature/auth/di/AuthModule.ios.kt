package com.falcon.tripingly.feature.auth.di

import org.koin.core.module.Module
import org.koin.dsl.module

// SocialSignIn comes from the app: MainViewController binds the Swift sign-in (iosApp/NativeSignIn.swift).
internal actual val authPlatformModule: Module = module {}
