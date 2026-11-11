# =============================================================================
# ProGuard / R8 Rules cho Video Player Pro (Release Build)
# =============================================================================
-optimizationpasses 5
-allowaccessmodification

# 1. Loại bỏ hoàn toàn DebugProbesKt trong bản Release
-assumenosideeffects class kotlinx.coroutines.debug.internal.DebugProbesKt { *; }
-dontwarn kotlinx.coroutines.debug.**

# 2. Giữ các Data Class được parse bởi Gson (OTA & Vault Metadata)
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-keep @androidx.annotation.Keep class * { *; }
-keep class com.videoplayer.app.update.GitHubRelease { *; }
-keep class com.videoplayer.app.update.GitHubAsset { *; }
-keep class com.videoplayer.app.update.VersionMetadata { *; }
-keep class com.videoplayer.app.util.VaultEntryMeta { *; }
-keep class com.videoplayer.app.util.VaultIndexData { *; }

# 3. Media3 / ExoPlayer Keep Rules (Chống crash khi khởi tạo Renderer/Extractor bằng Reflection)
-keepclassmembers class * implements androidx.media3.common.Player { *; }
-keepclasseswithmembers class * {
    @androidx.media3.common.util.UnstableApi <methods>;
}
-dontnote androidx.media3.**
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.extractor.** { *; }
-keep class androidx.media3.datasource.** { *; }
-keep class androidx.media3.ui.** { *; }

# 4. OkHttp3 & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# 5. Kotlinx Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keep class com.videoplayer.app.data.local.VideoBookmark { *; }
-keep class com.videoplayer.app.data.local.WatchHistoryEntry { *; }
-keep class com.videoplayer.app.data.local.WatchExperienceStoreData { *; }