package io.github.ehb54.quiz.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

/**
 * Calls [onAway] when the app leaves the screen: a call answered, Home, the
 * app switcher, the screen turning off. (The desktop app has its own version:
 * the window losing focus.)
 */
@Composable
fun WhenAway(onAway: () -> Unit) = LifecycleEventEffect(Lifecycle.Event.ON_PAUSE, onEvent = onAway)
