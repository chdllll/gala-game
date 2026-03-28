plugins {
    id("com.android.application")
    id("com.chaquo.python")
}

android {
    namespace = "org.gala.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "org.gala.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "gala2024"
            keyAlias = "release"
            keyPassword = "gala2024"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
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

chaquopy {
    defaultConfig {
        version = "3.10"

        buildPython("py", "-3.10")

        pip {
            install("aiohttp==3.9.1")
            install("aiosignal==1.3.1")
            install("attrs==23.2.0")
            install("frozenlist==1.4.1")
            install("multidict==6.0.4")
            install("propcache==0.2.0")
            install("yarl==1.9.4")
            install("idna==3.6")
            install("charset-normalizer==3.3.2")
            install("async-timeout==4.0.3")
            install("aiohappyeyeballs==2.1.0")
        }

        pyc {
            src = false
        }
    }

    sourceSets {
        getByName("main") {
            srcDir("src/main/python")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
}
