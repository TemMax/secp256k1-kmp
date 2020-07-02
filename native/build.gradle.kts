evaluationDependsOn(":jni:android")

val currentOs = org.gradle.internal.os.OperatingSystem.current()

val buildSecp256k1 by tasks.creating { group = "build" }

sealed class Cross {
    abstract fun cmd(target: String, project: Project): List<String>
    class DockCross(val cross: String) : Cross() {
        override fun cmd(target: String, project: Project): List<String> = listOf("${project.rootDir}/cross-scripts/dockcross-$cross", "bash", "-c", "CROSS=1 TARGET=$target ./build.sh")
    }
    class MultiArch(val crossTriple: String) : Cross() {
        override fun cmd(target: String, project: Project): List<String> {
            val uid = Runtime.getRuntime().exec("id -u").inputStream.use { it.reader().readText() }.trim().toInt()
            return listOf(
                "docker", "run", "--rm", "-v", "${project.projectDir.absolutePath}:/workdir",
                "-e", "CROSS_TRIPLE=$crossTriple", "-e", "TARGET=$target", "-e", "TO_UID=$uid", "-e", "CROSS=1",
                "multiarch/crossbuild", "./build.sh"
            )
        }
    }
}

val buildSecp256k1Jvm by tasks.creating {
    group = "build"
    buildSecp256k1.dependsOn(this)
}
fun creatingBuildSecp256k1(target: String, cross: Cross?) = tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1Jvm.dependsOn(this)

    inputs.files(projectDir.resolve("build.sh"))
    outputs.dir(projectDir.resolve("build/$target"))

    workingDir = projectDir
    environment("TARGET", target)
    commandLine((cross?.cmd(target, project) ?: emptyList()) + "./build.sh")
}
val buildSecp256k1Darwin by creatingBuildSecp256k1("darwin", if (currentOs.isMacOsX) null else Cross.MultiArch("x86_64-apple-darwin"))
val buildSecp256k1Linux by creatingBuildSecp256k1("linux", if (currentOs.isLinux) null else Cross.DockCross("linux-x64"))
val buildSecp256k1Mingw by creatingBuildSecp256k1("mingw", if (currentOs.isWindows) null else Cross.DockCross("windows-x64"))

val buildSecp256k1Ios by tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1.dependsOn(this)

    onlyIf { currentOs.isMacOsX }

    inputs.files(projectDir.resolve("build-ios.sh"))
    outputs.dir(projectDir.resolve("build/ios"))

    workingDir = projectDir
    commandLine("./build-ios.sh")
}

val buildSecp256k1Android by tasks.creating {
    group = "build"
    buildSecp256k1.dependsOn(this)
}
fun creatingBuildSecp256k1Android(arch: String) = tasks.creating(Exec::class) {
    group = "build"
    buildSecp256k1Android.dependsOn(this)

    inputs.files(projectDir.resolve("build-android.sh"))
    outputs.dir(projectDir.resolve("build/android/$arch"))

    workingDir = projectDir

    val toolchain = when {
        currentOs.isLinux -> "linux-x86_64"
        currentOs.isMacOsX -> "darwin-x86_64"
        currentOs.isWindows -> "windows-x86_64"
        else -> error("No Android toolchain defined for this OS: $currentOs")
    }
    environment("TOOLCHAIN", toolchain)
    environment("ARCH", arch)
    environment("ANDROID_NDK", (project(":jni:android").extensions["android"] as com.android.build.gradle.LibraryExtension).ndkDirectory)
    commandLine("./build-android.sh")
}
val buildSecp256k1AndroidX86_64 by creatingBuildSecp256k1Android("x86_64")
val buildSecp256k1AndroidX86 by creatingBuildSecp256k1Android("x86")
val buildSecp256k1AndroidArm64v8a by creatingBuildSecp256k1Android("arm64-v8a")
val buildSecp256k1AndroidArmeabiv7a by creatingBuildSecp256k1Android("armeabi-v7a")

val clean by tasks.creating {
    group = "build"
    doLast {
        delete(projectDir.resolve("build"))
    }
}