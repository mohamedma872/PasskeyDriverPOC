package com.example.passkeydriver.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.passkeydriver.data.DriverApi
import com.example.passkeydriver.ui.screens.AdminLoginScreen
import com.example.passkeydriver.ui.screens.AdminScreen
import com.example.passkeydriver.ui.screens.DashboardScreen
import com.example.passkeydriver.ui.screens.DriverDetailScreen
import com.example.passkeydriver.ui.screens.DriverPinScreen
import com.example.passkeydriver.ui.screens.DriverTapScreen
import com.example.passkeydriver.ui.screens.NfcReadScreen
import com.example.passkeydriver.ui.screens.NfcWriteScreen
import com.example.passkeydriver.ui.screens.PasswordLoginScreen
import com.example.passkeydriver.viewmodel.AdminViewModel
import com.example.passkeydriver.viewmodel.DriverAuthViewModel
import com.example.passkeydriver.viewmodel.NfcViewModel

object Routes {
    const val DRIVER_TAP = "driver_tap"
    const val DRIVER_PIN = "driver_pin/{driverId}/{driverName}"
    const val PASSWORD_LOGIN = "password_login"
    const val ADMIN_LOGIN = "admin_login"
    const val ADMIN = "admin"
    const val DRIVER_DETAIL = "driver_detail/{driverId}"
    const val NFC_WRITE = "nfc_write/{driverId}/{driverName}"
    const val NFC_READ = "nfc_read?expectedDriverId={expectedDriverId}&expectedDriverName={expectedDriverName}"
    const val DASHBOARD = "dashboard/{driverId}/{driverName}"

    fun driverPin(driverId: String, driverName: String) =
        "driver_pin/${enc(driverId)}/${enc(driverName)}"

    fun driverDetail(driverId: String) = "driver_detail/${enc(driverId)}"

    fun nfcWrite(driverId: String, driverName: String) =
        "nfc_write/${enc(driverId)}/${enc(driverName)}"

    fun nfcRead(expectedDriverId: String = "", expectedDriverName: String = "") =
        "nfc_read?expectedDriverId=${enc(expectedDriverId)}&expectedDriverName=${enc(expectedDriverName)}"

    fun dashboard(driverId: String, driverName: String) =
        "dashboard/${enc(driverId)}/${enc(driverName)}"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun AppNavigation(
    nfcViewModel: NfcViewModel,
    adminViewModel: AdminViewModel,
    driverAuthViewModel: DriverAuthViewModel,
    api: DriverApi
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DRIVER_TAP) {

        composable(Routes.DRIVER_TAP) {
            DriverTapScreen(
                nfcViewModel = nfcViewModel,
                api = api,
                onDriverIdentified = { driverId, driverName ->
                    navController.navigate(Routes.driverPin(driverId, driverName))
                },
                onPasswordLogin = { navController.navigate(Routes.PASSWORD_LOGIN) },
                onAdmin = { navController.navigate(Routes.ADMIN_LOGIN) }
            )
        }

        composable(
            route = Routes.DRIVER_PIN,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("driverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val driverName = backStackEntry.arguments?.getString("driverName") ?: return@composable
            DriverPinScreen(
                driverId = driverId,
                driverName = driverName,
                viewModel = driverAuthViewModel,
                onAuthenticated = { id ->
                    navController.navigate(Routes.dashboard(id, driverName)) {
                        popUpTo(Routes.DRIVER_TAP) { inclusive = false }
                    }
                },
                onForgotPin = { navController.navigate(Routes.PASSWORD_LOGIN) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PASSWORD_LOGIN) {
            PasswordLoginScreen(
                viewModel = driverAuthViewModel,
                onAuthenticated = { driverId, driverName ->
                    navController.navigate(Routes.dashboard(driverId, driverName)) {
                        popUpTo(Routes.DRIVER_TAP) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN_LOGIN) {
            AdminLoginScreen(
                viewModel = adminViewModel,
                onSuccess = {
                    navController.navigate(Routes.ADMIN) {
                        popUpTo(Routes.ADMIN_LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.ADMIN) {
            AdminScreen(
                viewModel = adminViewModel,
                onDriverDetail = { driver ->
                    navController.navigate(Routes.driverDetail(driver.id))
                },
                onReadCard = { navController.navigate(Routes.nfcRead()) },
                onLogout = {
                    adminViewModel.logout()
                    navController.navigate(Routes.DRIVER_TAP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.DRIVER_DETAIL,
            arguments = listOf(navArgument("driverId") { type = NavType.StringType })
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            DriverDetailScreen(
                driverId = driverId,
                api = api,
                onWriteCard = { driver ->
                    navController.navigate(Routes.nfcWrite(driver.id, driver.name))
                },
                onVerifyCard = { id, name ->
                    navController.navigate(Routes.nfcRead(id, name))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.NFC_WRITE,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("driverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val driverName = backStackEntry.arguments?.getString("driverName") ?: return@composable
            NfcWriteScreen(
                driverId = driverId,
                driverName = driverName,
                nfcViewModel = nfcViewModel,
                onSuccess = {
                    adminViewModel.markCardIssued(driverId)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.NFC_READ,
            arguments = listOf(
                navArgument("expectedDriverId") { type = NavType.StringType; defaultValue = "" },
                navArgument("expectedDriverName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val expectedDriverId = backStackEntry.arguments?.getString("expectedDriverId") ?: ""
            val expectedDriverName = backStackEntry.arguments?.getString("expectedDriverName") ?: ""
            NfcReadScreen(
                nfcViewModel = nfcViewModel,
                api = api,
                expectedDriverId = expectedDriverId,
                expectedDriverName = expectedDriverName,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.DASHBOARD,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("driverName") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val driverId = backStackEntry.arguments?.getString("driverId") ?: return@composable
            val driverName = backStackEntry.arguments?.getString("driverName") ?: return@composable
            DashboardScreen(
                driverId = driverId,
                driverName = driverName,
                onLogout = {
                    navController.navigate(Routes.DRIVER_TAP) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
