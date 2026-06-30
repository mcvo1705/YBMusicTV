package com.ybmusic.tv.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.ybmusic.tv.core.util.formatDuration
import com.ybmusic.tv.data.model.PlayMode
import com.ybmusic.tv.data.model.PlayerState
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.ui.theme.*

// ─── TrackCard ────────────────────────────────────────────────────────────────

/**
 * .focusable() ở Row gốc là bắt buộc: đây là item nằm trong LazyColumn, và
 * nếu không khai báo focusable rõ ràng, Compose có thể không đưa item này
 * vào focus tree khi danh sách rebuild (ví dụ sau khi search), khiến DPAD
 * "đứng" giữa danh sách — đúng loại lỗi performFocusNavigation đã gặp.
 */
@Composable
fun TrackCard(
    track: Track,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    onAddToPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        isPlaying -> Purple.copy(alpha = 0.20f)
        focused   -> BgVariant
        else      -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Thumbnail
        Box(
            Modifier.size(68.dp).clip(RoundedCornerShape(8.dp)),
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPlaying) {
                Box(
                    Modifier.fillMaxSize().background(Purple.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }

        // Info
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (isPlaying) Purple else TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(track.author, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
            if (track.durationSeconds > 0)
                Text(formatDuration(track.durationSeconds), style = MaterialTheme.typography.bodySmall, color = TextMuted.copy(alpha = 0.6f))
        }

        // Add to playlist — chỉ hiện khi focused, nhưng vẫn cần .focusable() riêng
        // vì nó là 1 target focus độc lập bên trong Row đã focusable (nested focus).
        if (focused && onAddToPlaylist != null) {
            TvIconBtn(Icons.Default.PlaylistAdd, onAddToPlaylist, tint = Purple)
        }
    }
}

// ─── MiniPlayer ───────────────────────────────────────────────────────────────

/**
 * focusGroup() ở Row chứa các nút điều khiển: đây chính là khu vực được liệt
 * kê là "hay gây crash focus nếu click remote" trong yêu cầu fix. Nguyên nhân
 * gốc là các IconButton/Box clickable không có .focusable() rõ ràng nằm cạnh
 * nhau — Compose không biết thứ tự traversal khi nhấn DPAD_LEFT/RIGHT giữa
 * các nút play/pause/next/prev. focusGroup() + .focusable() trên từng nút
 * giải quyết dứt điểm.
 */
@Composable
fun MiniPlayer(
    state: PlayerState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onCycleMode: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = state.currentTrack ?: return

    Surface(modifier = modifier.fillMaxWidth(), color = BgCard, tonalElevation = 8.dp) {
        Column {
            // Progress bar — không tương tác, không cần focusable
            val progress = if (state.durationMs > 0)
                (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress    = { progress },
                modifier    = Modifier.fillMaxWidth(),
                color       = Purple,
                trackColor  = BgVariant,
            )

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Art — không tương tác
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                )

                // Title — không tương tác
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.author, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
                }

                // Time — không tương tác
                Text(
                    "${formatDuration(state.positionMs / 1000)} / ${formatDuration(state.durationMs / 1000)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )

                // ── Controls: nhóm focus riêng ───────────────────────────────────
                Row(
                    modifier = Modifier.focusGroup(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvIconBtn(
                        icon = when (state.playMode) {
                            PlayMode.SEQUENTIAL -> Icons.Default.List
                            PlayMode.SHUFFLE    -> Icons.Default.Shuffle
                            PlayMode.REPEAT_ONE -> Icons.Default.RepeatOne
                        },
                        onClick = onCycleMode,
                        tint = TextMuted,
                    )

                    TvIconBtn(Icons.Default.SkipPrevious, onPrev)

                    // Play / Pause — big circle, .focusable() rõ ràng thay vì chỉ
                    // dựa vào .clickable() (clickable không tự thêm vào focus tree
                    // một cách nhất quán trên mọi phiên bản Compose).
                    var playPauseFocused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (playPauseFocused) PurpleDim else Purple)
                            .focusable()
                            .onFocusChanged { playPauseFocused = it.isFocused }
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (state.isBuffering) {
                            CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(
                                if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                    }

                    TvIconBtn(Icons.Default.SkipNext, onNext)
                }
            }
        }
    }
}

// ─── TvIconBtn ────────────────────────────────────────────────────────────────

/**
 * Wrapper chuẩn cho mọi icon button trên TV: luôn có .focusable() rõ ràng +
 * highlight màu Purple khi focused, để người dùng remote luôn biết đang ở
 * đâu trên màn hình — nguyên tắc UX bắt buộc của Android TV (không có con trỏ
 * chuột nên focus highlight là tín hiệu duy nhất).
 */
@Composable
fun TvIconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = TextPrimary) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (focused) BgVariant else Color.Transparent)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (focused) Purple else tint)
    }
}

// ─── Shared helpers ───────────────────────────────────────────────────────────

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Purple)
    }
}

@Composable
fun ErrorBox(msg: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(msg, color = TextMuted, style = MaterialTheme.typography.bodyLarge)
        if (onRetry != null)
            Button(onClick = onRetry, modifier = Modifier.focusable(), colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Thử lại") }
    }
}
