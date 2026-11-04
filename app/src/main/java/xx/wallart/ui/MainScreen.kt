package xx.wallart.ui

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.FilledIconButton
import androidx.compose.material.icons.filled.Check
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
import xx.wallart.DEFAULT_ART_SPEC
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
        vm.notices.collect { notice ->
            val text = context.getString(notice.text)
            val line = if (notice.tail.isEmpty()) text else "$text ${notice.tail}"
            Toast.makeText(context, line, Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(vm) {
        vm.shares.collect { uri ->
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            // A phone with nothing to share to says so rather than closing the app.
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                .onFailure { Toast.makeText(context, R.string.image_not_saved, Toast.LENGTH_LONG).show() }
        }
    }
    when (screen) {
        Screen.EDITOR -> EditorScreen(vm, onExit = onExit, onSettings = { screen = Screen.SETTINGS }, onAbout = onAbout)
        Screen.SETTINGS -> AppScreen(title = stringResource(R.string.settings), onBack = { screen = Screen.EDITOR }) {
            SettingsScreen()
        }
    }
}

/** How much of the window shows through the calibration's hint over the photo. */
private const val HINT_ALPHA = 0.9f

/** The height of the row under the wall: Save draft, or the calibration's size. */
private val BOTTOM_PANEL_HEIGHT = 64.dp

/** What the photo being picked is for. */
private enum class Pick { WALL, NEW_PICTURE, ART, FRAME }

/** The dialog open over the editor, if any. */
private enum class Dialog { NONE, NEW_PICTURE, SIZE, FRAME_COLOR, NEW_WALL, OPEN_DRAFT, FORMAT }

/** The selected picture's tools, in the pencil's menu. */
private enum class PictureTool(@param:StringRes val text: Int) {
    ART(R.string.artwork_photo),
    SIZE(R.string.size),
    RESIZE(R.string.resize),
    FRAME_COLOR(R.string.frame_color),
    FRAME_PHOTO(R.string.frame_photo),
    FORWARD(R.string.bring_forward),
    BACKWARD(R.string.send_backward),
    DUPLICATE(R.string.duplicate),
    REMOVE(R.string.remove_picture),
}

/** The wall and its pictures under the top bar; the calibration's size and buttons under the wall while one is made. */
@Composable
private fun EditorScreen(vm: WallViewModel, onExit: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    val state by vm.state.collectAsState()
    val rotation = rememberDeviceRotation()
    // Saveable: the system's picker can outlive the activity.
    var pick by rememberSaveable { mutableStateOf(Pick.WALL) }
    var dialog by rememberSaveable { mutableStateOf(Dialog.NONE) }
    // The size the next new picture is offered at: the one the last was given, kept while its photo is picked.
    var newSpec by rememberSaveable(stateSaver = ArtSpecSaver) { mutableStateOf(DEFAULT_ART_SPEC) }
    // The selected picture's handles out, to size it by: till Back, or the selection goes.
    var sizing by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.selected) { sizing = false }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            when (pick) {
                Pick.WALL -> vm.importPhoto(uri)
                Pick.NEW_PICTURE -> vm.addPicture(newSpec, uri)
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
    BackHandler(enabled = state.calibrating == null && state.selected != null) {
        if (sizing) sizing = false else vm.select(null)
    }

    // The keyboard too: the size fields sit at the bottom, where it comes up.
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime))) {
        TopBar(
            state,
            onExit = onExit,
            // Asked first only when there is work to lose.
            onNewWall = { if (state.hasWork) dialog = Dialog.NEW_WALL else vm.newWall() },
            onSaveDraft = vm::saveDraft,
            onSaveImage = vm::saveImage,
            onFormat = { dialog = Dialog.FORMAT },
            onShare = vm::shareImage,
            onOpenDraft = { if (state.hasWork) dialog = Dialog.OPEN_DRAFT else vm.openDraft() },
            onUndo = vm::undo,
            onRedo = vm::redo,
            onChooseWall = { choosePhoto(Pick.WALL) },
            onCalibrate = vm::startCalibration,
            onAddPicture = { if (!vm.calibrateFirst()) dialog = Dialog.NEW_PICTURE },
            onPictureTool = { tool ->
                when (tool) {
                    PictureTool.ART -> choosePhoto(Pick.ART)
                    PictureTool.SIZE -> dialog = Dialog.SIZE
                    PictureTool.RESIZE -> sizing = true
                    PictureTool.FRAME_COLOR -> dialog = Dialog.FRAME_COLOR
                    PictureTool.FRAME_PHOTO -> choosePhoto(Pick.FRAME)
                    PictureTool.FORWARD -> vm.restackSelected(1)
                    PictureTool.BACKWARD -> vm.restackSelected(-1)
                    PictureTool.DUPLICATE -> vm.duplicateSelected()
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
                // Only the wall turns with the phone; the bars, the hint and the dialogs stay upright.
                RotatedFrame(rotation, Modifier.fillMaxSize()) {
                    WallCanvas(
                        state,
                        image,
                        onMoveCorner = vm::moveCorner,
                        onSelect = vm::select,
                        onMovePicture = vm::movePicture,
                        onDragStart = vm::beginDrag,
                        onDragEnd = vm::endDrag,
                        onMoveCrop = vm::moveCrop,
                        onResizePicture = vm::resizePicture,
                        sizing = sizing && state.selected != null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // A photo the wall has but does not show yet — after a step back — is on its way, not to be chosen.
            } else if (!state.busy && state.wall.photo == null) {
                Button(onClick = { choosePhoto(Pick.WALL) }) { Text(stringResource(R.string.choose_wall_photo)) }
            }
            if (state.busy) CircularProgressIndicator()
            // What to do, over the photo rather than under it: the room under it stays one height.
            if (state.calibrating != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = HINT_ALPHA),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                ) {
                    Text(
                        stringResource(R.string.calibrate_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
        // The room under the wall is kept the one height whatever is in it, once there is a wall: a panel
        // that came and went, or grew, would shrink and grow the photo under the finger.
        if (state.image != null || state.hasWork) {
            Box(Modifier.fillMaxWidth().height(BOTTOM_PANEL_HEIGHT), contentAlignment = Alignment.Center) {
                val edit = state.calibrating
                if (edit != null) {
                    CalibrationBar(
                        edit,
                        onWidth = vm::setWidthText,
                        onHeight = vm::setHeightText,
                        onCancel = vm::cancelCalibration,
                        onDone = vm::applyCalibration,
                    )
                } else {
                    // Nothing else wants the room: the work is put aside as the draft from here.
                    // Off while the draft holds this very wall: nothing to save.
                    Button(onClick = vm::saveDraft, enabled = !state.busy && state.hasWork && state.wall != state.draft) {
                        Text(stringResource(R.string.save_draft))
                    }
                }
            }
        }
    }

    val picture = state.selectedPicture
    when {
        dialog == Dialog.FORMAT -> {
            val size = remember { vm.photoSize() }
            // A photo gone from under it: nothing to set the format against.
            LaunchedEffect(size) { if (size == null) dialog = Dialog.NONE }
            if (size != null) {
                FormatDialog(state.wall.format, size.first, size.second, onDismiss = { dialog = Dialog.NONE }) {
                    dialog = Dialog.NONE
                    vm.setFormat(it)
                }
            }
        }
        dialog == Dialog.NEW_PICTURE -> PictureSizeDialog(
            stringResource(R.string.new_picture),
            newSpec,
            onDismiss = { dialog = Dialog.NONE },
        ) { spec ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // An empty mount, to plan a gallery with before the artworks are to hand.
                DialogButton(stringResource(R.string.without_photo), enabled = spec != null) {
                    spec?.let {
                        newSpec = it
                        vm.addEmptyPicture(it)
                    }
                    dialog = Dialog.NONE
                }
                DialogButton(stringResource(R.string.choose_photo), enabled = spec != null) {
                    spec?.let {
                        newSpec = it
                        choosePhoto(Pick.NEW_PICTURE)
                    }
                    dialog = Dialog.NONE
                }
            }
        }
        dialog == Dialog.NEW_WALL -> ConfirmDialog(
            title = "${stringResource(R.string.new_wall_question)}?",
            text = stringResource(R.string.work_lost_warning),
            confirm = stringResource(R.string.start),
            onDismiss = { dialog = Dialog.NONE },
        ) {
            dialog = Dialog.NONE
            vm.newWall()
        }
        dialog == Dialog.OPEN_DRAFT -> ConfirmDialog(
            title = "${stringResource(R.string.open_draft)}?",
            text = stringResource(R.string.work_lost_warning),
            confirm = stringResource(R.string.open),
            onDismiss = { dialog = Dialog.NONE },
        ) {
            dialog = Dialog.NONE
            vm.openDraft()
        }
        picture == null || dialog == Dialog.NONE -> Unit
        dialog == Dialog.SIZE -> PictureSizeDialog(
            stringResource(R.string.size),
            picture.spec,
            onDismiss = { dialog = Dialog.NONE },
        ) { spec ->
            DialogButton(stringResource(R.string.done), enabled = spec != null) {
                spec?.let(vm::setArtSize)
                dialog = Dialog.NONE
            }
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
    onNewWall: () -> Unit,
    onSaveDraft: () -> Unit,
    onSaveImage: () -> Unit,
    onFormat: () -> Unit,
    onShare: () -> Unit,
    onOpenDraft: () -> Unit,
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
                val draft = state.draft
                val exportable = !state.busy && state.calibrating == null && state.image != null
                AppMenuItem(stringResource(R.string.save_image), enabled = exportable) {
                    menu = false
                    onSaveImage()
                }
                AppMenuItem(stringResource(R.string.share), enabled = exportable) {
                    menu = false
                    onShare()
                }
                AppMenuItem(stringResource(R.string.format), enabled = exportable) {
                    menu = false
                    onFormat()
                }
                AppMenuItem(stringResource(R.string.new_wall), enabled = !state.busy && state.hasWork) {
                    menu = false
                    onNewWall()
                }
                // Each only while it would change something: the draft as it is already, or the work already the draft.
                AppMenuItem(stringResource(R.string.save_draft), enabled = !state.busy && state.hasWork && state.wall != draft) {
                    menu = false
                    onSaveDraft()
                }
                AppMenuItem(stringResource(R.string.open_draft), enabled = !state.busy && draft != null && state.wall != draft) {
                    menu = false
                    onOpenDraft()
                }
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

/** The rectangle's size in centimetres and the way out either way, in one row: the panel under the wall's height. */
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
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = edit.widthText,
            onValueChange = onWidth,
            label = { Text("${stringResource(R.string.width)}, $cm", maxLines = 1) },
            singleLine = true,
            keyboardOptions = numbers,
            modifier = Modifier.weight(1f),
        )
        Text("×", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = edit.heightText,
            onValueChange = onHeight,
            label = { Text("${stringResource(R.string.height)}, $cm", maxLines = 1) },
            singleLine = true,
            keyboardOptions = numbers,
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
        FilledIconButton(onClick = onDone, enabled = edit.result != null) { Icon(Icons.Filled.Check, stringResource(R.string.done)) }
    }
}
