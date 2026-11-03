plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Versión: el workflow "Publicar versión" la define; en compilaciones normales se usa la de abajo.
val reupVersionName: String = System.getenv("REUP_VERSION_NAME") ?: "1.0"
val reupVersionCode: Int = System.getenv("REUP_VERSION_CODE")?.toIntOrNull() ?: 11

// Llave de firma de la versión pública (viene de los secretos de GitHub; nunca está en el repositorio).
val reupKeystore: String? = System.getenv("REUP_KEYSTORE")
val reupKeystorePassword: String? = System.getenv("REUP_KEYSTORE_PASSWORD")

android {
    namespace = "app.njsmusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.uriez.reup"
        minSdk = 26
        targetSdk = 34
        versionCode = reupVersionCode
        versionName = reupVersionName
    }

    signingConfigs {
        if (reupKeystore != null && reupKeystorePassword != null) {
            create("public") {
                storeFile = file(reupKeystore!!)
                storeType = "pkcs12"
                storePassword = reupKeystorePassword
                keyAlias = "reup"
                keyPassword = reupKeystorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("public")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
