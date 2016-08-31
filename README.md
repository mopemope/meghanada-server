# Meghanada-Server

A Java IDE Server for your editor. Java IDE-like features to your favourite text editor.

<i>WARNING! This is a project beta quality. Under heavy development now.</i>

## Features

Some planned and implemented features:

* Server supports a network connection
* `Gradle` and `Maven`(Beta) project support
* Run build tool task
* Compile your project
* Analyze java source
* Code completion
* Optimize import
* Jump declaration
* Run junit test (include test runner)

Meghanada-Server support only emacs client (meghanada-mode)

## Building

### Requirement

* JDK 8

### Build jar 

```
./gradlew clean shadowJar
```

## Run Server

```
java -jar meghanada.jar
```

Recommend settings jvm args

```
java -XX:+UseConcMarkSweepGC -XX:SoftRefLRUPolicyMSPerMB=50 -Xverify:none -Xms256m -Dfile.encoding=UTF-8 -jar meghanada.jar
```

## Project

### Gradle

TODO

### Maven (Beta)

TODO

### Customize Project Manually

* Write `.meghanada.conf` on project root.

TODO

## Plan

* Support lambda expression
* Support method refrence

## License

GPL v3, See [LICENSE](LICENSE) file.
