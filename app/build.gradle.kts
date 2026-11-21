plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.modificator.launcher"
    compileSdk = 17  // 适配 Android 4.2.2

    defaultConfig {
        applicationId = "com.zyyme.einklauncher"
        minSdk = 14     // Android 4.0
        targetSdk = 17  // Android 4.2.2
        versionCode = 52
        versionName = "4.1.1-42compat"
        // 移除 multiDex，Android 4.2.2 不需要
        // multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = false  // 禁用资源压缩，避免兼容性问题
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_7  // 使用 Java 7，兼容性更好
        targetCompatibility = JavaVersion.VERSION_1_7
        // 移除 coreLibraryDesugaring，Android 4.2.2 不支持
    }
    buildFeatures {
        buildConfig = true
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
}

configurations.all {
    resolutionStrategy {
        // MINA 2.1.6+ calls ServerSocketChannel.supportedOptions(), which is
        // unavailable on old Android releases this launcher still supports.
        force("org.apache.mina:mina-core:2.1.3")
    }
}

dependencies {
    // 移除 coreLibraryDesugaring
    // coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // 使用兼容版本
    implementation("androidx.core:core:1.9.0")  // 较低版本，兼容性更好
    implementation("androidx.appcompat:appcompat:1.4.2")  // 支持到 API 8+
    // 移除 multidex
    // implementation("androidx.multidex:multidex:2.0.1")

    implementation("org.apache.ftpserver:ftplet-api:1.2.1")
    implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
}
