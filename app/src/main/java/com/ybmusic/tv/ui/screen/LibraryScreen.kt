package com.ybmusic.tv.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.*
import com.ybmusic.tv.data.model.Playlist
import com.ybmusic.tv.ui.MainViewModel
import com.ybmusic.tv.ui.component.TrackCard
import com.ybmusic.tv.ui.theme.*

/**
 * Sidebar (danh sách playlist) và content (track list) là hai focusGroup()
 * riêng biệt — giống cấu trúc AppNavigation. Điều này quan trọng vì đây là
 * 1 trong 3 screen con được liệt kê là bắt buộc phải có focus tree hợp lệ.
 */
@Composable
fun LibraryScreen(vm: MainViewModel, modifier: Modifier = Modifier) {
    val playlists   by vm.playlists.collectAsState()
    val selectedId  by vm.selectedPlaylistId.collectAsState()
    val tracks      by vm.selectedTracks.collectAsState()
    val playerState by vm.playerState.collectAsState()

    var showCreate by remember { mutableStateOf(false) }

    if (showCreate) {
        CreateDialog(
            onConfirm = { vm.createPlaylist(it); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }

    Row(modifier = modifier.fillMaxSize().background(BgDeep)) {

        // ── Sidebar: danh sách playlist ──────────────────────────────────────
        Column(
            Modifier
                .width(260.dp)
                .fillMaxHeight()
                .background(BgCard)
                .padding(16.dp)
                .focusGroup(),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Thư viện", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                IconButton(onClick = { showCreate = true }) {
                    Icon(Icons.Default.Add, "Tạo mới", tint = Purple)
                }
            }
            Spacer(Modifier.height(12.dp))

            if (playlists.isEmpty()) {
                Text("Chưa có playlist.\nNhấn + để tạo.", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(playlists, key = { _, p -> p.id }) { _, pl ->
                        PlaylistItem(
                            pl       = pl,
                            selected = pl.id == selectedId,
                            onClick  = { vm.selectPlaylist(pl.id) },
                            onDelete = { vm.deletePlaylist(pl.id) },
                        )
                    }
                }
            }
        }

        // ── Content: track list ───────────────────────────────────────────────
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                selectedId == null -> CenterHint(Icons.Default.QueueMusic, "Chọn playlist bên trái")
                tracks.isEmpty()   -> CenterHint(Icons.Default.LibraryAdd, "Playlist trống")
                else -> Column(
                    Modifier.fillMaxSize().padding(24.dp).focusGroup(),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${tracks.size} bài", color = TextMuted, style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.playAll(tracks) },
                                colors = ButtonDefaults.buttonColors(containerColor = Purple),
                            ) {
                                Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Phát tất cả")
                            }
                            OutlinedButton(
                                onClick = { vm.playShuffle(tracks) },
                                border = ButtonDefaults.outlinedButtonBorder,
                            ) {
                                Icon(Icons.Default.Shuffle, null, tint = Purple, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp)); Text("Ngẫu nhiên", color = Purple)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        itemsIndexed(tracks, key = { _, t -> t.id }) { idx, track ->
                            TrackCard(
                                track    = track,
                                isPlaying = playerState.currentTrack?.id == track.id,
                                onPlay   = { vm.playAll(tracks, idx) },
                                onAddToPlaylist = { selectedId?.let { vm.removeTrack(it, track.id) } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistItem(pl: Playlist, selected: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            containerColor   = BgCard,
            title = { Text("Xoá playlist?", color = TextPrimary) },
            text  = { Text("\"${pl.name}\" sẽ bị xoá vĩnh viễn.", color = TextMuted) },
            confirmButton = {
                Button(
                    onClick = { onDelete(); confirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Xoá") }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Huỷ", color = TextMuted) } },
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Purple.copy(alpha = 0.25f) else if (selected) Purple.copy(alpha = 0.18f) else BgVariant)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.QueueMusic, null, tint = if (selected || focused) Purple else TextMuted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(pl.name, color = if (selected || focused) Purple else TextPrimary, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1)
        IconButton(onClick = { confirm = true }, modifier = Modifier.size(28.dp)) {
            Icon(Icons.Default.DeleteOutline, null, tint = TextMuted.copy(alpha = 0.4f), modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun CreateDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = BgCard,
        title = { Text("Tạo playlist mới", color = TextPrimary) },
        text  = {
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                placeholder = { Text("Tên playlist…", color = TextMuted) },
                singleLine  = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Purple, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary),
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = Purple),
            ) { Text("Tạo") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Huỷ", color = TextMuted) } },
    )
}

@Composable
private fun CenterHint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = TextMuted.copy(alpha = 0.2f), modifier = Modifier.size(88.dp))
        Spacer(Modifier.height(12.dp))
        Text(text, color = TextMuted.copy(alpha = 0.4f), style = MaterialTheme.typography.titleMedium)
    }
}
