plugins {
    java
    application
    id("com.diffplug.spotless") version "6.25.0"
}

group = "com.bdc"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.17.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0")
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("net.jqwik:jqwik:1.8.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("com.bdc.cli.Main")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
    workingDir = rootProject.projectDir
    // Pass system properties to tests
    systemProperty("updateGoldens", System.getProperty("updateGoldens", "false"))
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    workingDir = rootProject.projectDir
}

spotless {
    java {
        googleJavaFormat()
    }
}

tasks.register("installGitHooks") {
    description = "Installs git pre-commit hook for spotlessApply"
    group = "git hooks"

    doLast {
        val gitDir = rootProject.projectDir.resolve(".git")
        val sourceHook = rootProject.projectDir.resolve("scripts/hooks/pre-commit")

        // Check if .git exists and is a directory (not a file, as in worktrees)
        if (!gitDir.exists()) {
            throw GradleException("Not a git repository: .git directory not found at ${gitDir.absolutePath}")
        }
        if (!gitDir.isDirectory) {
            throw GradleException("Unsupported git setup: .git is not a directory (possibly a worktree). " +
                "Please install hooks manually or run from the main repository.")
        }

        val hooksDir = gitDir.resolve("hooks")
        if (!hooksDir.exists()) {
            hooksDir.mkdirs()
            println("Created hooks directory: ${hooksDir.absolutePath}")
        }

        if (!sourceHook.exists()) {
            throw GradleException("Source hook not found: ${sourceHook.absolutePath}")
        }

        val targetHook = hooksDir.resolve("pre-commit")
        sourceHook.copyTo(targetHook, overwrite = true)
        targetHook.setExecutable(true)
        println("Installed pre-commit hook to ${targetHook.absolutePath}")
    }
}
