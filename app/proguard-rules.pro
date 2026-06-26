# SMBJ 相关保留规则
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }

# Compose 相关
-dontwarn androidx.compose.**

# Kotlin 协程
-keepnames class kotlinx.coroutines.** { *; }

# 忽略 R8 缺少类警告
-ignorewarnings

# SMBJ 依赖的类
-dontwarn javax.el.**
-dontwarn net.engio.mbassy.**
-dontwarn org.eclipse.collections.**
-dontwarn javax.annotation.**
-dontwarn org.apache.commons.**
-dontwarn sun.misc.**
-dontwarn javax.naming.**
-dontwarn javax.sql.**
-dontwarn org.xmlpull.**
-dontwarn org.slf4j.**

# 保留 SMBJ 相关的反射类
-keep class net.engio.mbassy.** { *; }
-keep class javax.el.** { *; }
