plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.gymtracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.gymtracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "4.0"
    }

    // Clé de signature personnelle, gardée hors du dépôt (secret GitHub, voir README) : chaque nouvel APK
    // peut s'installer par-dessus le précédent sans perdre les données.
    // GYMTRACKER_KEYSTORE = chemin du fichier (défaut : app/signing.keystore), GYMTRACKER_KEYSTORE_PASSWORD = mot de passe.
    val keystoreFile = file(System.getenv("GYMTRACKER_KEYSTORE") ?: "signing.keystore")
    val keystorePassword = System.getenv("GYMTRACKER_KEYSTORE_PASSWORD")
    // Alias de la clé dans le fichier (GYMTRACKER_KEY_ALIAS, défaut : gymtracker).
    val keyAliasName = System.getenv("GYMTRACKER_KEY_ALIAS")?.takeIf { it.isNotBlank() } ?: "gymtracker"
    signingConfigs {
        if (keystoreFile.exists() && !keystorePassword.isNullOrEmpty()) {
            create("personal") {
                storeFile = keystoreFile
                storePassword = keystorePassword
                keyAlias = keyAliasName
                keyPassword = keystorePassword
            }
        }
    }
    // Sans la clé (ex. compilation locale), Android Studio utilise sa clé de debug habituelle.
    val personal = signingConfigs.findByName("personal")

    buildTypes {
        getByName("debug") {
            if (personal != null) signingConfig = personal
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Usage personnel : installable directement.
            signingConfig = personal ?: signingConfigs.getByName("debug")
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
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    // Affichage des photos d'exercices
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
