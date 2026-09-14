import com.diffplug.spotless.LineEnding

plugins {
    id("com.diffplug.spotless") version "6.25.0" apply false
    id("com.gradleup.nmcp.aggregation") version "1.6.2"
}

// The aggregation task resolves its packager in the root project.
repositories {
    mavenCentral()
}

/** Software and dataset versions are independent release streams. */
val dataRelease: String by lazy {
    val manifest = rootProject.file("blessed/manifest.json")
    val semantic =
        Regex("\"semantic\"\\s*:\\s*\"([^\"]+)\"").find(manifest.readText())?.groupValues?.get(1)
            ?: throw GradleException("No release_version.semantic in ${manifest.absolutePath}")
    semantic
}

val coreRelease: String by lazy {
    val versions = rootProject.file("release/versions.json")
    Regex("\"java_core\"\\s*:\\s*\"([^\"]+)\"").find(versions.readText())?.groupValues?.get(1)
        ?: throw GradleException("No java_core in ${versions.absolutePath}")
}

val dataPublishVersion: String by lazy {
    if (project.findProperty("release") == "true") dataRelease else "$dataRelease-SNAPSHOT"
}
val corePublishVersion: String by lazy {
    if (project.findProperty("release") == "true") coreRelease else "$coreRelease-SNAPSHOT"
}

extra["dataRelease"] = dataRelease
extra["coreRelease"] = coreRelease
extra["dataPublishVersion"] = dataPublishVersion
extra["corePublishVersion"] = corePublishVersion

subprojects {
    apply(plugin = "com.diffplug.spotless")

    tasks.withType<Jar>().configureEach {
        from(rootProject.files("LICENSE", "DATA_LICENSE", "NOTICE")) {
            into("META-INF/licenses")
        }
    }

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

    // Tests read the repository's data directories at runtime (workingDir is the repo root), so
    // they must be declared as inputs or the build cache will replay stale results after a YAML
    // or blessed/ change.
    val repoDataInputs = listOf("calendars", "modules", "chronologies", "blessed",
        "release-history", "sources", "spec", "python/bdc_calendars/data")
    tasks.withType<Test>().configureEach {
        repoDataInputs.forEach { dir ->
            inputs.dir(rootProject.file(dir)).withPropertyName("repoData.$dir").optional()
                .withPathSensitivity(PathSensitivity.RELATIVE)
        }
    }
}

/**
 * Publishing to Maven Central goes through the Central Portal: `publishAggregationToCentralPortal`
 * uploads one bundle containing every publication below. Credentials come from the
 * `sonatypeUsername`/`sonatypePassword` project properties (CI passes them as
 * `ORG_GRADLE_PROJECT_*`), so a local `publishToMavenLocal` needs no secrets at all.
 */
nmcpAggregation {
    centralPortal {
        username = providers.gradleProperty("sonatypeUsername")
        password = providers.gradleProperty("sonatypePassword")
        // Uploads land as a draft the release workflow (or a human) still has to release.
        publishingType = "USER_MANAGED"
    }
}

dependencies {
    nmcpAggregation(project(":core"))
    nmcpAggregation(project(":data"))
}
