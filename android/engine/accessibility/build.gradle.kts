plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "app.focuslite.engine.accessibility"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

    // The shipped rules file is the single source of truth: it is copied into the app as an asset.
    sourceSets["main"].assets.srcDir(rootProject.projectDir.resolve("../rules"))
}

dependencies {
    api(project(":core:model"))
    api(project(":engine:detection"))
    api(project(":engine:policy"))
    api(project(":engine:enforcement"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
