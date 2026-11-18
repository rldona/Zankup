# R8 in release builds (full mode) drops generic signatures it cannot see used, and Retrofit reads them
# at run time: without these, `suspend fun getHealth(): Response<Health>` becomes Response<Object> and every
# call fails with "Unable to create converter for class java.lang.Object".
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep,allowobfuscation interface com.zankup.android.api.endpoints.** { *; }
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
# The models are only named in those signatures, which R8 does not count as a use: kept, or the
# signature would point at a removed class and read as Object again.
-keep,allowobfuscation class com.zankup.android.api.model.** { *; }
