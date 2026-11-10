package com.example.tunemate

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.example.tunemate.data.PlaybackState
import com.example.tunemate.data.ThemePrefs
import com.example.tunemate.data.backgroundFlow
import com.example.tunemate.data.buildSongFromUri
import com.example.tunemate.data.playbackStateFlow
import com.example.tunemate.data.playlistsFlow
import com.example.tunemate.data.saveBackground
import com.example.tunemate.data.saveBackgroundImage
import com.example.tunemate.data.savePlaybackState
import com.example.tunemate.data.savePlaylists
import com.example.tunemate.data.saveSongs
import com.example.tunemate.data.saveTheme
import com.example.tunemate.data.songsFlow
import com.example.tunemate.data.themeFlow
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
    REPEAT_ONE,
    REPEAT_ALL,
    PLAY_ONCE
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
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )

        setContent {
            AppRoot()
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
    val themeFlow = remember(context) { context.themeFlow() }
    val themePrefs by themeFlow.collectAsState(initial = ThemePrefs())

    val bgColor = Color(themePrefs.backgroundColor)
    val accent = Color(themePrefs.accentColor)
    val textColor = Color(themePrefs.textColor)
    
    val surfaceColor = if (bgColor == Color.Black) Color(0xFF1E1E1E) else bgColor

    val customColorScheme = darkColorScheme(
        primary = accent,
        onPrimary = Color.White,
        primaryContainer = accent.copy(alpha = 0.2f),
        onPrimaryContainer = accent,
        secondary = accent,
        background = bgColor,
        onBackground = textColor,
        surface = surfaceColor,
        onSurface = textColor,
        surfaceVariant = surfaceColor,
        onSurfaceVariant = textColor.copy(alpha = 0.7f)
    )

    MaterialTheme(colorScheme = customColorScheme) {
        AppContent(themePrefs)
    }
}

@Composable
fun AppContent(themePrefs: ThemePrefs) {
    val context = LocalContext.current
    val controller = rememberMediaController(context)
    val scope = rememberCoroutineScope()

    val bgColor = MaterialTheme.colorScheme.background
    val accent = MaterialTheme.colorScheme.primary

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
    var currentSourcePlaylist by remember { mutableStateOf<String?>(null) }

    // 恢復標記
    var restored by remember { mutableStateOf(false) }

    // 導航
    var viewMode by remember { mutableStateOf<ViewMode>(ViewMode.Home) }
    var showSettings by remember { mutableStateOf(false) }

    // 全屏播放頁
    var showPlayerSheet by remember { mutableStateOf(false) }

    // 背景
    var backgroundUri by remember { mutableStateOf<Uri?>(null) }
    var blurRadius by remember { mutableFloatStateOf(12f) }
    var brightness by remember { mutableFloatStateOf(0.7f) }

    // 對話框
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }
    var removeRequest by remember { mutableStateOf<RemoveRequest?>(null) }
    var pendingImport by remember { mutableStateOf<List<Song>?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // 新增的對話框狀態
    var songToRename by remember { mutableStateOf<Song?>(null) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }

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
                val existing = context.songsFlow().first()
                val merged = (existing + picked).distinctBy { it.uriString }
                context.saveSongs(merged)
            } else {
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
        brightness = prefs.brightness
    }

    // 正在查看的歌單被刪除後自動回首頁
    LaunchedEffect(viewMode, playlists) {
        val mode = viewMode
        if (mode is ViewMode.Custom && playlists.none { it.name == mode.playlistName }) {
            viewMode = ViewMode.Home
        }
    }

    // ---------- 恢復上次播放狀態 ----------
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        if (restored) return@LaunchedEffect

        val curSongs = songsFlow.first()
        val curPlaylists = playlistsFlow.first()
        val saved = context.playbackStateFlow().first()

        restored = true

        if (saved.songUri == null) return@LaunchedEffect

        val queue: List<Song> = when {
            saved.playlistName == null -> curSongs
            else -> curPlaylists.firstOrNull { it.name == saved.playlistName }?.songs ?: curSongs
        }
        val idx = queue.indexOfFirst { it.uriString == saved.songUri }
        if (idx < 0) return@LaunchedEffect

        val restoredMode = saved.playMode
            ?.let { runCatching { PlayMode.valueOf(it) }.getOrNull() }
        if (restoredMode != null) playMode = restoredMode

        activeSongs = queue
        currentSourcePlaylist = saved.playlistName
        currentSong = queue[idx]
        positionMs = saved.positionMs

        val items = queue.map { MediaItem.fromUri(it.uri) }
        c.setMediaItems(items, idx, saved.positionMs)
        c.repeatMode = when (playMode) {
            PlayMode.REPEAT_ONE -> Player.REPEAT_MODE_ONE
            PlayMode.REPEAT_ALL -> Player.REPEAT_MODE_ALL
            PlayMode.PLAY_ONCE -> Player.REPEAT_MODE_OFF
        }
        c.prepare()
    }

    // ---------- 保存：歌曲/歌單/模式變化時立即保存 ----------
    LaunchedEffect(currentSong, currentSourcePlaylist, activeSongs, playMode) {
        val c = controller ?: return@LaunchedEffect
        val song = currentSong ?: return@LaunchedEffect
        context.savePlaybackState(
            PlaybackState(
                songUri = song.uriString,
                playlistName = currentSourcePlaylist,
                positionMs = c.currentPosition.coerceAtLeast(0L),
                playMode = playMode.name
            )
        )
    }

    // ---------- 保存：定期保存進度 ----------
    LaunchedEffect(controller) {
        val c = controller ?: return@LaunchedEffect
        while (true) {
            delay(3000)
            if (!restored) continue
            val song = currentSong ?: continue
            if (isDragging) continue
            context.savePlaybackState(
                PlaybackState(
                    songUri = song.uriString,
                    playlistName = currentSourcePlaylist,
                    positionMs = c.currentPosition.coerceAtLeast(0L),
                    playMode = playMode.name
                )
            )
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

    // 返回鍵：關閉全屏頁面或返回首頁
    BackHandler(enabled = showPlayerSheet || showSettings || viewMode != ViewMode.Home) {
        if (showPlayerSheet) showPlayerSheet = false
        else if (showSettings) showSettings = false
        else viewMode = ViewMode.Home
    }

    if (controller == null) {
        Box(
            Modifier.fillMaxSize().background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = accent)
        }
        return
    }

    // ---------- 本地播放函式 ----------
    fun playFrom(list: List<Song>, index: Int, sourcePlaylist: String?) {
        if (list.isEmpty() || index !in list.indices) return
        activeSongs = list
        currentSourcePlaylist = sourcePlaylist
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
        showPlayerSheet = true
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
    Box(Modifier.fillMaxSize()) {
        // 1. 全局背景層
        if (backgroundUri != null) {
            AsyncImage(
                model = backgroundUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(blurRadius.dp)
            )
            val maskAlpha = 1f - brightness
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = maskAlpha)))
        } else {
            Box(Modifier.fillMaxSize().background(bgColor))
        }

        // 2. 基礎頁面
        if (showSettings) {
            SettingsScreen(
                backgroundUri = backgroundUri,
                blurRadius = blurRadius,
                brightness = brightness,
                onBackgroundChanged = { uri, blur, bright ->
                    backgroundUri = uri
                    blurRadius = blur
                    brightness = bright
                    scope.launch { context.saveBackground(uri?.toString(), blur, bright) }
                },
                themePrefs = themePrefs,
                onThemeChanged = { bg, ac, tx ->
                    scope.launch { context.saveTheme(bg, ac, tx) }
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
                            currentPlayingUri = currentSong?.uriString,
                            currentSourcePlaylist = currentSourcePlaylist,
                            isPlaying = isPlaying,
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
                            currentPlayingUri = currentSong?.uriString,
                            isAllSongs = true,
                            isPlaying = isPlaying,
                            onBack = { viewMode = ViewMode.Home },
                            onSongClick = { song ->
                                val idx = songs.indexOf(song).coerceAtLeast(0)
                                playFrom(songs, idx, null)
                            },
                            onRemove = { song -> removeRequest = RemoveRequest(song, null) },
                            onRename = { song -> songToRename = song },
                            onAddToPlaylist = { song -> songToAddToPlaylist = song }
                        )

                        is ViewMode.Custom -> {
                            val pl = playlists.firstOrNull { it.name == mode.playlistName }
                            if (pl != null) {
                                SongListContent(
                                    title = pl.name,
                                    songs = pl.songs,
                                    currentPlayingUri = currentSong?.uriString,
                                    isAllSongs = false,
                                    isPlaying = isPlaying,
                                    onBack = { viewMode = ViewMode.Home },
                                    onSongClick = { song ->
                                        val idx = pl.songs.indexOf(song).coerceAtLeast(0)
                                        playFrom(pl.songs, idx, pl.name)
                                    },
                                    onRemove = { song -> removeRequest = RemoveRequest(song, pl.name) },
                                    onRename = { song -> songToRename = song },
                                    onAddToPlaylist = { song -> songToAddToPlaylist = song }
                                )
                            }
                        }
                    }
                }

                val barAlpha = ((1f - brightness) + 0.3f).coerceIn(0f, 1f)
                val barBg = if (backgroundUri != null) Color.Black.copy(alpha = barAlpha) else MaterialTheme.colorScheme.background
                BottomPlayerBar(
                    player = controller,
                    currentSong = currentSong,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isDragging = isDragging,
                    dragMs = dragMs,
                    backgroundColor = barBg,
                    onBarClick = { if (currentSong != null) showPlayerSheet = true },
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

        // 3. 全屏播放頁面
        AnimatedVisibility(
            visible = showPlayerSheet && currentSong != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            val song = currentSong
            if (song != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    if (backgroundUri != null) {
                        AsyncImage(
                            model = backgroundUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(blurRadius.dp)
                        )
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)))
                    }

                    PlayerSheetContent(
                        player = controller,
                        currentSong = song,
                        isPlaying = isPlaying,
                        playMode = playMode,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        isDragging = isDragging,
                        dragMs = dragMs,
                        onDismiss = { showPlayerSheet = false },
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
    }

    // ---------- 重新命名對話框 ----------
    if (songToRename != null) {
        val targetSong = songToRename!!
        var newName by remember { mutableStateOf(targetSong.title) }
        
        AlertDialog(
            onDismissRequest = { songToRename = null },
            title = { Text("重新命名") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("歌曲名稱") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = newName.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val trimmedName = newName.trim()
                            // 1. 更新所有歌曲中該歌曲的名稱
                            val curSongs = context.songsFlow().first()
                            val updatedSongs = curSongs.map { 
                                if (it.uriString == targetSong.uriString) it.copy(title = trimmedName) else it 
                            }
                            context.saveSongs(updatedSongs)

                            // 2. 更新所有歌單中該歌曲的名稱
                            val curPlaylists = context.playlistsFlow().first()
                            val updatedPlaylists = curPlaylists.map { pl ->
                                pl.copy(songs = pl.songs.map { 
                                    if (it.uriString == targetSong.uriString) it.copy(title = trimmedName) else it 
                                })
                            }
                            context.savePlaylists(updatedPlaylists)
                            
                            // 3. 如果是當前正在播放的歌曲，同步更新狀態
                            if (currentSong?.uriString == targetSong.uriString) {
                                currentSong = currentSong?.copy(title = trimmedName)
                            }
                        }
                        songToRename = null
                    }
                ) { Text("儲存") }
            },
            dismissButton = { TextButton(onClick = { songToRename = null }) { Text("取消") } }
        )
    }

    // ---------- 添加至其他歌單對話框 ----------
    if (songToAddToPlaylist != null) {
        val targetSong = songToAddToPlaylist!!
        // 過濾掉已經包含該歌曲的歌單
        val availablePlaylists = playlists.filter { pl ->
            pl.songs.none { it.uriString == targetSong.uriString }
        }

        if (availablePlaylists.isEmpty()) {
            AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                title = { Text("添加至其他歌單") },
                text = { Text("所有歌單都已包含此歌曲，或您尚未建立其他歌單。") },
                confirmButton = {
                    TextButton(onClick = { songToAddToPlaylist = null }) { Text("確定") }
                }
            )
        } else {
            var selectedPlaylistIndex by remember { mutableIntStateOf(-1) }
            AlertDialog(
                onDismissRequest = { songToAddToPlaylist = null },
                title = { Text("添加至其他歌單") },
                text = {
                    Column {
                        Text("選擇要加入的歌單：")
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(Modifier.heightIn(max = 300.dp)) {
                            items(availablePlaylists.indices.toList()) { index ->
                                val pl = availablePlaylists[index]
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPlaylistIndex = index }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = selectedPlaylistIndex == index,
                                        onClick = { selectedPlaylistIndex = index }
                                    )
                                    Text(pl.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = selectedPlaylistIndex in availablePlaylists.indices,
                        onClick = {
                            val targetPlaylist = availablePlaylists[selectedPlaylistIndex]
                            scope.launch {
                                val curPlaylists = context.playlistsFlow().first()
                                val updated = curPlaylists.map { pl ->
                                    if (pl.name == targetPlaylist.name) {
                                        pl.copy(songs = pl.songs + targetSong)
                                    } else pl
                                }
                                context.savePlaylists(updated)
                            }
                            songToAddToPlaylist = null
                        }
                    ) { Text("加入") }
                },
                dismissButton = { TextButton(onClick = { songToAddToPlaylist = null }) { Text("取消") } }
            )
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
                    val curSongs = context.songsFlow().first()
                    val merged = (curSongs + toAdd).distinctBy { it.uriString }
                    context.saveSongs(merged)

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
 * 全屏播放頁面 UI
 * ============================================================ */
@Composable
fun PlayerSheetContent(
    player: Player,
    currentSong: Song,
    isPlaying: Boolean,
    playMode: PlayMode,
    positionMs: Long,
    durationMs: Long,
    isDragging: Boolean,
    dragMs: Float,
    onDismiss: () -> Unit,
    onDragChange: (Float) -> Unit,
    onDragFinish: () -> Unit,
    onCyclePlayMode: () -> Unit
) {
    val onBg = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.primary

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.KeyboardArrowDown, "收起", tint = onBg, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                "正在播放",
                color = onBg,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { /* 分享 */ }) {
                Icon(Icons.Default.Share, "分享", tint = onBg, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            VinylRecord(isPlaying = isPlaying)
        }

        Spacer(Modifier.height(32.dp))

        Column(
            Modifier.fillMaxWidth()
        ) {
            Text(
                currentSong.title,
                color = onBg,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start
            )
            Text(
                currentSong.artist,
                color = onBg.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(24.dp))

        val sliderMax = durationMs.coerceAtLeast(1L).toFloat()
        val sliderValue = (if (isDragging) dragMs else positionMs.toFloat())
            .coerceIn(0f, sliderMax)

        Slider(
            value = sliderValue,
            onValueChange = onDragChange,
            onValueChangeFinished = onDragFinish,
            valueRange = 0f..sliderMax,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent
            )
        )

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatMs(if (isDragging) dragMs.toLong() else positionMs),
                color = onBg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                formatMs(durationMs),
                color = onBg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))

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
                    tint = onBg,
                    modifier = Modifier.size(40.dp)
                )
            }

            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(onBg)
                    .clickable {
                        if (player.isPlaying) player.pause() else player.play()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暫停",
                    tint = MaterialTheme.colorScheme.background,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }) {
                Icon(
                    Icons.Default.SkipNext,
                    "下一首",
                    tint = onBg,
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(onClick = onCyclePlayMode) {
                val (icon, label) = when (playMode) {
                    PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne to "單曲循環"
                    PlayMode.REPEAT_ALL -> Icons.Default.Repeat to "歌單循環"
                    PlayMode.PLAY_ONCE -> Icons.Default.Repeat to "只播一次"
                }
                val tint = if (playMode == PlayMode.PLAY_ONCE) onBg.copy(alpha = 0.5f) else accent
                Icon(icon, label, tint = tint, modifier = Modifier.size(28.dp))
            }
        }
        
        Spacer(Modifier.height(16.dp))
    }
}

/* ============================================================
 * 黑膠唱片組件
 * ============================================================ */
@Composable
fun VinylRecord(isPlaying: Boolean) {
    val rotation = remember { Animatable(0f) }
    val accent = MaterialTheme.colorScheme.primary

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                val remaining = 360f - (rotation.value % 360f)
                rotation.animateTo(
                    targetValue = rotation.value + remaining + 360f,
                    animationSpec = tween(
                        durationMillis = ((remaining + 360f) * 10).toInt(),
                        easing = LinearEasing
                    )
                )
            }
        }
    }

    Box(
        Modifier
            .size(280.dp)
            .rotate(rotation.value),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = Color(0xFF1A1A1A))

            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width / 2
            for (i in 1..15) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.03f),
                    radius = maxRadius * (i / 15f),
                    center = center,
                    style = Stroke(width = 2f)
                )
            }

            drawArc(
                color = Color.White.copy(alpha = 0.08f),
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(10f, 10f),
                size = androidx.compose.ui.geometry.Size(size.width - 20f, size.height - 20f),
                style = Stroke(width = 20f)
            )
        }

        Box(
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.8f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(60.dp)
            )
        }
        
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.Black)
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
    currentPlayingUri: String?,
    currentSourcePlaylist: String?,
    isPlaying: Boolean,
    onOpenAllSongs: () -> Unit,
    onOpenPlaylist: (Playlist) -> Unit,
    onDeletePlaylist: (Playlist) -> Unit,
    onImport: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showAddMenu by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TuneMate",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.weight(1f))

            Box {
                IconButton(onClick = { showAddMenu = true }) {
                    Icon(Icons.Default.Add, "新增", tint = accent)
                }
                DropdownMenu(
                    expanded = showAddMenu,
                    onDismissRequest = { showAddMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("匯入音頻") },
                        leadingIcon = {
                            Icon(Icons.Default.MusicNote, null, tint = accent)
                        },
                        onClick = {
                            showAddMenu = false
                            onImport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("建立歌單") },
                        leadingIcon = {
                            Icon(Icons.Default.QueueMusic, null, tint = accent)
                        },
                        onClick = {
                            showAddMenu = false
                            onCreatePlaylist()
                        }
                    )
                }
            }

            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, "設置", tint = accent)
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.weight(1f)) {
            item {
                val isSource = currentPlayingUri != null && currentSourcePlaylist == null
                PlaylistRow(
                    icon = Icons.Default.MusicNote,
                    title = "所有歌曲",
                    subtitle = "${songs.size} 首",
                    isHighlighted = isSource,
                    isAnimating = isSource && isPlaying,
                    onClick = onOpenAllSongs,
                    onDelete = null
                )
            }

            items(playlists) { pl ->
                val isSource = currentPlayingUri != null && currentSourcePlaylist == pl.name
                PlaylistRow(
                    icon = Icons.Default.QueueMusic,
                    title = pl.name,
                    subtitle = "${pl.songs.size} 首",
                    isHighlighted = isSource,
                    isAnimating = isSource && isPlaying,
                    onClick = { onOpenPlaylist(pl) },
                    onDelete = { onDeletePlaylist(pl) }
                )
            }
        }
    }
}

/* ============================================================
 * 歌單列
 * ============================================================ */
@Composable
private fun PlaylistRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isHighlighted: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    val breath = remember { Animatable(0f) }
    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            while (true) {
                breath.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
                breath.animateTo(0f, tween(2000, easing = FastOutSlowInEasing))
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val b = breath.value

    val bgAlpha = if (isHighlighted) 0.10f + b * 0.20f else 0f
    val iconAlpha = if (isHighlighted) 0.65f + b * 0.35f else 1f
    val subAlpha = if (isHighlighted) 0.70f + b * 0.30f else 1f

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(accent.copy(alpha = bgAlpha), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isHighlighted) Icons.Default.PlayArrow else icon,
            contentDescription = null,
            tint = if (isHighlighted) accent.copy(alpha = iconAlpha)
            else accent.copy(alpha = 0.7f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isHighlighted) accent else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (isHighlighted) accent.copy(alpha = subAlpha) 
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        if (onDelete != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("刪除歌單") },
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
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
}

/* ============================================================
 * 詳情頁：歌單內的歌曲
 * ============================================================ */
@Composable
private fun SongListContent(
    title: String,
    songs: List<Song>,
    currentPlayingUri: String?,
    isAllSongs: Boolean,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    onRename: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${songs.size} 首",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f))

        if (songs.isEmpty()) {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isAllSongs) "尚未匯入任何歌曲。\n使用右上角「＋」→「匯入音頻」。"
                    else "此歌單尚無歌曲。",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(songs) { song ->
                    val isCurrent = currentPlayingUri != null &&
                            song.uriString == currentPlayingUri
                    SongRow(
                        song = song,
                        isHighlighted = isCurrent,
                        isAnimating = isCurrent && isPlaying,
                        onClick = { onSongClick(song) },
                        onRemove = { onRemove(song) },
                        onRename = { onRename(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) }
                    )
                }
            }
        }
    }
}

/* ============================================================
 * 歌曲列
 * ============================================================ */
@Composable
private fun SongRow(
    song: Song,
    isHighlighted: Boolean,
    isAnimating: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onRename: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = MaterialTheme.colorScheme.primary

    val breath = remember { Animatable(0f) }
    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            while (true) {
                breath.animateTo(1f, tween(2000, easing = FastOutSlowInEasing))
                breath.animateTo(0f, tween(2000, easing = FastOutSlowInEasing))
            }
        }
    }

    val shape = RoundedCornerShape(12.dp)
    val b = breath.value

    val bgAlpha = if (isHighlighted) 0.10f + b * 0.20f else 0f
    val iconAlpha = if (isHighlighted) 0.65f + b * 0.35f else 1f
    val subAlpha = if (isHighlighted) 0.70f + b * 0.30f else 1f

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(accent.copy(alpha = bgAlpha), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (isHighlighted) Icons.Default.PlayArrow else Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (isHighlighted) accent.copy(alpha = iconAlpha)
            else accent.copy(alpha = 0.7f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isHighlighted) accent else MaterialTheme.colorScheme.onBackground,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist,
                color = if (isHighlighted) accent.copy(alpha = subAlpha) 
                        else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
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
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("添加至其他歌單") },
                    onClick = {
                        menuOpen = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("重新命名") },
                    onClick = {
                        menuOpen = false
                        onRename()
                    }
                )
                DropdownMenuItem(
                    text = { Text("從歌單移除", color = Color(0xFFFF5252)) },
                    onClick = {
                        menuOpen = false
                        onRemove()
                    }
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f))
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
    backgroundColor: Color,
    onBarClick: () -> Unit,
    onDragChange: (Float) -> Unit,
    onDragFinish: () -> Unit,
    onCyclePlayMode: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val onBg = MaterialTheme.colorScheme.onBackground

    Column(
        Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onBarClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentSong?.let { "正在播放：${it.title}" } ?: "尚未播放",
                color = if (currentSong != null) onBg else onBg.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }

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
                color = onBg.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                formatMs(durationMs),
                color = onBg.copy(alpha = 0.7f),
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
                    tint = onBg,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    "播放/暫停",
                    tint = onBg,
                    modifier = Modifier.size(44.dp)
                )
            }

            IconButton(onClick = {
                if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            }) {
                Icon(
                    Icons.Default.SkipNext,
                    "下一首",
                    tint = onBg,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onCyclePlayMode) {
                val (icon, label) = when (playMode) {
                    PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne to "單曲循環"
                    PlayMode.REPEAT_ALL -> Icons.Default.Repeat to "歌單循環"
                    PlayMode.PLAY_ONCE -> Icons.Default.Repeat to "只播一次"
                }
                val tint = if (playMode == PlayMode.PLAY_ONCE) onBg.copy(alpha = 0.5f) else accent
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
 * 其餘組件保持不變
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
                                    color = MaterialTheme.colorScheme.onSurface,
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

private enum class SettingsSubPage { MAIN, BACKGROUND, THEME, EQUALIZER }

@Composable
fun SettingsScreen(
    backgroundUri: Uri?,
    blurRadius: Float,
    brightness: Float,
    onBackgroundChanged: (Uri?, Float, Float) -> Unit,
    themePrefs: ThemePrefs,
    onThemeChanged: (Long, Long, Long) -> Unit,
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
            onOpenTheme = { subPage = SettingsSubPage.THEME },
            onOpenEqualizer = { subPage = SettingsSubPage.EQUALIZER },
            onBack = onBack
        )
        SettingsSubPage.BACKGROUND -> BackgroundSettingsPage(
            backgroundUri = backgroundUri,
            blurRadius = blurRadius,
            brightness = brightness,
            onBackgroundChanged = onBackgroundChanged,
            onBack = { subPage = SettingsSubPage.MAIN }
        )
        SettingsSubPage.THEME -> ThemeSettingsPage(
            themePrefs = themePrefs,
            onThemeChanged = onThemeChanged,
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
    onOpenTheme: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "設置",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsEntry(
            icon = Icons.Default.Image,
            title = "背景設定",
            subtitle = "更換背景圖片、調整模糊度與亮度",
            onClick = onOpenBackground
        )
        SettingsEntry(
            icon = Icons.Default.Palette,
            title = "主題",
            subtitle = "設定背景、特效與文字顏色",
            onClick = onOpenTheme
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
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f))
}

@Composable
private fun BackgroundSettingsPage(
    backgroundUri: Uri?,
    blurRadius: Float,
    brightness: Float,
    onBackgroundChanged: (Uri?, Float, Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingBlur by remember(blurRadius) { mutableFloatStateOf(blurRadius) }
    var pendingBrightness by remember(brightness) { mutableFloatStateOf(brightness) }
    var saving by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            saving = true
            scope.launch {
                val saved = context.saveBackgroundImage(uri)
                if (saved != null) {
                    onBackgroundChanged(saved, pendingBlur, pendingBrightness)
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
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "背景設定",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
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
                OutlinedButton(onClick = { onBackgroundChanged(null, pendingBlur, pendingBrightness) }) {
                    Text("清除背景")
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("背景模糊 ${pendingBlur.toInt()} dp", color = MaterialTheme.colorScheme.onBackground)
        Slider(
            value = pendingBlur,
            onValueChange = { pendingBlur = it },
            onValueChangeFinished = {
                onBackgroundChanged(backgroundUri, pendingBlur, pendingBrightness)
            },
            valueRange = 0f..25f
        )

        Spacer(Modifier.height(16.dp))

        Text("明亮度 ${(pendingBrightness * 100).toInt()}%", color = MaterialTheme.colorScheme.onBackground)
        Slider(
            value = pendingBrightness,
            onValueChange = { pendingBrightness = it },
            onValueChangeFinished = {
                onBackgroundChanged(backgroundUri, pendingBlur, pendingBrightness)
            },
            valueRange = 0f..1f
        )

        Spacer(Modifier.height(24.dp))

        Text("預覽", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (backgroundUri != null) {
                AsyncImage(
                    model = backgroundUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(pendingBlur.dp)
                )
                val maskAlpha = 1f - pendingBrightness
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = maskAlpha)))
            } else {
                Text("使用預設漸層背景", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun ThemeSettingsPage(
    themePrefs: ThemePrefs,
    onThemeChanged: (Long, Long, Long) -> Unit,
    onBack: () -> Unit
) {
    var localBg by remember(themePrefs.backgroundColor) { mutableLongStateOf(themePrefs.backgroundColor) }
    var localAccent by remember(themePrefs.accentColor) { mutableLongStateOf(themePrefs.accentColor) }
    var localText by remember(themePrefs.textColor) { mutableLongStateOf(themePrefs.textColor) }

    LaunchedEffect(localBg, localAccent, localText) {
        delay(200)
        if (localBg != themePrefs.backgroundColor || 
            localAccent != themePrefs.accentColor ||
            localText != themePrefs.textColor) {
            onThemeChanged(localBg, localAccent, localText)
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
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "主題",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(Modifier.height(24.dp))

        Text("背景顏色", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        HsvColorPicker(
            initialColor = Color(localBg),
            onColorChange = { localBg = it.toArgb().toLong() and 0xFFFFFFFFL }
        )

        Spacer(Modifier.height(32.dp))

        Text("呼吸發光特效顏色", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        HsvColorPicker(
            initialColor = Color(localAccent),
            onColorChange = { localAccent = it.toArgb().toLong() and 0xFFFFFFFFL }
        )

        Spacer(Modifier.height(32.dp))

        Text("文字顏色", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        HsvColorPicker(
            initialColor = Color(localText),
            onColorChange = { localText = it.toArgb().toLong() and 0xFFFFFFFFL }
        )

        Spacer(Modifier.height(32.dp))

        Text("預覽", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(Color(localBg), RoundedCornerShape(12.dp))
        ) {
            val previewAccent = Color(localAccent)
            val previewText = Color(localText)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(
                        previewAccent.copy(alpha = 0.20f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 12.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = previewAccent,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        "正在播放的歌曲",
                        color = previewText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "演出者",
                        color = previewText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "提示：預設背景僅在未設定背景圖片時顯示。",
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun HsvColorPicker(
    initialColor: Color,
    onColorChange: (Color) -> Unit
) {
    val hsv = remember(initialColor) {
        val r = initialColor.red
        val g = initialColor.green
        val b = initialColor.blue
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min
        val h = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6f)
            max == g -> 60f * (((b - r) / delta) + 2f)
            else -> 60f * (((r - g) / delta) + 4f)
        }
        val s = if (max == 0f) 0f else delta / max
        floatArrayOf(if (h < 0) h + 360f else h, s, max)
    }
    
    var h by remember { mutableFloatStateOf(hsv[0]) }
    var s by remember { mutableFloatStateOf(hsv[1]) }
    var v by remember { mutableFloatStateOf(hsv[2]) }

    Column(Modifier.fillMaxWidth()) {
        GradientSlider(
            value = h,
            onValueChange = {
                h = it
                onColorChange(Color.hsv(h, s, v))
            },
            colors = listOf(
                Color.Red, Color.Yellow, Color.Green, 
                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
            ),
            valueRange = 0f..360f
        )

        Spacer(Modifier.height(8.dp))

        GradientSlider(
            value = s,
            onValueChange = {
                s = it
                onColorChange(Color.hsv(h, s, v))
            },
            colors = listOf(Color.hsv(h, 0f, v), Color.hsv(h, 1f, v)),
            valueRange = 0f..1f
        )

        Spacer(Modifier.height(8.dp))

        GradientSlider(
            value = v,
            onValueChange = {
                v = it
                onColorChange(Color.hsv(h, s, v))
            },
            colors = listOf(Color.Black, Color.hsv(h, s, 1f)),
            valueRange = 0f..1f
        )
    }
}

@Composable
private fun GradientSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    colors: List<Color>,
    valueRange: ClosedFloatingPointRange<Float>
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .clip(RoundedCornerShape(8.dp))
        ) {
            drawRect(brush = Brush.horizontalGradient(colors))
        }
        
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            ),
            modifier = Modifier.fillMaxWidth()
        )
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
                Icon(Icons.Default.ArrowBack, "返回", tint = MaterialTheme.colorScheme.onBackground)
            }
            Text(
                "均衡器",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.height(16.dp))
        EqualizerScreen()
    }
}

private fun formatMs(ms: Long): String {
    if (ms <= 0) return "00:00"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%02d:%02d".format(m, s)
}
