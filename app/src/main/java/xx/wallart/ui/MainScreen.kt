package xx.wallart.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import xx.wallart.CalibrationEdit
import xx.wallart.EditorState
import xx.wallart.R
import xx.wallart.WallViewModel

/** Which screen fills the window. */
private enum class Screen { EDITOR, SETTINGS }

/** The editor, or a screen the ⋮ menu opened over it. */
@Composable
fun MainScreen(vm: WallViewModel, onExit: () -> Unit, onAbout: () -> Unit) {
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(Screen.EDITOR) }
    // Said whichever screen is open.
    LaunchedEffect(vm) {
        vm.notices.collect { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    when (screen) {
        Screen.EDITOR -> EditorScreen(vm, onExit = onExit, onSettings = { screen = Screen.SETTINGS }, onAbout = onAbout)
        Screen.SETTINGS -> AppScreen(title = stringResource(R.string.settings), onBack = { screen = Screen.EDITOR }) {
            SettingsScreen()
        }
    }
}

/** The wall and its pictures under the top bar; the calibration's size and buttons under the wall while one is made. */
@Composable
private fun EditorScreen(vm: WallViewModel, onExit: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    val state by vm.state.collectAsState()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }
    val choosePhoto = {
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    BackHandler(enabled = state.calibrating != null, onBack = vm::cancelCalibration)

    // The keyboard too: the size fields sit at the bottom, where it comes up.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
        TopBar(
            state,
            onExit = onExit,
            onChoosePhoto = choosePhoto,
            onCalibrate = vm::startCalibration,
            onSettings = onSettings,
            onAbout = onAbout,
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val image = state.image
            if (image != null) {
                WallCanvas(image, state.calibrating, onMoveCorner = vm::moveCorner, modifier = Modifier.fillMaxSize())
            } else if (!state.busy) {
                Button(onClick = choosePhoto) { Text(stringResource(R.string.choose_wall_photo)) }
            }
            if (state.busy) CircularProgressIndicator()
        }
        state.calibrating?.let { edit ->
            CalibrationBar(
                edit,
                onWidth = vm::setWidthText,
                onHeight = vm::setHeightText,
                onCancel = vm::cancelCalibration,
                onDone = vm::applyCalibration,
            )
        }
    }
}

/** Close on the left; the calibration and the ⋮ menu on the right. */
@Composable
private fun TopBar(
    state: EditorState,
    onExit: () -> Unit,
    onChoosePhoto: () -> Unit,
    onCalibrate: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LargeIconButton(Icons.Filled.Close, stringResource(R.string.close), onExit)
        Spacer(Modifier.weight(1f))
        LargeIconButton(
            Icons.Filled.Straighten,
            stringResource(R.string.calibrate),
            onCalibrate,
            enabled = state.image != null && state.calibrating == null && !state.busy,
        )
        Box {
            MoreButton(onClick = { menu = true })
            AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AppMenuItem(stringResource(R.string.wall_photo), enabled = !state.busy) {
                    menu = false
                    onChoosePhoto()
                }
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

/** What to do, the rectangle's size in centimetres, and the way out either way. */
@Composable
private fun CalibrationBar(
    edit: CalibrationEdit,
    onWidth: (String) -> Unit,
    onHeight: (String) -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val cm = stringResource(R.string.cm)
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.calibrate_hint), style = MaterialTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = edit.widthText,
                onValueChange = onWidth,
                label = { Text("${stringResource(R.string.width)}, $cm") },
                singleLine = true,
                keyboardOptions = numbers,
                modifier = Modifier.weight(1f),
            )
            Text("×", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = edit.heightText,
                onValueChange = onHeight,
                label = { Text("${stringResource(R.string.height)}, $cm") },
                singleLine = true,
                keyboardOptions = numbers,
                modifier = Modifier.weight(1f),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            DialogDismissButton(stringResource(R.string.cancel), onCancel)
            Button(onClick = onDone, enabled = edit.result != null) { Text(stringResource(R.string.done)) }
        }
    }
}
