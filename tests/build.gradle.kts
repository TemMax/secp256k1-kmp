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
            implementation(rootProject)
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