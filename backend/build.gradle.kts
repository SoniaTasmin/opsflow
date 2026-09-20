plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.spotless)
}

group = "com.opsflow"
version = "0.1.0-SNAPSHOT"
description = "OpsFlow — reporting, approval and bulk-import platform"

java {
    toolchain {
        // Spring Boot 4.1 supports Java 17-26. Java 21 LTS is deliberate:
        // see docs/adr/0007-technology-versions.md.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // Boot 4 renamed the OAuth2 starters under a `security-` prefix.
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Spring Boot 4 no longer auto-configures Flyway from the bare dependency.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    runtimeOnly("org.postgresql:postgresql")

    testImplementation(platform(libs.testcontainers.bom))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // Spring Boot 4 splits the test slices into per-technology modules;
    // @WebMvcTest is no longer part of spring-boot-starter-test.
    testImplementation("org.springframework.boot:spring-boot-webmvc-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation(libs.archunit)
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-parameters"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

spotless {
    java {
        target("src/**/*.java")
        palantirJavaFormat(libs.versions.palantirJavaFormat.get())
        removeUnusedImports()
        licenseHeaderFile(rootProject.file("config/spotless/license-header.txt"))
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint()
    }
}

/**
 * A wildcard import hides what a class actually depends on, which is exactly what the
 * module-boundary rules exist to make visible.
 *
 * Spotless cannot express this: a `custom` step holds a closure that Gradle's configuration
 * cache cannot serialize. ArchUnit cannot either, because import form is erased in bytecode.
 * So it is a small task with a declared file input, which stays configuration-cache safe.
 */
val checkNoWildcardImports by tasks.registering {
    val javaSources = fileTree("src") { include("**/*.java") }
    inputs.files(javaSources).withPropertyName("javaSources")
    outputs.upToDateWhen { true }
    doLast {
        val pattern = Regex("""(?m)^import\s+(static\s+)?[\w.]+\.\*;""")
        val offenders = javaSources.files.filter { pattern.containsMatchIn(it.readText()) }
        require(offenders.isEmpty()) {
            "Wildcard imports are not permitted:\n" + offenders.joinToString("\n") { "  $it" }
        }
    }
}

tasks.named("check") { dependsOn(checkNoWildcardImports) }
