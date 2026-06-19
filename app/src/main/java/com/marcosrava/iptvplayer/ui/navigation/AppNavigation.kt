package com.marcosrava.iptvplayer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.marcosrava.iptvplayer.ui.screens.BrowserScreen
import com.marcosrava.iptvplayer.ui.screens.FavoritesScreen
import com.marcosrava.iptvplayer.ui.screens.HomeScreen
import com.marcosrava.iptvplayer.ui.screens.PlaylistsScreen
import com.marcosrava.iptvplayer.ui.screens.VpnScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home      : Screen("home",      "Canales",   Icons.Default.Tv)
    object Playlists : Screen("playlists", "Listas",    Icons.Default.PlaylistPlay)
    object Favorites : Screen("favorites", "Favoritos", Icons.Default.Favorite)
    object Vpn       : Screen("vpn",       "VPN",       Icons.Default.VpnKey)
    object Browser   : Screen("browser",   "Ubuntu",    Icons.Default.Computer)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Playlists,
    Screen.Favorites,
    Screen.Vpn
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route != Screen.Browser.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.label) },
                            label = { Text(screen.label) },
                            selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                HomeScreen()
            }
            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    onNavigateToBrowser = {
                        navController.navigate(Screen.Browser.route)
                    }
                )
            }
            composable(Screen.Favorites.route) {
                FavoritesScreen()
            }
            composable(Screen.Vpn.route) {
                VpnScreen()
            }
            composable(Screen.Browser.route) {
                BrowserScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
