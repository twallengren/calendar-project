plugins {
    `java-library`
    `maven-publish`
    signing
}

group = "io.github.twallengren"
version = rootProject.extra["publishVersion"] as String

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
    withJavadocJar()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly(project(":data"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    workingDir = rootProject.projectDir
    // The parity test compares the facade against the published artifacts themselves.
    systemProperty("bdc.blessedDir", rootProject.projectDir.resolve("blessed").absolutePath)
}

tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "bdc-calendar-core"
            from(components["java"])
            pom {
                name.set("BDC Calendar Core")
                description.set(
                    "Dependency-free business-day calendar query API: date streams, joint " +
                        "calendars and the BusinessCalendars facade over the bundled datasets."
                )
                url.set("https://github.com/twallengren/calendar-project")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
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
    // Signing is opt-in: local builds and CI dry runs have no key and must still publish.
    isRequired = false
    val signingKey = project.findProperty("signingKey") as String?
    val signingPassword = project.findProperty("signingPassword") as String?
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
