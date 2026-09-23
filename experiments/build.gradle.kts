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

/** Sweeps the position-to-spot rule: ./gradlew :experiments:mapping --args="..." */
tasks.register<JavaExec>("mapping") {
    group = "application"
    description = "Rebuilds a played session's inputs under different spot rules."
    mainClass = "hearsay.experiments.MappingSweep"
    classpath = sourceSets["main"].runtimeClasspath
}

/** How far the price must move to mean anything: ./gradlew :experiments:threshold */
tasks.register<JavaExec>("threshold") {
    group = "application"
    description = "Sweeps observationThreshold against played sessions and the model."
    mainClass = "hearsay.experiments.ObservationThreshold"
    classpath = sourceSets["main"].runtimeClasspath
}

/** The stage 2 gate for a new good: ./gradlew :experiments:goods --args="--good iron" */
tasks.register<JavaExec>("goods") {
    group = "application"
    description = "Judges a good against diamond on the same seeds, with Target."
    mainClass = "hearsay.experiments.GoodTargets"
    classpath = sourceSets["main"].runtimeClasspath
}

/** CalibrationTest's false-failure rate and power: ./gradlew :experiments:guard */
tasks.register<JavaExec>("guard") {
    group = "application"
    description = "Resamples calibration runs from the real model to size CalibrationTest's checks."
    mainClass = "hearsay.experiments.CalibrationGuard"
    classpath = sourceSets["main"].runtimeClasspath
}

/** Can bulk selling start a panic nobody lied about: ./gradlew :experiments:selling */
tasks.register<JavaExec>("selling") {
    group = "application"
    description = "Sells every buyable good as hard as a player can, into quiet and lied-to villages."
    mainClass = "hearsay.experiments.Selling"
    classpath = sourceSets["main"].runtimeClasspath
}

/** The trend anchor, swept as E45's fix: ./gradlew :experiments:trend */
tasks.register<JavaExec>("trend") {
    group = "application"
    description = "Sweeps reading the price against its trailing average: selling, calibration, settling."
    mainClass = "hearsay.experiments.TrendSweep"
    classpath = sourceSets["main"].runtimeClasspath
}
