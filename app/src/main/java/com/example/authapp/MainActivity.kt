package com.example.authapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothSocket: BluetoothSocket
    private lateinit var outputStream: OutputStream
    private lateinit var connectionStatus: TextView
    private lateinit var authStatus: TextView

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val BLUETOOTH_PERMISSION_REQUEST_CODE = 2

    // HC-05 MAC Address (replace with your HC-05 MAC address)
    private val HC_05_MAC_ADDRESS = "00:22:12:01:4A:6F"
    private val UUID_SERIAL_PORT_PROFILE = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private var isConnected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        connectionStatus = findViewById(R.id.connectionStatus)
        authStatus = findViewById(R.id.authStatus)
        val connectButton: Button = findViewById(R.id.connectButton)
        val authButton: Button = findViewById(R.id.authButton)

        // Check and request permissions
        checkAndRequestPermissions()

        // Initialize Bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter == null) {
            connectionStatus.text = "Bluetooth not supported"
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }

        // Set up button click listeners
        connectButton.setOnClickListener {
            connectToHC05()
        }

        authButton.setOnClickListener {
            authenticateFingerprint()
        }
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val bluetoothPermissions = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                bluetoothPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (bluetoothPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, bluetoothPermissions.toTypedArray(), BLUETOOTH_PERMISSION_REQUEST_CODE)
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToHC05() {
        try {
            // Check permissions before proceeding
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    connectionStatus.text = "BLUETOOTH_CONNECT permission not granted"
                    return
                }
            }

            val device: BluetoothDevice = bluetoothAdapter.getRemoteDevice(HC_05_MAC_ADDRESS)
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID_SERIAL_PORT_PROFILE)
            bluetoothSocket.connect()
            outputStream = bluetoothSocket.outputStream

            isConnected = true
            connectionStatus.text = "Connected to HC-05"
            Toast.makeText(this, "Connected to HC-05", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            e.printStackTrace()
            connectionStatus.text = "SecurityException: Missing Bluetooth permissions"
        } catch (e: IOException) {
            e.printStackTrace()
            connectionStatus.text = "Failed to connect to HC-05: ${e.message}"
        }
    }

    private fun authenticateFingerprint() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                authStatus.text = "Fingerprint authentication available"
                showBiometricPrompt()
            }
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> {
                authStatus.text = "No fingerprint hardware available"
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> {
                authStatus.text = "Fingerprint hardware unavailable"
            }
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                authStatus.text = "No fingerprints enrolled"
            }
        }
    }

    private fun showBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                authStatus.text = "Fingerprint authenticated"
                sendResponseToArduino("Authenticated")
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                authStatus.text = "Fingerprint authentication failed"
                sendResponseToArduino("Failed")
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                authStatus.text = "Authentication error: $errString"
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fingerprint Authentication")
            .setSubtitle("Authenticate using your fingerprint")
            .setNegativeButtonText("Cancel")
            .build()

        biometricPrompt.authenticate(promptInfo)
    }

    private fun sendResponseToArduino(response: String) {
        if (!isConnected) {
            authStatus.text = "Not connected to HC-05"
            return
        }

        try {
            outputStream.write((response + "\n").toByteArray())
            authStatus.text = "Sent to Arduino: $response"
        } catch (e: IOException) {
            e.printStackTrace()
            authStatus.text = "Failed to send data to Arduino: ${e.message}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Location permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            BLUETOOTH_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Bluetooth not enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (::bluetoothSocket.isInitialized) {
                bluetoothSocket.close()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}