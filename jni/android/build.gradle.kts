plugins {
    id("com.android.library")
    kotlin("android")
    id("org.jetbrains.dokka")
    `maven-publish`
}

kotlin {
    explicitApi()

    jvmToolchain(17)
}

dependencies {
    api(project(":jni"))
}

android {
    namespace = "fr.acinq.secp256k1.jni"

    defaultConfig {
        compileSdk = 33
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path("src/main/CMakeLists.txt")
        }
    }

    ndkVersion = "25.2.9519653"

    afterEvaluate {
        tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().all {
            enabled = false
        }
    }
}

afterEvaluate {
    tasks.filter { it.name.startsWith("configureCMake") }.forEach {
        it.dependsOn(":native:buildSecp256k1Android")
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("android") {
                artifactId = "secp256k1-kmp-jni-android"
                from(components["release"])
//                val sourcesJar = task<Jar>("sourcesJar") {
//                    dependsOn(tasks.getByName("generateMetadataFileForAndroidPublication"))
//                    archiveClassifier.set("sources")
//                    from(android.sourceSets["main"].java.srcDirs)
//                }
//                artifact(sourcesJar)
            }
        }
    }
}
