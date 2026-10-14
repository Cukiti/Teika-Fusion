import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.roborazzi)
}

// ── Firma de RELEASE ────────────────────────────────────────────────────────────────────────
// Hasta 2026-07-28 la release se firmaba con el keystore de DEBUG, y eso rompía tres cosas a la
// vez: Google Sign-In (el SHA-1 del APK servido por Play no coincidía con ninguno registrado en
// Firebase), la atestación de App Check / Play Integrity, y —lo peor— el keystore de debug se
// regenera solo y no se respalda: perderlo significa no poder volver a publicar la app jamás.
//
// Las credenciales viven en `local.properties` (ya está en .gitignore) y el .p12 vive FUERA del
// repositorio, así que la clave privada no puede acabar en un commit.
val keystoreProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKeystore = keystoreProps.getProperty("RELEASE_STORE_FILE")
    ?.let { File(it).exists() } == true

android {
    namespace = "com.cukiti.teikafusion"
    // P-174 (2026-09-04, decisión del usuario): 36.1 → 37. Es lo que destapa el tope de `core-ktx`
    // 1.19 y `lifecycle` 2.11, documentado en el TOML desde I-06. OJO: compilar contra un SDK nuevo
    // cambia comportamientos por defecto del SISTEMA (no de la app), así que **pide un playtest
    // completo** antes de publicar — no es un cambio de una línea aunque lo parezca.
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cukiti.teikafusion"
        minSdk = 26
        targetSdk = 36
        versionCode = 30
        versionName = "1.0.30"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = File(keystoreProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = keystoreProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = keystoreProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = keystoreProps.getProperty("RELEASE_KEY_PASSWORD")
                // v1 desactivado: minSdk 26 y Play exige v2+. Menos superficie y build más rápido.
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sin credenciales configuradas NO se rompe el build (un clon recién hecho debe poder
            // compilar), pero el artefacto queda marcado como NO publicable y se avisa a gritos.
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                logger.warn(
                    "\n  ⚠  RELEASE SIN FIRMAR CON LA CLAVE REAL: faltan RELEASE_* en local.properties." +
                        "\n     El artefacto generado NO se puede subir a Google Play.\n"
                )
                signingConfigs.getByName("debug")
            }
        }
        debug {
            isMinifyEnabled = false
            // SIN applicationIdSuffix a propósito (2026-07-29): el debug comparte applicationId con la
            // release para que Google Sign-In funcione en desarrollo. El SHA-1 del keystore de debug
            // está registrado en `teika-fusion` bajo `com.cukiti.teikafusion`, y Credential Manager
            // exige que el par (applicationId, SHA) coincida con un cliente OAuth del proyecto.
            // Consecuencias asumidas: el debug apunta al proyecto de PRODUCCIÓN, sus pruebas cuentan
            // en ranking/métricas reales, y se instala PISANDO la release. Los IDs de AdMob siguen
            // separados por source set.
            //
            // Por eso hay UN SOLO `app/google-services.json` (2026-07-31): antes había una copia
            // idéntica en src/debug/ y otra en src/release/, y esa duplicación solo servía para que
            // un SHA-1 nuevo se pegara en una y no en la otra. La separación por source set solo
            // tiene sentido si las variantes apuntan a proyectos Firebase distintos, y aquí no.
            versionNameSuffix = "-dev"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    // Robolectric (solo tests JVM): el bot de niveles corre el GameViewModel REAL, que lee recursos
    // (res/raw, strings) — sin esto Robolectric no los ve.
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// El bot de niveles se configura por variables de entorno. Gradle no las ve como entrada de la tarea,
// así que con la caché de build activa reutilizaba el resultado anterior y el bot NO jugaba.
tasks.withType<Test>().configureEach {
    inputs.property("levelBot", providers.environmentVariable("LEVEL_BOT").orElse(""))
    inputs.property("levelBotRuns", providers.environmentVariable("LEVEL_BOT_RUNS").orElse(""))
    // Revisión completa (2026-09-26): la campaña por capítulos, su fusión y la comparación A/B.
    inputs.property("levelBotOut", providers.environmentVariable("LEVEL_BOT_OUT").orElse(""))
    inputs.property("levelBotMerge", providers.environmentVariable("LEVEL_BOT_MERGE").orElse(""))
    inputs.property("levelBotBase", providers.environmentVariable("LEVEL_BOT_BASE").orElse(""))
    inputs.property("levelBotFromRun", providers.environmentVariable("LEVEL_BOT_FROM_RUN").orElse(""))
}

dependencies {
    implementation(libs.lottie.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Explicit WorkManager — evita que R8 no aplique las consumer rules cuando
    // la dependencia llega solo de forma transitiva vía Firebase.
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.config)
    implementation(libs.firebase.functions)
    // App Check (Play Integrity): sella las llamadas a Firestore/Auth como provenientes de una
    // instalación legítima. Sin "enforcement" en la consola no bloquea nada — activar allí cuando
    // las métricas de la consola muestren tráfico verificado.
    implementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
    // Push remotas (reenganche/campañas desde Firebase Console) — local notifs van por WorkManager.
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.billing.ktx)
    // "Continuar con Google": Credential Manager moderno (recomendado por Firebase). Entrega un
    // Google ID token que Firebase convierte en identidad canónica (GoogleAuthProvider). El
    // serverClientId sale de R.string.default_web_client_id (lo genera el plugin google-services).
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}