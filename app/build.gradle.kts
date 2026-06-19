plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.orbit.xqwtyz"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

val kspFiles by configurations.creating {
  extendsFrom(configurations.ksp.get())
  isCanBeResolved = true
  isCanBeConsumed = false
}

val extractSqliteNative by tasks.registering {
  val inputFiles = objects.fileCollection().from(kspFiles)
  inputs.files(inputFiles)

  outputs.upToDateWhen { false }

  doLast {
    val sqliteJar = inputFiles.files.firstOrNull {
      it.name.startsWith("sqlite-jdbc") && it.name.endsWith(".jar")
    } ?: return@doLast

    val osName = System.getProperty("os.name").lowercase()
    val osArch = System.getProperty("os.arch").lowercase()
    val osPrefix = when {
      osName.contains("linux") -> "Linux"
      osName.contains("mac") || osName.contains("darwin") -> "Mac"
      osName.contains("win") -> "Windows"
      else -> "Linux"
    }
    val archSuffix = when {
      osArch.contains("aarch64") || osArch.contains("arm64") -> "aarch64"
      osArch.contains("amd64") || osArch.contains("x86_64") -> "x86_64"
      osArch.contains("x86") -> "x86"
      else -> "x86_64"
    }

    val nativeDir = "org/sqlite/native/$osPrefix/$archSuffix"
    val outputDir = layout.buildDirectory.dir("generated/sqlite-native").get().asFile
    val libDir = outputDir.resolve(nativeDir)
    delete(libDir)
    libDir.mkdirs()

    copy {
      from(zipTree(sqliteJar)) {
        include("$nativeDir/libsqlitejdbc.so")
      }
      into(outputDir)
    }

    val soFile = libDir.resolve("libsqlitejdbc.so")
    if (soFile.exists()) {
      System.setProperty("org.sqlite.lib.path", libDir.absolutePath)
      System.setProperty("org.sqlite.lib.name", "libsqlitejdbc.so")
      logger.lifecycle("sqlite-jdbc native lib extracted to ${libDir.absolutePath}")
    } else {
      logger.warn("Native lib NOT found for $osPrefix/$archSuffix in sqlite-jdbc jar")
    }
  }
}

tasks.matching { it.name.startsWith("ksp") }.configureEach {
  dependsOn(extractSqliteNative)
}


