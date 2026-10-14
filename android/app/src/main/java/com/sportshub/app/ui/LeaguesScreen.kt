package com.sportshub.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.League

private val SPORT_ORDER = listOf("f1", "soccer", "national", "cricket", "basketball", "football", "baseball", "hockey", "rugby")

@Composable
fun LeaguesScreen(onOpenLeague: (League) -> Unit) {
    val vm = loadViewModel("leagues") { it.leagues() }
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var sport by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Leagues", Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.displaySmall)
        when (val s = state) {
            Load.Loading -> LoadingBox()
            is Load.Error -> ErrorBox(s.message, vm::reload)
            is Load.Ready -> {
                val leagues = s.data
                val sports = SPORT_ORDER.filter { id -> leagues.any { it.sport == id } }
                val shown = leagues.filter {
                    (sport == null || it.sport == sport) &&
                        (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.abbreviation.equals(query, ignoreCase = true))
                }
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true, shape = CircleShape,
                    placeholder = { Text("Search ${leagues.size} competitions") },
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer, focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                )
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(selected = sport == null, onClick = { sport = null }, label = { Text("All") }, shape = CircleShape) }
                    items(sports) { id ->
                        FilterChip(selected = sport == id, onClick = { sport = if (sport == id) null else id }, label = { Text(sportLabel(id)) }, shape = CircleShape)
                    }
                }
                if (shown.isEmpty()) {
                    EmptyState("No matches", "Try a different search or sport filter.")
                } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)) {
                    items(shown, key = { it.id }) { league ->
                        GlassCard(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).entrance(shown.indexOf(league).coerceAtMost(8)),
                            onClick = { onOpenLeague(league) }, shape = MaterialTheme.shapes.medium,
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Crest(league.logo, league.name, size = 42.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(league.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(sportLabel(league.sport), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}
