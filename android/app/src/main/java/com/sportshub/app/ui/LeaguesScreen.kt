package com.sportshub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.League

private val SPORT_ORDER = listOf("soccer", "f1", "cricket", "basketball", "football", "baseball", "hockey", "rugby")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaguesScreen(onOpenLeague: (League) -> Unit) {
    val vm = loadViewModel("leagues") { it.leagues() }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Leagues") }) }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (val s = state) {
                Load.Loading -> LoadingBox()
                is Load.Error -> ErrorBox(s.message, vm::reload)
                is Load.Ready -> {
                    val leagues = s.data
                    val sports = SPORT_ORDER.filter { id -> leagues.any { it.sport == id } }
                    val shown = leagues.filter {
                        (sport == null || it.sport == sport) &&
                            (query.isBlank() || it.name.contains(query, ignoreCase = true) ||
                                it.abbreviation.equals(query, ignoreCase = true))
                    }
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            label = { Text("Search ${leagues.size} leagues") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { FilterChip(selected = sport == null, onClick = { sport = null }, label = { Text("All") }) }
                            items(sports) { id ->
                                FilterChip(
                                    selected = sport == id, onClick = { sport = if (sport == id) null else id },
                                    label = { Text(sportLabel(id)) },
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        if (shown.isEmpty()) {
                            EmptyState("No matches", "Try a different search or sport filter.")
                        } else LazyColumn(Modifier.fillMaxSize()) {
                            items(shown, key = { it.id }) { l ->
                                ListItem(
                                    headlineContent = { Text(l.name) },
                                    supportingContent = { Text(sportLabel(l.sport)) },
                                    leadingContent = { Logo(l.logo, l.name, 36.dp) },
                                    modifier = Modifier.clickable { onOpenLeague(l) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
