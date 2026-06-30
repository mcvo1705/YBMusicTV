##── NewPipe Extractor ────────────────────────────────────────────────────────
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

##── Rhino (org.mozilla.javascript) ───────────────────────────────────────────
# NewPipe dùng Rhino để giải mã chữ ký stream của YouTube. Rhino tham chiếu
# java.beans.* — package này không tồn tại trên Android, nên R8 báo "Missing
# class" và fail minifyRelease. Các tham chiếu này chỉ chạy trên JVM desktop,
# không bao giờ được gọi trên Android, nên an toàn khi -dontwarn.
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn java.beans.**

##── Media3 / ExoPlayer ───────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

##── OkHttp ───────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

##── Gson ─────────────────────────────────────────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

##── Hilt / Dagger ────────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

##── Kotlin coroutines ────────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

##── Core library desugaring runtime ──────────────────────────────────────────
# desugar_jdk_libs backport các API Java 8+ (java.time, streams, ...) vào package
# j$.**. Giữ lại để R8/minify không strip runtime desugar.
# Lưu ý: desugar_jdk_libs KHÔNG backport java.net.URLEncoder/URLDecoder, nên lỗi
# NoSuchMethodError của NewPipe được xử lý riêng bằng bytecode transform
# (NewPipeUrlEncoderFix), không phải bằng desugaring.
-keep class j$.** { *; }
-dontwarn j$.**
