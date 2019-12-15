# Meghanada-Server

[![Join the chat at https://gitter.im/mopemope/meghanada-server](https://badges.gitter.im/mopemope/meghanada-server.svg)](https://gitter.im/mopemope/meghanada-server?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) 
[![Patreon](https://img.shields.io/badge/patreon-become%20a%20patron-red.svg)](https://www.patreon.com/mopemope)
[![Github](https://github.com/mopemope/meghanada-server/workflows/Java%20CI/badge.svg)](https://github.com/mopemope/meghanada-server/actions)

A Java IDE Server for your editor. Java IDE-like features to your favourite text editor.

## Features

Some planned and implemented features:

* Server supports a network connection
* `Gradle` and `Maven` and `Eclipse` project support
* Run build tool task
* Compile your project
* Support annotaion processor
* Analyze java source (hooks into build)
* Code completion
* Optimize import
* Jump declaration (without source)
* Run junit test (include test runner)
* Search references
* Full-featured text search (default off)

Meghanada-Server support only emacs client (meghanada-mode)

The Meghanada architecture is almost the same as `ensime`. It is client server model.

Meghanada updates any information when saving and compile the java file.

## Building

### Requirement

* JDK 8 or later.

If your project were maven project, It needs `maven` and add `mvn` command your `$PATH`.

### Build jar

```
./gradlew clean goJF check shadowJar
```

## Usage Server

See help.

```
java -jar path/to/meghanada.jar --help
```

```
usage: meghanada server
    --gradle-version <arg>   set use gradle version
 -h,--help                   show help
 -l,--log <arg>              log file location. default:
                             /tmp/meghanada_server.log
    --output <arg>           output format (sexp, csv, json). default:
                             sexp
 -p,--port <arg>             set server port. default: 55555
 -r,--project <arg>          set project root path. default: current path
 -v,--verbose                show verbose message (DEBUG)
    --version                show version information
 -vv,--traceVerbose          show verbose message (TRACE)
```


## Run Server

```
java -jar path/to/meghanada.jar
```

Recommend settings jvm args.

```
java -XX:+UseConcMarkSweepGC -XX:SoftRefLRUPolicyMSPerMB=50 -Xverify:none -Xms256m -Dfile.encoding=UTF-8 -jar path/to/meghanada.jar
```

Meghanada-Server is required JDK 8 or later (not JRE). It used Compiler API.

## Contributing

Contributions are extremely welcome!

Please check execute the following command before contributing. then please push PR to `dev` branch.

```
./gradlew clean goJF check
```

### Customize project manually

* Write `.meghanada.conf` on project root.

example:

```
# This is an annotated reference of the options that may be set in a
# .meghanada.conf file.
#

# Set JAVA_HOME
# Type: string
java-home = "/usr/lib/jvm/default

# Set java version
# Type: string
#
# It is same effect following code.
# System.setProperty("java.specification.version", val);
java-version = "1.8"

# Set source compatibility
# Type: string
compile-source = "1.8"

# Set target compatibility
# Type: string
compile-target = "1.8"

java11-javac-args = [
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.resources=ALL-UNNAMED",
                 "--add-exports",
                 "jdk.compiler/com.sun.tools.javac.resources=ALL-UNNAMED",
                 "--add-exports",
                 "java.management/sun.management=ALL-UNNAMED",
]

# Set dependencies file list (jar filepath)
# Type: string list
dependencies = ["/home/user/.m2/repository/org/apache/maven/maven-model/3.3.9/maven-model-3.3.9.jar", "/home/user/.m2/repository/org/codehaus/plexus/plexus-utils/3.0.22/plexus-utils-3.0.22.jar", "/home/user/.m2/repository/org/apache/commons/commons-lang3/3.4/commons-lang3-3.4.jar" ... ]

# Set test dependencies file list (jar filepath)
# Type: string list
test-dependencies = ["/home/ma2/.m2/repository/junit/junit/4.12/junit-4.12.jar" ... ]

# Set source directories
# Type: string list
sources = ["src/main/java"]

# Set resource directories
# Type: string list
resources = ["src/main/resources"]

# Set classes output directory
# Type: string
output = "build/main/classes"

# Set test source directories
# Type: string list
test-sources = ["src/test/java"]

# Set testt resource directories
# Type: string list
test-resources = ["src/test/resources"]

# Set test classes output directory
# Type: string
test-output = "build/test/classes"

```

## License

GPL v3, See [LICENSE](LICENSE) file.
