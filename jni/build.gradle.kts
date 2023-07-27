plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    `maven-publish`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    explicitApi()

    jvmToolchain(17)
}

dependencies {
    api(rootProject)
}

val generateHeaders by tasks.creating(JavaCompile::class) {
    group = "build"
    classpath = sourceSets["main"].compileClasspath
//    destinationDirectory.set(file("${buildDir}/generated/jni"))
    destinationDirectory.set(file("${layout.buildDirectory.asFile.get()}/generated/jni"))
    source = sourceSets["main"].java
    options.compilerArgs = listOf(
//        "-h", file("${buildDir}/generated/jni").absolutePath,
        "-h", file("${layout.buildDirectory.asFile.get()}/generated/jni").absolutePath,
//        "-d", file("${buildDir}/generated/jni-tmp").absolutePath
        "-d", file("${layout.buildDirectory.asFile.get()}/generated/jni-tmp").absolutePath
    )
    // options.verbose = true
    doLast {
//        delete(file("${buildDir}/generated/jni-tmp"))
        delete(file("${layout.buildDirectory.asFile.get()}/generated/jni-tmp"))
    }
}

publishing {
    publications {
        create<MavenPublication>("jvm") {
            artifactId = "secp256k1-kmp-jni-common"
            from(components["java"])
            val sourcesJar = task<Jar>("sourcesJar") {
                archiveClassifier.set("sources")
                from(sourceSets["main"].allSource)
            }
            artifact(sourcesJar)
        }
    }
}
