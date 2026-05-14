plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.jpa")
}

apply(from = rootProject.file("gradle/java-integration-test.gradle"))

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))
    testImplementation(platform("org.springframework.boot:spring-boot-dependencies:3.2.4"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(project(":test-support"))

    "integrationTestImplementation"("org.springframework.boot:spring-boot-starter-test")
    "integrationTestImplementation"("org.testcontainers:junit-jupiter:1.19.3")
    "integrationTestImplementation"("org.testcontainers:kafka:1.19.3")
    "integrationTestImplementation"("org.testcontainers:postgresql:1.19.3")
    "integrationTestImplementation"(project(":test-support"))
}

tasks.jar { enabled = false }
