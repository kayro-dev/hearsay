plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "hearsay.experiments.Sweep"
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

/** The with-and-without-a-rumor experiment: ./gradlew :experiments:bubble --args="..." */
tasks.register<JavaExec>("bubble") {
    group = "application"
    description = "Sweeps market settings, running each seed with and without a planted rumor."
    mainClass = "hearsay.experiments.Bubble"
    classpath = sourceSets["main"].runtimeClasspath
}
