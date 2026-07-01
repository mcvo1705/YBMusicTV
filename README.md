# YB Music TV — Android TV App

Phiên bản Android TV native của YBMusic, viết bằng Kotlin + Jetpack Compose for TV.
Không cần server trung gian — lấy stream URL YouTube trực tiếp qua **NewPipe Extractor**.
## Screenshot

<p align="center">
  <img width="1920" height="1080" alt="tv" src="https://github.com/user-attachments/assets/ce42bc43-7e1c-4bb7-938c-ebea817234ea" />
  <img width="1920" height="1080" alt="screen" src="https://github.com/user-attachments/assets/8f49e7fe-ca08-4021-92d3-0366df1788f4" />

</p>

---

## Kiến trúc

```
YBMusicTV/
├── build.gradle.kts                            — Khai báo version các plugin (root)
├── settings.gradle.kts                         — Module + repository (google, mavenCentral, jitpack)
├── buildSrc/
│   └── src/main/kotlin/com/ybmusic/tv/build/
│       └── NewPipeUrlEncoderFix.kt             — Bytecode fix URLEncoder/URLDecoder cho NewPipe
│                                                  trên Android < API 33 (qua ASM instrumentation)
└── app/
    ├── build.gradle.kts                        — Plugins, dependencies, desugaring, instrumentation
    └── src/main/java/com/ybmusic/tv/
        ├── MainActivity.kt                     — Entry point
        ├── core/
        │   ├── player/
        │   │   └── PlayerController.kt         — Singleton quản lý ExoPlayer state
        │   └── util/
        │       └── FormatUtil.kt               — Helper format thời gian, bitrate, v.v.
        ├── data/
        │   ├── database/
        │   │   └── AppDatabase.kt              — Room DB + DAOs (playlist offline)
        │   ├── model/
        │   │   └── Track.kt                    — Data class Track
        │   ├── repository/
        │   │   ├── MusicRepository.kt          — Tầng data duy nhất ViewModel gọi
        │   │   └── SettingsRepository.kt       — DataStore: lưu cài đặt người dùng
        │   └── youtube/
        │       ├── NewPipeDownloader.kt        — OkHttp adapter cho NewPipe Extractor
        │       └── YouTubeSource.kt            — Search, resolve stream URL, cache, fallback SABR
        ├── di/
        │   └── AppModule.kt                    — Hilt DI providers
        ├── service/
        │   └── PlaybackService.kt              — Media3 foreground service (background audio)
        └── ui/
            ├── MainViewModel.kt                — Shared state toàn app
            ├── component/
            │   └── TvComponents.kt             — TrackCard, MiniPlayer, buttons (D-pad focus)
            ├── navigation/
            │   └── AppNavigation.kt            — NavHost + sidebar nav TV-style
            ├── screen/
            │   ├── SearchScreen.kt             — Tìm kiếm + phát nhạc
            │   ├── LibraryScreen.kt            — Quản lý playlist offline
            │   └── SettingsScreen.kt           — Màn hình cài đặt
            └── theme/
                └── Theme.kt                    — Colors, typography TV-optimized
```

---

## Stack công nghệ

| Thành phần | Thư viện | Lý do chọn |
|---|---|---|
| UI | Jetpack Compose + TV Material (`androidx.tv:tv-material`) | D-pad navigation, focus management |
| Stream URL | NewPipe Extractor v0.26.3 | Không cần server, hoạt động như NewPipe app; bản này có workaround cho YouTube SABR enforcement |
| Audio player | Media3 ExoPlayer 1.5.0 (+ DASH/HLS) | Background playback, MediaSession cho remote |
| DI | Hilt 2.52 | Standard Android DI |
| Database | Room 2.6.1 | Lưu playlist offline |
| Settings | DataStore Preferences 1.1.1 | Lưu cài đặt người dùng |
| Network | OkHttp 4.12.0 | Adapter downloader cho NewPipe |
| Image | Coil | Async thumbnail loading |
| Serialization | Gson | — |
| Bytecode fix | ASM (qua buildSrc + AGP Instrumentation API) | Vá `URLEncoder`/`URLDecoder` overload mới của NewPipe để chạy được trên Android cũ |

---

## Build & cài đặt

### Yêu cầu
- Android Studio Hedgehog (2023.1.1) trở lên
- JDK 17
- Android SDK 35 (compileSdk/targetSdk), minSdk 23

### Bước 1: Clone và mở project
```bash
git clone <repo-url> YBMusicTV
# File > Open > chọn thư mục YBMusicTV trong Android Studio
```

### Bước 2: Sync Gradle
Android Studio sẽ tự download dependencies (AGP, Kotlin, Compose BOM, NewPipe Extractor qua JitPack...). Cần internet lần đầu.

### Bước 3: Build APK
```
Build > Build Bundle(s) / APK(s) > Build APK(s)
```
Hoặc qua CLI:
```bash
./gradlew assembleDebug
```
File APK ở: `app/build/outputs/apk/debug/app-debug.apk`

(Project cũng build qua GitHub Actions CI — push code, Actions tự build APK.)

### Bước 4: Cài lên Xiaomi A43 Pro (Android TV) và Sony sản xuất 2019 => run smoothly...
**Cách 1: ADB (khuyến nghị)**
```bash
# Bật Developer Options trên TV: Settings > About > click Build Number 7 lần
# Bật USB Debugging hoặc ADB over Network
adb connect <IP_TV>:5555
adb install app-debug.apk
```

**Cách 2: USB Drive**
- Copy APK ra USB
- Cắm vào TV, dùng File Manager cài đặt

---

## Tính năng

- **Tìm kiếm YouTube** — gõ tên bài hoặc dán link video/playlist
- **Phát audio chất lượng cao** — opus/mp4a, không load video tiết kiệm băng thông
- **Fallback khi YouTube siết SABR** — nếu không còn audio stream riêng, tự fallback sang video progressive (muxed) để vẫn phát được nhạc
- **Background playback** — audio tiếp tục khi tắt màn hình TV (Media3 foreground service)
- **Điều khiển remote** — D-pad navigation đầy đủ, nút play/pause/next trên remote
- **Playlist offline** — tạo, xoá, thêm/bỏ bài, lưu trong Room database
- **Cài đặt người dùng** — lưu qua DataStore (màn hình Settings riêng)
- **Cache stream URL** — tránh gọi lại NewPipe Extractor liên tục cho cùng 1 video

---

## Lưu ý quan trọng

1. **Cần internet** — app stream trực tiếp từ YouTube, không download
2. **NewPipe Extractor có thể bị YouTube thay đổi/giới hạn bất ngờ** — các lỗi như `ContentNotAvailableException`, "No progressive audio stream", hoặc lỗi 429 (Too Many Requests) thường do YouTube đổi cách trả response phía họ. Theo dõi release của [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor/releases) để cập nhật version trong `app/build.gradle.kts` khi cần
3. **Không có quảng cáo** — NewPipe bypass được quảng cáo YouTube
4. **Chỉ stream audio** — không load video (trừ khi rơi vào fallback SABR), tiết kiệm băng thông so với xem YouTube thường
5. **Android cũ trên TV box** — `NewPipeUrlEncoderFix` (trong `buildSrc/`) vá lại lời gọi `URLEncoder`/`URLDecoder` của NewPipe bằng bytecode instrumentation, để chạy được trên Android TV box dùng API thấp hơn 33 (vd Xiaomi A43 Pro chạy Android 13 vẫn cần fix này do core library desugaring không backport overload đó)
