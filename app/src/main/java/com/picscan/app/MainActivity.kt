package com.picscan.app

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.picscan.app.data.model.BeerListType
import com.picscan.app.ui.screens.BeerCollectionScreen
import com.picscan.app.ui.screens.CameraScanScreen
import com.picscan.app.ui.screens.DrinkResultScreen
import com.picscan.app.ui.screens.HistoryScreen
import com.picscan.app.ui.screens.SettingsScreen
import com.picscan.app.ui.theme.PicScanTheme
import com.picscan.app.ui.viewmodel.ScannerViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ScannerViewModel by viewModels {
        val app = application as PicScanApplication
        ScannerViewModel.provideFactory(
            apiKeyRepo = app.apiKeyRepository,
            scannerRepo = app.geminiRepository,
            historyRepo = app.historyRepository,
            beerRepo = app.beerRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            PicScanTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PicScanNavigation(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PicScanNavigation(viewModel: ScannerViewModel) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "scan"
    ) {
        composable("scan") {
            CameraScanScreen(
                viewModel = viewModel,
                onNavigateToResult = { navController.navigate("result") },
                onNavigateToHistory = { navController.navigate("history") },
                onNavigateToBeers = { navController.navigate("beers") },
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }

        composable("result") {
            DrinkResultScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBeers = { navController.navigate("beers") }
            )
        }

        composable("beers") {
            BeerCollectionScreen(
                viewModel = viewModel,
                initialTab = BeerListType.KNOWN,
                onNavigateBack = { navController.popBackStack() },
                onBeerSelected = {
                    navController.navigate("result")
                }
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onItemSelected = {
                    navController.navigate("result")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
