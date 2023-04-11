package com.github.proegssilb.saberbuilder.ui

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import com.welie.blessed.BluetoothCentralManager
import com.welie.blessed.BluetoothPeripheral
import com.welie.blessed.PhyOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch


@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun DeviceListScreen(
    modifier: Modifier = Modifier,
    viewModel: DeviceListViewModel,
    onDevicePicked: (BLEDevice) -> Unit
) {
    val context = LocalContext.current

    // val permissionState = rememberMultiplePermissionsState(permissions = viewModel.permissionsNeeded)

    var permissionGranted = remember {
        viewModel.checkScanPermission(context)
    }

    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            viewModel.startScanning(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) {
        Log.d("DeviceListScreen", "Got callback. ${it.entries.joinToString { "${it.key}:${it.value}" }}")
        for (p in it.entries) {
            if (!p.value) {
                val shouldAsk = ActivityCompat.shouldShowRequestPermissionRationale(context as Activity, p.key)
                Log.d("DeviceListScreen", "Should ask again: ${p.key}, $shouldAsk")
            }
        }
        permissionGranted = viewModel.checkScanPermission(context)
    }

    Column(modifier = modifier
        .fillMaxSize()
        .wrapContentHeight(Alignment.CenterVertically)
    ) {
        // if (!permissionState.allPermissionsGranted) {
        //    PermissionPrompt(viewModel.missingPermissions) { permissionState.launchMultiplePermissionRequest() }
        if (!permissionGranted) {
            PermissionPrompt(viewModel.missingPermissions) {
                permissionLauncher.launch(viewModel.missingPermissions.toTypedArray())
            }
        } else if (viewModel.devices.any()) {
            DeviceList(devices = viewModel.devices.values.sortedBy { it.ble_address }.toList()) {
                viewModel.stopScanning(context)
                onDevicePicked(it)
            }
        } else {
            DeviceListPlaceholder(viewModel.scanning) { viewModel.startScanning(context)}
        }
    }
}

// TODO: Make this class comply with SRP.
class DeviceListViewModel(_context: Context) : ViewModel() {
    // BLE Scanning Code
    private val _missingPerms = mutableStateListOf<String>()
    val missingPermissions: List<String>
        get() = _missingPerms
    private val _devices = mutableStateMapOf<String, BLEDevice>()
    val devices: Map<String, BLEDevice>
        get() = _devices
    private val centralManager = BluetoothCentralManager(_context)

    val scanning: Boolean
        get() = centralManager.isScanning

    val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }

    // TODO: Remove this "Suppress" once the app is working & stuff stabilizes
    @Suppress("MemberVisibilityCanBePrivate")
    fun checkScanPermission(context: Context): Boolean {
        _missingPerms.clear()
        for (permission in this.permissionsNeeded) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission failed.")
                _missingPerms.add(permission)
            } else {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission succeeded.")
            }
        }

        return _missingPerms.isEmpty()
    }

    private fun assertScanPermissions(context: Context) {
        if (!this.checkScanPermission(context)) {
            val errMsg = "You must ensure the user has granted bluetooth permissions before scanning. Missing permissions: ${missingPermissions.joinToString()}"
            throw AssertionError(errMsg)
        }
    }

    @SuppressLint("MissingPermission", "Lint doesn't know that `assertScanPermissions` does check for permissions.")
    fun startScanning(context: Context) {
        assertScanPermissions(context)

        for(peripheral in centralManager.getConnectedPeripherals()) {
            handleScanResult(peripheral, null)
        }

        centralManager.scanForPeripherals(::handleScanResult) {}
    }

    private fun handleScanResult(peripheral: BluetoothPeripheral, _res: ScanResult?) {
        Log.d("DeviceList", "Callback for peripheral: ${peripheral.address}, $peripheral")
        try {
            val device = BLEDevice(peripheral.name, "", peripheral.address, peripheral)

            if (!_devices.contains(device.ble_address)) {
                _devices[device.ble_address] = device
            }
        } catch (se: SecurityException) {
            // We can't process any results.
            return
        }
    }

    @SuppressLint("MissingPermission", "Lint doesn't know that `assertScanPermissions` does check for permissions.")
    fun stopScanning(context: Context) {
        Log.d("DeviceList", "Scan stopping.")
        centralManager.stopScan()
    }
}

@Composable
fun DeviceList(devices: List<BLEDevice>, devicePicked: (BLEDevice) -> Unit) {
    Log.i("DeviceList", "Device count: ${devices.count()}")
    Column {
        Text(text = "Bluetooth Devices", style= MaterialTheme.typography.h1)
        LazyColumn(
            modifier = Modifier
                .padding(8.dp)
                .fillMaxSize()
        ) {
            items(
                items = devices,
                key = { device -> device.ble_address }
            ) { device ->
                DeviceCard(device = device, devicePicked)
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun DeviceCard(device: BLEDevice, devicePicked: (BLEDevice) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        onClick = { devicePicked(device) }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(text = device.name, style = MaterialTheme.typography.h2)
            Text(text = device.ble_address, style = MaterialTheme.typography.caption)
        }
    }
}

@Composable
fun DeviceListPlaceholder(scanning: Boolean, startScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
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
        Image(painter = if (scanning) {
            rememberAsyncImagePainter(
                ImageRequest.Builder(context).data(data = R.drawable.circular_progress_indicator_selective).apply(block = {
                    size(Size.ORIGINAL)
                }).build(), imageLoader = imageLoader
            )
        } else {
            painterResource(R.drawable.interrobang)
        }, contentDescription = null)
        Text(
            text = if (scanning) { "Scanning..." } else { "No devices found." },
            style = MaterialTheme.typography.h3,
            modifier = Modifier.padding(top = 25.dp)
        )
        // Subtitle
        Text(
            text = if (scanning) { "Please wait for results" } else { "If scanning does not start, click the button below." },
            style = MaterialTheme.typography.caption,
            modifier = Modifier.padding(vertical = 20.dp)
        )
        // Optional action button
        if (!scanning)
        {
            Button(onClick = startScan) {
                Text(text = "Start Scan")
            }
        }
    }
}

const val PERMISSION_PROMPT_TEXT =
    """This app requires the ability to scan for Bluetooth devices so that it can show a menu of sabers to manage. When you click the "Request Permissions" button below, the app will request the necessary permission. In older devices, scanning for Bluetooth devices is considered a location scan."""

const val PERMISSION_PROMPT_BUTTON = """Request Permissions"""

@Composable
fun PermissionPrompt(missing_permissions: List<String>, permissionCallback: () -> Unit) {
    val missingPermsStr = missing_permissions.joinToString(separator = "\n")
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(PERMISSION_PROMPT_TEXT)
        Text("Missing Permissions:\n$missingPermsStr", modifier = Modifier.padding(vertical = 30.dp))
        Button(onClick = permissionCallback) {
            Text(text = PERMISSION_PROMPT_BUTTON)
        }
    }
}