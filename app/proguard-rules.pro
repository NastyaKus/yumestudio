# JNI-мост
-keep class com.yume.studio.audio.NativeEngine { *; }
# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.yume.studio.model.** { *** Companion; *; }
-keep,includedescriptorclasses class com.yume.studio.model.**$$serializer { *; }
