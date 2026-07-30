package com.zestyy.bytetrack

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.zestyy.bytetrack.data.repository.PermissionsRepository
import com.zestyy.bytetrack.service.UsageTrackingService
import com.zestyy.bytetrack.ui.ByteTrackNavHost
import com.zestyy.bytetrack.ui.MainViewModel
import com.zestyy.bytetrack.ui.screens.PermissionsScreen
import com.zestyy.bytetrack.ui.theme.ByteTrackTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private lateinit var permissionsRepo: PermissionsRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permissionsRepo = PermissionsRepository(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            var hasUsageAccess by mutableStateOf(permissionsRepo.hasUsageAccess())

            ByteTrackTheme {
                androidx.compose.material3.Surface(
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                ) {
                    if (hasUsageAccess) {
                        ByteTrackNavHost(viewModel = viewModel)
                    } else {
                        PermissionsScreen(onGranted = {
                            hasUsageAccess = true
                            startTrackingService()
                        })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (permissionsRepo.hasUsageAccess()) {
            startTrackingService()
            viewModel.refresh()
        }
    }

    private fun startTrackingService() {
        val intent = Intent(this, UsageTrackingService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }
}
