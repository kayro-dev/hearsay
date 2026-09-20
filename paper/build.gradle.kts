import java.io.RandomAccessFile
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

/**
 * Whether a server is holding the world open. Minecraft keeps an exclusive lock on
 * session.lock for as long as it is running, so if the lock cannot be taken, it is.
 *
 * <p>Worth checking, because replacing the jar under a running server breaks it in a way
 * that looks like nothing at all until much later: classes already loaded keep working,
 * and the first one the plugin has not needed yet fails to load. A session that saves only
 * when you stop it is exactly the kind of thing that hits.
 */
fun aServerIsHoldingTheWorldOpen(pluginsFolder: File): Boolean {
    val serverRoot = pluginsFolder.parentFile ?: return false
    val locks = serverRoot.listFiles()?.mapNotNull { File(it, "session.lock").takeIf(File::exists) }
        ?: return false
    return locks.any { lock ->
        try {
            RandomAccessFile(lock, "rw").use { file ->
                val held = file.channel.tryLock()
                if (held == null) true else { held.release(); false }
            }
        } catch (ignored: Exception) {
            true // could not even open it: assume the server has it
        }
    }
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

    if (!project.hasProperty("forceDeploy") && aServerIsHoldingTheWorldOpen(File(destination))) {
        throw GradleException(
            "That server is running. Stop it before deploying.\n\n" +
                "Replacing the jar under a live server breaks its class loading: whatever\n" +
                "the plugin has already used keeps working, and the first class it has not\n" +
                "needed yet cannot be found. Saving a session is the usual casualty, since\n" +
                "nothing loads those classes until you stop.\n\n" +
                "Stop the server, deploy, start it again. If you are certain, pass\n" +
                "-PforceDeploy."
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
