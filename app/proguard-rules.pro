# R8 rules of the app (release builds are shrunk, see build.gradle.kts). The libraries bring
# their own rules (Room, Glide, Media3, Compose).

# Line numbers in stack traces (the log view shows them), without the source file names
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
