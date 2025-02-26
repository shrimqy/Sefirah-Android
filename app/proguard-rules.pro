# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep classes referenced by Apache SSHD
-dontwarn javax.management.**
-keep class javax.management.** { *; }

# Keep EdDSA crypto classes
-dontwarn net.i2p.crypto.eddsa.**
-keep class net.i2p.crypto.eddsa.** { *; }

# Keep SLF4J classes
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Keep Apache SSHD classes
-dontwarn org.apache.sshd.**
-keep class org.apache.sshd.** { *; }

# Keep Apache MINA classes
-dontwarn org.apache.mina.**
-keep class org.apache.mina.** { *; }

# Keep BouncyCastle classes
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }