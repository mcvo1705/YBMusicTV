package com.ybmusic.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.ybmusic.tv.data.model.Playlist
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.data.model.UiState
import com.ybmusic.tv.ui.MainViewModel
import com.ybmusic.tv.ui.component.*
import com.ybmusic.tv.ui.theme.*

// Nạp trang kết quả kế tiếp khi còn cách cuối danh sách bấy nhiêu item.
private const val PREFETCH_AHEAD = 8

/**
 * Toàn bộ Column gốc dùng .focusGroup() — khai báo rõ đây là một vùng focus
 * có cấu trúc (search bar → toolbar → list), giúp Compose focus traversal
 * không "lạc" ra ngoài tree khi nhấn DPAD_DOWN từ ô search xuống danh sách.
 *
 * Không set FocusRequester ở đây cho ô search: focus mặc định khi vào màn
 * hình này do sidebar (AppNavigation) giữ, đúng hành vi YouTube TV — search
 * bar chỉ nhận focus khi người dùng chủ động bấm DPAD_RIGHT từ sidebar.
 */
@Composable
fun SearchScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val query         by vm.searchQuery.collectAsState()
    val searchState   by vm.searchState.collectAsState()
    val playerState   by vm.playerState.collectAsState()
    val playlists     by vm.playlists.collectAsState()
    val canLoadMore   by vm.canLoadMore.collectAsState()
    val isLoadingMore by vm.isLoadingMore.collectAsState()
    val focus         = LocalFocusManager.current

    var addTrack by remember { mutableStateOf<Track?>(null) }

    addTrack?.let { t ->
        AddToPlaylistDialog(
            track     = t,
            playlists = playlists,
            onAdd     = { pid -> vm.addToPlaylist(pid, t); addTrack = null },
            onCreate  = { name -> vm.createPlaylist(name); addTrack = null },
            onDismiss = { addTrack = null },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BgDeep)
            .padding(horizontal = 36.dp, vertical = 20.dp)
            .focusGroup(),
    ) {
        // Header
        Column {
            Text(
                "♪YB music♪",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                ),
                color = TextPrimary,
            )
            Text(
                "The app design and coding by mcvo1705",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMuted,
            )
        }

        Spacer(Modifier.height(20.dp))

        // Search bar — OutlinedTextField tự là một focus target; KHÔNG thêm
        // .focusable() (sẽ tạo focus node thứ hai khiến phải nhấn DPAD 2 lần và
        // focus "rỗng" không mở bàn phím).
        OutlinedTextField(
            value       = query,
            onValueChange = vm::onQueryChanged,
            modifier    = Modifier
                .fillMaxWidth()
                // TextField trên Android TV "bẫy" DPAD (tự tiêu thụ key để di chuyển
                // con trỏ), nên DPAD_DOWN không thoát xuống danh sách được. Dùng
                // onPreviewKeyEvent để chặn TRƯỚC: DPAD_DOWN → chuyển focus xuống
                // toolbar/list; Enter → tìm kiếm.
                .onPreviewKeyEvent { e ->
                    when {
                        e.type == KeyEventType.KeyDown && e.key == Key.DirectionDown -> {
                            focus.moveFocus(FocusDirection.Down); true
                        }
                        e.type == KeyEventType.KeyUp && e.key == Key.Enter -> {
                            vm.search(); focus.clearFocus(); true
                        }
                        else -> false
                    }
                },
            placeholder = { Text("Tìm bài hát hoặc dán link YouTube…", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Purple) },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (query.isNotEmpty())
                        IconButton(onClick = { vm.onQueryChanged("") }) {
                            Icon(Icons.Default.Clear, null, tint = TextMuted)
                        }
                    Button(
                        onClick = { vm.search(); focus.clearFocus() },
                        colors  = ButtonDefaults.buttonColors(containerColor = Purple),
                        modifier = Modifier.padding(end = 6.dp),
                    ) { Text("Tìm") }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search(); focus.clearFocus() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Purple,
                unfocusedBorderColor = BgVariant,
                focusedTextColor     = TextPrimary,
                unfocusedTextColor   = TextPrimary,
                cursorColor          = Purple,
                focusedContainerColor   = BgCard,
                unfocusedContainerColor = BgCard,
            ),
            shape     = RoundedCornerShape(12.dp),
            singleLine = true,
        )

        Spacer(Modifier.height(20.dp))

        Box(Modifier.weight(1f)) {
            when (val s = searchState) {
                is UiState.Idle    -> IdleHint()
                is UiState.Loading -> LoadingBox(Modifier.fillMaxSize())
                is UiState.Error   -> ErrorBox(s.message, onRetry = { vm.search() }, modifier = Modifier.fillMaxSize())
                is UiState.Success -> {
                    val tracks = s.data
                    if (tracks.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Không tìm thấy kết quả", color = TextMuted, style = MaterialTheme.typography.titleMedium)
                        }
                    } else {
                        // focusGroup() riêng cho khu vực kết quả: toolbar (Phát tất cả /
                        // Ngẫu nhiên) + LazyColumn cùng nằm trong 1 nhóm focus, để DPAD_DOWN
                        // từ toolbar đi thẳng vào item đầu tiên của list, không bị nhảy lung tung.
                        Column(Modifier.focusGroup()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${tracks.size} kết quả", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { vm.playAll(tracks) },
                                        border = ButtonDefaults.outlinedButtonBorder,
                                    ) {
                                        Icon(Icons.Default.PlayArrow, null, tint = Purple, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Phát tất cả", color = Purple)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.playShuffle(tracks) },
                                        border = ButtonDefaults.outlinedButtonBorder,
                                    ) {
                                        Icon(Icons.Default.Shuffle, null, tint = Purple, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ngẫu nhiên", color = Purple)
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Phân trang vô hạn: nạp trước trang kế tiếp khi người dùng
                            // còn cách cuối danh sách PREFETCH_AHEAD item, để trang mới
                            // kịp về trước khi cuộn tới đáy (cuộn mượt, không khựng).
                            val listState = rememberLazyListState()
                            LaunchedEffect(listState, canLoadMore, tracks.size) {
                                snapshotFlow {
                                    listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                                }.collect { lastVisible ->
                                    if (canLoadMore && lastVisible >= tracks.size - PREFETCH_AHEAD) vm.loadMore()
                                }
                            }

                            // LazyColumn tự quản lý focus traversal giữa các item của nó,
                            // miễn là mỗi item con (TrackCard) focusable — đã có trong
                            // TvComponents.kt. Không cần focusGroup() thêm ở đây.
                            LazyColumn(
                                state = listState,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                            ) {
                                itemsIndexed(
                                    tracks,
                                    key         = { _, t -> t.id },
                                    contentType = { _, _ -> "track" },
                                ) { index, track ->
                                    TrackCard(
                                        track    = track,
                                        isPlaying = playerState.currentTrack?.id == track.id,
                                        onPlay   = { vm.playAll(tracks, index) },
                                        onAddToPlaylist = if (playlists.isNotEmpty()) ({ addTrack = track }) else null,
                                    )
                                }
                                if (isLoadingMore) {
                                    item(key = "__loading_more__") {
                                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = Purple, modifier = Modifier.size(28.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleHint() {
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Default.LibraryMusic, null, tint = TextMuted.copy(alpha = 0.25f), modifier = Modifier.size(96.dp))
        Spacer(Modifier.height(16.dp))
        Text("Tìm bài hát hoặc dán link YouTube", color = TextMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun AddToPlaylistDialog(
    track: Track,
    playlists: List<Playlist>,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newName    by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = { Text("Thêm vào playlist", color = TextPrimary) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.focusGroup()) {
                Text("\"${track.title}\"", color = TextMuted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                HorizontalDivider(color = BgVariant)
                playlists.forEach { pl ->
                    TextButton(onClick = { onAdd(pl.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlaylistAdd, null, tint = Purple)
                        Spacer(Modifier.width(8.dp))
                        Text(pl.name, color = TextPrimary)
                    }
                }
                if (showCreate) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text("Tên playlist mới", color = TextMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Purple,
                            focusedTextColor   = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = { if (newName.isNotBlank()) onCreate(newName.trim()) },
                        colors  = ButtonDefaults.buttonColors(containerColor = Purple),
                    ) { Text("Tạo & thêm") }
                } else {
                    TextButton(onClick = { showCreate = true }) {
                        Icon(Icons.Default.Add, null, tint = Purple)
                        Spacer(Modifier.width(4.dp))
                        Text("Tạo playlist mới", color = Purple)
                    }
                }
            }
        },
        confirmButton  = {},
        dismissButton  = { TextButton(onClick = onDismiss) { Text("Đóng", color = TextMuted) } },
    )
}
