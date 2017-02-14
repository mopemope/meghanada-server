
LOG4J = "2.8"
GRADLE = "3.3"

maven_jar(
    name = "org_apache_maven_maven_model",
    artifact = "org.apache.maven:maven-model:3.3.9",
)

bind(
    name = "maven-model-jar",
    actual = "@org_apache_maven_maven_model//jar",
)

maven_jar(
    name = "com_leacox_motif_motif",
    artifact = "com.leacox.motif:motif:0.1",
)

bind(
    name = "motif-jar",
    actual = "@com_leacox_motif_motif//jar",
)

maven_jar(
    name = "com_leacox_motif_motif_hamcrest",
    artifact = "com.leacox.motif:motif-hamcrest:0.1",
)

bind(
    name = "motif-hamcrest-jar",
    actual = "@com_leacox_motif_motif_hamcrest//jar",
)

maven_jar(
    name = "com_github_javaparser_javaparser_core",
    artifact = "com.github.javaparser:javaparser-core:2.5.1",
)

bind(
    name = "javaparser-core-jar",
    actual = "@com_github_javaparser_javaparser_core//jar",
)

maven_jar(
    name = "org_apache_logging_log4j_log4j_core",
    artifact = "org.apache.logging.log4j:log4j-core:" + LOG4J,
)

bind(
    name = "log4j-core-jar",
    actual = "@org_apache_logging_log4j_log4j_core//jar",
)

maven_jar(
    name = "org_apache_logging_log4j_log4j_api",
    artifact = "org.apache.logging.log4j:log4j-api:" + LOG4J,
)

bind(
    name = "log4j-api-jar",
    actual = "@org_apache_logging_log4j_log4j_api//jar",
)

maven_jar(
    name = "org_apache_logging_log4j_log4j_slf4j_impl",
    artifact = "org.apache.logging.log4j:log4j-slf4j-impl:" + LOG4J,
)

bind(
    name = "log4j-slf4j-impl-jar",
    actual = "@org_apache_logging_log4j_log4j_slf4j_impl//jar",
)

maven_jar(
    name = "org_apache_commons_commons_lang3",
    artifact = "org.apache.commons:commons-lang3:3.5",
)

bind(
    name = "commons-lang3-jar",
    actual = "@org_apache_commons_commons_lang3//jar",
)

maven_jar(
    name = "commons_cli_commons_cli",
    artifact = "commons-cli:commons-cli:1.3.1"
)

bind(
    name = "commons-cli-jar",
    actual = "@commons_cli_commons_cli//jar",
)

maven_jar(
    name = "org_gradle_gradle_tooling_api",
    artifact = "org.gradle:gradle-tooling-api:" + GRADLE,
    server = "gradle",
)

bind(
    name = "gradle-tooling-api-jar",
    actual = "@org_gradle_gradle_tooling_api//jar",
)

maven_jar(
    name = "com_google_guava_guava",
    artifact = "com.google.guava:guava:21.0",
)

bind(
    name = "guava-jar",
    actual = "@com_google_guava_guava//jar",
)

maven_jar(
    name = "org_ow2_asm_asm",
    artifact = "org.ow2.asm:asm:5.1"
)

bind(
    name = "asm-jar",
    actual = "@org_ow2_asm_asm//jar",
)

maven_jar(
    name = "com_esotericsoftware_kryo",
    artifact = "com.esotericsoftware:kryo:3.0.3",
)

bind(
    name = "kryo-jar",
    actual = "@com_esotericsoftware_kryo//jar",
)

maven_jar(
    name = "com_typesafe_config",
    artifact = "com.typesafe:config:1.3.1",
)

bind(
    name = "config-jar",
    actual = "@com_typesafe_config//jar",
)

maven_jar(
    name = "org_atteo_evo_inflector",
    artifact = "org.atteo:evo-inflector:1.2.2",
)

bind(
    name = "evo-inflector-jar",
    actual = "@org_atteo_evo_inflector//jar",
)

maven_jar(
    name = "junit_junit",
    artifact = "junit:junit:4.12",
)

bind(
    name = "junit-jar",
    actual = "@junit_junit//jar",
)

maven_jar(
    name = "org_jboss_forge_roaster_roaster_api",
    artifact = "org.jboss.forge.roaster:roaster-api:2.19.5.Final",
)

bind(
    name = "roaster-api-jar",
    actual = "@org_jboss_forge_roaster_roaster_api//jar",
)

maven_jar(
    name = "org_jboss_forge_roaster_roaster_jdt",
    artifact = "org.jboss.forge.roaster:roaster-jdt:2.19.5.Final",
)

bind(
    name = "roaster-jdt-jar",
    actual = "@org_jboss_forge_roaster_roaster_jdt//jar",
)

maven_jar(
    name = "com_github_luben_zstd_jni",
    artifact = "com.github.luben:zstd-jni:1.1.3",
)

bind(
    name = "zstd-jni-jar",
    actual = "@com_github_luben_zstd_jni//jar",
)

maven_jar(
    name = "com_android_tools_build_gradle",
    artifact = "com.android.tools.build:gradle:2.2.3",
    server = "bintray",
)

bind(
    name = "android-gradle-jar",
    actual = "@com_android_tools_build_gradle//jar",
)

maven_jar(
    name = "com_android_tools_build_builder_model",
    artifact = "com.android.tools.build:builder-model:2.2.3",
    server = "bintray",
)

bind(
    name = "android-builder-model-jar",
    actual = "@com_android_tools_build_builder_model//jar",
)

maven_jar(
    name = "org_codehaus_plexus_plexus_utils",
    artifact = "org.codehaus.plexus:plexus-utils:3.0.22",
)

bind(
    name = "plexus-utils-jar",
    actual = "@org_codehaus_plexus_plexus_utils//jar",
)

maven_jar(
    name = "org_objenesis_objenesis",
    artifact = "org.objenesis:objenesis:2.1",
)

bind(
    name = "objenesis-jar",
    actual = "@org_objenesis_objenesis//jar",
)

maven_jar(
    name = "com_esotericsoftware_reflectasm",
    artifact = "com.esotericsoftware:reflectasm:1.10.1"
)

bind(
    name = "reflectasm-jar",
    actual = "@com_esotericsoftware_reflectasm//jar",
)

maven_jar(
    name = "com_esotericsoftware_minlog",
    artifact = "com.esotericsoftware:minlog:1.3.0",
)

bind(
    name = "minlog-jar",
    actual = "@com_esotericsoftware_minlog//jar",
)

maven_jar(
    name = "org_slf4j_slf4j_api",
    artifact = "org.slf4j:slf4j-api:1.7.21",
)

bind(
    name = "slf4j-api-jar",
    actual = "@org_slf4j_slf4j_api//jar",
)

maven_jar(
    name = "org_hamcrest_hamcrest_core",
    artifact = "org.hamcrest:hamcrest-core:1.3",
)

bind(
    name = "hamcrest-core-jar",
    actual = "@org_hamcrest_hamcrest_core//jar",
)

maven_jar(
    name = "com_android_tools_build_gradle_api",
    artifact = "com.android.tools.build:gradle-api:2.2.3",
    server = "bintray",
)

bind(
    name = "android-gradle-api-jar",
    actual = "@com_android_tools_build_gradle_api//jar",
)

maven_jar(
    name = "io_takari_junit_takari_cpsuite",
    artifact = "io.takari.junit:takari-cpsuite:1.2.7",
)

bind (
    name = "takari-cpsuite-jar",
    actual = "@io_takari_junit_takari_cpsuite//jar",
)

maven_server(
    name = "gradle",
    url = "http://repo.gradle.org/gradle/libs-releases-local",
)

maven_server(
    name = "bintray",
    url = "https://jcenter.bintray.com",
)
