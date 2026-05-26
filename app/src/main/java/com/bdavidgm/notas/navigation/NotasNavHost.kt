package com.bdavidgm.notas.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bdavidgm.notas.NotasApplication
import com.bdavidgm.notas.ui.detail.DetailScreen
import com.bdavidgm.notas.ui.detail.DetailViewModel
import com.bdavidgm.notas.ui.home.HomeScreen
import com.bdavidgm.notas.ui.home.HomeViewModel

@Composable
fun NotasNavHost() {
    val navController = rememberNavController()
    val repository = (LocalContext.current.applicationContext as NotasApplication).repository

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
    ) {
        composable(ROUTE_HOME) {
            val vm: HomeViewModel = viewModel(factory = HomeViewModel.factory(repository))
            HomeScreen(
                viewModel = vm,
                onOpenNote = { id -> navController.navigate("detail/$id") },
            )
        }
        composable(
            route = "detail/{noteId}",
            arguments = listOf(
                navArgument("noteId") { type = NavType.LongType },
            ),
        ) { entry ->
            val noteId = entry.arguments!!.getLong("noteId")
            val vm: DetailViewModel = viewModel(
                key = noteId.toString(),
                factory = DetailViewModel.factory(noteId, repository),
            )
            DetailScreen(
                viewModel = vm,
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }
}

private const val ROUTE_HOME = "home"
