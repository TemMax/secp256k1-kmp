import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.`maven-publish`
import org.gradle.kotlin.dsl.signing
import java.util.Properties
import org.jetbrains.dokka.Platform

plugins {
    `maven-publish`
    signing
    id("org.jetbrains.dokka")
}

// Stub secrets to let the project sync and build without the publication values set up
ext["signing.keyId"] = null
ext["signing.password"] = null
ext["signing.secretKeyRingFile"] = null
ext["ossrhUsername"] = null
ext["ossrhPassword"] = null

// Grabbing secrets from local.properties file or from environment variables, which could be used on CI
val secretPropsFile = project.rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    secretPropsFile.reader().use {
        Properties().apply {
            load(it)
        }
    }.onEach { (name, value) ->
        ext[name.toString()] = value
    }
} else {
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.secretKeyRingFile"] = System.getenv("SIGNING_SECRET_KEY_RING_FILE")
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
}

val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

fun getExtraString(name: String) = ext[name]?.toString()

publishing {
    repositories {
        maven {
            name = "sonatype"
            setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = getExtraString("ossrhUsername")
                password = getExtraString("ossrhPassword")
            }
        }
    }

    publications.withType<MavenPublication> {
        group = "com.duglasher.secp256k1"
        version = "0.10.2"

        artifact(javadocJar.get())

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

if (project.name !in listOf("native", "tests")) {
//    afterEvaluate {
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

        javadocJar.get().dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
        javadocJar.get().from(dokkaOutputDir)
//    }
}

signing {
    sign(publishing.publications)
}