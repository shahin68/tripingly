package com.falcon.tripingly.feature.social.presentation.people

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.falcon.tripingly.core.designsystem.component.ShowMessage
import com.falcon.tripingly.core.model.social.PeopleList
import com.falcon.tripingly.core.ui.UiText
import com.falcon.tripingly.feature.social.generated.resources.Res
import com.falcon.tripingly.feature.social.generated.resources.social_back
import com.falcon.tripingly.feature.social.generated.resources.social_followers
import com.falcon.tripingly.feature.social.generated.resources.social_followers_empty
import com.falcon.tripingly.feature.social.generated.resources.social_following
import com.falcon.tripingly.feature.social.generated.resources.social_following_empty
import com.falcon.tripingly.feature.social.generated.resources.social_friends
import com.falcon.tripingly.feature.social.generated.resources.social_friends_empty
import com.falcon.tripingly.feature.social.generated.resources.social_retry
import com.falcon.tripingly.feature.social.presentation.component.PersonRow
import com.falcon.tripingly.feature.social.presentation.people.PeopleViewModel.Action
import com.falcon.tripingly.feature.social.presentation.social.Centered
import com.falcon.tripingly.feature.social.presentation.social.EmptyText
import com.falcon.tripingly.feature.social.presentation.social.LoadMoreAtEnd
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeopleRoute(
    list: PeopleList,
    onBack: () -> Unit,
    onOpenProfile: (username: String) -> Unit,
    viewModel: PeopleViewModel = koinViewModel(key = "people:$list") { parametersOf(list) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (list) {
                                PeopleList.Friends -> Res.string.social_friends
                                PeopleList.Following -> Res.string.social_following
                                PeopleList.Followers -> Res.string.social_followers
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.social_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ShowMessage(
                message = state.message?.asString(),
                onShown = { viewModel.onAction(Action.OnDismissMessage) },
                isError = state.message !is UiText.Note,
            )
            when {
                !state.hasLoaded && state.isLoading -> Centered { CircularProgressIndicator() }
                !state.hasLoaded -> Centered {
                    TextButton(onClick = { viewModel.onAction(Action.OnRetry) }) { Text(stringResource(Res.string.social_retry)) }
                }
                state.hasLoaded && state.people.isEmpty() -> Centered {
                    EmptyText(
                        stringResource(
                            when (list) {
                                PeopleList.Friends -> Res.string.social_friends_empty
                                PeopleList.Following -> Res.string.social_following_empty
                                PeopleList.Followers -> Res.string.social_followers_empty
                            },
                        ),
                    )
                }
                else -> {
                    val listState = rememberLazyListState()
                    LoadMoreAtEnd(listState) { viewModel.onAction(Action.OnLoadMore) }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(state.people, key = { it.id }) { user ->
                            PersonRow(user = user, onClick = { onOpenProfile(user.username) })
                        }
                    }
                }
            }
        }
    }
}
