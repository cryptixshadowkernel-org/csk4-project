# WebRTC
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Socket.io
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Volley
-keep class com.android.volley.** { *; }

# Gson
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Room
-keep class androidx.room.** { *; }

# App models
-keep class com.csk4.app.** { *; }
-dontwarn com.csk4.app.**
