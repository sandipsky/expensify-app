package com.example.expensify

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.example.expensify.core.date.Today
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var today: Today

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the next Firebase call just works, or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.USE_EMULATORS) requestLocalNetworkAccess()
        setContent {
            ExpensifyTheme {
                AppRoot()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Midnight may have passed while the app was in the background.
        today.refresh()
    }

    /**
     * Android 17 (API 37) drops local-network connections from apps targeting
     * SDK 37 unless they hold ACCESS_LOCAL_NETWORK, and the Firebase emulators
     * are on the local address 10.0.2.2. Debug builds only; the permission is
     * declared in the debug manifest alone.
     */
    private fun requestLocalNetworkAccess() {
        if (Build.VERSION.SDK_INT < LOCAL_NETWORK_PROTECTION_SDK) return
        val permission = "android.permission.ACCESS_LOCAL_NETWORK"
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            localNetworkPermission.launch(permission)
        }
    }

    private companion object {
        /** Android 17. */
        const val LOCAL_NETWORK_PROTECTION_SDK = 37
    }
}
