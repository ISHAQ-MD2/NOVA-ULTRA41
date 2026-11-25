plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.nova.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.nova.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 36
        versionName = "36.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("debugConfig") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debugConfig")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debugConfig")
        }
    }
    lint { abortOnError = false }
}
dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("androidx.core:core-ktx:1.13.1")
    // NOVA Browser: 1.12.1 = compileSdk 34 ke saath chalta hai (1.15+ ko compileSdk 35 chahiye) aur profile (MULTI_PROFILE) support rakhta hai
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.airbnb.android:lottie:6.5.2")
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    val apkSrc = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk")
    val outDir = rootProject.layout.projectDirectory.dir("apkv38")
    doLast {
        val src = apkSrc.get().asFile
        if (src.exists()) {
            val destDir = outDir.asFile
            destDir.mkdirs()
            src.copyTo(File(destDir, "app-debug.apk"), overwrite = true)
        }
    }
}

val copyNovaApk by tasks.registering {
    val releaseApkDir = layout.buildDirectory.dir("outputs/apk/release")
    val novaApkDir = rootProject.layout.projectDirectory.dir("nova-apk")
    val verName = android.defaultConfig.versionName ?: "1.0"
    doLast {
        val srcDir = releaseApkDir.get().asFile
        val apkFile = srcDir.listFiles()?.firstOrNull {
            it.isFile && it.extension == "apk" && !it.name.contains("unsigned", ignoreCase = true)
        } ?: srcDir.listFiles()?.firstOrNull { it.isFile && it.extension == "apk" }
        if (apkFile != null && apkFile.exists()) {
            val destDir = novaApkDir.asFile
            if (!destDir.exists()) destDir.mkdirs()
            destDir.listFiles()?.forEach { old ->
                if (old.isFile && old.extension.equals("apk", ignoreCase = true)) old.delete()
            }
            val target = File(destDir, "nova-release-v$verName.apk")
            apkFile.copyTo(target, overwrite = true)
            println("NOVA release APK copied to: ${target.absolutePath}")
        } else {
            println("copyNovaApk: No release APK found in ${srcDir.absolutePath}")
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(copyNovaApk)
}
