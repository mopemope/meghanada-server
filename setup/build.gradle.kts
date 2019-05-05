import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.RecordingCopyTask
import org.ajoberstar.grgit.Grgit

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    id("java")
    id("maven")
    id("application")
    id("com.github.johnrengelman.shadow").version("5.0.0")
    id("com.jfrog.bintray").version("1.8.4")
}

val group = "meghanada"
val setupVersion = "0.0.2"
var buildVersion = "release"

val gitFile = File("./.git")
if (gitFile.exists()) {
    val grgit = Grgit.open()
    buildVersion = grgit.head().abbreviatedId
}
val longVersion = "$setupVersion-$buildVersion"
val date: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
val applicationName = "meghanada-setup"

base {
    archivesBaseName = applicationName
    version = setupVersion
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    implementation("commons-cli:commons-cli:1.4")
}

application {
    mainClassName = "meghanada.SetupMain"
}

bintray {
    user = System.getenv("BINTRAY_USER")
    key = System.getenv("BINTRAY_KEY")

    publish = true

    filesSpec(delegateClosureOf<RecordingCopyTask> {
        from("build/libs")
        into(".")
    })

    pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
        repo = "meghanada"
        name = "meghanada-setup"
        vcsUrl = "https://github.com/mopemope/meghanada-server.git"
        githubRepo = "mopemope/meghanada-server"
        githubReleaseNotesFile = "README.md"
        setLicenses("GPL-3.0")
        setLabels("java", "emacs")

        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = "$setupVersion"
            desc = "Meghanada Server setup $setupVersion"
        })

    })
}


tasks {

    val processResources by existing
    val classes by existing
    val shadowJar by existing
    val clean by existing

    withType<ShadowJar> {}

    val embedVersion = register<Copy>("embedVersion") {
        from("src/main/resources/VERSION")
        into("build/resources/main")
        expand(mapOf("buildDate" to date, "version" to longVersion, "appName" to applicationName))
        dependsOn(processResources)
    }

    classes {
        dependsOn(embedVersion)
    }

    val installEmacsHome = register<Copy>("installEmacsHome") {
        val home = System.getProperty("user.home")
        from("build/libs/meghanada-setup-${setupVersion}.jar")
        into("$home/.emacs.d/meghanada/")
        dependsOn(shadowJar)
    }

    clean {
        doLast({
            file(".meghanada").deleteRecursively()
        })
    }
}
