# ============================================================
#  Teika Fusion — ProGuard / R8 rules
# ============================================================

# Keep source file names and line numbers for Crashlytics stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all annotations (needed by Compose, serialization, Billing, etc.)
-keepattributes *Annotation*

# ── Kotlin ────────────────────────────────────────────────────
# Kotlin metadata (needed for reflection and serialization)
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$Companion { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── kotlinx.serialization ─────────────────────────────────────
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * {
    static kotlinx.serialization.KSerializer serializer(...);
    static ** $serializer;
    static synthetic ** access$*(...);
}
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ── Jetpack Compose ───────────────────────────────────────────
# R8 full mode strips some Compose internals; keep runtime essentials
-keep class androidx.compose.runtime.** { *; }
-keep interface androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ── AndroidX DataStore ────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ── AndroidX Lifecycle / ViewModel ───────────────────────────
-keep class androidx.lifecycle.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── Firebase Analytics ────────────────────────────────────────
-keep class com.google.firebase.analytics.** { *; }
-dontwarn com.google.firebase.analytics.**

# ── Firebase Crashlytics ──────────────────────────────────────
# Crashlytics needs these for proper deobfuscated stack traces
-keep class com.google.firebase.crashlytics.** { *; }
-dontwarn com.google.firebase.crashlytics.**
-keepattributes EnclosingMethod,InnerClasses

# ── Firebase Auth ─────────────────────────────────────────────
-keep class com.google.firebase.auth.** { *; }
-dontwarn com.google.firebase.auth.**

# ── Firebase Firestore ─────────────────────────────────────────
-keep class com.google.firebase.firestore.** { *; }
-dontwarn com.google.firebase.firestore.**

# ── Firebase Remote Config ─────────────────────────────────────
-keep class com.google.firebase.remoteconfig.** { *; }
-dontwarn com.google.firebase.remoteconfig.**

# ── Firebase Data Transport ────────────────────────────────────
# JobInfoSchedulerService se registra en el manifest y se inicia
# por nombre; si R8 lo renombra la app crashea al arrancar el servicio.
-keep class com.google.android.datatransport.** { *; }
-dontwarn com.google.android.datatransport.**

# ── Google AdMob / Mobile Ads ─────────────────────────────────
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# ── Google Play Billing v7 ────────────────────────────────────
-keep class com.android.billingclient.api.** { *; }
-dontwarn com.android.billingclient.**

# ── Lottie ────────────────────────────────────────────────────
-keep class com.airbnb.lottie.** { *; }
-dontwarn com.airbnb.lottie.**

# ── AndroidX Room ─────────────────────────────────────────────
# Room genera clases *_Impl que se cargan por reflexión (Room.getGeneratedImplementation).
# Sin estas reglas, R8 (full mode) las borra/renombra → crash al iniciar.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}
-dontwarn androidx.room.**

# ── AndroidX WorkManager ──────────────────────────────────────
# WorkManager se auto-inicializa vía androidx.startup en el arranque; su WorkDatabase_Impl
# (generado por Room) DEBE sobrevivir a R8 o la app crashea en el primer segundo.
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
# …y sus NOMBRES (2026-09-04). WorkManager guarda en su base de datos el nombre de la clase del
# worker, no una referencia: instancia por reflexión al disparar. La regla de arriba conserva el
# constructor pero deja que R8 RENOMBRE la clase, y el nombre obfuscado no tiene por qué ser el
# mismo entre dos versiones — así que un aviso programado por la versión N puede quedar apuntando a
# un nombre que en la N+1 ya es de otra clase, o de ninguna. El síntoma sería el peor posible: una
# notificación que no llega, sin error visible en ningún sitio.
-keepnames class * extends androidx.work.ListenableWorker
-dontwarn androidx.work.**

# ── AndroidX App Startup ──────────────────────────────────────
-keep class * extends androidx.startup.Initializer { <init>(); }
-keep class androidx.startup.** { *; }
-dontwarn androidx.startup.**

# ── SQLite (backend de Room) ──────────────────────────────────
-keep class androidx.sqlite.** { *; }
-dontwarn androidx.sqlite.**

# ── (RETIRADO 2026-09-04, P-180, decisión del usuario) ────────
# Aquí vivieron un día dos `-keepnames` sobre `GameEvent` y sus subclases. Nacieron como CORRECCIÓN:
# `MechanicCapsule` comparaba `javaClass.simpleName` contra literales como "KingWallRise", R8
# renombraba la clase —medido en el mapping: `GameEvent$KingWallRise -> og1`— y las ocho cápsulas de
# bioma no salían NUNCA en la app publicada mientras salían siempre en el móvil de desarrollo.
#
# P-176 les quitó el motivo: el impacto se reconoce ahora por TIPO (`BiomeRhythm.isImpact`), que el
# compilador comprueba y la ofuscación no puede romper. Se quedaron un día por comodidad —leer los
# crashes con nombres— y el usuario decidió retirarlas: el shrinker recupera su margen.
#
# **Lo que NO se puede volver a hacer** es comparar el nombre de una clase con un literal. Eso lo
# vigila `ShrinkerKeepContractTest`, que exige un `-keepnames` para cualquier tipo cuyo `simpleName`
# se compare en caliente. Si esa prueba te ha traído hasta aquí: no añadas la regla, quita la
# comparación.

# ── App-specific keeps ────────────────────────────────────────
# Estado de partida — se serializa. OJO: hasta el 2026-09-04 estas dos reglas decían
# `com.cukiti.teikafusion.GameState`, un paquete en el que esa clase NUNCA ha vivido (está en
# `.model`), así que no protegían nada. No era un bug —lo cubre el `-keep` de arriba para todo
# @Serializable— pero es una regla que APARENTA proteger, que es peor que no tenerla.
-keep class com.cukiti.teikafusion.model.GameState { *; }
-keep class com.cukiti.teikafusion.model.GameState$* { *; }
-keepclassmembers class com.cukiti.teikafusion.** {
    @kotlinx.serialization.Serializable *;
}

# Singleton managers (accessed via reflection-like patterns)
-keep class com.cukiti.teikafusion.Analytics { *; }
-keep class com.cukiti.teikafusion.AdManager { *; }
-keep class com.cukiti.teikafusion.BillingManager { *; }
-keep class com.cukiti.teikafusion.GoogleManager { *; }
-keep class com.cukiti.teikafusion.CloudSaveRepository { *; }


# ── Google Sign-In (Credential Manager + Google ID) ───────────
# Las librerías traen consumer-rules; mantenemos sus tipos por seguridad (parsing por Bundle).
-keep class androidx.credentials.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn androidx.credentials.**
-dontwarn com.google.android.libraries.identity.googleid.**
