package com.ybmusic.tv.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.ybmusic.tv.ui.MainViewModel
import com.ybmusic.tv.ui.component.MiniPlayer
import com.ybmusic.tv.ui.screen.LibraryScreen
import com.ybmusic.tv.ui.screen.SearchScreen
import com.ybmusic.tv.ui.screen.SettingsScreen
import com.ybmusic.tv.ui.theme.*

sealed class Dest(val route: String, val label: String, val icon: ImageVector) {
    data object Search   : Dest("search",   "Tìm kiếm", Icons.Default.Search)
    data object Library  : Dest("library",  "Thư viện", Icons.Default.LibraryMusic)
    data object Settings : Dest("settings", "Cài đặt",  Icons.Default.Settings)
}

private val navItems = listOf(Dest.Search, Dest.Library, Dest.Settings)

/**
 * Root TV UI. Không dùng NavigationRail (pattern mobile/tablet, focus system
 * không ổn định với DPAD trên Android TV — gây "parameter must be a descendant
 * of this view" / performFocusNavigation crash).
 *
 * Thay bằng Row + Column tự code, có focusGroup() rõ ràng và focus mặc định
 * được set qua FocusRequester ngay khi vào màn hình — đúng pattern YouTube TV /
 * Android TV apps chuẩn.
 */
@Composable
fun AppNavigation(vm: MainViewModel) {
    val nav         = rememberNavController()
    val playerState by vm.playerState.collectAsState()

    val firstItemFocusRequester = remember { FocusRequester() }

    // Set focus vào item sidebar đầu tiên ngay khi UI render lần đầu.
    // Đây là điều bắt buộc trên Android TV: nếu không có gì được focus,
    // remote DPAD sẽ không biết điều hướng đi đâu → crash hoặc đứng UI.
    LaunchedEffect(Unit) {
        firstItemFocusRequester.requestFocus()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(BgDeep)
            // focusGroup() khai báo rõ đây là 1 vùng focus có cấu trúc,
            // giúp Compose focus traversal không bị "lạc" giữa sidebar và content.
            .focusGroup(),
    ) {

        Row(Modifier.weight(1f)) {

            // ── TV Sidebar (tự code, không dùng NavigationRail) ─────────────────
            TvSidebar(
                navController = nav,
                firstItemFocusRequester = firstItemFocusRequester,
            )

            // ── Content ───────────────────────────────────────────────────────
            NavHost(
                navController    = nav,
                startDestination = Dest.Search.route,
                modifier         = Modifier.weight(1f).fillMaxHeight(),
                enterTransition  = { fadeIn() },
                exitTransition   = { fadeOut() },
            ) {
                composable(Dest.Search.route)   { SearchScreen(vm,  Modifier.fillMaxSize()) }
                composable(Dest.Library.route)  { LibraryScreen(vm, Modifier.fillMaxSize()) }
                composable(Dest.Settings.route) { SettingsScreen(vm, Modifier.fillMaxSize()) }
            }
        }

        // ── Mini player ───────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = playerState.currentTrack != null,
            enter   = slideInVertically { it } + fadeIn(),
            exit    = slideOutVertically { it } + fadeOut(),
        ) {
            MiniPlayer(
                state       = playerState,
                onPlayPause = vm::togglePlayPause,
                onNext      = vm::playNext,
                onPrev      = vm::playPrev,
                onCycleMode = vm::cyclePlayMode,
            )
        }
    }
}

/**
 * Sidebar tự code thay cho NavigationRail.
 * Mỗi item là 1 Row có .focusable() rõ ràng + key handler riêng (Enter/DPAD_CENTER
 * để chọn) — đúng pattern Android TV thay vì dựa vào ripple/click của Material
 * components (vốn build cho touch, không cho remote).
 */
@Composable
private fun TvSidebar(
    navController: androidx.navigation.NavHostController,
    firstItemFocusRequester: FocusRequester,
) {
    val backStack by navController.currentBackStackEntryAsState()
    val current    = backStack?.destination

    Column(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight()
            .background(BgCard)
            .padding(vertical = 20.dp, horizontal = 12.dp)
            .focusGroup(),
    ) {
        Icon(
            Icons.Default.MusicNote, null,
            tint = Purple,
            modifier = Modifier.padding(horizontal = 8.dp).size(28.dp),
        )

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = BgVariant)
        Spacer(Modifier.height(12.dp))

        navItems.forEachIndexed { index, dest ->
            val selected = current?.route == dest.route

            SidebarItem(
                label    = dest.label,
                icon     = dest.icon,
                selected = selected,
                modifier = if (index == 0) {
                    Modifier.focusRequester(firstItemFocusRequester)
                } else {
                    Modifier
                },
                onSelect = {
                    navController.navigate(dest.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState    = true
                    }
                },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    val bg = when {
        focused  -> Purple.copy(alpha = 0.25f)
        selected -> Purple.copy(alpha = 0.12f)
        else     -> androidx.compose.ui.graphics.Color.Transparent
    }
    val borderColor = if (focused) Purple else androidx.compose.ui.graphics.Color.Transparent

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(2.dp, borderColor, RoundedCornerShape(10.dp))
            // focusable() là bắt buộc — không có nó, item này không nằm trong
            // focus tree và DPAD sẽ nhảy qua, gây crash performFocusNavigation
            // khi Compose không tìm được next focus target hợp lệ.
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            // Bắt cả remote select key (Enter / DPAD_CENTER) và click thường
            // (trường hợp dùng chuột/touch khi test trên emulator).
            .onKeyEvent { e ->
                if ((e.key == Key.Enter || e.key == Key.DirectionCenter) && e.type == KeyEventType.KeyUp) {
                    onSelect(); true
                } else false
            }
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon, null,
            tint = if (focused || selected) Purple else TextMuted,
            modifier = Modifier.size(22.dp),
        )
        Text(
            label,
            color = if (focused || selected) Purple else TextPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
