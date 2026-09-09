plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.pkm.shift"
    compileSdk = 35

    defaultConfig {
        applicationId = "kr.pkm.shift"
        minSdk = 26          // java.time 사용
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }

    // 저장소에 들어 있는 고정 키로 서명합니다.
    // CI 가 매번 새 디버그 키를 만들면 서명이 달라져 "앱이 설치되지 않았습니다" 로
    // 업데이트가 실패하는데, 키를 고정하면 그냥 덮어쓰기가 됩니다.
    signingConfigs {
        create("personal") {
            storeFile = rootProject.file("keystore/personal.jks")
            storePassword = "shiftwidget"
            keyAlias = "shift"
            keyPassword = "shiftwidget"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("personal")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
}
