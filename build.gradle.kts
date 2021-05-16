plugins {
    // id("com.gradle.build-scan") version "3.0"
    id("com.github.ben-manes.versions") version "0.28.0"
    id("org.ajoberstar.grgit") version "4.0.2"
    id("com.github.sherter.google-java-format") version "0.9"
    id("net.ltgt.errorprone") version "1.2.1"
}

repositories {
    mavenCentral()
    jcenter()
    maven { url = uri("https://maven.google.com") }
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
}

googleJavaFormat {
    toolVersion = "1.7"
    exclude("server/src/test/resources/*")
    exclude("server/out/*")
    exclude("src/test/resources/*")
    exclude("out/*")
}

dependencies {
    errorprone("com.google.errorprone:error_prone_core:2.7.1")
    errorproneJavac("com.google.errorprone:javac:9+181-r4173-1")
}

allprojects {
    repositories {
        mavenCentral()
        jcenter()
        maven { url = uri("https://maven.google.com") }
        maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    }
}
