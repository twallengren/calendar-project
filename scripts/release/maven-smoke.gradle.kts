plugins {
    java
}

repositories {
    mavenCentral()
}

val smoke by configurations.creating
dependencies {
    smoke("io.github.twallengren:bdc-calendar-core:${property("coreVersion")}")
    smoke("io.github.twallengren:bdc-calendar-data:${property("dataVersion")}")
}

tasks.register("resolveRelease") {
    doLast {
        val files = smoke.resolve()
        require(files.any { it.name.startsWith("bdc-calendar-core-") })
        require(files.any { it.name.startsWith("bdc-calendar-data-") })
        println("Resolved released Java artifacts from Maven Central")
    }
}
