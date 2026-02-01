package de.beat2er.garage

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.beat2er.garage.ui.screens.HomeScreen
import de.beat2er.garage.ui.theme.GarageTheme
import de.beat2er.garage.viewmodel.GarageViewModel

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* Permissions handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions()

        setContent {
            GarageTheme {
                val viewModel: GarageViewModel = viewModel()
                val uiState by viewModel.uiState.collectAsState()

                HomeScreen(
                    uiState = uiState,
                    onTriggerDevice = viewModel::triggerDevice,
                    onAddDevice = viewModel::addDevice,
                    onUpdateDevice = viewModel::updateDevice,
                    onDeleteDevice = viewModel::deleteDevice,
                    onImportDevices = viewModel::importDevices,
                    onExportConfig = viewModel::getExportConfig,
                    onClearToast = viewModel::clearToast,
                    onStartScan = viewModel::startBleScan,
                    onStopScan = viewModel::stopBleScan,
                    onToggleDebug = viewModel::toggleDebugMode,
                    onClearLogs = viewModel::clearDebugLogs,
                    onShowToast = viewModel::showToast,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()

        // Bluetooth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        // Kamera (fuer QR-Scanner)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}
