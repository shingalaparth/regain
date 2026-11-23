plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}
tasks.withType<JavaCompile> { options.release.set(17) }

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Tests read the real shipped rules file, so a broken rules edit fails the build.
    systemProperty(
        "rules.path",
        rootProject.projectDir.resolve("../rules/platform-rules.json").canonicalPath,
    )
}
