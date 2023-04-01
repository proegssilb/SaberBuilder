package com.github.proegssilb.saberbuilder

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.proegssilb.saberbuilder.ui.theme.SaberBuilderTheme


class DeviceList : ComponentActivity() {

    private lateinit var bleScanner: BLEScanner

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { isGranted: Map<String, Boolean> ->
            if (isGranted.values.any { b -> b }) {
                compose()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bleScanner = BLEScanner(this)

        compose()
    }

    /// Draw the UI. We don't want to call this every frame, but we should call it when external state changes.
    private fun compose() {
        bleScanner.checkScanPermission(this)
        Log.d("MainActivity", "Permissions missing while composing: ${bleScanner.missing_permissions.joinToString()}")
        if (bleScanner.missing_permissions.isEmpty()) {
            bleScanner.enableBluetooth(this)
            bleScanner.startScanning(this) { _ -> this.compose() }
        }
        setContent {
            SaberBuilderTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    val hasPermission = bleScanner.checkScanPermission(this)
                    val devices = bleScanner.devices.values.toList()
                    MainScreen(hasPermission, bleScanner.missing_permissions, devices, ::onRequestPermissions)
                }
            }
        }
    }

    private fun onRequestPermissions() {
        val hasPerms = bleScanner.checkScanPermission(this)
        if (hasPerms && bleScanner.missing_permissions.isEmpty()) {
            return
        }

        requestPermissionLauncher.launch(bleScanner.missing_permissions.toTypedArray())
    }
}

@Composable
fun MainScreen(
    hasPermissions: Boolean,
    missingPermissions: List<String>,
    devices: List<BLEDevice>,
    permissionCallback: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = "Bluetooth Devices")
        if (hasPermissions) {
            DeviceList(devices = devices)
        } else {
            PermissionPrompt(missingPermissions, permissionCallback)
        }
    }
}

@Composable
fun DeviceList(devices: List<BLEDevice>) {
    Log.i("DeviceList", "Device count: ${devices.count()}")
    LazyColumn(modifier = Modifier
        .padding(8.dp)
        .fillMaxSize()) {
        items(
            items = devices,
            key = { device -> device.ble_address }
        ) { device ->
            Text(
                text = "${device.name} - ${device.ble_address}",
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .fillParentMaxWidth()
            )
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

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val hasPermissions = true
    val missingPermissions = listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    val devices = listOf(
        BLEDevice("Device 1", "", "78:9A:BC:CD:EF:12"),
        BLEDevice("Device 2", "", "78:9A:BC:CD:DF:13"),
    )
    SaberBuilderTheme {
        MainScreen(hasPermissions, missingPermissions, devices) { }
    }
}
