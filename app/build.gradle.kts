plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.omniclaw"
  compileSdk = 36

  flavorDimensions += "agent"

  productFlavors {
    create("normal") {
      dimension = "agent"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"OmniClaw\"")
      manifestPlaceholders["appLabel"] = "OmniClaw"
    }
    create("opencode") {
      dimension = "agent"
      applicationIdSuffix = ".opencode"
      versionNameSuffix = "-opencode"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"opencode\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"OpenCode\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + OpenCode\"")
      manifestPlaceholders["appLabel"] = "Orbit + OpenCode"
    }
    create("openclaude") {
      dimension = "agent"
      applicationIdSuffix = ".openclaude"
      versionNameSuffix = "-openclaude"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"openclaude\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"OpenClaude\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + OpenClaude\"")
      manifestPlaceholders["appLabel"] = "Orbit + OpenClaude"
    }
    create("claudecode") {
      dimension = "agent"
      applicationIdSuffix = ".claudecode"
      versionNameSuffix = "-claudecode"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"claude-code\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"Claude Code\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + Claude Code\"")
      manifestPlaceholders["appLabel"] = "Orbit + Claude Code"
    }
    create("codex") {
      dimension = "agent"
      applicationIdSuffix = ".codex"
      versionNameSuffix = "-codex"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"codex\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"Codex\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + Codex\"")
      manifestPlaceholders["appLabel"] = "Orbit + Codex"
    }
  }

  defaultConfig {
    applicationId = "com.aistudio.orbit.xqwtyz"
    minSdk = 24
    targetSdk = 36
    versionCode = 12
    versionName = "1.11"

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
    create("ciDebug") {
      storeFile = file("debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("ciDebug")
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

  // Agent preparation tasks for flavor-specific pre-bundled agents
  // Runs the prepare-agent.sh script to download and bundle each agent
  // as a compressed asset in the flavor's asset directory.
  androidComponents {
    onVariants { variant ->
      val p = project
      val flavorName = variant.flavorName ?: return@onVariants
      if (flavorName == "normal") return@onVariants

      val prepareAgent = tasks.register("prepareAgent${variant.name.replaceFirstChar { it.uppercase() }}") {
        doLast {
          val script = p.rootProject.projectDir.resolve("scripts/prepare-agent.sh")
          val assetDir = p.projectDir.resolve("src/$flavorName/assets")
          val archiveFile = assetDir.resolve("agent.tar.gz")

          if (archiveFile.exists() && archiveFile.length() > 0L) {
            p.logger.lifecycle("Agent archive exists for $flavorName (${archiveFile.length()} bytes), skipping")
            p.logger.lifecycle("  Delete $archiveFile to force rebuild")
            p.logger.lifecycle("  Or run: ${script.absolutePath} $flavorName")
          } else {
            p.logger.lifecycle("Preparing agent for $flavorName...")
            p.exec {
              commandLine(script.absolutePath, flavorName)
            }
          }
        }
      }

      tasks.matching { it.name == "merge${variant.name.replaceFirstChar { it.uppercase() }}Assets" }.configureEach {
        dependsOn(prepareAgent)
      }
    }
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.security.crypto)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  implementation("dev.rikka.shizuku:api:13.1.5")
  implementation("dev.rikka.shizuku:provider:13.1.5")
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

tasks.withType<Test> {
  maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
  forkEvery = 50
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
