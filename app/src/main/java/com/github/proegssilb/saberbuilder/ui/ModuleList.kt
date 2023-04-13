package com.github.proegssilb.saberbuilder.ui

import android.bluetooth.BluetoothGattService
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.size.Size
import com.github.proegssilb.saberbuilder.BLEDevice
import com.github.proegssilb.saberbuilder.R
import com.github.proegssilb.saberbuilder.SaberModule
import com.welie.blessed.BluetoothCentralManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class Module(
    val name: String,
    val render_location: Point,
    val target_location: Point,
    val saberMod: SaberModule,
)

data class ModuleListViewState (
    val loaded: Boolean,
    val loading: Boolean,
    val modules: List<SaberModule>?,
    val selectedBLEDevice: BLEDevice?,
    val displayModules: List<Module> = listOf(),
        )

class ModuleListViewModel() : ViewModel() {
    private val _uiState = MutableStateFlow(ModuleListViewState(
        loaded = false,
        loading = false,
        modules = null,
        selectedBLEDevice = null
    ))
    val uiState: StateFlow<ModuleListViewState> = _uiState.asStateFlow()

    fun setBleDevice(newBLEDevice: BLEDevice) {
        viewModelScope.run {
            _uiState.update { it.copy(selectedBLEDevice = newBLEDevice) }
        }
    }

    fun loadModuleList(context: Context) {
        Log.d("ModuleListViewModel", "Loading modules...")
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, loaded = false) }
            if (_uiState.value.selectedBLEDevice != null && _uiState.value.selectedBLEDevice?.device != null) {
                val bleDevice = _uiState.value.selectedBLEDevice!!
                val peripheral = bleDevice.device!!

                // Connect, and then wait up to 10s for the background service discovery to complete.
                peripheral.connect()
                val serviceAttempts = 20
                val serviceDelay: Long = 500
                for (attempt in 1..serviceAttempts) {
                    if (peripheral.services.any()) {
                        break
                    }
                    delay(serviceDelay)
                }

                // Build the module list, but note that we might have timed out.
                val services = peripheral.services
                if (services.any()) {
                    val modsList = services.filter(::isServiceASaberModule).map(::serviceToMod)
                    Log.d("ModuleListViewModel", "Found modules: ${modsList.joinToString { "${it.name} (${it.address})" }}")
                    _uiState.update { it.copy(modules = modsList, displayModules = modsList
                        .map(::saberModToDisplayable) ) }
                } else {
                    Log.w("ModuleListViewModel", "Could not find modules on device: ${bleDevice.name} (${bleDevice.ble_address})")
                }
                _uiState.update { it.copy(loading = false, loaded = true) }
            }
        }
    }

    private val nameMap = mapOf (
        UUID.fromString("7d0a7103-7699-494e-b638-deadbeef0000") to "Blade LED",
        UUID.fromString("7d0a309f-7699-494e-b638-deadbeef0000") to "Mixer Service",
        UUID.fromString("7d0a00b1-7699-494e-b638-deadbeef0000") to "I2C On/Off LED Button",
        UUID.fromString("7d0a00b2-7699-494e-b638-deadbeef0000") to "Raw On/Off LED Button",
        UUID.fromString("adaf0001-4369-7263-7569-74507974686e") to "Adafruit Information Service"
    )

    private fun isServiceASaberModule(service: BluetoothGattService): Boolean {
        val minServiceId = 0x7d0a00007699494e
        val maxServiceId = 0x7d0aFFFF7699494e
        val id = service.uuid
        val bits = id.mostSignificantBits
        Log.d("ModuleListViewModel", "Filtering service: $id, ${java.lang.Long.toHexString(bits)}")
        return bits in minServiceId..maxServiceId
    }

    private fun serviceToMod(service: BluetoothGattService): SaberModule {
        val serviceId = service.uuid
        val instanceId = service.instanceId
        val name = nameMap[serviceId] ?: "Unnamed Service"

        return SaberModule(name, serviceId, instance = instanceId)
    }

    private fun saberModToDisplayable(saberMod: SaberModule): Module {
        return Module(saberMod.name, Point(0, 0), Point(0, 0), saberMod)
    }
}

@Composable
fun ModuleListScreen(
    bleDevice: BLEDevice?,
    modifier: Modifier = Modifier,
    viewModel: ModuleListViewModel = androidx.lifecycle.viewmodel.compose.viewModel(),
    onModuleClick: (SaberModule) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(bleDevice) {
        if (bleDevice != null) {
            viewModel.setBleDevice(bleDevice)
            viewModel.loadModuleList(context)
        }
    }

    Log.d("ModuleListScreen", "Module list: ${uiState.displayModules.joinToString { it.name }}")

    if (!uiState.loaded) {
        ModuleListLoadingScreen(working = uiState.loading, modifier = modifier)
    } else {
        ModuleList(modules = uiState.displayModules) { mod: Module -> onModuleClick(mod.saberMod) }
    }
}

@Composable
fun ModuleListLoadingScreen(working: Boolean, modifier: Modifier = Modifier) {
    WorkingOrErrorWindow(
        working = working,
        title = if (working) {"Loading..."} else {"Error while loading data from device."},
        subtitle = if (working) {"We have to pull data & process it."} else {"Could be a connection issue, or your saber might be weird."})
}

@Composable
fun WorkingOrErrorWindow(working: Boolean, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        ModuleListBackground()
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            val context = LocalContext.current
            val imageLoader = ImageLoader.Builder(context)
                .components {
                    if (Build.VERSION.SDK_INT >= 28) {
                        add(ImageDecoderDecoder.Factory())
                    } else {
                        add(GifDecoder.Factory())
                    }
                }
                .build()
            Image(painter = if (working) {
                rememberAsyncImagePainter(
                    ImageRequest.Builder(context).data(data = R.drawable.circular_progress_indicator_selective).apply(block = {
                        size(Size.ORIGINAL)
                    }).build(), imageLoader = imageLoader
                )
            } else {
                painterResource(R.drawable.interrobang)
            }, contentDescription = null)
            Text(
                text = title,
                style = MaterialTheme.typography.h3,
                modifier = modifier.padding(top = 25.dp)
            )
            // Subtitle
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption,
                modifier = modifier.padding(vertical = 20.dp)
            )
        }
    }
}

@Composable
fun ModuleList(
    modules: List<Module>,
    modifier: Modifier = Modifier,
    onModuleSelected: (Module) -> Unit,
) {
    Box(
        modifier = modifier.fillMaxSize()
    ) {
        ModuleListBackground()
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(modules) { module ->
                ModuleListItem(module = module) {
                    onModuleSelected(module)
                }
            }
        }
    }
}

@Composable
fun ModuleListItem(
    module: Module,
    onModuleClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onModuleClick),
        backgroundColor = Color.White,
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = module.name,
                style = MaterialTheme.typography.h6,
                color = Color.Black
            )
        }
    }
}

@Composable
fun ModuleListBackground(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(id = R.drawable.saber_with_runes_blue),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}