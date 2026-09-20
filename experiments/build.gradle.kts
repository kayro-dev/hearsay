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
    mainClass = "hearsay.experiments.BubbleSweep"
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

/** Calibration across village sizes: ./gradlew :experiments:sizes --args="..." */
tasks.register<JavaExec>("sizes") {
    group = "application"
    description = "Sweeps village size on the movement model and on a recorded session."
    mainClass = "hearsay.experiments.VillageSizes"
    classpath = sourceSets["main"].runtimeClasspath
}

/** Does who you tell matter: ./gradlew :experiments:planters --args="..." */
tasks.register<JavaExec>("planters") {
    group = "application"
    description = "Splits the variation in a lie's spread into luck and who was told."
    mainClass = "hearsay.experiments.PlanterEffect"
    classpath = sourceSets["main"].runtimeClasspath
}

/** How long the market remembers its traders: ./gradlew :experiments:window --args="..." */
tasks.register<JavaExec>("window") {
    group = "application"
    description = "Sweeps the market window against a recorded session and the model."
    mainClass = "hearsay.experiments.MarketWindow"
    classpath = sourceSets["main"].runtimeClasspath
}
