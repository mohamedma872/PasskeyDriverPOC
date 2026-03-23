package com.example.passkeydriver.navigation

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.passkeydriver.ui.screens.CredentialLoginScreen
import com.example.passkeydriver.ui.screens.DashboardScreen
import com.example.passkeydriver.ui.screens.DriverListScreen
import com.example.passkeydriver.ui.screens.FaceVerifyLoginScreen
import com.example.passkeydriver.ui.screens.PinLoginScreen
import com.example.passkeydriver.ui.screens.SelfRegisterScreen
import com.example.passkeydriver.viewmodel.CredentialLoginViewModel
import com.example.passkeydriver.viewmodel.DriverListViewModel
import com.example.passkeydriver.viewmodel.FaceVerifyLoginViewModel
import com.example.passkeydriver.viewmodel.PinLoginViewModel
import com.example.passkeydriver.viewmodel.RegisterViewModel
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val DRIVER_PICKER    = "driver_list"
    const val PIN_LOGIN        = "pin_login/{driverId}/{name}"
    const val FACE_VERIFY      = "face_verify/{driverId}/{name}"
    const val DASHBOARD        = "dashboard/{driverId}/{name}"
    const val REGISTER         = "register"
    const val CREDENTIAL_LOGIN = "credential_login"

    fun pinLogin(driverId: String, name: String) =
        "pin_login/$driverId/${URLEncoder.encode(name, "UTF-8")}"

    fun faceVerify(driverId: String, name: String) =
        "face_verify/$driverId/${URLEncoder.encode(name, "UTF-8")}"

    fun dashboard(driverId: String, name: String) =
        "dashboard/$driverId/${URLEncoder.encode(name, "UTF-8")}"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.DRIVER_PICKER) {

        // ── Driver picker (start) ──────────────────────────────────────────────
        composable(Routes.DRIVER_PICKER) {
            val vm: DriverListViewModel = viewModel()
            DriverListScreen(
                viewModel = vm,
                onDriverSelected = { driver ->
                    navController.navigate(Routes.pinLogin(driver.id, driver.name))
                },
                onRegister = { navController.navigate(Routes.REGISTER) }
            )
        }

        // ── PIN login ──────────────────────────────────────────────────────────
        composable(
            route = Routes.PIN_LOGIN,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("name")     { type = NavType.StringType }
            )
        ) { back ->
            val driverId = back.arguments?.getString("driverId") ?: return@composable
            val name     = URLDecoder.decode(back.arguments?.getString("name") ?: "", "UTF-8")
            val app      = LocalContext.current.applicationContext as Application
            val vm: PinLoginViewModel = viewModel(
                factory = PinLoginViewModel.Factory(app, driverId)
            )
            PinLoginScreen(
                viewModel   = vm,
                driverName  = name,
                onDriverFound = { id, n ->
                    navController.navigate(Routes.faceVerify(id, n)) {
                        popUpTo(Routes.DRIVER_PICKER) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
                onFallbackLogin = { navController.navigate(Routes.CREDENTIAL_LOGIN) }
            )
        }

        // ── Face verification (second factor) ──────────────────────────────────
        composable(
            route = Routes.FACE_VERIFY,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("name")     { type = NavType.StringType }
            )
        ) { back ->
            val driverId = back.arguments?.getString("driverId") ?: return@composable
            val name     = URLDecoder.decode(back.arguments?.getString("name") ?: "", "UTF-8")
            val vm: FaceVerifyLoginViewModel = viewModel(
                factory = FaceVerifyLoginViewModel.Factory(driverId, name)
            )
            FaceVerifyLoginScreen(
                viewModel = vm,
                onSuccess = { id ->
                    navController.navigate(Routes.dashboard(id, name)) {
                        popUpTo(Routes.DRIVER_PICKER) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack(Routes.DRIVER_PICKER, inclusive = false) }
            )
        }

        // ── Dashboard ──────────────────────────────────────────────────────────
        composable(
            route = Routes.DASHBOARD,
            arguments = listOf(
                navArgument("driverId") { type = NavType.StringType },
                navArgument("name")     { type = NavType.StringType }
            )
        ) { back ->
            val driverId = back.arguments?.getString("driverId") ?: return@composable
            val name     = URLDecoder.decode(back.arguments?.getString("name") ?: "", "UTF-8")
            DashboardScreen(
                driverId   = driverId,
                driverName = name,
                onLogout   = {
                    navController.navigate(Routes.DRIVER_PICKER) {
                        popUpTo(Routes.DRIVER_PICKER) { inclusive = true }
                    }
                }
            )
        }

        // ── Self registration ──────────────────────────────────────────────────
        composable(Routes.REGISTER) {
            val vm: RegisterViewModel = viewModel()
            SelfRegisterScreen(
                viewModel = vm,
                onRegistered = { driverId ->
                    val name = vm.driverName.value.trim()
                    navController.navigate(Routes.dashboard(driverId, name)) {
                        popUpTo(Routes.DRIVER_PICKER) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        // ── Credential fallback login ──────────────────────────────────────────
        composable(Routes.CREDENTIAL_LOGIN) {
            val vm: CredentialLoginViewModel = viewModel()
            CredentialLoginScreen(
                viewModel = vm,
                onSuccess = { driverId, name ->
                    navController.navigate(Routes.dashboard(driverId, name)) {
                        popUpTo(Routes.DRIVER_PICKER) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
