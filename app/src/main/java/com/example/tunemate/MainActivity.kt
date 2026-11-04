package com.example.tunemate

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.tunemate.data.backgroundFlow
import com.example.tunemate.data.playlistsFlow
import com.example.tunemate.data.queryAudioFiles
import com.example.tunemate.data.saveBackground
import com.example.tunemate.data.savePlaylists
import com.example.tunemate.equalizer.EqualizerScreen
import com.example.tunemate.model.Playlist
import com.example.tunemate.model.Song
import com.example.tunemate.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import com.example.tunemate.data.saveBackgroundImage
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableLongStateOf
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var songs by mutableStateOf<List<Song>>(emptyList())

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshSongs() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 忽略 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )


        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                AppRoot(
                    songs = songs,
                    onRequestSongs = { requestAudioPermission() }
                )
            }
        }

        requestNotifPermissionIfNeeded()
        maybeRefresh()
    }

    private fun requestAudioPermission() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            refreshSongs()
        } else {
            audioPermLauncher.launch(perm)
        }
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun maybeRefresh() {
        val perm = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        if (checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED) {
            refreshSongs()
        }
    }

    private fun refreshSongs() {
        songs = queryAudioFiles(contentResolver)
    }
}

/* ---------------- MediaController 连接 ---------------- */

@Composable
private fun rememberMediaController(context: Context): MediaController? {
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(Unit) {
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            controller = try {
                future.get()
            } catch (e: Exception) {
                null
            }
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(future)
            controller = null
        }
    }
    return controller
}

/* ---------------- 根 UI ---------------- */

@Composable
fun AppRoot(songs: List<Song>, onRequestSongs: () -> Unit) {
    val context = LocalContext.current
    val controller = rememberMediaController(context)

    if (controller == null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0B0B1A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
        }
        return
    }

    // 主界面与设置页的切换
    var showSettings by remember { mutableStateOf(false) }

    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }

    // 背景设置提升到 AppRoot 层，两个页面共享
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        context.playlistsFlow().collect { playlists = it }
    }
    LaunchedEffect(Unit) {
        val prefs = context.backgroundFlow().first()
        backgroundUri = prefs.uri?.let { Uri.parse(it) }
        blurRadius = prefs.blur
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onRepeatModeChanged(mode: Int) { repeatMode = mode }
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val uri = item?.localConfiguration?.uri?.toString() ?: return
                currentSong = songs.firstOrNull { it.uriString == uri }
            }
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        repeatMode = controller.repeatMode
        val cur = controller.currentMediaItem?.localConfiguration?.uri?.toString()
        currentSong = cur?.let { u -> songs.firstOrNull { it.uriString == u } }
        onDispose { controller.removeListener(listener) }
    }

    Box(Modifier.fillMaxSize().background(Color(0xFF0B0B1A))) {
        if (showSettings) {
            SettingsScreen(
                backgroundUri = backgroundUri,
                blurRadius = blurRadius,
                onBackgroundChanged = { uri, blur ->
                    backgroundUri = uri
                    blurRadius = blur
                    scope.launch { context.saveBackground(uri?.toString(), blur) }
                },
                onBack = { showSettings = false }
            )
        } else {
            PlayerScreen(
                player = controller,
                songs = songs,
                playlists = playlists,
                currentSong = currentSong,
                isPlaying = isPlaying,
                repeatMode = repeatMode,
                backgroundUri = backgroundUri,
                blurRadius = blurRadius,
                onRequestSongs = onRequestSongs,
                onCreatePlaylist = { showCreateDialog = true },
                onOpenSettings = { showSettings = true }
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            allSongs = songs,
            onDismiss = { showCreateDialog = false },
            onConfirm = { pl ->
                scope.launch { context.savePlaylists(playlists + pl) }
                showCreateDialog = false
            }
        )
    }
}

/* ---------------- 播放器页面 ---------------- */

@Composable
fun PlayerScreen(
    player: Player,
    songs: List<Song>,
    playlists: List<Playlist>,
    currentSong: Song?,
    isPlaying: Boolean,
    repeatMode: Int,
    backgroundUri: Uri?,
    blurRadius: Float,
    onRequestSongs: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var activeList by remember { mutableStateOf<List<Song>>(emptyList()) }
    // 时间轴状态
var positionMs by remember { mutableLongStateOf(0L) }
var durationMs by remember { mutableLongStateOf(0L) }
var isDragging by remember { mutableStateOf(false) }
var dragPosition by remember { mutableFloatStateOf(0f) }

// 每 500ms 更新一次播放进度（拖拽时不覆盖）
LaunchedEffect(player) {
    while (true) {
        if (!isDragging) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            val d = player.duration
            durationMs = if (d > 0L) d else 0L
        }
        delay(500)
    }
}

// 切歌时重置拖拽状态
LaunchedEffect(currentSong) {
    isDragging = false
    positionMs = 0L
    durationMs = 0L
}

    Box(Modifier.fillMaxSize()) {
        // ---- 背景层 ----
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(blurRadius.dp)
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0F3460)))
                )
            )
        }

        // ---- 内容层 ----
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            // 顶栏：标题 + 设置按钮
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TuneMate",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "設置", tint = Color.White)
                }
            }

            Spacer(Modifier.height(8.dp))

            Row {
                Button(onClick = onRequestSongs) { Text("匯入音頻") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onCreatePlaylist) { Text("建立歌單") }
            }
            Spacer(Modifier.height(8.dp))

            if (playlists.isNotEmpty()) {
                Text("我的歌單", color = Color.LightGray)
                LazyColumn(Modifier.heightIn(max = 120.dp)) {
                    items(playlists) { pl ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (pl.songs.isNotEmpty()) {
                                        activeList = pl.songs
                                        playList(player, pl.songs, 0)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.QueueMusic,
                                contentDescription = null,
                                tint = Color(0xFF4FC3F7)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("${pl.name}（${pl.songs.size}）", color = Color.White)
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
            }

            Text("所有歌曲（${songs.size}）", color = Color.LightGray)
            LazyColumn(Modifier.weight(1f)) {
                items(songs) { song ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                val list = if (activeList.isEmpty()) songs else activeList
                                val idx = list.indexOf(song).coerceAtLeast(0)
                                playList(player, list, idx)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = Color(0xFF4FC3F7)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                color = Color.Gray,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

            currentSong?.let {
                Text(
                    "正在播放：${it.title}",
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---- 时间轴 ----
Spacer(Modifier.height(4.dp))
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        formatTime(if (isDragging) dragPosition.toLong() else positionMs),
        color = Color.LightGray,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(44.dp)
    )
    Slider(
        value = if (isDragging) dragPosition
                else positionMs.toFloat().coerceIn(0f, (if (durationMs > 0) durationMs.toFloat() else 1f)),
        onValueChange = {
            isDragging = true
            dragPosition = it
        },
        onValueChangeFinished = {
            player.seekTo(dragPosition.toLong())
            positionMs = dragPosition.toLong()
            isDragging = false
        },
        valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
        enabled = durationMs > 0L,
        modifier = Modifier.weight(1f).height(32.dp)
    )
    Text(
        formatTime(durationMs),
        color = Color.LightGray,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.width(44.dp)
    )
}

            // ---- 播放控制列 ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        "上一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放/暫停",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                IconButton(onClick = {
                    if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                }) {
                    Icon(
                        Icons.Default.SkipNext,
                        "下一首",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }

               IconButton(onClick = {
    player.repeatMode = when (player.repeatMode) {
        Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_OFF
        else -> Player.REPEAT_MODE_ONE
    }
}) {
    Icon(
        imageVector = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
            else -> Icons.Default.Repeat
        },
        contentDescription = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> "單曲循環"
            Player.REPEAT_MODE_ALL -> "歌單循環"
            else -> "停止循環"
        },
        tint = if (repeatMode == Player.REPEAT_MODE_OFF)
            Color.Gray
        else
            Color(0xFF4FC3F7),
        modifier = Modifier.size(32.dp)
    )
}
            }
        }
    }
}

/* ---------------- 设置页面 ---------------- */

/* ---------------- 设置页面 ---------------- */

private enum class SettingsSubPage { MAIN, BACKGROUND, EQUALIZER }

@Composable
fun SettingsScreen(
    backgroundUri: Uri?,
    blurRadius: Float,
    onBackgroundChanged: (Uri?, Float) -> Unit,
    onBack: () -> Unit
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.MAIN) }

    // 子页面返回 → 设置主页；设置主页返回 → 播放器
    BackHandler(enabled = true) {
        if (subPage == SettingsSubPage.MAIN) onBack()
        else subPage = SettingsSubPage.MAIN
    }

    when (subPage) {
        SettingsSubPage.MAIN -> SettingsMainPage(
            onOpenBackground = { subPage = SettingsSubPage.BACKGROUND },
            onOpenEqualizer = { subPage = SettingsSubPage.EQUALIZER },
            onBack = onBack
        )
        SettingsSubPage.BACKGROUND -> BackgroundSettingsPage(
            backgroundUri = backgroundUri,
            blurRadius = blurRadius,
            onBackgroundChanged = onBackgroundChanged,
            onBack = { subPage = SettingsSubPage.MAIN }
        )
        SettingsSubPage.EQUALIZER -> EqualizerSettingsPage(
            onBack = { subPage = SettingsSubPage.MAIN }
        )
    }
}

/* ---- 设置主页：两个入口 ---- */

@Composable
private fun SettingsMainPage(
    onOpenBackground: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
        }
        Text(
            "設置",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsEntry(
            icon = Icons.Default.Image,
            title = "背景設定",
            subtitle = "更換背景圖片、調整模糊度",
            onClick = onOpenBackground
        )
        SettingsEntry(
            icon = Icons.Default.Equalizer,
            title = "均衡器",
            subtitle = "調整低音、中音、高音",
            onClick = onOpenEqualizer
        )
    }
}

@Composable
private fun SettingsEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color(0xFF4FC3F7),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
}

/* ---- 子页面：背景设定 ---- */

@Composable
private fun BackgroundSettingsPage(
    backgroundUri: Uri?,
    blurRadius: Float,
    onBackgroundChanged: (Uri?, Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBlur by remember(blurRadius) { mutableFloatStateOf(blurRadius) }
    var saving by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            saving = true
            scope.launch {
                val saved = context.saveBackgroundImage(uri)
                if (saved != null) {
                    onBackgroundChanged(saved, pendingBlur)
                }
                saving = false
            }
        }
    }

    Column(
    Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
        }
        Text(
            "背景設定",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }

        Spacer(Modifier.height(24.dp))

                Row {
            Button(
                enabled = !saving,
                onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) { Text(if (saving) "正在儲存…" else "更換背景圖片") }
            if (backgroundUri != null) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { onBackgroundChanged(null, pendingBlur) }) {
                    Text("清除背景")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("背景模糊 ${pendingBlur.toInt()} dp", color = Color.White)
        Slider(
            value = pendingBlur,
            onValueChange = { pendingBlur = it },
            onValueChangeFinished = {
                onBackgroundChanged(backgroundUri, pendingBlur)
            },
            valueRange = 0f..25f
        )

        Spacer(Modifier.height(24.dp))

        Text("預覽", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(Color(0xFF1A1A2E)),
            contentAlignment = Alignment.Center
        ) {
            if (backgroundUri != null) {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(pendingBlur.dp)
                )
            } else {
                Text("使用預設漸層背景", color = Color.Gray)
            }
        }
    }
}

/* ---- 子页面：均衡器 ---- */

@Composable
private fun EqualizerSettingsPage(onBack: () -> Unit) {
    Column(
    Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .verticalScroll(rememberScrollState())
        .padding(16.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
        }
        Text(
            "均衡器",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        }
        Spacer(Modifier.height(16.dp))
        EqualizerScreen()
    }
}
/* ---------------- 创建歌单对话框 ---------------- */

@Composable
fun CreatePlaylistDialog(
    allSongs: List<Song>,
    onDismiss: () -> Unit,
    onConfirm: (Playlist) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val selected = remember { mutableStateListOf<Song>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("建立歌單") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("歌單名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (allSongs.isEmpty()) {
                    Text("尚無歌曲，請先匯入音頻。", color = Color.Gray)
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(allSongs) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selected.contains(song)) selected.remove(song)
                                        else selected.add(song)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = selected.contains(song),
                                    onCheckedChange = {
                                        if (it) selected.add(song) else selected.remove(song)
                                    }
                                )
                                Text(
                                    song.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && selected.isNotEmpty(),
                onClick = { onConfirm(Playlist(name.trim(), selected.toList())) }
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ---------------- 播放列表辅助 ---------------- */

private fun playList(player: Player, list: List<Song>, startIndex: Int) {
    if (list.isEmpty()) return
    val items = list.map { MediaItem.fromUri(it.uri) }
    player.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
    player.prepare()
    player.play()
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "%d:%02d".format(min, sec)
}
