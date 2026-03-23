package com.vodr.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vodr.library.ui.LibraryScreen
import com.vodr.player.ui.PlayerScreen

object VodrNavRoutes {
    const val libraryRoute = "library"
    const val generateRoute = "generate"
    const val playerRoute = "player"

    val routes = listOf(libraryRoute, generateRoute, playerRoute)
    const val startDestination = libraryRoute
}

@Composable
fun VodrNavHost(
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = VodrNavRoutes.startDestination,
    ) {
        composable(VodrNavRoutes.libraryRoute) {
            LibraryScreen()
        }
        composable(VodrNavRoutes.generateRoute) {
        }
        composable(VodrNavRoutes.playerRoute) {
            PlayerScreen()
        }
    }
}
