plugins {
    kotlin("multiplatform")
    if (System.getProperty("includeAndroid")?.toBoolean() == true) {
        id("com.android.library")
    }
}

kotlin {
    explicitApi()

    jvmToolchain(17)

    val includeAndroid = System.getProperty("includeAndroid")?.toBoolean() ?: true

    val commonMain by sourceSets.getting {
        dependencies {
            implementation(project(":secp256k1-kmp"))
        }
    }
    val commonTest by sourceSets.getting {
        dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        compilations["main"].dependencies {
            implementation(project(":jni:jvm:all"))
        }
        compilations["test"].dependencies {
            implementation(kotlin("test-junit"))
        }
    }

    if (includeAndroid) {
        androidTarget {
            compilations.all {
                kotlinOptions.jvmTarget = "17"
            }
            sourceSets["androidMain"].dependencies {
                implementation(project(":jni:android"))
            }
            sourceSets["commonTest"].dependencies {
                implementation(kotlin("test-junit"))
                implementation("androidx.test.ext:junit:1.1.2")
                implementation("androidx.test.espresso:espresso-core:3.3.0")
            }
        }
    }

    linuxX64("linux")

    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    macosX64()
    watchosArm64()
    tvosArm64()
}

val includeAndroid = System.getProperty("includeAndroid")?.toBoolean() ?: true
if (includeAndroid) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "fr.acinq.secp256k1.tests"

        defaultConfig {
            compileSdk = 33
            minSdk = 21
            testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }

        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }

        sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

        afterEvaluate {
            tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().all {
                enabled = false
            }
        }
    }
}

// Disable cross compilation
allprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        afterEvaluate {
            val currentOs = org.gradle.internal.os.OperatingSystem.current()
            val targetsToExclude = when {
                currentOs.isLinux   -> listOf()
                currentOs.isMacOsX  -> listOf("linux")
                currentOs.isWindows -> listOf("linux")
                else                -> listOf("linux")
            }.mapNotNull { kotlin.targets.findByName(it) as? org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget }

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