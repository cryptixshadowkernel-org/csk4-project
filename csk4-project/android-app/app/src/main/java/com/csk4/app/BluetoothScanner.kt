package com.csk4.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.ActivityCompat
import org.json.JSONArray
import org.json.JSONObject

class BluetoothScanner(private val ctx: Context) {

    fun scan(onDone: (JSONArray) -> Unit) {
        val arr = JSONArray()
        val manager = ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter: BluetoothAdapter? = manager?.adapter
        if (adapter == null || !adapter.isEnabled) { onDone(arr); return }

        try {
            if (Build.VERSION.SDK_INT < 31 ||
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
                adapter.bondedDevices?.forEach { d ->
                    arr.put(JSONObject().apply {
                        put("name", d.name ?: "Unknown")
                        put("address", d.address)
                        put("type", "paired")
                        put("rssi", 0)
                    })
                }
            }
        } catch (_: Exception) {}

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) { onDone(arr); return }
        if (Build.VERSION.SDK_INT >= 31 &&
            ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_SCAN)
            != PackageManager.PERMISSION_GRANTED) { onDone(arr); return }

        val found = mutableSetOf<String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { d ->
                    if (found.add(d.address)) {
                        try {
                            val name = if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 31) d.name else null
                            arr.put(JSONObject().apply {
                                put("name", name ?: result.scanRecord?.deviceName ?: "Unknown")
                                put("address", d.address)
                                put("type", "ble")
                                put("rssi", result.rssi)
                            })
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        try { scanner.startScan(cb) } catch (_: Exception) {}

        Handler(Looper.getMainLooper()).postDelayed({
            try { scanner.stopScan(cb) } catch (_: Exception) {}
            onDone(arr)
        }, 8000)
    }
}