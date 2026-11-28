package com.falcon.tripingly.core.data.tips

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In memory; on the device the app keeps these in DataStore. */
class FakeTipRepository : TipRepository {
    val dismissed = MutableStateFlow(emptySet<Tip>())

    override fun isDismissed(tip: Tip): Flow<Boolean> = dismissed.map { tip in it }

    override suspend fun dismiss(tip: Tip) {
        dismissed.value += tip
    }
}
