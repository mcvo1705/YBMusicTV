package com.ybmusic.tv

import android.app.Application
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.ybmusic.tv.data.youtube.NewPipeDownloader
import com.ybmusic.tv.ui.MainViewModel
import com.ybmusic.tv.ui.navigation.AppNavigation
import com.ybmusic.tv.ui.theme.YBMusicTVTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import org.schabi.newpipe.extractor.NewPipe

// ── Application ───────────────────────────────────────────────────────────────

@HiltAndroidApp
class YBMusicApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Khởi tạo NewPipe với OkHttp downloader
        NewPipe.init(NewPipeDownloader.instance())
    }
}

// ── MainActivity ──────────────────────────────────────────────────────────────

/**
 * Entry point. Hai điều quan trọng cho Android TV ở đây:
 *
 * 1. KHÔNG can thiệp dispatchKeyEvent / focus search ở Activity level trừ khi
 *    thật sự cần — để Compose tự quản lý focus tree của nó. Việc Activity và
 *    Compose tranh nhau xử lý DPAD là nguyên nhân phổ biến của lỗi
 *    "parameter must be a descendant of this view".
 *
 * 2. setContent chỉ gọi 1 lần, không có state nào ở Activity level điều khiển
 *    việc render lại toàn bộ tree — tránh re-compose làm mất focus đang giữ.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        vm.player.connect()

        setContent {
            YBMusicTVTheme {
                AppNavigation(vm = vm)
            }
        }
    }

    /**
     * Một số TV box (bao gồm dòng Xiaomi) gửi cả KEYCODE_DPAD_CENTER và
     * KEYCODE_ENTER cho nút OK trên remote. Để tránh trường hợp event bị
     * Activity "ăn" trước khi tới Compose focus system, ta để hành vi mặc
     * định của ComponentActivity xử lý — không override gì thêm.
     *
     * Nếu sau này cần bắt phím cứng (BACK, MEDIA_PLAY_PAUSE từ remote vật lý)
     * thì override ở đây, KHÔNG override generic key navigation (DPAD_UP/DOWN/
     * LEFT/RIGHT) vì đó là việc của Compose FocusManager.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        vm.player.disconnect()
        super.onDestroy()
    }
}
