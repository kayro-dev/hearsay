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

    doFirst {
        if (serverPluginsDirectory.isNullOrBlank()) {
            throw GradleException(
                "No server path. Create local.properties in the project root with:\n" +
                    "  server.plugins.dir=/path/to/your/server/plugins\n" +
                    "It is gitignored, since it is specific to your machine."
            )
        }
    }

    from(tasks.jar)
    into(serverPluginsDirectory ?: "build/unset")

    doLast {
        logger.lifecycle("Copied to $serverPluginsDirectory.")
        logger.lifecycle("Restart the server to load it. Do not use /reload: it is known")
        logger.lifecycle("to leave plugins in a broken state.")
    }
}
