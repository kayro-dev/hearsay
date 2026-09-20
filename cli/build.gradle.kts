plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "hearsay.cli.Main"
}
