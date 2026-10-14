package com.sportshub.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sportshub.app.notify.Notifier
import com.sportshub.app.ui.HomeScreen
import com.sportshub.app.ui.LeagueScreen
import com.sportshub.app.ui.LeaguesScreen
import com.sportshub.app.ui.LocalAnimatedScope
import com.sportshub.app.ui.LocalSharedTransitionScope
import com.sportshub.app.ui.MatchSheet
import com.sportshub.app.ui.MyTeamsScreen
import com.sportshub.app.ui.SettingsScreen
import com.sportshub.app.ui.SheetRequest
import com.sportshub.app.ui.SportsTheme
import com.sportshub.app.ui.SquadScreen
import com.sportshub.app.ui.TeamScreen

class MainActivity : ComponentActivity() {
    private var pendingTeam by mutableStateOf<Pair<String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTeam = intent.teamTarget()
        setContent { SportsTheme { AppRoot(pendingTeam) { pendingTeam = null } } }
    }

    // Tapping a notification while the app is open delivers the target here.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTeam = intent.teamTarget()
    }

    private fun Intent.teamTarget(): Pair<String, String>? {
        val league = getStringExtra(Notifier.EXTRA_LEAGUE)
        val team = getStringExtra(Notifier.EXTRA_TEAM)
        return if (league != null && team != null) league to team else null
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
private fun squadRoute(leagueId: String, teamId: String) = "squad/${Uri.encode(leagueId)}/${Uri.encode(teamId)}"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppRoot(pendingTeam: Pair<String, String>?, onPendingConsumed: () -> Unit) {
    val nav = rememberNavController()
    val route = nav.currentBackStackEntryAsState().value?.destination?.route
    var sheet by remember { mutableStateOf<SheetRequest?>(null) }

    LaunchedEffect(pendingTeam) {
        pendingTeam?.let { (league, team) ->
            nav.navigate(teamRoute(league, team)) { launchSingleTop = true }
            onPendingConsumed()
        }
    }

    // A Surface (not a bare Box) so Text and Icon default to a readable colour on the dark background.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
      Box(Modifier.fillMaxSize()) {
        SharedTransitionLayout {
            val sharedScope = this
            NavHost(
                nav, startDestination = "feed", modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(320)) + slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it / 14 } },
                exitTransition = { fadeOut(tween(200)) },
                popEnterTransition = { fadeIn(tween(320)) },
                popExitTransition = { fadeOut(tween(220)) + slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 14 } },
            ) {
                val openMatch: (com.sportshub.app.data.Fixture, com.sportshub.app.data.FollowedTeam?) -> Unit = { f, t -> sheet = SheetRequest(f, t) }
                composable("feed") {
                    val anim = this
                    CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope, LocalAnimatedScope provides anim) {
                        HomeScreen(
                            onAddTeam = { nav.openTab("leagues") },
                            onOpenTeam = { nav.navigate(teamRoute(it.leagueId, it.teamId)) },
                            onOpenMatch = openMatch,
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                }
                composable("teams") {
                    val anim = this
                    CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope, LocalAnimatedScope provides anim) {
                        MyTeamsScreen(onAddTeam = { nav.openTab("leagues") }, onOpenTeam = { nav.navigate(teamRoute(it.leagueId, it.teamId)) })
                    }
                }
                composable("leagues") { LeaguesScreen(onOpenLeague = { nav.navigate(leagueRoute(it.id)) }) }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                composable("league/{leagueId}") { entry ->
                    val leagueId = Uri.decode(entry.arguments?.getString("leagueId").orEmpty())
                    LeagueScreen(leagueId, onBack = { nav.popBackStack() }, onOpenTeam = { nav.navigate(teamRoute(leagueId, it)) })
                }
                composable("team/{leagueId}/{teamId}") { entry ->
                    val anim = this
                    val leagueId = Uri.decode(entry.arguments?.getString("leagueId").orEmpty())
                    val teamId = Uri.decode(entry.arguments?.getString("teamId").orEmpty())
                    CompositionLocalProvider(LocalSharedTransitionScope provides sharedScope, LocalAnimatedScope provides anim) {
                        TeamScreen(
                            leagueId, teamId,
                            onBack = { nav.popBackStack() },
                            onOpenLeague = { nav.navigate(leagueRoute(leagueId)) },
                            onOpenSquad = { nav.navigate(squadRoute(leagueId, teamId)) },
                            onOpenMatch = openMatch,
                        )
                    }
                }
                composable("squad/{leagueId}/{teamId}") { entry ->
                    SquadScreen(
                        Uri.decode(entry.arguments?.getString("leagueId").orEmpty()),
                        Uri.decode(entry.arguments?.getString("teamId").orEmpty()),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = TABS.any { it.route == route },
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(320)) { it } + fadeIn(tween(320)),
            exit = slideOutVertically(tween(220)) { it } + fadeOut(tween(180)),
        ) {
            FloatingNavBar(route, onSelect = { nav.openTab(it) })
        }

        sheet?.let { MatchSheet(it) { sheet = null } }
      }
    }
}

private fun NavHostController.openTab(route: String) = navigate(route) {
    popUpTo("feed") { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
private fun FloatingNavBar(current: String?, onSelect: (String) -> Unit) {
    Surface(
        Modifier.navigationBarsPadding().padding(bottom = 14.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 14.dp,
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
            TABS.forEach { tab ->
                val selected = current == tab.route
                Row(
                    Modifier.clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(tab.route) }
                        .padding(horizontal = 16.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon, contentDescription = tab.label,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    AnimatedVisibility(
                        visible = selected,
                        enter = expandHorizontally(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) + fadeIn(),
                        exit = shrinkHorizontally(tween(180)) + fadeOut(tween(120)),
                    ) {
                        Text(tab.label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
