plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cn.modificator.launcher"
    compileSdk = 31  // 使用现代 AndroidX 库需要的最低版本

    defaultConfig {
        applicationId = "com.zyyme.einklauncher"
        minSdk = 14     // Android 4.0
        targetSdk = 17  // Android 4.2.2 - 运行时行为保持不变
        versionCode = 52
        versionName = "4.1.1-42compat"
        // 启用 multidex 支持旧版本 Android
        multiDexEnabled = true

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
        // 启用 Java 8 特性支持（需要 desugaring）
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
        // coreLibraryDesugaringEnabled 在 AGP 7.x 中默认启用，无需设置
    }
    buildFeatures {
        buildConfig = true
    }
    lint {
        // 禁用 targetSdk 检查，因为我们针对的是 Android 4.2.2
        checkReleaseBuilds = false
        abortOnError = false
    }
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("libs")
        }
    }
    packagingOptions {
        // 解决重复的 META-INF 文件问题
        exclude("META-INF/DEPENDENCIES")
        exclude("META-INF/LICENSE")
        exclude("META-INF/LICENSE.txt")
        exclude("META-INF/NOTICE")
        exclude("META-INF/NOTICE.txt")
        exclude("META-INF/services/javax.annotation.processing.Processor")
    }
}

configurations.all {
    resolutionStrategy {
        // MINA 2.1.6+ calls ServerSocketChannel.supportedOptions(), which is
        // unavailable on old Android releases this launcher still supports.
        force("org.apache.mina:mina-core:2.1.3")

        // 强制所有依赖使用兼容 Android 4.2.2 (API 17) 的版本
        // 防止传递依赖引入高版本库
        force("androidx.core:core:1.6.0")
        force("androidx.appcompat:appcompat:1.4.1")
        force("androidx.appcompat:appcompat-resources:1.4.1")
        force("androidx.lifecycle:lifecycle-runtime:2.4.0")
        force("androidx.lifecycle:lifecycle-process:2.4.0")
        force("androidx.lifecycle:lifecycle-viewmodel:2.4.0")
        force("androidx.annotation:annotation:1.3.0")
        force("androidx.annotation:annotation-experimental:1.1.0")
        force("androidx.emoji2:emoji2:1.1.0")
        force("androidx.emoji2:emoji2-views-helper:1.1.0")
        force("androidx.collection:collection:1.1.0")
        force("androidx.concurrent:concurrent-futures:1.1.0")
        force("androidx.arch.core:core-common:2.1.0")
        force("androidx.arch.core:core-runtime:2.1.0")
    }
}

dependencies {
    // 启用 desugaring，支持 Java 8 特性在旧版 Android 上运行
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // 使用兼容 Android 4.2.2 (API 17) 的旧版本
    implementation(libs.androidx.core.ktx)  // core 1.6.0
    implementation(libs.androidx.appcompat)  // appcompat 1.4.1
    // 启用 multidex 支持
    implementation("androidx.multidex:multidex:2.0.1")

    // FTP 服务器库 - 使用 Android 兼容版本
    // 注意：标准 Apache FTPServer 库不兼容 Android，需要使用 Android 适配版本
    // 暂时注释掉，如果需要 FTP 功能，请使用 android-cupboard 或其他 Android 库
    // implementation("org.apache.ftpserver:ftplet-api:1.2.1")
    // implementation("org.apache.ftpserver:ftpserver-core:1.2.1")
}
