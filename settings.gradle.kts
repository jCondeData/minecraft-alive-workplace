pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases")
    }
    plugins {
        // Versions for plugins applied in build.gradle.kts (one place to bump them).
        id("me.modmuss50.mod-publish-plugin") version "2.2.1"
    }
}

plugins {
    // One source tree, one Gradle subproject per Minecraft version ("node", versions/<mc>/).
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Applies the right Loom per node: fabric-loom-remap (obfuscated, up to 1.21.11) or fabric-loom (26.1+).
    id("dev.kikugie.loom-back-compat") version "0.4.3"
    // Gradle runs on JDK 25; this fetches the JDK each node compiles and runs with (21 for 1.21.1) when missing.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Every Minecraft version the mod builds for. Adding one: add it here and a section to
        // stonecutter.properties.toml, then build every node (CLAUDE.md, ROADMAP Milestone 19).
        versions("1.21.1")
        // The version the checked-in sources are written for (the Cobbleverse pack's). Switch back to it
        // (`./gradlew "Reset active project"`) before committing.
        vcsVersion = "1.21.1"
    }
}

rootProject.name = "alive-workplace"
