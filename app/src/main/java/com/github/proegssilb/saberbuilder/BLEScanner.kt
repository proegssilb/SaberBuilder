package com.github.proegssilb.saberbuilder

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log

/**
 * Manages the verbs associated with scanning.
 *
 * There's a lot of fields, but the actual goal is to make sure scanning can run, and then run it. If the APIs for BLE
 * change, this is the class that gets edited. It isolates the app from the BLE APIs.
 */
class BLEScanner(private val context: Context) {
    private val bluetoothManager: BluetoothManager? = context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    var scanning = false
    private val scanCallbackHandle = MyScanCallback()
    private val handler = Handler()
    private var resultCallback: ((BLEDevice) -> Unit)? = null
    private val permissionsNeeded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
    }
    val devices: HashMap<String, BLEDevice> = hashMapOf()
    val missing_permissions: MutableList<String> = mutableListOf()

    // Stops scanning after 90 seconds.
    private val SCAN_PERIOD: Long = 90000

    fun checkScanPermission(): Boolean {
        missing_permissions.clear()
        var hasPermission = true
        for (permission in this.permissionsNeeded) {
            if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission failed.")
                hasPermission = false
                missing_permissions.add(permission)
            } else {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission succeeded.")
            }
        }
        return hasPermission
    }

    private fun assertScanPermissions() {
        if (!this.checkScanPermission()) {
            val errMsg = "You must ensure the user has granted bluetooth permissions before scanning. Missing permissions: ${missing_permissions.joinToString()}"
            throw AssertionError(errMsg)
        }
    }

    fun scannerSupported() = bluetoothLeScanner != null

    @SuppressLint("MissingPermission", "Lint doesn't know that `assertScanPermissions` does check for permissions.")
    fun startScanning(function: (BLEDevice) -> Unit) {
        assertScanPermissions()
        this.resultCallback = function
        if (!scanning) { // Stops scanning after a pre-defined scan period.
            Log.i("BLEScanner", "Starting scan")
            bluetoothLeScanner?.startScan(scanCallbackHandle)
            scanning = true
            handler.postDelayed({
                Log.i("BLEScanner", "Scan timed out")
                bluetoothLeScanner?.stopScan(scanCallbackHandle)
                scanning = false
            }, SCAN_PERIOD)
        }
    }

    @SuppressLint("MissingPermission", "Lint doesn't know that `assertScanPermissions` does check for permissions.")
    fun stopScanning() {
        assertScanPermissions()
        Log.i("BLEScanner", "Stopping scan")
        bluetoothLeScanner?.stopScan(scanCallbackHandle)
    }

    private inner class MyScanCallback : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            processResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            if (results == null) { return }
            for (result in results) {
                processResult(result)
            }
        }

        fun processResult(result: ScanResult?) {
            if (result == null) { return }
            try {
                val device = BLEDevice(result.device.name ?: "(unnamed)", "", result.device.address)
                if (!devices.contains(device.ble_address)) {
                    devices[device.ble_address] = device
                    if (resultCallback != null) {
                        resultCallback?.invoke(device)
                        Log.i("BLEScanner", "Modified device list: ${devices.values.map {d -> "${d.name} (${d.ble_address})" }.joinToString()}")
                    }
                }
            } catch (se: SecurityException) {
                // We can't process any results.
                return;
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Handle scan failure
        }
    }
}