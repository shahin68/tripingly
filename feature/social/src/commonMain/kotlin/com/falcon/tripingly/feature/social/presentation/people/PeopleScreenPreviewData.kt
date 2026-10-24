package com.falcon.tripingly.feature.social.presentation.people

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.falcon.tripingly.core.model.trip.UserSummary
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_error_load_people
import kotlinx.collections.immutable.persistentListOf

/** Loaded, loading, nobody yet, and a first page that failed. */
internal class PeopleScreenPreviewStates : PreviewParameterProvider<PeopleViewModel.State> {
    override val values = sequenceOf(
        PeopleViewModel.State(
            people = persistentListOf(UserSummary("u1", "anna", "Anna"), UserSummary("u2", "jonas.k", "Jonas")),
            hasLoaded = true,
        ),
        PeopleViewModel.State(isLoading = true),
        PeopleViewModel.State(hasLoaded = true),
        PeopleViewModel.State(message = UiText.Resource(Res.string.social_error_load_people)),
    )
}
