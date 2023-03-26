plugins {
    id("java")
    id("org.jetbrains.intellij") version "1.13.2"
    id("io.freefair.lombok") version "5.3.3.3"
}

group = "net.binis.intellij"
version = "1.0.5"

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("dev.binis:code-generator:1.0.5")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set("2022.3.2")
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("com.intellij.java"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }

    patchPluginXml {
        sinceBuild.set("223")
        untilBuild.set("231.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
