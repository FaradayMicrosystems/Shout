package app.shout

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Pink = Color(0xFFFF4D8D)
private val Bg = Color(0xFF121218)
private val Tile = Color(0xFF23232E)
private val TileOn = Color(0xFF6A2BFF)

class MainActivity : ComponentActivity() {
    lateinit var store: SoundStore
    lateinit var player: Player
    lateinit var recorder: Recorder
    lateinit var megaphone: Megaphone
    var pendingUri by mutableStateOf<Uri?>(null)          // file picked, waiting for a name
    var finishedRec by mutableStateOf<java.io.File?>(null) // recording done, waiting for a name
    var editMode by mutableStateOf(false)
    var recording by mutableStateOf(false)
    var megaOn by mutableStateOf(false)   // Megaphone toggle latched on
    var talking by mutableStateOf(false)  // Hold-to-talk pressed
    var picker: androidx.activity.result.ActivityResultLauncher<Array<String>>? = null
    var tileSize by mutableFloatStateOf(150f) // soundboard tile width in dp (the "scale")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC // hardware volume keys = speaker volume
        store = SoundStore(applicationContext)
        player = Player(applicationContext)
        recorder = Recorder(applicationContext)
        megaphone = Megaphone(applicationContext)
        tileSize = getSharedPreferences("ui", MODE_PRIVATE).getFloat("tile", 150f)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Pink, background = Bg, surface = Bg)) {
                App(this)
            }
        }
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun saveTileSize() = getSharedPreferences("ui", MODE_PRIVATE).edit().putFloat("tile", tileSize).apply()

    override fun onDestroy() {
        player.stopAll(); megaphone.stop(); recorder.cancel()
        super.onDestroy()
    }
}

@Composable
fun App(act: MainActivity) {
    var showSettings by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Bg,
        topBar = { SpeakerBar(onSettings = { showSettings = true }) },
        bottomBar = { BottomControls(act) }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) { SoundboardGrid(act) }
    }
    if (showSettings) MegaphoneSettings(act) { showSettings = false }
}

/** Shows whether a Bluetooth speaker is connected, so you know where sound is going. */
@Composable
fun SpeakerBar(onSettings: () -> Unit) {
    val ctx = LocalContext.current
    val am = remember { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    fun btName(): String? = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP }?.productName?.toString()
    var bt by remember { mutableStateOf(btName()) }
    DisposableEffect(Unit) {
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(d: Array<out AudioDeviceInfo>?) { bt = btName() }
            override fun onAudioDevicesRemoved(d: Array<out AudioDeviceInfo>?) { bt = btName() }
        }
        am.registerAudioDeviceCallback(cb, null)
        onDispose { am.unregisterAudioDeviceCallback(cb) }
    }
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("Shout", fontSize = 22.sp, fontWeight = FontWeight.Black, color = Pink)
        Spacer(Modifier.weight(1f))
        Icon(if (bt != null) Icons.Default.BluetoothConnected else Icons.Default.PhoneAndroid, null,
            tint = if (bt != null) Color(0xFF4DD0FF) else Color.Gray, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(bt ?: "Phone speaker (no Bluetooth)", color = if (bt != null) Color.White else Color.Gray,
            fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        IconButton(onClick = onSettings) { Icon(Icons.Default.Tune, "Megaphone settings", tint = Color.White) }
    }
}

// ---------------------------------------------------------------- Soundboard

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoundboardGrid(act: MainActivity) {
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf<Sound?>(null) }

    Column(Modifier.fillMaxSize()) {
        if (act.editMode) {
            // Scale control: smaller tiles = more sounds per screen.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ZoomOut, null, tint = Color.Gray)
                Slider(value = act.tileSize, onValueChange = { act.tileSize = it }, onValueChangeFinished = { act.saveTileSize() },
                    valueRange = 90f..260f, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                Icon(Icons.Default.ZoomIn, null, tint = Color.Gray)
            }
            Text("Tap a sound to rename it. Tap the red X to remove it.", color = Color.Gray, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp))
        }
        val tileH = (act.tileSize * 0.7f).dp
        val fontSize = (act.tileSize / 8.5f).coerceIn(12f, 26f).sp
        Box(Modifier.weight(1f)) {
            if (!act.editMode && act.store.sounds.isEmpty()) {
                Text("No sounds yet.\nTap  Edit sounds  below to add some.", color = Color.Gray,
                    textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
            }
            // Scrolls like an app drawer when there are more sounds than fit on screen.
            LazyVerticalGrid(
                columns = GridCells.Adaptive(act.tileSize.dp),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (act.editMode) {
                    item(key = "add") {
                        EditTile("Add from files", Icons.Default.LibraryAdd, Tile, tileH) {
                            act.picker?.launch(arrayOf("audio/*"))
                        }
                    }
                    item(key = "rec") { RecordTile(act, tileH) }
                }
                items(act.store.sounds, key = { it.id }) { s ->
                    val on = act.player.playing[s.id] == true
                    Box {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (on) TileOn else Tile,
                            modifier = Modifier.fillMaxWidth().height(tileH).combinedClickable(
                                onClick = {
                                    if (act.editMode) editing = s
                                    else act.player.toggle(s) { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }
                                },
                                onLongClick = { editing = s }
                            )
                        ) {
                            Box(Modifier.padding(10.dp), contentAlignment = Alignment.Center) {
                                Text(s.name, fontSize = fontSize, fontWeight = FontWeight.Bold, color = Color.White,
                                    textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        if (act.editMode) {
                            SmallFloatingActionButton(onClick = { act.player.stop(s.id); act.store.delete(s) },
                                containerColor = Color(0xFFE53935), shape = RoundedCornerShape(50),
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp)) {
                                Icon(Icons.Default.Close, "Remove", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            if (act.player.playing.isNotEmpty() && !act.editMode) {
                ExtendedFloatingActionButton(onClick = { act.player.stopAll() }, containerColor = Color.DarkGray,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    icon = { Icon(Icons.Default.Stop, null, tint = Color.White) },
                    text = { Text("Stop all", color = Color.White) })
            }
        }
    }

    act.pendingUri?.let { uri ->
        NameDialog(title = "Name this sound", initial = fileName(ctx, uri),
            onDismiss = { act.pendingUri = null },
            onConfirm = { act.store.add(it, uri); act.pendingUri = null })
    }
    act.finishedRec?.let { f ->
        NameDialog(title = "Save recording", initial = "Recording ${act.store.sounds.size + 1}",
            onDismiss = { f.delete(); act.finishedRec = null },
            onConfirm = { act.store.add(it, Uri.fromFile(f)); act.finishedRec = null })
    }
    editing?.let { s ->
        NameDialog(title = "Edit sound", initial = s.name,
            onDismiss = { editing = null },
            onConfirm = { act.store.rename(s, it); editing = null },
            onDelete = { act.player.stop(s.id); act.store.delete(s); editing = null })
    }
}

private fun fileName(ctx: Context, uri: Uri): String {
    var name = "Sound"
    runCatching {
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c: Cursor ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
    }
    return name.substringBeforeLast('.')
}

@Composable
fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, onDelete: (() -> Unit)? = null) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true, label = { Text("Name") }) },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim().ifEmpty { "Sound" }) }) { Text("Save") } },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = Color(0xFFFF6B6B)) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// ---------------------------------------------------------------- Edit-mode tiles

@Composable
fun EditTile(label: String, icon: ImageVector, color: Color, height: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = color,
        border = androidx.compose.foundation.BorderStroke(2.dp, Pink),
        modifier = Modifier.fillMaxWidth().height(height)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = Color.White)
            Text(label, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun rememberMic(): Pair<() -> Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    return { granted } to { launcher.launch(Manifest.permission.RECORD_AUDIO) }
}

/** Tap to start recording, tap again to stop, then name it and it joins the soundboard. */
@Composable
fun RecordTile(act: MainActivity, height: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val (hasMic, askMic) = rememberMic()
    var secs by remember { mutableIntStateOf(0) }
    LaunchedEffect(act.recording) { secs = 0; while (act.recording) { delay(1000); secs++ } }
    EditTile(if (act.recording) "Stop  %d:%02d".format(secs / 60, secs % 60) else "Record new",
        if (act.recording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
        if (act.recording) Color(0xFFE53935) else Tile, height) {
        if (!hasMic()) { askMic(); return@EditTile }
        if (!act.recording) {
            try {
                // The phone can't record and run the Megaphone at the same time.
                act.megaOn = false; act.talking = false; act.megaphone.stop()
                act.player.stopAll()
                act.recorder.start(act.store.newRecordingFile())
                act.recording = true; act.keepScreenOn(true)
            } catch (e: Exception) { Toast.makeText(ctx, "Couldn't start recording", Toast.LENGTH_LONG).show() }
        } else {
            act.recording = false; act.keepScreenOn(false)
            act.finishedRec = act.recorder.stop()
            if (act.finishedRec == null) Toast.makeText(ctx, "Too short, try again", Toast.LENGTH_SHORT).show()
        }
    }
}

// ---------------------------------------------------------------- Permanent bottom bar

/** Always visible:  [ Edit sounds ]  [ Megaphone (toggle) ]  [ Hold to talk ] */
@Composable
fun BottomControls(act: MainActivity) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val (hasMic, askMic) = rememberMic()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // Keep access to the file after the app restarts.
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            act.pendingUri = uri
        }
    }
    SideEffect { act.picker = picker }
    var stopJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(act.megaOn, act.talking) { if (!act.recording) act.keepScreenOn(act.megaOn || act.talking) }

    val micError: (String) -> Unit = { msg ->
        act.runOnUiThread { act.megaOn = false; act.talking = false; Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }
    }
    fun applyMic() {
        if (act.megaOn || act.talking) {
            stopJob?.cancel(); stopJob = null
            act.megaphone.muted = false
            act.megaphone.start(micError)
        } else if (act.megaphone.isRunning) {
            // Mute instantly, but keep the mic warm for a few seconds so the next
            // push-to-talk doesn't clip your first word while Bluetooth wakes up.
            act.megaphone.muted = true
            stopJob?.cancel()
            stopJob = scope.launch { delay(4000); act.megaphone.stop() }
        }
    }
    val micBlocked = act.recording

    Surface(color = Color(0xFF1A1A22), tonalElevation = 4.dp) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarButton(if (act.editMode) "Done" else "Edit sounds",
                if (act.editMode) Icons.Default.Check else Icons.Default.Edit,
                if (act.editMode) Color(0xFF2E7D32) else Tile, Modifier.weight(1f), enabled = !act.recording) {
                act.editMode = !act.editMode
            }

            BarButton(if (act.megaOn) "Megaphone ON" else "Megaphone",
                if (act.megaOn) Icons.Default.Campaign else Icons.Default.VolumeOff,
                if (act.megaOn) Pink else Tile, Modifier.weight(1.2f), enabled = !micBlocked) {
                if (!hasMic()) { askMic(); return@BarButton }
                act.megaOn = !act.megaOn; applyMic()
            }

            // Push to talk: live only while your finger is on it.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (act.talking) Pink else Tile,
                modifier = Modifier.weight(1.2f).height(64.dp).pointerInput(micBlocked) {
                    detectTapGestures(onPress = {
                        if (micBlocked) return@detectTapGestures
                        if (!hasMic()) { askMic(); return@detectTapGestures }
                        act.talking = true; applyMic()
                        tryAwaitRelease()
                        act.talking = false; applyMic()
                    })
                }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Mic, null, tint = if (micBlocked) Color.Gray else Color.White)
                    Text(if (act.talking) "TALKING" else "Hold to talk", fontSize = 12.sp, maxLines = 1,
                        color = if (micBlocked) Color.Gray else Color.White)
                }
            }
        }
    }
}

@Composable
fun BarButton(label: String, icon: ImageVector, color: Color, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp), color = color,
        modifier = modifier.height(64.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = if (enabled) Color.White else Color.Gray)
            Text(label, fontSize = 12.sp, maxLines = 1, color = if (enabled) Color.White else Color.Gray)
        }
    }
}

// ---------------------------------------------------------------- Megaphone settings

@Composable
fun MegaphoneSettings(act: MainActivity, onClose: () -> Unit) {
    var volume by remember { mutableFloatStateOf(act.megaphone.volume) }
    var echo by remember { mutableStateOf(act.megaphone.echo) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Megaphone") },
        text = {
            Column {
                Text("Mic volume: ${(volume * 100).toInt()}%")
                Slider(value = volume, onValueChange = { volume = it; act.megaphone.volume = it }, valueRange = 0f..3f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Echo", modifier = Modifier.weight(1f))
                    Switch(checked = echo, onCheckedChange = { echo = it; act.megaphone.echo = it })
                }
                Spacer(Modifier.height(12.dp))
                Text("Bluetooth speakers add their own delay (about 0.15 to 0.3 s), so you'll hear yourself slightly late. " +
                    "Keep the speaker away from the phone to avoid squealing feedback. " +
                    "Use the phone's volume keys for overall speaker loudness.",
                    color = Color.Gray, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } }
    )
}
