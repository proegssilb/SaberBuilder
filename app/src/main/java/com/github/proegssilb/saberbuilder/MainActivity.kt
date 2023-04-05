package com.github.proegssilb.saberbuilder

import android.Manifest
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.github.proegssilb.saberbuilder.ui.DeviceListScreen
import com.github.proegssilb.saberbuilder.ui.DeviceListViewModel
import com.github.proegssilb.saberbuilder.ui.theme.SaberBuilderTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.runtime.getValue
import com.github.proegssilb.saberbuilder.ui.ModuleList


class MainActivity : ComponentActivity() {

    private lateinit var viewModel: SaberBuilderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = SaberBuilderViewModel(this)
        this.lifecycle.addObserver(viewModel.deviceListViewModel)

        setContent {
            SaberBuilderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    SaberBuilderApp(Modifier, viewModel)
                }
            }
        }
    }
}

data class SaberBuilderAppState (
    val activeBLEDevice: BLEDevice?,
        )

class SaberBuilderViewModel(context: Context): ViewModel() {
    private val _uiState = MutableStateFlow(SaberBuilderAppState(null))
    val uiState: StateFlow<SaberBuilderAppState> = _uiState.asStateFlow()
    val deviceListViewModel = DeviceListViewModel(context)

    fun setActiveBleDevice(bleDevice: BLEDevice) {
        _uiState.value = SaberBuilderAppState(bleDevice)
    }
}

@Composable
fun SaberBuilderApp(
    modifier: Modifier = Modifier,
    viewModel: SaberBuilderViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Get the name of the current screen
    val currentScreen = CurrentScreen.valueOf(
        backStackEntry?.destination?.route ?: CurrentScreen.DeviceList.name
    )
    Scaffold(
        topBar = {
            SaberBuilderAppBar(
                currentScreen = currentScreen,
                canNavigateBack = navController.previousBackStackEntry != null,
                navigateUp = { navController.navigateUp() }
            )
        }
    ) {innerPadding ->
        val uiState by viewModel.uiState.collectAsState()
        NavHost(
            navController = navController,
            startDestination = CurrentScreen.DeviceList.name,
            modifier = modifier.padding(innerPadding)
        ) {
            // v----- Routes -------v
            composable(route = CurrentScreen.DeviceList.name) {
                DeviceListScreen(viewModel = viewModel.deviceListViewModel) {
                    viewModel.setActiveBleDevice(it)
                    navController.navigate(CurrentScreen.ModuleList.name)
                }
            }

            composable(route = CurrentScreen.ModuleList.name) {
                ModuleList(listOf(), {})
            }

            composable(route = CurrentScreen.ModuleConfig.name) {
                // TODO
            }
            // ^----- Routes -------^
        }
    }
}

enum class CurrentScreen(@StringRes val title: Int) {
    DeviceList(R.string.app_name),
    ModuleList(R.string.app_name),
    ModuleConfig(R.string.app_name)
}

/**
 * Composable that displays the topBar and displays back button if back navigation is possible.
 */
@Composable
fun SaberBuilderAppBar(
    currentScreen: CurrentScreen,
    canNavigateBack: Boolean,
    navigateUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        title = { Text(stringResource(currentScreen.title)) },
        modifier = modifier,
        navigationIcon = {
            if (canNavigateBack) {
                IconButton(onClick = navigateUp) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_button)
                    )
                }
            }
        }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun DefaultPreview() {
    val hasPermissions = true
    val missingPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val devices = if (true) {
        listOf(
            BLEDevice("Device 1", "", "78:9A:BC:CD:EF:12"),
            BLEDevice("Device 2", "", "78:9A:BC:CD:DF:13"),
        )
    } else {
        listOf()
    }
    val scanning = true
    SaberBuilderTheme {
        ModuleList(modules = listOf(), onModuleSelected = {})
//        DeviceList(devices = devices)
        // MainScreen(hasPermissions, missingPermissions, devices, scanning, { }) { }
    }
}
