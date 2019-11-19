import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.RecordingCopyTask
import org.ajoberstar.grgit.Grgit

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    java
    maven
    `maven-publish`
    application
    id("com.github.johnrengelman.shadow") version "5.1.0"
    id("com.jfrog.bintray") version "1.8.4"
}

val group = "io.github.mopemope"
var serverVersion = "1.2.1"
var buildVersion = "release"

val gitFile = File("./.git")
if (gitFile.exists()) {
    val grgit = Grgit.open()
    val branch = grgit.branch.current().name
    // val branches = listOf("master", "dev")
    // if (!branches.contains(branch)) {
    //     serverVersion = "$serverVersion-$branch"
    // }
    buildVersion = grgit.head().abbreviatedId
}

val longVersion = "$serverVersion-$buildVersion"
val date: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
val applicationName = "meghanada"

val junitVersion = "5.5.2"
val gradleVersion = "6.0.1"
val log4jVersion = "2.12.1"
val xodusVersion = "1.3.124"
val opencensusVersion = "0.24.0"

base {
    archivesBaseName = applicationName
    version = serverVersion
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

dependencies {
    val javaHome: String = System.getProperty("java.home")
    val toolsJar = files("$javaHome/../lib/tools.jar")
    implementation(toolsJar)
    implementation("com.google.googlejavaformat:google-java-format:1.7")
    implementation("org.apache.maven:maven-model-builder:3.6.2")
    implementation("com.leacox.motif:motif:0.1")
    implementation("com.leacox.motif:motif-hamcrest:0.1")
    implementation("com.github.javaparser:javaparser-core:3.15.5")
    implementation("org.apache.logging.log4j:log4j-core:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-api:$log4jVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jVersion")
    implementation("commons-cli:commons-cli:1.4")
    implementation("org.gradle:gradle-tooling-api:$gradleVersion")
    implementation("com.google.guava:guava:28.1-jre")
    implementation("org.ow2.asm:asm:7.2")
    implementation("com.typesafe:config:1.4.0")
    implementation("org.atteo:evo-inflector:1.2.2")

    implementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    implementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    implementation("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
    implementation("org.junit.vintage:junit-vintage-engine:$junitVersion")
    implementation("org.junit.platform:junit-platform-launcher:1.5.2")

    implementation("com.android.tools.build:builder-model:3.4.0")
    implementation("io.takari.junit:takari-cpsuite:1.2.7")
    implementation("org.jboss.windup.decompiler:decompiler-api:4.2.1.Final")
    implementation("org.jboss.windup.decompiler:decompiler-fernflower:4.2.1.Final")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("org.eclipse.jdt:org.eclipse.jdt.core:3.19.0")
    implementation("de.ruedigermoeller:fst:2.56")

    implementation("org.jetbrains.xodus:xodus-query:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-openAPI:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-vfs:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-utils:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-lucene-directory:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-environment:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-entity-store:$xodusVersion")
    implementation("org.jetbrains.xodus:xodus-compress:$xodusVersion")

    implementation("io.opencensus:opencensus-api:$opencensusVersion")
    implementation("io.opencensus:opencensus-impl:$opencensusVersion")
    implementation("io.opencensus:opencensus-contrib-zpages:$opencensusVersion")
    implementation("io.opencensus:opencensus-exporter-trace-stackdriver:$opencensusVersion")
    implementation("io.opencensus:opencensus-exporter-stats-stackdriver:$opencensusVersion")

    implementation("com.github.oshi:oshi-core:3.13.3")
}

application {
    mainClassName = "meghanada.Main"
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
        name = "meghanada"
        vcsUrl = "https://github.com/mopemope/meghanada-server.git"
        githubRepo = "mopemope/meghanada-server"
        githubReleaseNotesFile = "README.md"
        setLicenses("GPL-3.0")
        setLabels("java", "emacs")

        version(delegateClosureOf<BintrayExtension.VersionConfig> {
            name = serverVersion
            desc = "Meghanada Server $serverVersion"
        })

    })
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/mopemope/meghanada-server")
            credentials {
                username = System.getenv("GPR_USER")
                password = System.getenv("GPR_API_KEY")
            }
        }
    }
    publications {
        register("gpr", MavenPublication::class) {
            from(components["java"])
            this.artifactId = "meghanada-server"
        }
    }
}

tasks {

    val processResources by existing
    val classes by existing
    val clean by existing

    val shadowJar = withType<ShadowJar> {
        mergeServiceFiles()
        classifier = null
        exclude("tools.jar")
        relocate("junit.framework", "meghanada.junit.framework")
        relocate("com.github.javaparser", "meghanada.com.github.javaparser")
        relocate("com.google", "meghanada.com.google")
        relocate("org.objectweb.asm", "meghanada.org.objectweb.asm")
    }

    withType<Test> {
        jvmArgs("-Xverify:none")
        testLogging {
            events("PASSED", "FAILED", "SKIPPED")
            setExceptionFormat("full")
        }
    }

    val embedVersion = register<Copy>("embedVersion") {
        from("src/main/resources/VERSION")
        into("build/resources/main")
        expand(Pair("buildDate", date), Pair("version", longVersion), Pair("appName", applicationName))
        dependsOn(processResources)
    }

    classes {
        dependsOn(embedVersion)
    }
    named("publishGprPublicationToGitHubPackagesRepository") {
        dependsOn(shadowJar)
    }

    val installEmacsHome = register<Copy>("installEmacsHome") {
        val home = System.getProperty("user.home")
        from("build/libs/meghanada-$serverVersion.jar")
        into("$home/.emacs.d/meghanada/")
        dependsOn(shadowJar)
    }

    clean {
        doLast {
            file(".meghanada").deleteRecursively()
        }
    }
}
