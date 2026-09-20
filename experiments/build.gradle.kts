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

/** Tunes the market wobble on quiet villages: ./gradlew :experiments:noise --args="..." */
tasks.register<JavaExec>("noise") {
    group = "application"
    description = "Runs quiet villages across noise settings and reports how often one panics."
    mainClass = "hearsay.experiments.Noise"
    classpath = sourceSets["main"].runtimeClasspath
}

/** Paired-worlds comparison over many villages: ./gradlew :experiments:worlds --args="..." */
tasks.register<JavaExec>("worlds") {
    group = "application"
    description = "Runs the paired-worlds counterfactual over many parent seeds."
    mainClass = "hearsay.experiments.ManyWorlds"
    classpath = sourceSets["main"].runtimeClasspath
}
