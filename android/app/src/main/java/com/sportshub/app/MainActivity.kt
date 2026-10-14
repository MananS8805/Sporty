package com.sportshub.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportshub.app.ui.FeedScreen
import com.sportshub.app.ui.LeagueScreen
import com.sportshub.app.ui.LeaguesScreen
import com.sportshub.app.ui.MyTeamsScreen
import com.sportshub.app.ui.SportsTheme
import com.sportshub.app.ui.TeamScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SportsTheme { AppRoot() } }
    }
}

private class Tab(val route: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("feed", "Home", Icons.Default.Home),
    Tab("teams", "My teams", Icons.Default.Star),
    Tab("leagues", "Leagues", Icons.Default.Search),
)

// League and team ids contain ':' and '.', so encode them for use in routes.
private fun leagueRoute(leagueId: String) = "league/${Uri.encode(leagueId)}"
private fun teamRoute(leagueId: String, teamId: String) = "team/${Uri.encode(leagueId)}/${Uri.encode(teamId)}"

@Composable
private fun AppRoot() {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (TABS.any { it.route == route }) NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = route == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("feed") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { pad ->
        NavHost(nav, startDestination = "feed", modifier = Modifier.padding(pad)) {
            composable("feed") {
                FeedScreen(
                    onAddTeam = { nav.navigate("leagues") },
                    onOpenTeam = { nav.navigate(teamRoute(it.leagueId, it.teamId)) },
                )
            }
            composable("teams") {
                MyTeamsScreen(
                    onAddTeam = { nav.navigate("leagues") },
                    onOpenTeam = { nav.navigate(teamRoute(it.leagueId, it.teamId)) },
                )
            }
            composable("leagues") { LeaguesScreen(onOpenLeague = { nav.navigate(leagueRoute(it.id)) }) }
            composable("league/{leagueId}") { entry ->
                val leagueId = Uri.decode(entry.arguments?.getString("leagueId").orEmpty())
                LeagueScreen(
                    leagueId = leagueId,
                    onBack = { nav.popBackStack() },
                    onOpenTeam = { nav.navigate(teamRoute(leagueId, it)) },
                )
            }
            composable("team/{leagueId}/{teamId}") { entry ->
                val leagueId = Uri.decode(entry.arguments?.getString("leagueId").orEmpty())
                val teamId = Uri.decode(entry.arguments?.getString("teamId").orEmpty())
                TeamScreen(
                    leagueId = leagueId,
                    teamId = teamId,
                    onBack = { nav.popBackStack() },
                    onOpenLeague = { nav.navigate(leagueRoute(leagueId)) },
                )
            }
        }
    }
}
