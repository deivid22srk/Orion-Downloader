-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.orion.downloader.**$$serializer { *; }
-keepclassmembers class com.orion.downloader.** {
    *** Companion;
}
-keepclasseswithmembers class com.orion.downloader.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep class com.orion.downloader.core.DownloadEngine {
    native <methods>;
}
