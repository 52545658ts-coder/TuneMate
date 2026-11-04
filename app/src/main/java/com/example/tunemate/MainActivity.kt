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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
 * 移除歌曲請求
 * fromPlaylistName == null 表示「從所有歌曲刪除」（同時從所有歌單刪除）
 * 否則僅從指定歌單移除
 * ============================================================ */
private data class RemoveRequest(
    val song: Song,
    val fromPlaylistName: String?
)

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

    // 持久化資料
    val songsFlow = remember(context) { context.songsFlow() }
    val playlistsFlow = remember(context) { context.playlistsFlow() }
    val songs by songsFlow.collectAsState(initial = emptyList())
    val playlists by playlistsFlow.collectAsState(initial = emptyList())

    // 供 Player.Listener 讀取最新值
    val songsState = rememberUpdatedState(songs)
    val activeSongsState = remember { mutableStateOf<List<Song>>(emptyList()) }
    val playModeState = remember { mutableStateOf(PlayMode.REPEAT_ALL) }

    // 播放狀態
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var playMode by remember { mutableStateOf(PlayMode.REPEAT_ALL) }
    var activeSongs by remember { mutableStateOf<List<Song>>(emptyList()) }

    // 導航
    var viewMode by remember { mutableStateOf<ViewMode>(ViewMode.Home) }
    var showSettings by remember { mutableStateOf(false) }

    // 背景
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }

    // 對話框
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var removeRequest by remember { mutableStateOf<RemoveRequest?>(null) }
    var pendingImport by remember { mutableStateOf<List<Song>?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }

    // 播放進度
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }
    var dragMs by remember { mutableFloatStateOf(0f) }

    // 同步到 state 供 listener 讀取
    LaunchedEffect(activeSongs) { activeSongsState.value = activeSongs }
    LaunchedEffect(playMode) { playModeState.value = playMode }

    // ---------- 音頻選擇器（多選） ----------
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
                // 有自訂歌單：讓使用者選擇
                pendingImport = picked
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

    // 正在查看的歌單被刪除後自動回首頁
    LaunchedEffect(viewMode, playlists) {
        val mode = viewMode
        if (mode is ViewMode.Custom && playlists.none { it.name == mode.playlistName }) {
            viewMode = ViewMode.Home
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
                currentSong = songsState.value.firstOrNull { it.uriString == uri }
                    ?: activeSongsState.value.firstOrNull { it.uriString == uri }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED &&
                    playModeState.value == PlayMode.PLAY_ONCE
                ) {
                    c.pause()
                }
            }
        }
        c.addListener(listener)
        isPlaying = c.isPlaying
        val cur = c.currentMediaItem?.localConfiguration?.uri?.toString()
        currentSong = cur?.let { u -> songsState.value.firstOrNull { it.uriString == u } }
        onDispose { c.removeListener(listener) }
    }

    // 返回鍵：詳情頁回首頁
    BackHandler(enabled = !showSettings && viewMode != ViewMode.Home) {
        viewMode = ViewMode.Home
    }

    // MediaController 尚未就緒
    if (controller == null) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF0B0B1A)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFF4FC3F7))
        }
        return
    }

    // ---------- 本地播放函式 ----------
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
                    when (val mode = viewMode) {
                        is ViewMode.Home -> PlaylistListContent(
                            songs = songs,
                            playlists = playlists,
                            onOpenAllSongs = { viewMode = ViewMode.AllSongs },
                            onOpenPlaylist = { pl -> viewMode = ViewMode.Custom(pl.name) },
                            onDeletePlaylist = { pl -> playlistToDelete = pl },
                            onImport = { importLauncher.launch(arrayOf("audio/*")) },
                            onCreatePlaylist = { showCreateDialog = true },
                            onOpenSettings = { showSettings = true }
                        )

                        is ViewMode.AllSongs -> SongListContent(
                            title = "所有歌曲",
                            songs = songs,
                            isAllSongs = true,
                            onBack = { viewMode = ViewMode.Home },
                            onSongClick = { song ->
                                val idx = songs.indexOf(song).coerceAtLeast(0)
                                playFrom(songs, idx)
                            },
                            onRemove = { song -> removeRequest = RemoveRequest(song, null) }
                        )

                        is ViewMode.Custom -> {
                            val pl = playlists.firstOrNull { it.name == mode.playlistName }
                            if (pl != null) {
                                SongListContent(
                                    title = pl.name,
                                    songs = pl.songs,
                                    isAllSongs = false,
                                    onBack = { viewMode = ViewMode.Home },
                                    onSongClick = { song ->
                                        val idx = pl.songs.indexOf(song).coerceAtLeast(0)
                                        playFrom(pl.songs, idx)
                                    },
                                    onRemove = { song ->
                                        removeRequest = RemoveRequest(song, pl.name)
                                    }
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
                    scope.launch {
                        val cur = context.playlistsFlow().first()
                        context.savePlaylists(cur.filter { it.name != pl.name })
                    }
                    if ((viewMode as? ViewMode.Custom)?.playlistName == pl.name) {
                        viewMode = ViewMode.Home
                    }
                    playlistToDelete = null
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) { Text("取消") }
            }
        )
    }

    // ---------- 移除歌曲確認 ----------
    if (removeRequest != null) {
        val req = removeRequest!!
        val isAllSongs = req.fromPlaylistName == null
        AlertDialog(
            onDismissRequest = { removeRequest = null },
            title = { Text(if (isAllSongs) "刪除歌曲" else "從歌單移除") },
            text = {
                Text(
                    if (isAllSongs)
                        "確定要刪除「${req.song.title}」嗎？此操作會同時從所有歌單中移除。"
                    else
                        "確定要將「${req.song.title}」從歌單「${req.fromPlaylistName}」移除嗎？"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (isAllSongs) {
                            val curSongs = context.songsFlow().first()
                            context.saveSongs(
                                curSongs.filter { it.uriString != req.song.uriString }
                            )
                            val curPlaylists = context.playlistsFlow().first()
                            context.savePlaylists(
                                curPlaylists.map { pl ->
                                    pl.copy(songs = pl.songs.filter {
                                        it.uriString != req.song.uriString
                                    })
                                }
                            )
                        } else {
                            val curPlaylists = context.playlistsFlow().first()
                            context.savePlaylists(
                                curPlaylists.map { pl ->
                                    if (pl.name == req.fromPlaylistName)
                                        pl.copy(songs = pl.songs.filter {
                                            it.uriString != req.song.uriString
                                        })
                                    else pl
                                }
                            )
                        }
                    }
                    removeRequest = null
                }) { Text(if (isAllSongs) "刪除" else "移除") }
            },
            dismissButton = {
                TextButton(onClick = { removeRequest = null }) { Text("取消") }
            }
        )
    }

    // ---------- 匯入後選擇加入哪個歌單 ----------
    if (showImportDialog && pendingImport != null) {
        ImportToPlaylistDialog(
            pickedCount = pendingImport!!.size,
            playlists = playlists,
            onDismiss = {
                showImportDialog = false
                pendingImport = null
            },
            onConfirm = { targetName ->
                val toAdd = pendingImport!!
                scope.launch {
                    // 1) 始終加入「所有歌曲」
                    val curSongs = context.songsFlow().first()
                    val merged = (curSongs + toAdd).distinctBy { it.uriString }
                    context.saveSongs(merged)

                    // 2) 若選了自訂歌單，也加入該歌單
                    if (targetName != null) {
                        val curPlaylists = context.playlistsFlow().first()
                        context.savePlaylists(
                            curPlaylists.map { pl ->
                                if (pl.name == targetName)
                                    pl.copy(songs = (pl.songs + toAdd).distinctBy { it.uriString })
                                else pl
                            }
                        )
                    }
                }
                showImportDialog = false
                pendingImport = null
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

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
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
                    onClick = onOpenAllSongs,
                    onDelete = null
                )
            }

            // 自訂歌單
            items(playlists) { pl ->
                PlaylistRow(
                    icon = Icons.Default.QueueMusic,
                    title = pl.name,
                    subtitle = "${pl.songs.size} 首",
                    onClick = { onOpenPlaylist(pl) },
                    onDelete = { onDeletePlaylist(pl) }
                )
            }
        }
    }
}

/* ============================================================
 * 歌單列（主頁）
 * ============================================================ */
@Composable
private fun PlaylistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
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
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        if (onDelete != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = Color.LightGray
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("刪除歌單", color = Color.White) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray
            )
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
}

/* ============================================================
 * 詳情頁：歌單內的歌曲
 * ============================================================ */
@Composable
private fun SongListContent(
    title: String,
    songs: List<Song>,
    isAllSongs: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemove: (Song) -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = Color.White)
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${songs.size} 首",
                color = Color.LightGray,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))

        if (songs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isAllSongs) "尚未匯入任何歌曲。\n使用右上角「＋」→「匯入音頻」。"
                    else "此歌單尚無歌曲。",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(songs) { song ->
                    SongRow(
                        song = song,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemove(song) }
                    )
                }
            }
        }
    }
}

/* ============================================================
 * 歌曲列（含 ⋮ 選單）
 * ============================================================ */
@Composable
private fun SongRow(
    song: Song,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
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

        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "更多",
                    tint = Color.LightGray
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("從歌單移除", color = Color.White) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
}

/* ============================================================
 * 底部播放控制條
 * ============================================================ */
@Composable
private fun BottomPlayerBar(
    player: Player,
    currentSong: Song?,
    isPlaying: Boolean,
    playMode: PlayMode,
    positionMs: Long,
    durationMs: Long,
    isDragging: Boolean,
    dragMs: Float,
    onDragChange: (Float) -> Unit,
    onDragFinish: () -> Unit,
    onCyclePlayMode: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B0B1A).copy(alpha = 0.92f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 正在播放
        Text(
            text = currentSong?.let { "正在播放：${it.title}" } ?: "尚未播放",
            color = if (currentSong != null) Color.White else Color.Gray,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )

        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
        val sliderValue = (if (isDragging) dragMs else positionMs.toFloat())
            .coerceIn(0f, sliderMax)

        Slider(
            value = sliderValue,
            onValueChange = onDragChange,
            onValueChangeFinished = onDragFinish,
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

        Row(
            Modifier.fillMaxWidth(),
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
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暫停",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            IconButton(onClick = {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }) {
                Icon(
                    Icons.Default.SkipNext,
                    "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onCyclePlayMode) {
                val (icon, label) = when (playMode) {
                    PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne to "單曲循環"
                    PlayMode.REPEAT_ALL -> Icons.Default.Repeat to "歌單循環"
                    PlayMode.PLAY_ONCE -> Icons.Default.Repeat to "只播一次"
                }
                val tint = if (playMode == PlayMode.PLAY_ONCE) Color.Gray
                else Color(0xFF4FC3F7)
                Icon(
                    icon,
                    label,
                    tint = tint,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/* ============================================================
 * 建立歌單對話框
 * ============================================================ */
@Composable
private fun CreatePlaylistDialog(
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
                    Text("尚無歌曲，可建立空歌單後再匯入。", color = Color.Gray)
                } else {
                    Text(
                        "可選歌曲（${selected.size} 首）",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(allSongs) { song ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (selected.contains(song)) selected.remove(song)
                                        else selected.add(song)
                                    }
                                    .padding(vertical = 2.dp),
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
                                    color = Color.White,
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
                enabled = name.isNotBlank(),
                onClick = { onConfirm(Playlist(name.trim(), selected.toList())) }
            ) { Text("儲存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/* ============================================================
 * 匯入後選擇加入哪個歌單
 * ============================================================ */
@Composable
private fun ImportToPlaylistDialog(
    pickedCount: Int,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit
) {
    var selectedIndex by remember { mutableIntStateOf(-1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("加入歌單") },
        text = {
            Column {
                Text(
                    "已選擇 $pickedCount 首歌曲，將自動加入「所有歌曲」。",
                    color = Color.LightGray
                )
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
                val target = if (selectedIndex in playlists.indices)
                    playlists[selectedIndex].name
                else null
                onConfirm(target)
            }) { Text("加入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/* ============================================================
 * 設置
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
 * 工具：毫秒 → mm:ss
 * ============================================================ */
private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
