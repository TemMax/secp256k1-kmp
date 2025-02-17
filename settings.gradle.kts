pluginManagement {
    includeBuild("build-logic")

    repositories {
        google()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "secp256k1-kmp"

// We use a property defined in `local.properties` to know whether we should build the android application or not.
// For example, iOS developers may want to skip that most of the time.
val skipAndroid = File("$rootDir/local.properties").takeIf { it.exists() }
    ?.inputStream()?.use { java.util.Properties().apply { load(it) } }
    ?.run { getProperty("skip.android", "false")?.toBoolean() }
    ?: false

// Use system properties to inject the property in other gradle build files.
System.setProperty("includeAndroid", (!skipAndroid).toString())

include(
    ":secp256k1-kmp",
    ":native",
    ":jni",
    ":jni:jvm",
    ":jni:jvm:darwin",
    ":jni:jvm:linux",
    ":jni:jvm:mingw",
    ":jni:jvm:all",
    ":tests"
)

if (!skipAndroid) {
    print("building android library")
    include(":jni:android")
} else {
    print("skipping android build")
}
