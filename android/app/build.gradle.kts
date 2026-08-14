plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.clouddetect.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.clouddetect.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // Le modèle .tflite ne doit pas être recompressé par l'outil de build.
    androidResources {
        noCompress += "tflite"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Utilisé par WeatherClient (Dispatchers.IO, withTimeoutOrNull, suspendCancellableCoroutine).
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")

    // 2.16.1 ne reconnaît pas les opérateurs (FULLY_CONNECTED v12 notamment) émis par les
    // versions récentes du convertisseur TFLite : Interpreter() plante à l'exécution avec
    // "Didn't find op for builtin opcode 'FULLY_CONNECTED' version '12'". Il faut donc 2.17.0.
    implementation("org.tensorflow:tensorflow-lite:2.17.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}

configurations.all {
    // com.google.ai.edge.litert:litert-api (successeur renommé de TFLite chez Google) duplique
    // des classes déjà fournies par org.tensorflow:tensorflow-lite-api. L'exclusion ciblée sur
    // une seule dépendance ne suffit pas : plusieurs chemins transitifs (tensorflow-lite comme
    // tensorflow-lite-support) l'introduisent, d'où une exclusion globale sur toute config.
    exclude(group = "com.google.ai.edge.litert", module = "litert-api")
}
