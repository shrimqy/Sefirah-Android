-dontobfuscate
-dontusemixedcaseclassnames
-verbose

-keepattributes *Annotation*

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers enum * {
    public static **[] values();
    public static **[] entries;
    public static ** valueOf(java.lang.String);
}

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

-keep class androidx.annotation.Keep

-keep @androidx.annotation.Keep class * {*;}

-keepclasseswithmembers class * {
    @androidx.annotation.Keep <methods>;
}

-keepclasseswithmembers class * {
    @androidx.annotation.Keep <fields>;
}

-keepclasseswithmembers class * {
    @androidx.annotation.Keep <init>(...);
}

-keep class javax.management.** { *; }

-keep class net.i2p.crypto.eddsa.** { *; }

-keep class org.slf4j.** { *; }

-keep class org.apache.sshd.** { *; }

-keep class org.apache.mina.** { *; }

-keep class org.bouncycastle.** { *; }

-keep class java.rmi.** { *; }

-keep class javax.security.** { *; }

-keep class org.apache.tomcat.jni.** { *; }

-keep class org.ietf.jgss.** { *; }

-keep class org.slf4j.impl.** { *; }

-dontwarn java.rmi.**
-dontwarn javax.security.**
-dontwarn org.apache.tomcat.jni.**
-dontwarn org.ietf.jgss.**
-dontwarn org.slf4j.impl.**
-dontwarn net.i2p.crypto.eddsa.**
-dontwarn javax.management.**