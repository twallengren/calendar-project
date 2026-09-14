import java.net.URLClassLoader
import java.time.LocalDate
import java.util.zip.ZipFile

plugins {
    java
}

repositories {
    mavenCentral()
}

val smoke by configurations.creating
val requestedCoreVersion = providers.gradleProperty("coreVersion").orNull
val requestedDataVersion = providers.gradleProperty("dataVersion").orNull
if (requestedCoreVersion != null && requestedDataVersion != null) {
    dependencies {
        smoke("io.github.twallengren:bdc-calendar-core:$requestedCoreVersion")
        smoke("io.github.twallengren:bdc-calendar-data:$requestedDataVersion")
    }
}

tasks.register("resolveRelease") {
    doLast {
        require(requestedCoreVersion != null && requestedDataVersion != null) {
            "resolveRelease requires coreVersion and dataVersion"
        }
        val files = smoke.resolve()
        require(files.size == 2) {
            "The core/data runtime must be dependency-free; resolved: ${files.map { it.name }}"
        }
        val core = files.single { it.name.startsWith("bdc-calendar-core-") }
        val data = files.single { it.name.startsWith("bdc-calendar-data-") }
        verifyRuntime(core, data, project.property("dataVersion").toString())
        println("Executed released Java artifacts from Maven Central")
    }
}

tasks.register("verifyLocalRelease") {
    doLast {
        verifyRuntime(
            project.file(project.property("coreJar")),
            project.file(project.property("dataJar")),
            project.property("dataVersion").toString(),
        )
        println("Executed locally built Java core/data artifacts")
    }
}

fun verifyRuntime(core: File, data: File, expectedDataVersion: String) {
    require(core.isFile && data.isFile) { "Core and data runtime jars must exist" }
    URLClassLoader(
        arrayOf(core.toURI().toURL(), data.toURI().toURL()),
        ClassLoader.getPlatformClassLoader(),
    ).use { loader ->
        val calendars = loader.loadClass("com.bdc.calendar.BusinessCalendars")
        val actualVersion = calendars.getMethod("dataVersion").invoke(null).toString()
        require(actualVersion == expectedDataVersion) {
            "Expected bundled data $expectedDataVersion but loaded $actualVersion"
        }
        @Suppress("UNCHECKED_CAST")
        val available = calendars.getMethod("available").invoke(null) as Set<String>
        require(available.isNotEmpty()) { "Released data contains no calendars" }
        val stream = calendars.getMethod("of", String::class.java).invoke(null, available.first())
        require(stream.javaClass.classLoader === loader) {
            "Calendar implementation leaked from the Gradle runtime classpath"
        }

        // When the data jar carries native-date provenance, prove the enriched
        // assessment API can read at least one such record from the packaged data.
        val nativeDates = nativeEventDates(data)
        if (nativeDates.isNotEmpty()) {
            val assessment = stream.javaClass.methods.firstOrNull {
                it.name == "assessment" && it.parameterTypes.contentEquals(arrayOf(LocalDate::class.java))
            } ?: error("Native provenance is bundled but DateStream.assessment is absent")
            var observedNative = false
            for ((calendarId, dates) in nativeDates) {
                val calendar = calendars.getMethod("of", String::class.java).invoke(null, calendarId)
                for (date in dates) {
                    val value = assessment.invoke(calendar, LocalDate.parse(date))
                    @Suppress("UNCHECKED_CAST")
                    val events = value.javaClass.getMethod("events").invoke(value) as List<Any?>
                    if (events.any {
                            it != null &&
                                it.javaClass.getMethod("nominalNativeDate").invoke(it) != null
                        }
                    ) {
                        observedNative = true
                        break
                    }
                }
                if (observedNative) break
            }
            require(observedNative) {
                "Packaged native provenance was not exposed through DayAssessment"
            }
        }
    }
}

fun nativeEventDates(data: File): Map<String, Set<String>> {
    val calendars = linkedMapOf<String, MutableSet<String>>()
    ZipFile(data).use { archive ->
        val entries = archive.entries().asSequence().toList()
        val nativeCalendars =
            entries
                .filter { it.name.matches(Regex("bdc/calendars/[^/]+/metadata\\.json")) }
                .filter { entry ->
                    archive.getInputStream(entry).bufferedReader().use { reader ->
                        reader.readText().contains("nominal_native_date")
                    }
                }
                .map { it.name.split('/')[2] }
                .toSet()
        for (calendarId in nativeCalendars) {
            val entry = archive.getEntry("bdc/calendars/$calendarId/holidays.csv") ?: continue
            val dates = calendars.getOrPut(calendarId) { linkedSetOf() }
            archive.getInputStream(entry).bufferedReader().useLines { lines ->
                lines.drop(1).filter { it.isNotBlank() }.forEach { dates.add(it.substringBefore(',')) }
            }
        }
    }
    return calendars
}
