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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
 * 導航狀態
 * ============================================================ */
sealed class ViewMode {
    object Home : ViewMode()
    object AllSongs : ViewMode()
    data class Custom(val playlistName: String) : ViewMode()
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

    // 已匯入歌曲 & 歌單（持久化）
    val songsFlow = remember { context.songsFlow() }
    val playlistsFlow = remember { context.playlistsFlow() }
    val songs by songsFlow.collectAsState(initial = emptyList())
    val playlists by playlistsFlow.collectAsState(initial = emptyList())

    // 播放狀態
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playMode by remember { mutableStateOf(PlayMode.REPEAT_ALL) }
    var activeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // 導航
    var viewingMode by remember { mutableStateOf<ViewMode>(ViewMode.Home) }
    var showSettings by remember { mutableStateOf(false) }

    // 背景
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }

    // 對話框狀態
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var songToRemove by remember { mutableStateOf<Pair<Playlist, Song>?>(null) }
    var pendingImportSongs by remember { mutableStateOf<List<Song>?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    // 播放進度
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableFloatStateOf(0f) }

    // ---------- 系統音頻選擇器（多選） ----------
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNullOrEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val picked = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                    buildSongFromUri(context, uri)
                }
            }
            if (picked.isEmpty()) return@launch

            if (playlists.isEmpty()) {
                // 沒有自訂歌單：直接加入「所有歌曲」
                val existing = context.songsFlow().first()
                val merged = (existing + picked).distinctBy { it.uriString }
                context.saveSongs(merged)
            } else {
                // 有自訂歌單：跳出選擇器
                pendingImportSongs = picked
                showImportDialog = true
            }
        }
    }

    // 背景設定載入
    LaunchedEffect(Unit) {
        val prefs = context.backgroundFlow().first()
        backgroundUri = prefs.uri?.let { Uri.parse(it) }
        blurRadius = prefs.blur
    }

    // 正在查看的歌單若已被刪除，回到主頁
    LaunchedEffect(viewingMode, playlists) {
        val mode = viewingMode
        if (mode is ViewMode.Custom && playlists.none { it.name == mode.playlistName }) {
            viewingMode = ViewMode.Home
        }
    }

    // 進度輪詢
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            val dur = c.duration
            if (dur != C.TIME_UNSET && dur > 0) durationMs = dur
            if (!isDragging) {
                positionMs = c.currentPosition.coerceIn(0L, durationMs)
            }
            delay(300)
        }
    }

    // 播放器監聽
    DisposableEffect(controller) {
        val c = controller ?: return@DisposableEffect onDispose { }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val uri = item?.localConfiguration?.uri?.toString() ?: return
                currentSong = songs.firstOrNull { it.uriString == uri }
                    ?: activeSongs.firstOrNull { it.uriString == uri }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && playMode == PlayMode.PLAY_ONCE) {
                    c.pause()
                }
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        val cur = c.currentMediaItem?.localConfiguration?.uri?.toString()
        currentSong = cur?.let { u -> songs.firstOrNull { it.uriString == u } }
        onDispose { c.removeListener(listener) }
    }

    // 返回鍵：從詳情返回主頁
    BackHandler(enabled = !showSettings && viewingMode != ViewMode.Home) {
        viewingMode = ViewMode.Home
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

    // ---------- 本地函式：播放 ----------
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

    // ---------- 主體 ----------
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
            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (val mode = viewingMode) {
                        is ViewMode.Home -> PlaylistListContent(
                            songs = songs,
                            playlists = playlists,
                            onOpenAllSongs = { viewingMode = ViewMode.AllSongs },
                            onOpenPlaylist = { pl -> viewingMode = ViewMode.Custom(pl.name) },
                            onDeletePlaylist = { pl -> playlistToDelete = pl },
                            onImport = { importLauncher.launch(arrayOf("audio/*")) },
                            onCreatePlaylist = { showCreateDialog = true },
                            onOpenSettings = { showSettings = true }
                        )

                        is ViewMode.AllSongs -> SongListContent(
                            title = "所有歌曲",
                            songs = songs,
                            canRemove = false,
                            onBack = { viewingMode = ViewMode.Home },
                            onSongClick = { song ->
                                val idx = songs.indexOf(song).coerceAtLeast(0)
                                playFrom(songs, idx)
                            }
                        )

                        is ViewMode.Custom -> {
                            val pl = playlists.firstOrNull { it.name == mode.playlistName }
                            if (pl != null) {
                                SongListContent(
                                    title = pl.name,
                                    songs = pl.songs,
                                    canRemove = true,
                                    onBack = { viewingMode = ViewMode.Home },
                                    onSongClick = { song ->
                                        val idx = pl.songs.indexOf(song).coerceAtLeast(0)
                                        playFrom(pl.songs, idx)
                                    },
                                    onRemoveSong = { song -> songToRemove = pl to song }
                                )
                            }
                        }
                    }
                }

                BottomPlayerBar(
                    player = controller,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isDragging = isDragging,
                    dragMs = dragMs,
                    onDragChange = { v ->
                        isDragging = true
                        dragMs = v
                    },
                    onDragFinish = {
                        controller.seekTo(dragMs.toLong())
                        positionMs = dragMs.toLong()
                        isDragging = false
                        if (!controller.isPlaying) controller.play()
                    },
                    onCyclePlayMode = { cyclePlayMode() }
                )
            }
        }
    }

    // ---------- 建立歌單對話框 ----------
    if (showCreateDialog) {
        CreatePlaylistDialog(
            allSongs = songs,
            onDismiss = { showCreateDialog = false },
            onConfirm = { pl ->
                if (playlists.none { it.name == pl.name }) {
                    scope.launch { context.savePlaylists(playlists + pl) }
                }
                showCreateDialog = false
            }
        )
    }

    // ---------- 刪除歌單確認 ----------
    if (playlistToDelete != null) {
        val pl = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("刪除歌單") },
            text = { Text("確定要刪除歌單「${pl.name}」嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { context.savePlaylists(playlists.filter { it.name != pl.name }) }
                    if ((viewingMode as? ViewMode.Custom)?.playlistName == pl.name) {
                        viewingMode = ViewMode.Home
                    }
                    playlistToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("取消") }
            }
        )
    }

    // ---------- 從歌單移除確認 ----------
    if (songToRemove != null) {
        val (pl, song) = songToRemove!!
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("從歌單移除") },
            text = { Text("確定要將「${song.title}」從歌單「${pl.name}」移除嗎？") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val updated = playlists.map {
                            if (it.name == pl.name)
                                it.copy(songs = it.songs.filter { s -> s.uriString != song.uriString })
                            else it
                        }
                        context.savePlaylists(updated)
                    }
                    songToRemove = null
                }) { Text("移除") }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("取消") }
            }
        )
    }

    // ---------- 匯入後選擇加入哪個歌單 ----------
    if (showImportDialog && pendingImportSongs != null) {
        var selectedIndex by remember { mutableIntStateOf(-1) }
        AlertDialog(
            onDismissRequest = {
                showImportDialog = false
                pendingImportSongs = null
            },
            title = { Text("加入歌單") },
            text = {
                Column {
                    Text("已選擇 ${pendingImportSongs!!.size} 首歌曲，將自動加入「所有歌曲」。")
                    Spacer(Modifier.height(12.dp))
                    Text("要額外加入哪個歌單？", fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedIndex = -1 }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIndex == -1,
                            onClick = { selectedIndex = -1 }
                        )
                        Text("不加入其他歌單")
                    }

                    for (i in playlists.indices) {
                        val pl = playlists[i]
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { selectedIndex = i }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedIndex == i,
                                onClick = { selectedIndex = i }
                            )
                            Text(
                                pl.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val pending = pendingImportSongs ?: return@TextButton
                    val idx = selectedIndex
                    scope.launch {
                        // 加入「所有歌曲」
                        val existing = context.songsFlow().first()
                        val merged = (existing + pending).distinctBy { it.uriString }
                        context.saveSongs(merged)

                        // 若選擇了自訂歌單，也加入該歌單
                        if (idx >= 0 && idx < playlists.size) {
                            val pl = playlists[idx]
                            val updatedPl = pl.copy(
                                songs = (pl.songs + pending).distinctBy { it.uriString }
                            )
                            context.savePlaylists(
                                playlists.map { if (it.name == pl.name) updatedPl else it }
                            )
                        }
                    }
                    showImportDialog = false
                    pendingImportSongs = null
                }) { Text("加入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    pendingImportSongs = null
                }) { Text("取消") }
            }
        )
    }
}

/* ============================================================
 * 主頁：歌單列表
 * ============================================================ */
@Composable
private fun PlaylistListContent(
    songs: List<Song>,
    playlists: List<Playlist>,
    onOpenAllSongs: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onImport: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TuneMate",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(Modifier.weight(1f))

            // ➕ 加號下拉選單
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
                            onImport()
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

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            // 「所有歌曲」虛擬歌單
            item {
                PlaylistRow(
                    icon = Icons.Default.MusicNote,
                    title = "所有歌曲",
                    subtitle = "${songs.size} 首",
                    onClick = onOpenAllSongs
                )
            }

            // 自訂歌單
            items(playlists) { pl ->
                PlaylistRow(
                    icon = Icons.Default.QueueMusic,
                    title = pl.name,
                    subtitle = "${pl.songs.size}
