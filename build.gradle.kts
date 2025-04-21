plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.4.0"
    id("io.freefair.lombok") version "8.13.1"
}

group = "net.binis.intellij"
version = "1.2.23.1"

repositories {
    mavenLocal()
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("dev.binis:code-generator:1.2.23")
    intellijPlatform {
        intellijIdeaCommunity("2025.1")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "251"
        }
    }
    pluginVerification  {
        ides {
            recommended()
        }
    }
}
