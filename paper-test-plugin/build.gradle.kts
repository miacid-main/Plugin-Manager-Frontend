plugins {
  java
  id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.pluginmanager"
version = "1.0.0"

repositories {
  mavenCentral()
  maven("https://repo.papermc.io/repository/maven-public/")
}

java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
  implementation("com.google.code.gson:gson:2.11.0")
}

tasks {
  withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(21)
  }

  shadowJar {
    archiveBaseName.set("PluginManagerTestPlugin")
    archiveClassifier.set("")
  }

  build {
    dependsOn(shadowJar)
  }
}
