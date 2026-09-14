import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless") version "6.25.0" apply false
}

/**
 * The published version of the Java artifacts tracks the calendar data release recorded in
 * `blessed/manifest.json`, so `bdc-calendar-core` and `bdc-calendar-data` always agree on which
 * dataset they ship. Builds are snapshots unless `-Prelease=true` is passed.
 */
val dataRelease: String by lazy {
    val manifest = rootProject.file("blessed/manifest.json")
    val semantic =
        Regex("\"semantic\"\\s*:\\s*\"([^\"]+)\"").find(manifest.readText())?.groupValues?.get(1)
            ?: throw GradleException("No release_version.semantic in ${manifest.absolutePath}")
    semantic
}

val publishVersion: String by lazy {
    if (project.findProperty("release") == "true") dataRelease else "$dataRelease-SNAPSHOT"
}

extra["dataRelease"] = dataRelease
extra["publishVersion"] = publishVersion

subprojects {
    apply(plugin = "com.diffplug.spotless")

    repositories {
        mavenCentral()
    }

    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        // Fixed line endings: the default git-attributes policy cannot be serialized by the
        // configuration cache and fails with "Error while evaluating property 'lineEndingsPolicy'".
        lineEndings = LineEnding.UNIX
        java {
            target("src/*/java/**/*.java")
            googleJavaFormat()
        }
    }
}
