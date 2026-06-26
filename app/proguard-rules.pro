# SMBJ 相关保留规则
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }

# Compose 相关
-dontwarn androidx.compose.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.** { *; }
