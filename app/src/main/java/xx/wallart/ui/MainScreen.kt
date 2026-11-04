package xx.wallart.ui

import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
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
import xx.wallart.PhotoSlot
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

/** What the photo being picked is for. */
private enum class Pick { WALL, NEW_PICTURE, ART, FRAME }

/** The dialog open over the editor, if any. */
private enum class Dialog { NONE, SIZE, FRAME_COLOR }

/** The selected picture's tools, in the pencil's menu. */
private enum class PictureTool(@param:StringRes val text: Int) {
    ART(R.string.artwork_photo),
    SIZE(R.string.size),
    FRAME_COLOR(R.string.frame_color),
    FRAME_PHOTO(R.string.frame_photo),
    FORWARD(R.string.bring_forward),
    BACKWARD(R.string.send_backward),
    REMOVE(R.string.remove_picture),
}

/** The wall and its pictures under the top bar; the calibration's size and buttons under the wall while one is made. */
@Composable
private fun EditorScreen(vm: WallViewModel, onExit: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    val state by vm.state.collectAsState()
    // Saveable: the system's picker can outlive the activity.
    var pick by rememberSaveable { mutableStateOf(Pick.WALL) }
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            when (pick) {
                Pick.WALL -> vm.importPhoto(uri)
                Pick.NEW_PICTURE -> vm.addPicture(uri)
                Pick.ART -> vm.setPicturePhoto(PhotoSlot.ART, uri)
                Pick.FRAME -> vm.setPicturePhoto(PhotoSlot.FRAME, uri)
            }
        }
    }
    fun choosePhoto(purpose: Pick) {
        pick = purpose
        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
    BackHandler(enabled = state.calibrating != null, onBack = vm::cancelCalibration)
    BackHandler(enabled = state.calibrating == null && state.selected != null) { vm.select(null) }

    // The keyboard too: the size fields sit at the bottom, where it comes up.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
        TopBar(
            state,
            onExit = onExit,
            onUndo = vm::undo,
            onRedo = vm::redo,
            onChooseWall = { choosePhoto(Pick.WALL) },
            onCalibrate = vm::startCalibration,
            onAddPicture = { if (!vm.calibrateFirst()) choosePhoto(Pick.NEW_PICTURE) },
            onPictureTool = { tool ->
                when (tool) {
                    PictureTool.ART -> choosePhoto(Pick.ART)
                    PictureTool.SIZE -> dialog = Dialog.SIZE
                    PictureTool.FRAME_COLOR -> dialog = Dialog.FRAME_COLOR
                    PictureTool.FRAME_PHOTO -> choosePhoto(Pick.FRAME)
                    PictureTool.FORWARD -> vm.restackSelected(1)
                    PictureTool.BACKWARD -> vm.restackSelected(-1)
                    PictureTool.REMOVE -> vm.removeSelected()
                }
            },
            onShadow = vm::toggleShadow,
            onSettings = onSettings,
            onAbout = onAbout,
        )
        Box(Modifier.weight(1f).fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
            val image = state.image
            if (image != null) {
                WallCanvas(
                    state,
                    image,
                    onMoveCorner = vm::moveCorner,
                    onSelect = vm::select,
                    onMovePicture = vm::movePicture,
                    onDragStart = vm::beginDrag,
                    onDragEnd = vm::endDrag,
                    modifier = Modifier.fillMaxSize(),
                )
                // A photo the wall has but does not show yet — after a step back — is on its way, not to be chosen.
            } else if (!state.busy && state.wall.photo == null) {
                Button(onClick = { choosePhoto(Pick.WALL) }) { Text(stringResource(R.string.choose_wall_photo)) }
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

    val picture = state.selectedPicture
    when {
        picture == null || dialog == Dialog.NONE -> Unit
        dialog == Dialog.SIZE -> PictureSizeDialog(picture, onDismiss = { dialog = Dialog.NONE }) { w, h, m ->
            vm.setSize(w, h, m)
            dialog = Dialog.NONE
        }
        dialog == Dialog.FRAME_COLOR -> FrameColorDialog(
            current = picture.frameColor.takeIf { picture.framePhoto == null },
            onDismiss = { dialog = Dialog.NONE },
        ) {
            vm.setFrameColor(it)
            dialog = Dialog.NONE
        }
    }
}

/** Close on the left; the calibration, a new picture, the selected one's tools and the ⋮ menu on the right. */
@Composable
private fun TopBar(
    state: EditorState,
    onExit: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onChooseWall: () -> Unit,
    onCalibrate: () -> Unit,
    onAddPicture: () -> Unit,
    onPictureTool: (PictureTool) -> Unit,
    onShadow: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var tools by remember { mutableStateOf(false) }
    val editing = state.image != null && state.calibrating == null && !state.busy
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LargeIconButton(Icons.Filled.Close, stringResource(R.string.close), onExit)
        Spacer(Modifier.weight(1f))
        val stepping = state.calibrating == null && !state.busy
        LargeIconButton(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), onUndo, enabled = stepping && state.canUndo)
        LargeIconButton(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), onRedo, enabled = stepping && state.canRedo)
        LargeIconButton(Icons.Filled.Straighten, stringResource(R.string.calibrate), onCalibrate, enabled = editing)
        LargeIconButton(Icons.Filled.AddPhotoAlternate, stringResource(R.string.add_picture), onAddPicture, enabled = editing)
        val selected = state.selectedPicture
        if (selected != null && editing) {
            Box {
                LargeIconButton(Icons.Filled.Edit, stringResource(R.string.picture), { tools = true })
                val index = state.wall.pictures.indexOf(selected)
                AppMenu(expanded = tools, onDismissRequest = { tools = false }) {
                    PictureTool.entries.forEach { tool ->
                        val enabled = when (tool) {
                            PictureTool.FORWARD -> index < state.wall.pictures.lastIndex
                            PictureTool.BACKWARD -> index > 0
                            else -> true
                        }
                        AppMenuItem(stringResource(tool.text), enabled = enabled) {
                            tools = false
                            onPictureTool(tool)
                        }
                    }
                }
            }
        }
        Box {
            MoreButton(onClick = { menu = true })
            AppMenu(expanded = menu, onDismissRequest = { menu = false }) {
                AppMenuItem(stringResource(R.string.wall_photo), enabled = !state.busy) {
                    menu = false
                    onChooseWall()
                }
                AppMenuItem(stringResource(R.string.shadow), checked = state.wall.shadow) {
                    menu = false
                    onShadow()
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
