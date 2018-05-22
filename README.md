# Meghanada-Server

[![Join the chat at https://gitter.im/mopemope/meghanada-server](https://badges.gitter.im/mopemope/meghanada-server.svg)](https://gitter.im/mopemope/meghanada-server?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![CircleCI](https://circleci.com/gh/mopemope/meghanada-server.svg?style=svg)](https://circleci.com/gh/mopemope/meghanada-server)

A Java IDE Server for your editor. Java IDE-like features to your favourite text editor.

## Features

Some planned and implemented features:

* Server supports a network connection
* `Gradle` and `Maven` project support
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

## Project

### Gradle

TODO

### Maven

TODO

### Customize project manually

* Write `.meghanada.conf` on project root.

TODO

## License

GPL v3, See [LICENSE](LICENSE) file.
