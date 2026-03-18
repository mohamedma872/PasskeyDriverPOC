package com.example.passkeydriver.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.passkeydriver.data.DriverRepository
import com.example.passkeydriver.ui.screens.DashboardScreen
import com.example.passkeydriver.ui.screens.DriverListScreen
import com.example.passkeydriver.ui.screens.RegisterScreen
import com.example.passkeydriver.viewmodel.DriverListViewModel
import com.example.passkeydriver.viewmodel.RegisterViewModel

object Routes {
    const val DRIVER_LIST = "driver_list"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard/{driverId}"

    fun dashboard(driverId: String) = "dashboard/$driverId"
}

@Composable
fun AppNavigation(
    driverListViewModel: DriverListViewModel,
    registerViewModel: RegisterViewModel
) {
    val navController = rememberNavController()
    val activity = LocalContext.current as Activity

    LaunchedEffect(Unit) {
        driverListViewModel.setActivity(activity)
        registerViewModel.setActivity(activity)
    }

    NavHost(
        navController = navController,
        startDestination = Routes.DRIVER_LIST
    ) {
        composable(Routes.DRIVER_LIST) {
            DriverListScreen(
                viewModel = driverListViewModel,
                onAddDriver = { navController.navigate(Routes.REGISTER) },
                onDriverAuthenticated = { driverId ->
                    navController.navigate(Routes.dashboard(driverId))
                }
            )
        }

        composable(Routes.REGISTER) {
            RegisterScreen(
                viewModel = registerViewModel,
                onBack = { navController.popBackStack() },
                onRegistrationComplete = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DASHBOARD,
            arguments = listOf(navArgument("driverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val driver = DriverRepository.findById(driverId) ?: return@composable

            DashboardScreen(
                driver = driver,
                onLogout = {
                    navController.popBackStack(Routes.DRIVER_LIST, inclusive = false)
                }
            )
        }
    }
}
