package com.falcon.tripingly.feature.map.presentation.component

import androidx.compose.runtime.Composable

/** The app shares the screen with another app: Android's split screen, an iPad's Split View or Slide Over. */
@Composable
internal expect fun isSplitScreen(): Boolean
