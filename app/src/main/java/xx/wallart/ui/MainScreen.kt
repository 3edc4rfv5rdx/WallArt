package xx.wallart.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import xx.wallart.R

/** Which screen fills the window. */
private enum class Screen { EDITOR, SETTINGS }

/** The editor, or a screen the ⋮ menu opened over it. */
@Composable
fun MainScreen(onExit: () -> Unit, onAbout: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf(Screen.EDITOR) }
    when (screen) {
        Screen.EDITOR -> EditorScreen(onExit = onExit, onSettings = { screen = Screen.SETTINGS }, onAbout = onAbout)
        Screen.SETTINGS -> AppScreen(title = stringResource(R.string.settings), onBack = { screen = Screen.EDITOR }) {
            SettingsScreen()
        }
    }
}

/** The wall and its pictures under the top bar. */
@Composable
private fun EditorScreen(onExit: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
        TopBar(onExit = onExit, onSettings = onSettings, onAbout = onAbout)
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp))
    }
}

/** Close on the left; the ⋮ menu on the right. */
@Composable
private fun TopBar(onExit: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LargeIconButton(Icons.Filled.Close, stringResource(R.string.close), onExit)
        Spacer(Modifier.weight(1f))
        Box {
            MoreButton(onClick = { menu = true })
            AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AppMenuItem(stringResource(R.string.settings)) {
                    menu = false
                    onSettings()
                }
                AppMenuItem(stringResource(R.string.about)) {
                    menu = false
                    onAbout()
                }
            }
        }
    }
}
