package com.sportshub.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sportshub.app.data.Fixture
import com.sportshub.app.data.FollowedTeam
import com.sportshub.app.data.MatchInsight
import com.sportshub.app.data.Prediction
import com.sportshub.app.data.WatchInfo

data class SheetRequest(val fixture: Fixture, val team: FollowedTeam?)

/** Match details: the forecast with everything it is based on, and where to watch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchSheet(request: SheetRequest, onDismiss: () -> Unit) {
    val f = request.fixture
    TeamTheme(parseHex(request.team?.color)) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            val vm = loadViewModel("insight:${f.id}") { it.insight(f) }
            val state by vm.state.collectAsStateWithLifecycle()
            Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
                SheetHeader(f)
                when (val s = state) {
                    is Load.Ready -> InsightBody(f, request.team, s.data)
                    is Load.Error -> Text(s.message, Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                    Load.Loading -> Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth().height(140.dp).shimmer(MaterialTheme.shapes.large))
                        Box(Modifier.fillMaxWidth().height(90.dp).shimmer(MaterialTheme.shapes.large))
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(f: Fixture) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(f.leagueName.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (f.home != null && f.away != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Crest(f.home.logo, f.home.name, size = 60.dp)
                    Text(f.home.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                }
                if (f.status == "scheduled") {
                    Countdown(f.date, style = MaterialTheme.typography.headlineMedium)
                } else {
                    Text("${f.homeScore ?: "-"} – ${f.awayScore ?: "-"}", style = MaterialTheme.typography.displaySmall)
                }
                Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Crest(f.away.logo, f.away.name, size = 60.dp)
                    Text(f.away.name, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        } else {
            Text(f.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Text(formatKickoff(f.date), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        f.venue?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center) }
        if (f.sessions.isNotEmpty() && f.status != "final") {
            Spacer(Modifier.height(12.dp))
            f.sessions.forEach {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(it.name, style = MaterialTheme.typography.bodySmall)
                    Text(formatKickoff(it.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun InsightBody(f: Fixture, team: FollowedTeam?, insight: MatchInsight) {
    val p = insight.prediction
    SectionHeader("Forecast")
    if (p.available && f.home != null && f.away != null) ForecastCard(f, p) else NoForecastCard(p)
    if (p.factors.isNotEmpty()) {
        SectionHeader("What it's based on")
        GlassCard(Modifier.padding(horizontal = 20.dp)) {
            p.factors.forEachIndexed { i, factor ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.fillMaxWidth().padding(16.dp).entrance(i)) {
                    Text(factor.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(factor.value, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
                    factor.detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
        }
    }
    if (p.available) {
        Text(
            "Probabilities come from bookmaker odds with the bookmaker's margin removed. They describe what the market expects, not a certainty, and this isn't betting advice.",
            Modifier.padding(horizontal = 24.dp, vertical = 12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (f.status != "final") WatchSection(insight.watch)
}

@Composable
private fun ForecastCard(f: Fixture, p: Prediction) {
    val home = p.home!!.toFloat()
    val away = p.away!!.toFloat()
    val draw = p.draw?.toFloat()
    val c = MaterialTheme.colorScheme
    val homeColor = c.primary
    val awayColor = c.onSurfaceVariant.copy(alpha = 0.55f)
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            ProbabilityRing(
                segments = listOfNotNull(home to homeColor, draw?.let { it to MaterialTheme.extra.draw }, away to awayColor),
                size = 118.dp, stroke = 13.dp,
            ) { CountUpText(home * 100f, suffix = "%", style = MaterialTheme.typography.headlineMedium) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(homeColor, f.home?.name ?: "Home", home)
                if (draw != null) LegendRow(MaterialTheme.extra.draw, "Draw", draw)
                LegendRow(awayColor, f.away?.name ?: "Away", away)
            }
        }
        Text(p.source, Modifier.padding(start = 18.dp, end = 18.dp, bottom = 14.dp), style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
    }
}

@Composable
private fun LegendRow(color: androidx.compose.ui.graphics.Color, label: String, value: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        CountUpText(value * 100f, suffix = "%", style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun NoForecastCard(p: Prediction) {
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("No expert forecast", style = MaterialTheme.typography.titleMedium)
            Text(p.unavailableReason ?: "Nothing is published for this match.", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WatchSection(watch: WatchInfo) {
    val uri = LocalUriHandler.current
    SectionHeader("Where to watch")
    GlassCard(Modifier.padding(horizontal = 20.dp)) {
        Column(Modifier.padding(18.dp)) {
            Text("Showing results for ${com.sportshub.app.data.Region.countryName(watch.region)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            if (watch.options.isEmpty()) {
                Text("No confirmed broadcaster for this match in your region yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    watch.options.forEach { WatchChip(it) }
                }
                watch.options.firstNotNullOfOrNull { it.note }?.let {
                    Text(it, Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (watch.options.any { !it.confirmed }) {
                    Text("A '?' means this isn't confirmed for the series.", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            watch.verifiedOn?.let {
                Text("Broadcast rights last checked $it. They change between seasons.", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(14.dp))
            OutlinedButton(onClick = { runCatching { uri.openUri(watch.searchUrl) } }, Modifier.fillMaxWidth()) { Text("Search where to watch") }
        }
    }
}
