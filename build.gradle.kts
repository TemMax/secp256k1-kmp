import org.gradle.internal.os.OperatingSystem
import org.jetbrains.dokka.Platform

plugins {
    kotlin("multiplatform") version "1.9.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.dokka") version "1.8.10"
    `maven-publish`
}

allprojects {
    group = "com.duglasher.secp256k1"
    version = "0.10.2"
}

val currentOs = OperatingSystem.current()

allprojects {
    val javadocJar = tasks.create<Jar>("javadocJar") {
        archiveClassifier.set("javadoc")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    // Publication
    plugins.withId("maven-publish") {
        publishing {
            publications.withType<MavenPublication>().configureEach {
                version = project.version as String
                group = project.group

                artifact(javadocJar)

                pom {
                    name.set("secp256k1 for Kotlin/Multiplatform")
                    description.set("Bitcoin's secp256k1 library ported to Kotlin/Multiplatform for JVM, Android, iOS (+ x64 and arm64 simulator), macOS, watchOS, tvOS & Linux")
                    url.set("https://github.com/TemMax/secp256k1-kmp")
                    licenses {
                        license {
                            name.set("Apache License v2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }
                    issueManagement {
                        system.set("Github")
                        url.set("https://github.com/TemMax/secp256k1-kmp/issues")
                    }
                    scm {
                        connection.set("https://github.com/TemMax/secp256k1-kmp.git")
                        url.set("https://github.com/TemMax/secp256k1-kmp")
                    }
                    developers {
                        developer {
                            name.set("TemMax")
                        }
                    }
                }
            }
        }
    }

    if (project.name !in listOf("native", "tests")) {
        afterEvaluate {
            val dokkaOutputDir = layout.buildDirectory.asFile.get().resolve("dokka")

            tasks.dokkaHtml {
                outputDirectory.set(file(dokkaOutputDir))
                dokkaSourceSets {
                    configureEach {
                        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
                        val platformName = when (platform.get()) {
                            Platform.jvm    -> "jvm"
                            Platform.js     -> "js"
                            Platform.native -> "native"
                            Platform.common -> "common"
                            Platform.wasm   -> "wasm"
                        }
                        displayName.set(platformName)

                        perPackageOption {
                            matchingRegex.set(".*\\.internal.*") // will match all .internal packages and sub-packages
                            suppress.set(true)
                        }
                    }
                }
            }

            val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
                delete(dokkaOutputDir)
            }

            javadocJar.dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
            javadocJar.from(dokkaOutputDir)
        }
    }
}

allprojects {
    afterEvaluate {
        tasks.withType<AbstractTestTask> {
            testLogging {
                events("passed", "skipped", "failed", "standard_out", "standard_error")
                showExceptions = true
                showStackTraces = true
            }
        }
    }
}