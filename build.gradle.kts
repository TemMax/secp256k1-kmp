plugins {
    kotlin("multiplatform") version "1.4-M2-mt"
    id("com.android.library") version "4.0.0"
}
group = "fr.acinq.phoenix"
version = "1.0-1.4-M2"

repositories {
    jcenter()
    google()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    maven("https://dl.bintray.com/kotlin/kotlin-eap")
}

val currentOs = org.gradle.internal.os.OperatingSystem.current()

kotlin {
    explicitApi()

    val commonMain by sourceSets.getting {
        dependencies {
            implementation(kotlin("stdlib-common"))
        }
    }
    val commonTest by sourceSets.getting {
        dependencies {
            implementation(kotlin("test-common"))
            implementation(kotlin("test-annotations-common"))
        }
    }

    val jvmAndAndroidMain by sourceSets.creating {
        dependsOn(commonMain)
        dependencies {
            implementation(kotlin("stdlib-jdk8"))
        }
    }

    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        (tasks[compilations["main"].processResourcesTaskName] as ProcessResources).apply{
            dependsOn("copyJni")
            from(buildDir.resolve("jniResources"))
        }
        compilations["main"].defaultSourceSet.dependsOn(jvmAndAndroidMain)
        compilations["test"].dependencies {
            implementation(kotlin("test-junit"))
        }
    }

    android {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        sourceSets["androidMain"].dependsOn(jvmAndAndroidMain)
        sourceSets["androidTest"].dependencies {
            implementation(kotlin("test-junit"))
            implementation("androidx.test.ext:junit:1.1.1")
            implementation("androidx.test.espresso:espresso-core:3.2.0")
        }
    }

    fun org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget.secp256k1CInterop() {
        compilations["main"].cinterops {
            val libsecp256k1 by creating {
                includeDirs.headerFilterOnly(project.file("native/secp256k1/include/"))
//                includeDirs("/usr/local/lib")
                tasks[interopProcessingTaskName].dependsOn("buildSecp256k1Ios")
            }
        }
    }

    val nativeMain by sourceSets.creating { dependsOn(commonMain) }

    linuxX64 {
        secp256k1CInterop()
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf("-include-binary", "$rootDir/native/build/linux/libsecp256k1.a")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
    }

    ios {
        secp256k1CInterop()
        // https://youtrack.jetbrains.com/issue/KT-39396
        compilations["main"].kotlinOptions.freeCompilerArgs += listOf("-include-binary", "$rootDir/native/build/ios/libsecp256k1.a")
        compilations["main"].defaultSourceSet.dependsOn(nativeMain)
    }

    sourceSets.all {
        languageSettings.useExperimentalAnnotation("kotlin.RequiresOptIn")
    }

}

android {
    defaultConfig {
        compileSdkVersion(30)
        minSdkVersion(21)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {}
        }
    }

    externalNativeBuild {
        cmake {
            setPath("src/androidMain/CMakeLists.txt")
        }
    }
    ndkVersion = "21.3.6528147"

    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")
}

val buildSecp256k1 by tasks.creating { group = "build" }
sealed class Cross {
    abstract fun cmd(target: String, nativeDir: File): List<String>
    class DockCross(val cross: String) : Cross() {
        override fun cmd(target: String, nativeDir: File): List<String> = listOf("./dockcross-$cross", "bash", "-c", "TARGET=$target ./build.sh")
    }
    class MultiArch(val crossTriple: String) : Cross() {
        override fun cmd(target: String, nativeDir: File): List<String> {
            val uid = Runtime.getRuntime().exec("id -u").inputStream.use { it.reader().readText() }.trim().toInt()
            return listOf(
                "docker", "run", "--rm", "-v", "${nativeDir.absolutePath}:/workdir",
                "-e", "CROSS_TRIPLE=$crossTriple", "-e", "TARGET=$target", "-e", "TO_UID=$uid",
                "multiarch/crossbuild", "./build.sh"
            )
        }
    }
}
fun creatingBuildSecp256k1(target: String, cross: Cross?) = tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1.dependsOn(this)

    inputs.files(projectDir.resolve("native/build.sh"))
    outputs.dir(projectDir.resolve("native/build/$target"))

    workingDir = projectDir.resolve("native")
    environment("TARGET", target)
    commandLine((cross?.cmd(target, workingDir) ?: emptyList()) + "./build.sh")
}
val buildSecp256k1Darwin by creatingBuildSecp256k1("darwin", if (currentOs.isMacOsX) null else Cross.MultiArch("x86_64-apple-darwin"))
val buildSecp256k1Linux by creatingBuildSecp256k1("linux", if (currentOs.isLinux) null else Cross.DockCross("linux-x64"))
val buildSecp256k1Mingw by creatingBuildSecp256k1("mingw", if (currentOs.isWindows) null else Cross.DockCross("windows-x64"))

val copyJni by tasks.creating(Sync::class) {
    dependsOn(buildSecp256k1)
    from(projectDir.resolve("native/build/linux/libsecp256k1-jni.so")) { rename { "libsecp256k1-jni-linux-x86_64.so" } }
    from(projectDir.resolve("native/build/darwin/libsecp256k1-jni.dylib")) { rename { "libsecp256k1-jni-darwin-x86_64.dylib" } }
    from(projectDir.resolve("native/build/mingw/secp256k1-jni.dll")) { rename { "secp256k1-jni-mingw-x86_64.dll" } }
    into(buildDir.resolve("jniResources/fr/acinq/secp256k1/native"))
}

val buildSecp256k1Ios by tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1.dependsOn(this)

    onlyIf { currentOs.isMacOsX }

    inputs.files(projectDir.resolve("native/build-ios.sh"))
    outputs.dir(projectDir.resolve("native/build/ios"))

    workingDir = projectDir.resolve("native")
    commandLine("./build-ios.sh")
}

val buildSecp256k1Android by tasks.creating {
    group = "build"
    buildSecp256k1.dependsOn(this)
}
fun creatingBuildSecp256k1Android(arch: String) = tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1Android.dependsOn(this)

    inputs.files(projectDir.resolve("native/build-android.sh"))
    outputs.dir(projectDir.resolve("native/build/android/$arch"))

    workingDir = projectDir.resolve("native")

    val toolchain = when {
        currentOs.isLinux -> "linux-x86_64"
        currentOs.isMacOsX -> "darwin-x86_64"
        currentOs.isWindows -> "windows-x86_64"
        else -> error("No Android toolchain defined for this OS: $currentOs")
    }
    environment("TOOLCHAIN", toolchain)
    environment("ARCH", arch)
    environment("ANDROID_NDK", android.ndkDirectory)
    commandLine("./build-android.sh")
}
val buildSecp256k1AndroidX86_64 by creatingBuildSecp256k1Android("x86_64")
val buildSecp256k1AndroidX86 by creatingBuildSecp256k1Android("x86")
val buildSecp256k1AndroidArm64v8a by creatingBuildSecp256k1Android("arm64-v8a")
val buildSecp256k1AndroidArmeabiv7a by creatingBuildSecp256k1Android("armeabi-v7a")

afterEvaluate {
    configure(listOf("Debug", "Release").map { tasks["externalNativeBuild$it"] }) {
        dependsOn(buildSecp256k1Android)
    }
}

tasks["clean"].doLast {
    delete(projectDir.resolve("native/build"))
}

afterEvaluate {
    tasks.withType<com.android.build.gradle.tasks.factory.AndroidUnitTest>().all {
        enabled = false
    }
}