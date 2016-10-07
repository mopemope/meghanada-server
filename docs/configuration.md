# Configuration

## Configuration from environment

### MEGHANADA_HOME

TODO

### MEGHANADA_LOG_LEVEL

TODO

### MEGHANADA_GRADLE_VERSION

TODO

### MEGHANADA_MAVEN_PATH

TODO

### MEGHANADA_FAST_BOOT

TODO

### MEGHANADA_CLASS_FUZZY_SEARCH

TODO

### MEGHANADA_SOURCE_CACHE

TODO

### MEGHANADA_ANALYZE_ALL

TODO

## Meghanada config file

Meghanada can be customized and override project settings by `.meghanada.conf`.
`.meghanada.conf` location should be project root.

`.meghanada.conf` layout below.

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

# Set include file filter
# Type: string list
#
# Filter strings are must regex string.
include-file = [".*Parser*." ... ]

# Set exclude file filter
# Type: string list
#
# Filter strings are must regex string.
exclude-file = [".*TEST*." ... ]

```

## Example

### android studio project

It is to use `meghanada` in Android Studio project.

First step, Run `./gradlew build` and generate `R.java`.
2nd step, write `.meghanada.conf`

#### app/build.gradle

```
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.android.support:appcompat-v7:24.2.1'
    compile 'com.android.support:design:24.2.1'
    testCompile 'junit:junit:4.12'
}
```

#### app/.meghanada.conf

```
# use debug
compile-source = "1.7"
compile-target = "1.7"
sources = ["build/generated/source/r/debug"]
output = "build/intermediates/classes/debug"
test-output = "build/intermediates/classes/test/debug"

# add dependencies
# com.android.support:appcompat-v7:24.2.1
# com.android.support:design:24.2.1
# android-support-v4
#
dependencies = ["/opt/android-sdk/platforms/android-24/android.jar",
"/opt/android-sdk/extras/android/support/v7/appcompat/libs/android-support-v7-appcompat.jar",
"/opt/android-sdk/extras/android/support/v7/appcompat/libs/android-support-v4.jar",
"/opt/android-sdk/extras/android/support/design/libs/android-support-design.jar"]

# junit 4.12
# replace your path
test-dependencies = ["/home/ma2/.gradle/caches/modules-2/files-2.1/junit/junit/4.12/2973d150c0dc1fefe998f834810d68f278ea58ec/junit-4.12.jar"]

```

Finally. open file.

```
$ emacs app/src/main/java/com/example/ma2/myapplication/MainActivity.java
```
