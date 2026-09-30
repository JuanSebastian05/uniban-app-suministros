import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Firebase: el plugin google-services solo se aplica si existe
// app/google-services.json (NO versionado, ver README). Así el proyecto
// compila aunque un integrante aún no tenga el archivo.
// ---------------------------------------------------------------------------
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
} else {
    logger.warn("google-services.json no encontrado: Firebase queda sin configurar (el build continúa).")
}

// ---------------------------------------------------------------------------
// BASE_URL del backend: se lee de local.properties (clave api.baseUrl) o de
// una propiedad de Gradle (-Papi.baseUrl=...). Por defecto apunta al backend
// local visto desde el emulador de Android (10.0.2.2 = localhost del PC).
// ---------------------------------------------------------------------------
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val apiBaseUrl: String = (localProps.getProperty("api.baseUrl")
    ?: providers.gradleProperty("api.baseUrl").orNull
    ?: "http://10.0.2.2:8000/").let { if (it.endsWith("/")) it else "$it/" }

android {
    namespace = "co.edu.udea.uniban.suministros"
    compileSdk = 35

    defaultConfig {
        applicationId = "co.edu.udea.uniban.suministros"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

// Room: exporta el esquema para versionar migraciones cuando exista AppDatabase.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Base Android + Compose + Material 3
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Navegación + MVVM
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room (persistencia local)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Retrofit / OkHttp (red)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)

    // WorkManager (sincronización futura)
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase Authentication (solo identidad)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)

    // Pruebas
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
