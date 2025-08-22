plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.2"
    id("io.freefair.lombok") version "8.13.1"
}

group = "net.binis.intellij"
version = "1.2.28"

repositories {
    mavenLocal()
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("dev.binis:code-generator:1.2.28")
    intellijPlatform {
        intellijIdeaCommunity("2025.2")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "252"
        }
    }
    pluginVerification  {
        ides {
            recommended()
        }
    }
}
