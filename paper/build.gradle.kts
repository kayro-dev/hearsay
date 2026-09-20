import java.util.Properties

plugins {
    java
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    implementation(project(":core"))
    // Provided by the server at runtime, so compileOnly. Coordinates and the required
    // toolchain are from Paper's own docs for 26.2, not from memory.
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

// The server has no idea what Hearsay's core is, so it travels inside the plugin jar.
tasks.jar {
    archiveBaseName = "Hearsay"
    archiveVersion = ""
    from(project(":core").sourceSets["main"].output)
}

/**
 * Where this machine's server keeps its plugins. Personal to whoever is building, so it
 * lives in local.properties, which is not committed.
 */
val serverPluginsDirectory: String? =
    rootProject.file("local.properties").takeIf { it.exists() }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
            .getProperty("server.plugins.dir")
    }

tasks.register<Copy>("deploy") {
    group = "build"
    description = "Builds the plugin and copies it into the server's plugins folder."
    dependsOn(tasks.jar)

    // Held as a plain string local to this block. A task action that reached back out to
    // the script itself would be something the configuration cache cannot store.
    val destination = serverPluginsDirectory

    if (destination.isNullOrBlank()) {
        throw GradleException(
            "No server path. Create local.properties in the project root with:\n" +
                "  server.plugins.dir=/path/to/your/server/plugins\n" +
                "and point it at the plugins folder, not the server folder. It is\n" +
                "gitignored, since the path is yours rather than the project's."
        )
    }

    from(tasks.jar)
    into(destination)

    doLast {
        logger.lifecycle("Copied to $destination")
        logger.lifecycle("Restart the server to load it. Not /reload: that is known to")
        logger.lifecycle("leave plugins in a broken state.")
    }
}
