package xx.wallart

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import dev.about.About
import dev.about.AboutConfig
import dev.updater.AboutUpdater
import dev.updater.Updater
import dev.updater.UpdaterConfig
import xx.wallart.ui.accentAt
import xx.wallart.ui.MainScreen
import xx.wallart.ui.WallArtTheme
import xx.wallart.ui.WindowDark
import xx.wallart.ui.WindowLight
import xx.wallart.ui.isDarkTheme

// One description of this app's release, for the silent check at start-up and
// the About dialog's button alike.
val UPDATER_CONFIG = UpdaterConfig(appKey = "wallart", repo = "WallArt")

class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<WallViewModel>()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(localizedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.load(this)
        // Before the start-up check, so an update dialog is already in the app's accent.
        applyDialogAccent()
        // Looks for a newer build in this app's own GitHub release and asks
        // before it downloads anything.
        Updater.checkOnStart(this, UPDATER_CONFIG)
        applyWindowTheme()

        setContent {
            val themeMode by AppSettings.themeMode.collectAsState()
            val accentIndex by AppSettings.accentIndex.collectAsState()
            // The window is outside the composition: a theme changed in
            // Settings is carried out to the bars by hand.
            LaunchedEffect(themeMode) { applyWindowTheme() }
            // The shared dialogs are outside it too, and read the accent when they open.
            LaunchedEffect(accentIndex) { applyDialogAccent() }

            WallArtTheme(themeMode, accentIndex) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreen(vm = viewModel, onExit = ::finish, onAbout = ::showAbout)
                }
            }
        }
    }

    /** Out of sight the process may be ended at any moment: the work is written now. */
    override fun onStop() {
        super.onStop()
        viewModel.saveNow()
    }

    /**
     * The window background before the first frame and the colour the system
     * bar icons are drawn for, both following the app's theme rather than the
     * system's.
     */
    private fun applyWindowTheme() {
        val systemInDark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val dark = isDarkTheme(AppSettings.themeMode.value, systemInDark)
        window.setBackgroundDrawable((if (dark) WindowDark else WindowLight).toArgb().toDrawable())
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }

    /** The accent the About and update dialogs are drawn in: the one picked in Settings. */
    private fun applyDialogAccent() {
        About.accent = accentAt(AppSettings.accentIndex.value).toArgb()
    }

    private fun showAbout() {
        About.show(
            this,
            AboutConfig(
                repo = UPDATER_CONFIG.repoName,
                buildDate = BuildConfig.BUILD_DATE,
                updates = AboutUpdater(UPDATER_CONFIG),
            ),
        )
    }
}
