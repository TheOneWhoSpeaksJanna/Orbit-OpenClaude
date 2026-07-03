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
      // opencode is shipped as an npm package (@opencode-ai/cli), no GitHub fallback needed
      buildConfigField("String", "AGENT_FALLBACK_REPO_URL", "\"\"")
      manifestPlaceholders["appLabel"] = "Orbit + OpenCode"
    }
    create("openclaude") {
      dimension = "agent"
      applicationIdSuffix = ".openclaude"
      versionNameSuffix = "-openclaude"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"openclaude\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"OpenClaude\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + OpenClaude\"")
      buildConfigField("String", "AGENT_FALLBACK_REPO_URL",
        "\"https://github.com/Gitlawb/openclaude.git\"")
      manifestPlaceholders["appLabel"] = "Orbit + OpenClaude"
    }
    create("claudecode") {
      dimension = "agent"
      applicationIdSuffix = ".claudecode"
      versionNameSuffix = "-claudecode"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"claude-code\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"Claude Code\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + Claude Code\"")
      // @anthropic-ai/claude-code npm package — no public GitHub mirror
      buildConfigField("String", "AGENT_FALLBACK_REPO_URL", "\"\"")
      manifestPlaceholders["appLabel"] = "Orbit + Claude Code"
    }
    create("codex") {
      dimension = "agent"
      applicationIdSuffix = ".codex"
      versionNameSuffix = "-codex"
      buildConfigField("String", "FLAVOR_PRESET_AGENT_ID", "\"codex\"")
      buildConfigField("String", "FLAVOR_PRESET_AGENT_NAME", "\"Codex\"")
      buildConfigField("String", "FLAVOR_APP_LABEL", "\"Orbit + Codex\"")
      // @openai/codex npm package — no public GitHub mirror
      buildConfigField("String", "AGENT_FALLBACK_REPO_URL", "\"\"")
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

    // ── ABI configuration ───────────────────────────────────────────
    // We bundle libbusybox.so as a native library so Android extracts it to
    // /data/app/<pkg>/lib/<abi>/ which has an SELinux label that ALLOWS exec.
    // Files in /data/data/<pkg>/files/ (app_data_file label) CANNOT be exec'd
    // on Android 10+ due to W^X enforcement. See OrbitRuntimeManager.
    ndk {
      abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
    }

    // ── Centralized, overridable app-level constants ──────────────
    // Forks can override any of these with -P<key>=<value> on the Gradle
    // command line, or via ~/.gradle/gradle.properties, without editing code.
    buildConfigField("String", "OPENROUTER_REFERRER_URL",
      "\"${project.findProperty("orbit.openRouterReferrerUrl") ?: "https://github.com/TheOneWhoSpeaksJanna/Orbit-AI"}\"")
    buildConfigField("String", "OPENROUTER_APP_TITLE",
      "\"${project.findProperty("orbit.openRouterAppTitle") ?: "Orbit AI"}\"")

    // Per-flavor fallback GitHub repo used when an agent's bundled tarball
    // is missing from APK assets. Each flavor section below can override.
    buildConfigField("String", "AGENT_FALLBACK_REPO_URL",
      "\"${project.findProperty("orbit.agentFallbackRepoUrl") ?: "https://github.com/Gitlawb/openclaude.git"}\"")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotBlank() } ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("ciDebug") {
      val debugKeystore = file("debug.keystore")
      if (!debugKeystore.exists()) {
        // Generate debug keystore on CI where it's not committed (gitignored)
        ProcessBuilder(
          "keytool", "-genkeypair", "-keystore", debugKeystore.absolutePath,
          "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
          "-validity", "10000",
          "-storepass", "android", "-keypass", "android",
          "-dname", "CN=Android Debug, O=Android, C=US"
        ).inheritIO().start().waitFor()
      }
      storeFile = debugKeystore
      storePassword = System.getenv("CI_DEBUG_STORE_PASSWORD") ?: "android"
      keyAlias = "androiddebugkey"
      keyPassword = System.getenv("CI_DEBUG_KEY_PASSWORD") ?: "android"
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

  // Extract native libraries (.so files) from the APK to the filesystem.
  // This is REQUIRED for libbusybox.so and libproot.so to be exec'able.
  packaging {
    jniLibs {
      useLegacyPackaging = true
    }
  }

  // Prevent AAPT2 from decompressing .tar.gz assets. Without this,
  // AAPT2 decompresses alpine-rootfs.tar.gz into alpine-rootfs.tar
  // (9MB instead of 3.9MB) and the code can't find it by name.
  androidResources {
    noCompress += listOf("tar.gz", "tar", "gz")
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }

  // ── Compose compiler performance flags ──────────────────────────────
  // Strong skipping (Kotlin 2.2+) lets the compiler skip recomposition
  // for lambdas and unstable params that are structurally equal, which
  // cuts a lot of unnecessary recompositions in deeply-nested UI trees
  // like ours. Default in newer Kotlin but explicit here for clarity.
  // We also enable inclusion of source info in debug builds to make
  // Compose Layout Inspector recomposition counts reliable.
  composeCompiler {
    // Disable the strong-skipping requirement for explicit @Stable
    // annotations on simple value classes — the compiler will infer
    // stability for them. (This is the default but pinned so future
    // Kotlin upgrades don't silently regress performance.)
    includeSourceInformation = true
  }

  // Agent preparation tasks for flavor-specific pre-bundled agents
  // Runs the prepare-agent.sh script to download and bundle each agent
  // as a compressed asset in the flavor's asset directory.
  androidComponents {
    onVariants { variant ->
      val flavorName = variant.flavorName ?: return@onVariants
      if (flavorName == "normal") return@onVariants

      val prepareAgent = tasks.register("prepareAgent${variant.name.replaceFirstChar { it.uppercase() }}") {
        doLast {
          val script = rootProject.projectDir.resolve("scripts/prepare-agent.sh")
          val assetDir = projectDir.resolve("src/$flavorName/assets")
          val archiveFile = assetDir.resolve("agent.tar.gz")

          if (archiveFile.exists() && archiveFile.length() > 0L) {
            logger.lifecycle("Agent archive exists for $flavorName (${archiveFile.length()} bytes), skipping")
            logger.lifecycle("  Delete $archiveFile to force rebuild")
            logger.lifecycle("  Or run: ${script.absolutePath} $flavorName")
          } else {
            logger.lifecycle("Preparing agent for $flavorName...")
            val proc = ProcessBuilder(script.absolutePath, flavorName)
              .directory(rootProject.projectDir)
              .inheritIO()
              .start()
            val exitCode = proc.waitFor()
            if (exitCode != 0) {
              throw GradleException("Agent preparation failed with exit code $exitCode")
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
  implementation(libs.commons.compress)
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

  val outputDir = layout.buildDirectory.dir("generated/sqlite-native")
  outputs.dir(outputDir)

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
    val libDir = outputDir.get().asFile.resolve(nativeDir)
    delete(libDir)
    libDir.mkdirs()

    copy {
      from(zipTree(sqliteJar)) {
        include("$nativeDir/libsqlitejdbc.so")
      }
      into(outputDir.get().asFile)
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
