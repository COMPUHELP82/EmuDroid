import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
}

group = "com.luis.emudroid"
version = "1.0.0"

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
}

compose.desktop {
    application {
        mainClass = "emudroid.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "EmuDroid"
            packageVersion = "1.0.0"
            description = "Emulador de Android para Windows"
            vendor = "Luis"
            includeAllModules = true

            windows {
                menu = true
                shortcut = true
                dirChooser = true
                menuGroup = "EmuDroid"
                upgradeUuid = "6f1c2a7e-3b8d-4e21-9a55-0d7c4b1e8f30"
            }
        }
    }
}
