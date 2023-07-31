plugins {
    kotlin("multiplatform") version "1.9.0" apply false
    id("com.android.library") version "8.1.0" apply false
    id("org.jetbrains.dokka") version "1.8.10"
}

allprojects {
    group = "com.duglasher.secp256k1"
    version = "0.10.2"
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