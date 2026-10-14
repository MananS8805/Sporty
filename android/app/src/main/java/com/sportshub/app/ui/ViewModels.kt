package com.sportshub.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sportshub.app.SportsApp
import com.sportshub.app.data.Feed
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.data.Repository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ready<T>(val data: T) : Load<T>
}

private fun Throwable.friendly(): String = when (this) {
    is java.net.ConnectException, is java.net.UnknownHostException, is java.net.SocketTimeoutException ->
        "Can't reach the server. Is the backend running (and `adb reverse tcp:8000 tcp:8000` done)?"
    is retrofit2.HttpException -> "Server error (${code()})."
    else -> message ?: "Something went wrong."
}

/** Loads one value, keeps showing the old value if a refresh fails. */
class LoadViewModel<T>(private val loader: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                _state.value = Load.Ready(loader())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value !is Load.Ready) _state.value = Load.Error(e.friendly())
            } finally {
                _refreshing.value = false
            }
        }
    }
}

@Composable
fun <T> loadViewModel(key: String, loader: suspend (Repository) -> T): LoadViewModel<T> {
    val repo = (LocalContext.current.applicationContext as SportsApp).repo
    return viewModel(key = key, factory = viewModelFactory { initializer { LoadViewModel { loader(repo) } } })
}

@Composable
fun rememberRepo(): Repository = (LocalContext.current.applicationContext as SportsApp).repo

data class FeedUi(
    val follows: List<FollowedTeam>? = null, // null until the on-device list has loaded
    val feed: Feed? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

/** Rebuilds the home feed whenever the set of followed teams changes. */
class FeedViewModel(private val repo: Repository) : ViewModel() {
    private val _ui = MutableStateFlow(FeedUi())
    val ui: StateFlow<FeedUi> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            repo.follows.teams.collectLatest { follows ->
                _ui.value = _ui.value.copy(follows = follows, feed = if (follows.isEmpty()) null else _ui.value.feed)
                if (follows.isNotEmpty()) load(follows)
            }
        }
    }

    fun refresh() {
        val follows = _ui.value.follows ?: return
        if (follows.isNotEmpty()) viewModelScope.launch { load(follows) }
    }

    private suspend fun load(follows: List<FollowedTeam>) {
        _ui.value = _ui.value.copy(refreshing = true, error = null)
        try {
            val feed = repo.feed(follows.map { it.key })
            _ui.value = _ui.value.copy(feed = feed, refreshing = false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            _ui.value = _ui.value.copy(refreshing = false, error = e.friendly())
        }
    }
}

@Composable
fun feedViewModel(): FeedViewModel {
    val repo = rememberRepo()
    return viewModel(factory = viewModelFactory { initializer { FeedViewModel(repo) } })
}
