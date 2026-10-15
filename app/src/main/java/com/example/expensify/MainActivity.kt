package com.example.expensify

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.expensify.core.date.Today
import com.example.expensify.core.device.DeviceState
import com.example.expensify.core.ui.AppVisibility
import com.example.expensify.designsystem.theme.ExpensifyTheme
import com.example.expensify.feature.auth.domain.ThemePref
import com.example.expensify.feature.notifications.AlertNavigator
import com.example.expensify.feature.notifications.domain.AlertLinks
import com.example.expensify.navigation.AppRoot
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var today: Today

    @Inject
    lateinit var deviceState: DeviceState

    @Inject
    lateinit var visibility: AppVisibility

    @Inject
    lateinit var alertNavigator: AlertNavigator

    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the next Firebase call just works, or not */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (BuildConfig.USE_EMULATORS) requestLocalNetworkAccess()
        // A tap on a system notification opened the app: go where the alert leads once the shell is up.
        // Only on a fresh start; a rotation hands the same intent back and must not navigate again.
        if (savedInstanceState == null) openAlertLink(intent)
        // The theme the profile last asked for, read once before the first frame so it never flashes (req SET-02).
        val initialTheme = runBlocking { deviceState.theme.first() }
        setContent {
            val stored by deviceState.theme.collectAsStateWithLifecycle(initialValue = initialTheme)
            val dark = when (ThemePref.fromStored(stored)) {
                ThemePref.Light -> false
                ThemePref.Dark -> true
                ThemePref.System -> isSystemInDarkTheme()
            }
            ExpensifyTheme(darkTheme = dark) {
                AppRoot()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openAlertLink(intent)
    }

    override fun onStart() {
        super.onStart()
        visibility.onStart()
    }

    override fun onStop() {
        visibility.onStop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Midnight may have passed while the app was in the background.
        today.refresh()
    }

    /** An `expensify://` link from a system notification (architecture §8); anything else is ignored. */
    private fun openAlertLink(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        AlertLinks.parse(intent.dataString)?.let(alertNavigator::open)
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
