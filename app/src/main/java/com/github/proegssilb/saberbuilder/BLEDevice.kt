package com.github.proegssilb.saberbuilder

import android.annotation.SuppressLint
import com.welie.blessed.BluetoothPeripheral

class BLEDevice(val name: String, val manufacturer: String, val ble_address: String, val device: BluetoothPeripheral? = null)
{
    @SuppressLint("MissingPermission")
    constructor(peripheral: BluetoothPeripheral) : this(peripheral.name, "", peripheral.address, peripheral) { }

}