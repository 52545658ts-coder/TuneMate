package com.example.tunemate

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.tunemate.data.backgroundFlow
import com.example.tunemate.data.buildSongFromUri
import com.example.tunemate.data.playlistsFlow
import com.example.tunemate.data.saveBackground
import com.example.tunemate.data.saveBackgroundImage
import com.example.tunemate.data.savePlaylists
import com.example.tunemate.data.saveSongs
import com.example.tunemate.data.songsFlow
import com.example.tunemate.equalizer.EqualizerScreen
import com.example.tunemate.model.Playlist
import com.example.tunemate.model.Song
import com.example.tunemate.playback.PlaybackService
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/* ============================================================
 * 播放模式
 * ============================================================ */
enum class PlayMode {
    REPEAT_ONE,   // 單曲循環
    REPEAT_ALL,   // 歌單循環
    PLAY_ONCE     // 只播當前這首一次，播完停止
}

/* ============================================================
 * Activity
 * ============================================================ */
class MainActivity : ComponentActivity() {

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
                AppRoot()
            }
        }

        requestNotifPermissionIfNeeded()
    }

    private fun requestNotifPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/* ============================================================
 * MediaController 連接
 * ============================================================ */
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

/* ============================================================
 * 根 UI
 * ============================================================ */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val controller = rememberMediaController(context)
    val scope = rememberCoroutineScope()

    // 已匯入歌曲（持久化於 DataStore），只包含使用者手動挑選的音頻
    val songs by context.songsFlow().collectAsState(initial = emptyList())
    // Player.Listener 只建立一次，需要讀取最新 songs，故用 rememberUpdatedState 包裝
    val songsState = rememberUpdatedState(songs)

    // ---------- 系統音頻選擇器（支援多選） ----------
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val existing = context.songsFlow().first()
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    // 取得可跨重啟存取的讀取權限
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    buildSongFromUri(context, uri)
                }
            }
            if (picked.isNotEmpty()) {
                val merged = (existing + picked).distinctBy { it.uriString }
                context.saveSongs(merged)
            }
        }
    }

    if (controller == null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0B0B1A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
        }
        return
    }

    var showSettings by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }

    var playMode by remember { mutableStateOf(PlayMode.REPEAT_ALL) }
    var activeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }

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
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val uri = item?.localConfiguration?.uri?.toString() ?: return
                val currentSongs = songsState.value
                currentSong = currentSongs.firstOrNull { it.uriString == uri }
                    ?: activeSongs.firstOrNull { it.uriString == uri }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    playMode == PlayMode.PLAY_ONCE
                ) {
                    controller.pause()
                }
            }
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        val cur = controller.currentMediaItem?.localConfiguration?.uri?.toString()
        currentSong = cur?.let { u -> songsState.value.firstOrNull { it.uriString == u } }
        onDispose { controller.removeListener(listener) }
    }

    fun playFrom(list: List<Song>, index: Int) {
        if (list.isEmpty() || index !in list.indices) return
        activeSongs = list
        val song = list[index]

        when (playMode) {
            PlayMode.PLAY_ONCE -> {
                controller.setMediaItem(MediaItem.fromUri(song.uri))
                controller.repeatMode = Player.REPEAT_MODE_OFF
            }
            PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL -> {
                val items = list.map { MediaItem.fromUri(it.uri) }
                controller.setMediaItems(items, index, 0L)
                controller.repeatMode =
                    if (playMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE
                    else Player.REPEAT_MODE_ALL
            }
        }
        controller.prepare()
        controller.play()
    }

    fun cyclePlayMode() {
        val old = playMode
        playMode = when (old) {
            PlayMode.REPEAT_ONE -> PlayMode.REPEAT_ALL
            PlayMode.REPEAT_ALL -> PlayMode.PLAY_ONCE
            PlayMode.PLAY_ONCE -> PlayMode.REPEAT_ONE
        }

        val currentItem = controller.currentMediaItem ?: return
        val currentPos = controller.currentPosition
        val currentUri = currentItem.localConfiguration?.uri?.toString()

        when (playMode) {
            PlayMode.PLAY_ONCE -> {
                controller.setMediaItem(currentItem, currentPos)
                controller.repeatMode = Player.REPEAT_MODE_OFF
                controller.prepare()
            }
            PlayMode.REPEAT_ONE, PlayMode.REPEAT_ALL -> {
                if (old == PlayMode.PLAY_ONCE && activeSongs.isNotEmpty()) {
                    val idx = activeSongs.indexOfFirst { it.uriString == currentUri }
                        .coerceAtLeast(0)
                    val items = activeSongs.map { MediaItem.fromUri(it.uri) }
                    controller.setMediaItems(items, idx, currentPos)
                    controller.prepare()
                }
                controller.repeatMode =
                    if (playMode == PlayMode.REPEAT_ONE) Player.REPEAT_MODE_ONE
                    else Player.REPEAT_MODE_ALL
            }
        }
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
                playMode = playMode,
                backgroundUri = backgroundUri,
                blurRadius = blurRadius,
                activeSongs = activeSongs,
                onRequestSongs = { importLauncher.launch(arrayOf("audio/*")) },
                onCreatePlaylist = { showCreateDialog = true },
                onPlaySong = { list, idx -> playFrom(list, idx) },
                onCyclePlayMode = { cyclePlayMode() },
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

/* ============================================================
 * 播放器頁面
 * ============================================================ */
@Composable
fun PlayerScreen(
    player: Player,
    songs: List<Song>,
    playlists: List<Playlist>,
    currentSong: Song?,
    isPlaying: Boolean,
    playMode: PlayMode,
    backgroundUri: Uri?,
    blurRadius: Float,
    activeSongs: List<Song>,
    onRequestSongs: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaySong: (List<Song>, Int) -> Unit,
    onCyclePlayMode: () -> Unit,
    onOpenSettings: () -> Unit
) {
    // ---------- 播放進度狀態 ----------
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableFloatStateOf(0f) }

    // ---------- 加號下拉選單狀態 ----------
    var showAddMenu by remember { mutableStateOf(false) }

    LaunchedEffect(player) {
        while (true) {
            val dur = player.duration
            if (dur != C.TIME_UNSET && dur > 0) durationMs = dur
            if (!isDragging) {
                positionMs = player.currentPosition.coerceIn(0L, durationMs)
            }
            delay(300)
        }
    }

    Box(Modifier.fillMaxSize()) {
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

        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
            // ===================== 頂部標題列 =====================
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "TuneMate",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.weight(1f))

                // ➕ 加號按鈕 + 下拉式選單
                Box {
                    IconButton(onClick = { showAddMenu = true }) {
                        Icon(Icons.Default.Add, "新增", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = showAddMenu,
                        onDismissRequest = { showAddMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("匯入音頻", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = Color(0xFF4FC3F7)
                                )
                            },
                            onClick = {
                                showAddMenu = false
                                onRequestSongs()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("建立歌單", color = Color.White) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.QueueMusic,
                                    contentDescription = null,
                                    tint = Color(0xFF4FC3F7)
                                )
                            },
                            onClick = {
                                showAddMenu = false
                                onCreatePlaylist()
                            }
                        )
                    }
                }

                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, "設置", tint = Color.White)
                }
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
                                        onPlaySong(pl.songs, 0)
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
                                val idxInActive = activeSongs.indexOf(song)
                                if (idxInActive >= 0) {
                                    onPlaySong(activeSongs, idxInActive)
                                } else {
                                    val fallback = songs.indexOf(song)
                                    if (fallback >= 0) onPlaySong(songs, fallback)
                                }
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

            // ===================== 播放進度條 =====================
            val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
            val sliderValue = (if (isDragging) dragMs else positionMs.toFloat())
                .coerceIn(0f, sliderMax)

            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isDragging = true
                        dragMs = it
                    },
                    onValueChangeFinished = {
                        player.seekTo(dragMs.toLong())
                        positionMs = dragMs.toLong()
                        isDragging = false
                        if (!player.isPlaying) player.play()
                    },
                    valueRange = 0f..sliderMax,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatMs(if (isDragging) dragMs.toLong() else positionMs),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        formatMs(durationMs),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // ===================== 控制按鈕 =====================
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

                IconButton(onClick = onCyclePlayMode) {
                    val (icon, label) = when (playMode) {
                        PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne to "單曲循環"
                        PlayMode.REPEAT_ALL -> Icons.Default.Repeat to "歌單循環"
                        // 只播一次：使用與循環相同的 Repeat 圖示，但顯示為灰色
                        PlayMode.PLAY_ONCE -> Icons.Default.Repeat to "只播一次"
                    }
                    val tint = if (playMode == PlayMode.PLAY_ONCE) {
                        Color.Gray
                    } else {
                        Color(0xFF4FC3F7)
                    }
                    Icon(
                        icon,
                        label,
                        tint = tint,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

/* ============================================================
 * 設置頁面
 * ============================================================ */
private enum class SettingsSubPage { MAIN, BACKGROUND, EQUALIZER }

@Composable
fun SettingsScreen(
    backgroundUri: Uri?,
    blurRadius: Float,
    onBackgroundChanged: (Uri?, Float) -> Unit,
    onBack: () -> Unit
) {
    var subPage by remember { mutableStateOf(SettingsSubPage.MAIN) }

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

/* ============================================================
 * 建立歌單對話框
 * ============================================================ */
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

/* ============================================================
 * 工具：毫秒 → mm:ss
 * ============================================================ */
private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
