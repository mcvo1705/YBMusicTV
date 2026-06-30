package com.ybmusic.tv.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ybmusic.tv.core.util.formatDuration
import com.ybmusic.tv.data.model.PlayMode
import com.ybmusic.tv.data.model.PlayerState
import com.ybmusic.tv.data.model.Track
import com.ybmusic.tv.ui.theme.*

// ─── TrackCard ────────────────────────────────────────────────────────────────

/**
 * Chỉ MỘT focus target duy nhất cho mỗi item: .clickable đã tự thêm item vào
 * focus tree (nó bao gồm focusable bên trong). TRƯỚC đây Row vừa .focusable()
 * vừa .clickable() → tạo HAI focus node chồng lên nhau cùng vùng, khiến DPAD
 * phải nhấn 2 lần mới qua được item kế và highlight (.onFocusChanged) lệch pha
 * so với item đang thực sự focus. Bỏ .focusable() thừa, đặt .onFocusChanged()
 * NGAY TRƯỚC .clickable() để bám đúng trạng thái focus của node clickable.
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

    // Selection effect kiểu SmartTube / YouTube TV: nền + viền + phóng nhẹ khi
    // focus, đổi mượt qua animateColorAsState/animateFloatAsState thay vì nhảy
    // tức thời. scale chỉ ~1.02 để hàng không đè lên nhau.
    val targetBg = when {
        isPlaying -> Purple.copy(alpha = 0.20f)
        focused   -> BgVariant
        else      -> Color.Transparent
    }
    val bg     by animateColorAsState(targetBg, tween(150), label = "trackBg")
    val border by animateColorAsState(
        if (focused) Purple else Color.Transparent, tween(150), label = "trackBorder",
    )
    val scale  by animateFloatAsState(if (focused) 1.02f else 1f, tween(150), label = "trackScale")

    Row(
        modifier = modifier
            .fillMaxWidth()
            // zIndex để item đang focus (phóng to) vẽ đè lên hàng kế, không bị cắt.
            .zIndex(if (focused) 1f else 0f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, border, RoundedCornerShape(10.dp))
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
            TvAsyncImage(
                url = track.thumbnailUrl,
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

        // Add to playlist — chỉ hiện khi item đang focus; TvIconBtn tự là một
        // focus target (qua .clickable) nên DPAD_RIGHT sẽ tới được nút này.
        if (focused && onAddToPlaylist != null) {
            TvIconBtn(Icons.Default.PlaylistAdd, onAddToPlaylist, tint = Purple)
        }
    }
}

// ─── MiniPlayer ───────────────────────────────────────────────────────────────

/**
 * focusGroup() ở Row chứa các nút điều khiển gom chúng thành một nhóm focus,
 * giúp Compose biết thứ tự traversal khi nhấn DPAD_LEFT/RIGHT giữa các nút
 * mode/prev/play-pause/next. Mỗi nút chỉ có MỘT focus target (qua .clickable),
 * không chồng thêm .focusable() — tránh lệch highlight và phải nhấn 2 lần.
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
            // Khi đang buffer chưa biết thời lượng → thanh chạy vô hạn để báo
            // "đang tải"; khi đã phát được → thanh tiến trình theo vị trí.
            if (state.isBuffering && state.durationMs <= 0) {
                LinearProgressIndicator(
                    modifier   = Modifier.fillMaxWidth(),
                    color      = Purple,
                    trackColor = BgVariant,
                )
            } else {
                LinearProgressIndicator(
                    progress    = { progress },
                    modifier    = Modifier.fillMaxWidth(),
                    color       = Purple,
                    trackColor  = BgVariant,
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Art — không tương tác
                TvAsyncImage(
                    url = track.thumbnailUrl,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                )

                // Title — không tương tác. Khi có lỗi phát/trích xuất thì hiện lý do
                // (màu error) thay cho tên tác giả để người dùng biết vì sao không phát.
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.titleSmall, color = TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.error != null) {
                        Text(state.error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(track.author, style = MaterialTheme.typography.bodySmall, color = TextMuted, maxLines = 1)
                    }
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

                    // Play / Pause — big circle. .onFocusChanged trước .clickable để
                    // highlight bám đúng focus của node clickable (một focus target).
                    var playPauseFocused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (playPauseFocused) PurpleDim else Purple)
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
 * Wrapper chuẩn cho mọi icon button trên TV: MỘT focus target qua .clickable +
 * highlight màu Purple khi focused (.onFocusChanged đặt trước .clickable), để
 * người dùng remote luôn biết đang ở đâu — focus highlight là tín hiệu duy nhất
 * trên Android TV (không có con trỏ chuột).
 */
@Composable
fun TvIconBtn(icon: ImageVector, onClick: () -> Unit, tint: Color = TextPrimary) {
    var focused by remember { mutableStateOf(false) }
    val bg    by animateColorAsState(if (focused) BgVariant else Color.Transparent, tween(150), label = "iconBg")
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, tween(150), label = "iconScale")
    Box(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = if (focused) Purple else tint)
    }
}

// ─── TvAsyncImage ─────────────────────────────────────────────────────────────

/**
 * Ảnh thumbnail dùng chung: crossfade khi tải xong (mượt, không "nháy") +
 * placeholder/error là một ô màu nền thay vì khoảng trống trắng. Dùng cho cả
 * TrackCard và MiniPlayer để hành vi tải ảnh nhất quán.
 */
@Composable
fun TvAsyncImage(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(url)
            .crossfade(200)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = ColorPainter(BgVariant),
        error       = ColorPainter(BgVariant),
        modifier    = modifier,
    )
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
            Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Purple)) { Text("Thử lại") }
    }
}
