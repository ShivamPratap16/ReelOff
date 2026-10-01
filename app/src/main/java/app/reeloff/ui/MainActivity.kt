package app.reeloff.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as SystemSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.reeloff.data.Settings
import app.reeloff.service.ReelOffAccessibilityService

class MainActivity : ComponentActivity(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var settings: Settings
    private var state by mutableStateOf<UiState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        settings = Settings.get(this)
        refresh()
        setContent {
            ReelOffTheme {
                val current = state ?: return@ReelOffTheme
                if (!current.disclosureAccepted) {
                    DisclosureScreen(
                        onAccept = {
                            settings.disclosureAccepted = true
                            openAccessibilitySettings()
                        },
                        onDecline = { finish() },
                    )
                } else {
                    MainScreen(
                        state = current,
                        settings = settings,
                        onOpenAccessibility = ::openAccessibilitySettings,
                        onOpenAppInfo = ::openAppInfo,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        settings.prefs.registerOnSharedPreferenceChangeListener(this)
        refresh()
    }

    override fun onPause() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) = refresh()

    private fun refresh() {
        state = UiState.from(settings, isServiceEnabled(this))
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(SystemSettings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(SystemSettings.ACTION_SETTINGS))
        }
    }

    /** Sideloaded apps on Android 13+ must "Allow restricted settings" from App info first. */
    private fun openAppInfo() {
        startActivity(
            Intent(SystemSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    companion object {
        fun isServiceEnabled(context: Context): Boolean {
            val expected = ComponentName(context, ReelOffAccessibilityService::class.java)
            val enabled = SystemSettings.Secure.getString(
                context.contentResolver,
                SystemSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
        }
    }
}
