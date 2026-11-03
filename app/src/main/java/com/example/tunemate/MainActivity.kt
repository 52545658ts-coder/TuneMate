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
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.tunemate.model.Playlist
import com.example.tunemate.model.Song
import com.example.tunemate.playback.PlaybackService
import com.example.tunemate.tuner.TunerScreen
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var songs by mutableStateOf<List<Song>>(emptyList())

    private val audioPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) refreshSongs() }

    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 忽略结果，通知被拒绝时服务仍可运行 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    var tab by remember { mutableIntStateOf(0) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var repeatMode by remember { mutableIntStateOf(Player.REPEAT_MODE_ALL) }
    var showCreateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        context.playlistsFlow().collect { playlists = it }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onRepeatModeChanged(mode: Int) {
                repeatMode = mode
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val uri = item?.localConfiguration?.uri?.toString() ?: return
                currentSong = songs.firstOrNull { it.uriString == uri }
            }
        }
        controller.addListener(listener)
        // 初次同步状态
        isPlaying = controller.isPlaying
        repeatMode = controller.repeatMode
        val cur = controller.currentMediaItem?.localConfiguration?.uri?.toString()
        currentSong = cur?.let { u -> songs.firstOrNull { it.uriString == u } }

        onDispose { controller.removeListener(listener) }
    }

    Scaffold(
        containerColor = Color(0xFF0B0B1A),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF1A1A2E)) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    label = { Text("播放器") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.GraphicEq, contentDescription = null) },
                    label = { Text("调音器") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (tab) {
                0 -> PlayerScreen(
                    player = controller,
                    songs = songs,
                    playlists = playlists,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    onRequestSongs = onRequestSongs,
                    onCreatePlaylist = { showCreateDialog = true }
                )
                1 -> TunerScreen()
            }
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
    onRequestSongs: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }
    var pendingBlur by remember { mutableFloatStateOf(12f) }
    var showBgSlider by remember { mutableStateOf(false) }
    var activeList by remember { mutableStateOf<List<Song>>(emptyList()) }

    // 从 DataStore 恢复背景设置
    LaunchedEffect(Unit) {
        val prefs = context.backgroundFlow().first()
        backgroundUri = prefs.uri?.let { Uri.parse(it) }
        blurRadius = prefs.blur
        pendingBlur = prefs.blur
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            backgroundUri = uri
            scope.launch {
                context.saveBackground(uri.toString(), blurRadius)
            }
        }
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
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f))
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF0F3460)))
                )
            )
        }

        // ---- 内容层 ----
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TuneMate",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }) {
                    Icon(Icons.Default.Image, "选择背景", tint = Color.White)
                }
                IconButton(onClick = { showBgSlider = !showBgSlider }) {
                    Icon(Icons.Default.Tune, "背景模糊", tint = Color.White)
                }
            }

            if (showBgSlider) {
                Text("背景模糊 ${pendingBlur.toInt()} dp", color = Color.White)
                Slider(
                    value = pendingBlur,
                    onValueChange = { pendingBlur = it },
                    onValueChangeFinished = {
                        blurRadius = pendingBlur
                        scope.launch {
                            context.saveBackground(backgroundUri?.toString(), pendingBlur)
                        }
                    },
                    valueRange = 0f..25f
                )
            }

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

            // ---- 播放控制列 ----
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 上一首
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

                // 暂停/继续
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        "播放/暂停",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                // 下一首
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

                // 循环模式切换
                IconButton(onClick = {
                    player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_ONE)
                        Player.REPEAT_MODE_ALL
                    else
                        Player.REPEAT_MODE_ONE
                }) {
                    Icon(
                        if (repeatMode == Player.REPEAT_MODE_ONE)
                            Icons.Default.RepeatOne
                        else
                            Icons.Default.Repeat,
                        if (repeatMode == Player.REPEAT_MODE_ONE) "单曲循环" else "歌单循环",
                        tint = Color(0xFF4FC3F7),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
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