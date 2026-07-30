package com.zestyy.bytetrack.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zestyy.bytetrack.ui.components.GlassCard
import com.zestyy.bytetrack.ui.screens.AppsScreen
import com.zestyy.bytetrack.ui.screens.DashboardScreen
import com.zestyy.bytetrack.ui.screens.TimelineScreen
import com.zestyy.bytetrack.ui.theme.ByteOrange
import com.zestyy.bytetrack.ui.theme.TextTertiary

private sealed class Tab(val route: String, val label: String) {
    data object Dashboard : Tab("dashboard", "Home")
    data object Timeline : Tab("timeline", "Timeline")
    data object Apps : Tab("apps", "Apps")
}

private val tabs = listOf(Tab.Dashboard, Tab.Timeline, Tab.Apps)

private fun iconFor(tab: Tab) = when (tab) {
    is Tab.Dashboard -> Icons.Filled.Home
    is Tab.Timeline -> Icons.Filled.Timeline
    is Tab.Apps -> Icons.Filled.Apps
}

@Composable
fun ByteTrackNavHost(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route

    fun goToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // THE BUG THIS FIXES ("hit back and the app completely closes"): with three bottom tabs but
    // only a single Activity, whether system back "goes to Home tab" or "exits the app" was left
    // to NavHost's own back-stack bookkeeping - which, combined with saveState/restoreState on
    // every tab switch, meant back from Timeline or Apps was exiting straight past Home instead
    // of landing on it first. Made explicit instead of implicit: back is only ever allowed to
    // exit the app from the Home tab, same as it does now anyway - every other tab first sends
    // you Home, exactly like tapping the Home icon would.
    BackHandler(enabled = currentRoute != null && currentRoute != Tab.Dashboard.route) {
        goToTab(Tab.Dashboard.route)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = Tab.Dashboard.route) {
            composable(Tab.Dashboard.route) { DashboardScreen(viewModel) }
            composable(Tab.Timeline.route) { TimelineScreen(viewModel) }
            composable(Tab.Apps.route) { AppsScreen(viewModel) }
        }

        // Floating liquid-glass tab bar, iOS-style
        Box(modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)) {
            GlassCard(contentPadding = 8.dp) {
                Row {
                    tabs.forEach { tab ->
                        val selected = currentRoute == tab.route
                        Box(
                            modifier = Modifier
                                .clickable {
                                    if (!selected) goToTab(tab.route)
                                }
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = iconFor(tab),
                                    contentDescription = tab.label,
                                    tint = if (selected) ByteOrange else TextTertiary,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                Text(tab.label, color = if (selected) ByteOrange else TextTertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}
