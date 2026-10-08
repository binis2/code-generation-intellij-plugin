plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.19.0"
    id("io.freefair.lombok") version "9.8.0"
}

group = "net.binis.intellij"
version = "1.4.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenLocal()
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    implementation("dev.binis:code-generator:1.4.0")
    intellijPlatform {
        intellijIdea("2026.2.3")
        bundledPlugin("com.intellij.java")
    }
}

intellijPlatform {
    buildSearchableOptions = false

    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
        }
    }
    pluginVerification  {
        // The published plugin ID contains "intellij" and can't be changed anymore.
        freeArgs = listOf("-mute", "TemplateWordInPluginId")
        ides {
            recommended()
        }
    }
}
