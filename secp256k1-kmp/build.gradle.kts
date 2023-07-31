import org.gradle.configurationcache.extensions.capitalized
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

plugins {
    kotlin("multiplatform")
    id("publication.convention")
}

kotlin {
    explicitApi()

    jvmToolchain(17)

    val commonMain by sourceSets.getting

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    fun KotlinNativeTarget.secp256k1CInterop(target: String) {
        compilations["main"].cinterops {
            val libsecp256k1 by creating {
                includeDirs.headerFilterOnly(rootProject.file("native/secp256k1/include/"))
                tasks[interopProcessingTaskName].dependsOn(":native:buildSecp256k1${target.capitalized()}")
            }
        }
    }

    val nativeMain by sourceSets.creating { dependsOn(commonMain) }

    linuxX64("linux") {
        secp256k1CInterop("host")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/linux/libsecp256k1.a"
        )
    }

    iosArm64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64-iphoneos.a"
        )
    }

    iosSimulatorArm64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64_x86_x64-iphonesimulator.a"
        )
    }

    iosX64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64_x86_x64-iphonesimulator.a"
        )
    }

    macosX64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/x86_x64-macosx.a"
        )
    }

    macosArm64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64-macosx.a"
        )
    }

    watchosArm64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64-watchos.a"
        )
    }

    //    watchosSimulatorArm64 {
    //        secp256k1CInterop("ios")
    //        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
    //        // https://youtrack.jetbrains.com/issue/KT-39396
    //        compilations["main"].kotlinOptions.freeCompilerArgs += listOf("-include-binary", "$rootDir/native/build/ios/arm64_x86_x64-watchossimulator.a")
    //    }

    tvosArm64 {
        secp256k1CInterop("ios")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf(
            "-include-binary",
            "$rootDir/native/build/ios/arm64-tvos.a"
        )
    }

    sourceSets.all {
        languageSettings.optIn("kotlin.RequiresOptIn")
    }
}

// Sign first, publish after
tasks.withType<AbstractPublishToMaven> {
    dependsOn(tasks.withType<Sign>())
}

// Disable cross compilation
allprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        afterEvaluate {
            val currentOs = OperatingSystem.current()
            val targetsToExclude = when {
                currentOs.isLinux   -> listOf()
                currentOs.isMacOsX  -> listOf("linux")
                currentOs.isWindows -> listOf("linux")
                else                -> listOf("linux")
            }.mapNotNull { kotlin.targets.findByName(it) as? KotlinNativeTarget }

            configure(targetsToExclude) {
                compilations.all {
                    cinterops.all { tasks[interopProcessingTaskName].enabled = false }
                    compileTaskProvider.get().enabled = false
                    tasks[processResourcesTaskName].enabled = false
                }
                binaries.all { linkTask.enabled = false }

                mavenPublication {
                    val publicationToDisable = this
                    tasks.withType<AbstractPublishToMaven>()
                        .all { onlyIf { publication != publicationToDisable } }
                    tasks.withType<GenerateModuleMetadata>()
                        .all { onlyIf { publication.get() != publicationToDisable } }
                }
            }
        }
    }
}