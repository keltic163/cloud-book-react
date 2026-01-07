package com.krendstudio.cloudledger.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.krendstudio.cloudledger.ui.components.LoadingScreen
import com.krendstudio.cloudledger.ui.screens.HomeScreen
import com.krendstudio.cloudledger.ui.screens.OnboardingScreen
import com.krendstudio.cloudledger.ui.screens.WelcomeScreen
import com.krendstudio.cloudledger.viewmodel.AppViewModel
import com.krendstudio.cloudledger.viewmodel.AuthUiState
import com.krendstudio.cloudledger.viewmodel.LedgerUiState

private const val ROUTE_LOADING = "loading"
private const val ROUTE_WELCOME = "welcome"
private const val ROUTE_ONBOARDING = "onboarding"
private const val ROUTE_HOME = "home"

@Composable
fun AppNavGraph(
    authState: AuthUiState,
    ledgerState: LedgerUiState,
    viewModel: AppViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val forceOnboarding by viewModel.forceOnboarding.collectAsState()
    val targetRoute = when {
        authState.isLoading || ledgerState.isInitializing -> ROUTE_LOADING
        authState.user == null -> ROUTE_WELCOME
        forceOnboarding -> ROUTE_ONBOARDING
        ledgerState.currentLedgerId == null -> ROUTE_ONBOARDING
        else -> ROUTE_HOME
    }

    LaunchedEffect(targetRoute) {
        if (navController.currentDestination?.route != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_LOADING,
        modifier = modifier
    ) {
        composable(ROUTE_LOADING) { LoadingScreen() }
        composable(ROUTE_WELCOME) {
            WelcomeScreen(viewModel = viewModel)
        }
        composable(ROUTE_ONBOARDING) {
            OnboardingScreen(viewModel = viewModel)
        }
        composable(ROUTE_HOME) {
            HomeScreen(viewModel = viewModel, ledgerState = ledgerState)
        }
    }
}
