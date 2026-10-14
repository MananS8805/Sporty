package com.sportshub.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.FollowedTeam
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTeamsScreen(onAddTeam: () -> Unit, onOpenTeam: (FollowedTeam) -> Unit) {
    val repo = rememberRepo()
    val follows by repo.follows.teams.collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(topBar = {
        TopAppBar(title = { Text("My teams") }, actions = { TextButton(onClick = onAddTeam) { Text("Add") } })
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (follows.isEmpty()) {
                EmptyState("No teams yet", "Find a league and tap the star next to your team.", "Browse leagues", onAddTeam)
            } else LazyColumn(Modifier.fillMaxSize()) {
                items(follows, key = { it.key }) { t ->
                    ListItem(
                        headlineContent = { Text(t.name) },
                        supportingContent = { Text(t.leagueName) },
                        leadingContent = { Logo(t.logo, t.name, 40.dp) },
                        trailingContent = {
                            IconButton(onClick = { scope.launch { repo.unfollow(t.key) } }) {
                                Icon(Icons.Default.Close, contentDescription = "Unfollow ${t.name}")
                            }
                        },
                        modifier = Modifier.clickable { onOpenTeam(t) },
                    )
                }
            }
        }
    }
}
