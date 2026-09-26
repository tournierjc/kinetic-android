# Wire.kt reads JSON field by field through Gson's JsonObject, so no reflective
# model needs keeping. Only the entry points the manifest and Compose reach by
# name have to survive R8.
-keepattributes *Annotation*

# OkHttp/Okio ship their own consumer rules; Gson's are bundled with the artifact.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
