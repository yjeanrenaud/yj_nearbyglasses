package com.example.nearbyglasses

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var edtKeywords: EditText
    private lateinit var edtRssi: EditText
    private lateinit var edtCooldown: EditText

    private lateinit var listLog: ListView
    private lateinit var logAdapter: ArrayAdapter<String>
    private val logItems = mutableListOf<String>()

    private val reqCode = 4242
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BleScanService.ACTION_LOG_EVENT) return
            val name = intent.getStringExtra(BleScanService.EXTRA_NAME) ?: "(unbenannt)"
            val rssi = intent.getIntExtra(BleScanService.EXTRA_RSSI, -999)
            val time = intent.getLongExtra(BleScanService.EXTRA_TIME, System.currentTimeMillis())

            val line = "${timeFmt.format(Date(time))}  $name  (RSSI $rssi dBm)"
            logItems.add(0, line)
            logAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Notifs.ensureChannels(this)

        status = findViewById(R.id.txtStatus)
        edtKeywords = findViewById(R.id.edtKeywords)
        edtRssi = findViewById(R.id.edtRssi)
        edtCooldown = findViewById(R.id.edtCooldown)
        listLog = findViewById(R.id.listLog)

        logAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, logItems)
        listLog.adapter = logAdapter

        // Prefs laden in UI
        edtKeywords.setText(Settings.getKeywords(this).joinToString(","))
        edtRssi.setText(Settings.getRssiThreshold(this).toString())
        edtCooldown.setText((Settings.getCooldownMs(this) / 1000L).toString())

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val keywordsCsv = edtKeywords.text?.toString()?.trim().orEmpty()
            val rssi = edtRssi.text?.toString()?.trim()?.toIntOrNull() ?: -75
            val cooldownSec = edtCooldown.text?.toString()?.trim()?.toIntOrNull() ?: 15

            Settings.save(this, keywordsCsv, rssi, cooldownSec)
            status.text = "Status: Filter gespeichert"
        }

        findViewById<Button>(R.id.btnClear).setOnClickListener {
            logItems.clear()
            logAdapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (ensurePermissions()) startServiceCompat()
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, BleScanService::class.java))
            status.text = "Status: Scan gestoppt"
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(receiver, IntentFilter(BleScanService.ACTION_LOG_EVENT))
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun startServiceCompat() {
        val i = Intent(this, BleScanService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(i) else startService(i)
        status.text = "Status: Scan läuft (Foreground Service)"
    }

    private fun ensurePermissions(): Boolean {
        val perms = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms += Manifest.permission.BLUETOOTH_SCAN
            perms += Manifest.permission.BLUETOOTH_CONNECT
        } else {
            perms += Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.POST_NOTIFICATIONS
        }

        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        return if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), reqCode)
            false
        } else true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == reqCode) {
            val ok = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            status.text = if (ok) "Status: Berechtigungen ok" else "Status: Berechtigungen verweigert"
        }
    }
}
