-optimizationpasses 8
-dontobfuscate
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
	public static void checkExpressionValueIsNotNull(...);
	public static void checkNotNullExpressionValue(...);
	public static void checkReturnedValueIsNotNull(...);
	public static void checkFieldIsNotNull(...);
	public static void checkParameterIsNotNull(...);
	public static void checkNotNullParameter(...);
}

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn coil3.PlatformContext

-keep class io.github.landwarderer.futon.settings.NotificationSettingsLegacyFragment
-keep class io.github.landwarderer.futon.settings.about.changelog.ChangelogFragment

-keep class io.github.landwarderer.futon.core.exceptions.* { *; }
-keep class io.github.landwarderer.futon.core.prefs.ScreenshotsPolicy { *; }
-keep class io.github.landwarderer.futon.backups.ui.periodical.PeriodicalBackupSettingsFragment { *; }
-keep class org.jsoup.parser.Tag
-keep class org.jsoup.internal.StringUtil

# Mihon / Tachiyomi extension support.
# Dynamically loaded extension APKs call this host surface from outside R8's static graph.
-keepattributes Signature
-keepattributes Annotation
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault
-keepattributes kotlin.Metadata

-keep class eu.kanade.tachiyomi.** { *; }
-keep interface eu.kanade.tachiyomi.** { *; }
-keeppackagenames eu.kanade.tachiyomi.**
-keepclassmembers class eu.kanade.tachiyomi.** {
    public <init>(...);
    public protected *;
}

-keep class uy.kohesive.injekt.** { *; }
-keep interface uy.kohesive.injekt.** { *; }
-keeppackagenames uy.kohesive.injekt.**
-keepclassmembers class uy.kohesive.injekt.** { *; }

-keep class io.github.landwarderer.futon.mihon.** { *; }
-keeppackagenames io.github.landwarderer.futon.mihon.**
-keepclassmembers class io.github.landwarderer.futon.mihon.** {
    public <init>(...);
    public protected *;
}

# Dynamic class loading.
-keep class io.github.landwarderer.futon.mihon.ChildFirstPathClassLoader { *; }
-keep public class * extends dalvik.system.PathClassLoader { *; }
-keep public class * extends dalvik.system.BaseDexClassLoader { *; }
-keep class dalvik.system.** { *; }
-dontwarn dalvik.system.**

-keep class kotlin.Metadata { *; }
-keep public class * implements eu.kanade.tachiyomi.source.Source
-keep public class * implements eu.kanade.tachiyomi.source.SourceFactory

# OkHttp / Okio are part of the parent-owned extension runtime.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepclassmembers class okhttp3.** {
    public <init>(...);
}
-keep class okio.** { *; }
-keep interface okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Common extension libraries that may only be referenced from an external APK.
-keep class org.jsoup.** { *; }
-keep class rx.** { *; }
-keep interface rx.** { *; }
-dontwarn rx.**
-keep class io.reactivex.** { *; }
-keep interface io.reactivex.** { *; }
-dontwarn io.reactivex.**
-keep class com.google.gson.** { *; }

# QuickJS compatibility. Futon already ships the QuickJS runtime, but extension calls are dynamic.
-keep class app.cash.quickjs.** { *; }
-keep interface app.cash.quickjs.** { *; }
-keepclassmembers class app.cash.quickjs.** {
    public <init>(...);
    public protected *;
}
-keep class com.dokar.quickjs.** { *; }
-keep interface com.dokar.quickjs.** { *; }
-keepclassmembers class com.dokar.quickjs.** {
    public <init>(...);
    public protected *;
}

# kotlinx.serialization is frequently reached through generated serializers in extension APKs.
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class kotlinx.serialization.** { *; }
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keepclassmembers @kotlinx.serialization.Serializable class * {
    *** Companion;
}
-keepclassmembers class **$$serializer {
    *** INSTANCE;
}
-dontwarn kotlinx.serialization.**

# zstd-kmp resolves these classes from native code via JNI FindClass.
-keep class com.squareup.zstd.** { *; }
-keep interface com.squareup.zstd.** { *; }

# AndroidX Preference is ConfigurableSource's externally invoked ABI.
-keep class androidx.preference.** { *; }
-keep interface androidx.preference.** { *; }
-keepclassmembers class androidx.preference.** {
    public <init>(...);
    public protected *;
}

# Application/SharedPreferences are resolved directly through Injekt by extensions.
-keep class android.app.Application { *; }
-keepclassmembers class * extends android.app.Application {
    public <init>(...);
}
-keep class android.content.SharedPreferences { *; }
-keep interface android.content.SharedPreferences$** { *; }

# Kotlin runtime and reflection are part of the dynamic extension ABI.
-keep class kotlin.** { *; }
-keep interface kotlin.** { *; }
-dontwarn kotlin.**
-keep class kotlin.LazyKt** { *; }
-keep class kotlin.collections.CollectionsKt** { *; }
-keep class kotlin.sequences.SequencesKt** { *; }
-keep class kotlin.text.StringsKt** { *; }
-keep class kotlin.comparisons.ComparisonsKt** { *; }
-keep class kotlin.io.FilesKt** { *; }
-keep class kotlin.jvm.internal.** { *; }
-keep class kotlin.jvm.functions.** { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

-dontwarn uy.kohesive.injekt.**
-dontwarn eu.kanade.tachiyomi.**
