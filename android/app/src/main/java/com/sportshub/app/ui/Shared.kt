package com.sportshub.app.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/** Scopes provided by the navigation host so any composable can take part in a shared-element transition. */
@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

val LocalAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Marks this element as shared between screens (e.g. a team crest flying from Home to the team page). No-op outside a transition host. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIfAvailable(key: Any): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val anim = LocalAnimatedScope.current ?: return this
    return with(shared) { this@sharedElementIfAvailable.sharedElement(rememberSharedContentState(key), anim) }
}

fun crestKey(leagueId: String, teamId: String) = "crest:$leagueId:$teamId"
