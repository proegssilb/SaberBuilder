package com.github.proegssilb.saberbuilder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.core.app.ActivityCompat

const val REQUEST_ENABLE_BT = 15500

data class BLEDevice(val name: String, val manufacturer: String, val ble_address: String)

class BLEScanner(current_context: Context) {
    private val bluetoothManager: BluetoothManager? = current_context.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private var scanning = false
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

    fun checkScanPermission(current_activity: Context): Boolean {
        missing_permissions.clear()
        var hasPermission = true
        for (permission in this.permissionsNeeded) {
            if (current_activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission failed.")
                hasPermission = false
                missing_permissions.add(permission)
            } else {
                Log.i("BLEScanner.checkScanPermission", "Check for perm $permission succeeded.")
            }
        }
        return hasPermission
    }

    fun assertScanPermissions(current_activity: Context) {
        if (!this.checkScanPermission(current_activity)) {
            val errMsg = "You must ensure the user has granted bluetooth permissions before scanning. Missing permissions: ${missing_permissions.joinToString()}"
            throw AssertionError(errMsg)
        }
    }

    fun enableBluetooth(current_activity: Activity) {
        assertScanPermissions(current_activity)
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            ActivityCompat.startActivityForResult(
                current_activity,
                enableBtIntent,
                REQUEST_ENABLE_BT,
                null
            )
        }
    }

    fun scannerSupported() = bluetoothLeScanner != null

    fun startScanning(current_activity: Context, function: (BLEDevice) -> Unit) {
        assertScanPermissions(current_activity)
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
    fun stopScanning(current_activity: Context) {
        assertScanPermissions(current_activity)
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
                    }
                }
            } catch (se: SecurityException) {
                // We can't process any results.
                return;
            }
            Log.i("BLEScanner", "Modified device list: ${devices.keys.joinToString()}")
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Handle scan failure
        }
    }
}