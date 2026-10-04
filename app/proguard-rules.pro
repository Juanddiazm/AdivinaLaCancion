# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class com.guesssong.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.guesssong.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.guesssong.app.**$$serializer { *; }
