import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import org.yaml.snakeyaml.Yaml
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.fasterxml.jackson.core:jackson-databind:2.17.0")
        classpath("org.yaml:snakeyaml:2.2")
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing
    id("com.gradleup.nmcp")
}

group = "io.github.twallengren"
version = rootProject.extra["dataPublishVersion"] as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    // The artifact has no sources, but Maven Central requires a sources and a javadoc jar on
    // every release upload; with no sources these are (accepted) empty jars.
    withSourcesJar()
    withJavadocJar()
}

val csvHeader =
    "date,type,description,key,source_module,observed_from,close_time,status"

val blessedDir: File = rootProject.file("blessed")
val generatedResourcesDir: Provider<Directory> = layout.buildDirectory.dir("generated-resources")
val includeBase: Boolean = project.findProperty("includeBase") == "true"

/**
 * Packs the published artifacts in `blessed/` into the jar's resources.
 *
 * WEEKEND rows are ~85% of an artifact and carry no information the calendar's weekend policy does
 * not already hold, so they are dropped here and rebuilt at query time from the `weekend_policy`
 * block lifted out of `resolved.yaml` — exactly what `python/scripts/sync_data.py` does for the
 * Python wheel. `core`'s parity test proves the reconstruction is lossless.
 */
val generateCalendarData =
    tasks.register("generateCalendarData") {
        description = "Packs blessed/ calendar artifacts into the data jar's resources"
        group = "build"

        inputs.dir(blessedDir).withPropertyName("blessed")
        inputs.property("includeBase", includeBase)
        val outputDir = generatedResourcesDir
        outputs.dir(outputDir).withPropertyName("generatedResources")

        doLast {
            val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
            val writer =
                ObjectMapper()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writerWithDefaultPrettyPrinter()

            val target = outputDir.get().dir("bdc/calendars").asFile
            target.deleteRecursively()
            target.mkdirs()

            @Suppress("UNCHECKED_CAST")
            val blessedManifest =
                mapper.readValue(blessedDir.resolve("manifest.json"), Map::class.java)
                    as Map<String, Any?>

            @Suppress("UNCHECKED_CAST")
            val blessedCalendars = blessedManifest["calendars"] as Map<String, Map<String, Any?>>

            val selected =
                blessedCalendars
                    .filterValues { includeBase || (it["kind"] ?: "market") in setOf("market", "payment") }
                    .keys
                    .sorted()

            // The MIC/alias table has one source of truth: blessed/manifest.json's own "aliases"
            // map, written by `tools manifest` (derived from every calendar's metadata.json) and
            // kept in step with `python/scripts/sync_data.py`, which reads the same map.
            @Suppress("UNCHECKED_CAST")
            val aliases = (blessedManifest["aliases"] as? Map<String, String>).orEmpty()

            aliases.forEach { (alias, canonical) ->
                if (canonical !in selected) {
                    throw GradleException("Alias $alias points at unbundled calendar $canonical")
                }
            }

            val index = linkedMapOf<String, Any?>()
            for (calendarId in selected) {
                val source = blessedDir.resolve(calendarId)
                val calendarOut = target.resolve(calendarId)
                calendarOut.mkdirs()

                @Suppress("UNCHECKED_CAST")
                val metadata =
                    LinkedHashMap(
                        mapper.readValue(source.resolve("metadata.json"), Map::class.java)
                            as Map<String, Any?>
                    )
                metadata["weekend_policy"] = weekendPolicy(source.resolve("resolved.yaml"))
                calendarOut.resolve("metadata.json").writeText(
                    writer.writeValueAsString(metadata) + "\n"
                )

                val lines = source.resolve("events.csv").readLines().filter { it.isNotBlank() }
                if (lines.first() != csvHeader) {
                    throw GradleException(
                        "$calendarId: unexpected events.csv header ${lines.first()}"
                    )
                }
                val kept =
                    lines
                        .drop(1)
                        .filter { splitCsv(it)[1] != "WEEKEND" }
                        .sortedWith(
                            compareBy(
                                { splitCsv(it)[0] },
                                { splitCsv(it)[1] },
                                { splitCsv(it)[3] },
                            )
                        )
                calendarOut.resolve("holidays.csv").writeText(
                    (listOf(csvHeader) + kept).joinToString("\n", postfix = "\n")
                )

                index[calendarId] =
                    linkedMapOf(
                        "kind" to (blessedCalendars.getValue(calendarId)["kind"] ?: "market"),
                        "range_start" to blessedCalendars.getValue(calendarId)["range_start"],
                        "range_end" to blessedCalendars.getValue(calendarId)["range_end"],
                    )
            }

            @Suppress("UNCHECKED_CAST")
            val release = blessedManifest["release_version"] as Map<String, Any?>
            val manifest =
                linkedMapOf<String, Any?>(
                    "schema_version" to "1.0",
                    "data_version" to release["semantic"],
                    "data_git_sha" to release["git_sha"],
                    "generation_date" to release["generation_date"],
                    "blessed_at" to blessedManifest["blessed_at"],
                    "calendars" to index,
                    "aliases" to aliases.toSortedMap(),
                )
            target.resolve("manifest.json").writeText(writer.writeValueAsString(manifest) + "\n")

            logger.lifecycle(
                "Packed ${selected.size} calendars (data v${release["semantic"]}) into " +
                    target.relativeTo(rootProject.projectDir)
            )
        }
    }

/** Splits one artifact CSV line, honouring the quoting the emitter uses for embedded commas. */
fun splitCsv(line: String): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                current.append('"')
                i++
            }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> {
                fields.add(current.toString())
                current.setLength(0)
            }
            else -> current.append(c)
        }
        i++
    }
    fields.add(current.toString())
    return fields
}

/** Lifts the `weekend_policy` block out of a resolved calendar, with dates as ISO strings. */
fun weekendPolicy(resolved: File): Map<String, Any?> {
    @Suppress("UNCHECKED_CAST")
    val root = Yaml().load<Map<String, Any?>>(resolved.readText())
    val policy = root["weekend_policy"] ?: return mapOf("days" to emptyList<String>(), "periods" to emptyList<Any>())

    @Suppress("UNCHECKED_CAST")
    val map = policy as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    val periods = (map["periods"] as? List<Map<String, Any?>>).orEmpty()
    return linkedMapOf(
        "days" to (map["days"] as? List<*>).orEmpty().map { it.toString() },
        "periods" to
            periods.map { period ->
                linkedMapOf(
                    "days" to (period["days"] as? List<*>).orEmpty().map { it.toString() },
                    "from" to isoDate(period["from"]),
                    "to" to isoDate(period["to"]),
                )
            },
    )
}

/** SnakeYAML resolves unquoted `1952-05-30` to a [Date]; the data files carry ISO strings. */
fun isoDate(value: Any?): String? =
    when (value) {
        null -> null
        is Date -> {
            val format = SimpleDateFormat("yyyy-MM-dd")
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.format(value)
        }
        else -> value.toString()
    }

sourceSets {
    main {
        resources {
            srcDir(generatedResourcesDir)
        }
    }
}

tasks.named("processResources") {
    dependsOn(generateCalendarData)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "bdc-calendar-data"
            from(components["java"])
            pom {
                name.set("BDC Calendar Data")
                description.set(
                    "Published business-day calendar datasets (holiday tables and weekend " +
                        "policies) packaged as classpath resources for bdc-calendar-core. The " +
                        "calendar data is dedicated to the public domain under CC0 1.0."
                )
                url.set("https://github.com/twallengren/calendar-project")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                    license {
                        name.set("CC0 1.0 Universal (calendar data)")
                        url.set("https://creativecommons.org/publicdomain/zero/1.0/")
                    }
                }
                developers {
                    developer {
                        id.set("twallengren")
                        name.set("Toren Wallengren")
                        url.set("https://github.com/twallengren")
                    }
                }
                scm {
                    url.set("https://github.com/twallengren/calendar-project")
                    connection.set("scm:git:https://github.com/twallengren/calendar-project.git")
                    developerConnection.set(
                        "scm:git:ssh://git@github.com/twallengren/calendar-project.git"
                    )
                }
            }
        }
    }
}

signing {
    isRequired = false
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = (project.findProperty("signingPassword") as String?) ?: ""
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}

// The sources jar would otherwise repackage the generated resources; keep it an empty stub.
tasks.named<Jar>("sourcesJar") {
    dependsOn("generateCalendarData")
    exclude("bdc/**")
}
