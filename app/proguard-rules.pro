# Regras do R8 para o build de release do AiStack.

# Atributos que o Gson usa via reflexão (genéricos e anotações).
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Mensagens do protocolo (serializadas pelo Gson pelos nomes dos campos) e modelos.
-keep class br.com.amberwrite.aistack.core.relay.RelayProtocol$* { *; }
-keep class br.com.amberwrite.aistack.data.model.** { *; }
-keepclassmembers enum * { *; }

# Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# BouncyCastle (X25519/Ed25519/HKDF): o provedor é registrado por reflexão.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# OkHttp / Okio
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Tink (security-crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn com.google.api.client.http.**
-dontwarn org.joda.time.**

# ML Kit (leitor de QR)
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
