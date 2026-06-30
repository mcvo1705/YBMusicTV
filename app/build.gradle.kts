import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationScope
import com.ybmusic.tv.build.NewPipeUrlEncoderFix

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace  = "com.ybmusic.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ybmusic.tv"
        minSdk        = 23
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled   = false
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Bật desugaring: NewPipe Extractor gọi URLEncoder.encode(String, Charset)
        // — overload này chỉ tồn tại trong java.net.URLEncoder từ Android API 33.
        // Trên TV box chạy Android cũ hơn (ví dụ Android 9-11 phổ biến ở Android
        // TV box), runtime chỉ có overload cũ URLEncoder.encode(String, String)
        // → NoSuchMethodError khi search, dù minSdk Gradle set là 23 (Gradle
        // không tự kiểm tra việc gọi API mới hơn minSdk trong code thư viện bên
        // thứ ba). Desugaring "dịch" các lệnh gọi API Java 8+ về dạng tương thích
        // ngược, chạy được trên API thấp hơn mà không cần sửa code NewPipe.
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }

    lint {
        // NonNullableMutableLiveDataDetector (check "NullSafeMutableLiveData")
        // bị crash với lint đi kèm AGP 8.7.3 + Kotlin 2.0.21 K2 UAST
        // (IncompatibleClassChangeError / "Found class ... but interface was
        // expected"). Đây là lỗi của chính lint, không phải code app, nhưng nó
        // làm task lintVitalRelease fail và chặn assembleRelease. Tắt riêng
        // detector lỗi này để build release chạy được.
        disable += "NullSafeMutableLiveData"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/LICENSE-notice.md"
        }
    }
}

// Áp dụng bytecode fix cho NewPipe Utils (xem NewPipeUrlEncoderFix) trên mọi
// build type. InstrumentationScope.ALL để bao gồm cả class trong dependency.
androidComponents {
    onVariants { variant ->
        variant.instrumentation.transformClassesWith(
            NewPipeUrlEncoderFix::class.java,
            InstrumentationScope.ALL,
        ) {}
        variant.instrumentation.setAsmFramesComputationMode(
            FramesComputationMode.COPY_FRAMES,
        )
    }
}

dependencies {
    // ── Compose BOM ──────────────────────────────────────────────────────────
    val composeBom = platform("androidx.compose:compose-bom:2024.11.00")
    implementation(composeBom)

    // ── AndroidX core ────────────────────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // ── Compose ──────────────────────────────────────────────────────────────
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Compose for TV ───────────────────────────────────────────────────────
    implementation("androidx.tv:tv-foundation:1.0.0")
    implementation("androidx.tv:tv-material:1.0.0")

    // ── Navigation ───────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // ── Media3 / ExoPlayer ───────────────────────────────────────────────────
    implementation("androidx.media3:media3-exoplayer:1.5.0")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.0")
    implementation("androidx.media3:media3-session:1.5.0")
    implementation("androidx.media3:media3-ui:1.5.0")
    implementation("androidx.media3:media3-common:1.5.0")

    // ── Hilt ─────────────────────────────────────────────────────────────────
    implementation("com.google.dagger:hilt-android:2.52")
    ksp("com.google.dagger:hilt-android-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Room ─────────────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── Coroutines ───────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.9.0")

    // ── Network ──────────────────────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // ── Image loading ────────────────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.7.0")

    // ── NewPipe Extractor ────────────────────────────────────────────────────
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.2")

    // ── DataStore (Settings sprint) ──────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // ── Gson ─────────────────────────────────────────────────────────────────
    implementation("com.google.code.gson:gson:2.11.0")

    // ── Core library desugaring runtime ──────────────────────────────────────
    // Bắt buộc phải có dependency này khi isCoreLibraryDesugaringEnabled = true,
    // nếu không Gradle sẽ báo lỗi thiếu desugar runtime khi build.
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")
}
