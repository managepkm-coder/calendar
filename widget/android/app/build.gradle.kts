plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "kr.pkm.shift"
    compileSdk = 35

    defaultConfig {
        // 예전 빌드(kr.pkm.shift)와 서명이 달라 업데이트 설치가 거부되므로
        // 패키지를 바꿔 완전히 새 앱으로 설치되게 한다
        applicationId = "kr.pkm.geunmupyo"
        minSdk = 26          // java.time 사용
        targetSdk = 35
        versionCode = 17
        versionName = "2.6"
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

// 의존성 없음 — 프레임워크 API 와 코틀린 표준 라이브러리만 사용합니다.
// androidx 를 넣으면 쓰지도 않는 코드가 dex 를 4MB 넘게 부풀립니다.
dependencies { }
