import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.0"
}

group = "com.github.amejonah1200"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.google.code.gson:gson:2.9.0")
  implementation("io.arrow-kt:arrow-core:1.1.2")
}

tasks.test {
  useJUnitPlatform()
}

tasks.withType<KotlinCompile>() {
  kotlinOptions.jvmTarget = "11"
  kotlinOptions.freeCompilerArgs += "-Xuse-k2"
}
