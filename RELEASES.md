# Version 1.0.4 (2018-06-05)

* Support eclipse project (experimental).
* Fix broken custom source formatter.
* Fix broken optimize import.
* Fix decompiled source parse error.
* Decrease cpu usage  a little.
* Fix some bugs and improve stability.

# Version 1.0.3 (2018-05-24)

* Support import completion from symbol (added import-at-point function).
* Completion matcher is selectable (default is prefix matcher).
* Fix broken static import completion.
* Fix import statement sort (in case google-java-format).
* Fix some bugs and improve stability.

# Version 1.0.2 (2018-05-08)

* Show progress when meghanada downloading server module
* Fix some bugs and improve stability.

# Version 1.0.1 (2018-04-26)

* Disable full-text-search by default
* Supported import static method completion (experimental)
* Fix package completion
* Fix some bugs and improve stability.

# Version 1.0.0 (2018-04-07)

## Highlights

* Change to download server module from setup program
* Add full-featured text search as search-everywhere (meghanada-searcheverywhere)
* Supported to automatically assign port number
* Decrease jar file size
* Fix bugs and improve stability.

# Version 0.9.2 (2018-02-22)

* Fix bugs and improve stability.
* Update some libraries

# Version 0.9.1 (2018-02-14)

* Initial support for Windows
* Experimental support Android plugin for gradle. (supported only 3.0.0+)
* Update some libraries
* Fix bugs and improve stability.

# Version 0.9.0 (2017-12-31)

* Initial support for Java 9
* Changeable meghanada cache directory
* Update some libraries
* Fix bugs and improve stability.

# Version 0.8.4 (2017-11-29)

* Update some libraries
* Fix bugs and improve stability

# Version 0.8.3 (2017-07-25)

* Support external debugger
* Fix autocompletion when use multiline statement
* Fix bugs and improve stability.

# Version 0.8.2 (2017-06-29)

## Highlights

* Add type information command (meghanada-typeinfo)

# Version 0.8.1 (2017-06-26)

* Fix non-escaped code string used for reference.
* Fix implicit type conversion bugs

# Version 0.8.0 (2017-06-26)

## Highlights

* Support main class execution (meghanada-exec-main).
* Support on the fly syntax checking (and analyze, compile) .
* Support search reference.
* Fix bugs and improve stability.

# Version 0.7.13 (2017-06-12)

* Fix cache update bugs

# Version 0.7.11 (2017-06-09)

* Change to use fast-serialization.
* Change to use xodous for cache backend.
* Change not to build gradle subproject by default
* Fix auto change project.
* Fix jump to references to super class's field.
* Fix optimize import is broken.
* Fix bugs and improve stability.

# Version 0.7.10 (2017-05-26)

* Fix incorrect compile target file.
* Fix import completion.
* Fix compilation sort.
* Fix bugs and improve stability.

# Version 0.7.9 (2017-05-24)

* Change completion sort to a better sort.
* Fix Sexp parse error.
* Fix crash when displaying eldoc.
* Fix an empty line on optimize import.
* Fix many bugs and improve stability.

# Version 0.7.8 (2017-05-22)

* Fix many bugs and improve stability.

# Version 0.7.7 (2017-05-18)

* Fix many bugs and improve stability.

# Version 0.7.6 (2017-05-16)

* Change sort order of the candidates to more better
* Support maven parent pom
* Add server error logger
* Change default formatter to google-java-format
* Support make import from eclipse code format settings (meghanadaFormatter.xml)
* Fix many bugs and improve stability.

# Version 0.7.5 (2017-04-27)

* Exclude anonymous class from candidates
* Use maven ModelBuilder

# Version 0.7.4 (2017-04-13)

* Fix import all and optimize import bugs
* Fix jump to enum declaration bugs

# Version 0.7.3 (2017-04-03)

* Fix optimize import
* Fix el-doc API bugs

# Version 0.7.2 (2017-03-28)

* Fix NPE #11
* Improve code fomatter

# Version 0.7.1 (2017-03-23)

* Fix jump to declaration bugs

# Version 0.7.0 (2017-03-18)

## Highlights

* Support show declaration (eldoc support)
* Using kryo 4 and improve cache performance
* Improve gradle sub module build performance
* Fix zip and file stream memory leaks
* Fix broken android project completions
* Fix many bugs.

# Version 0.6.6 (2017-03-01)

* Fix jump to declarations bugs.

# Version 0.6.5 (2017-02-28)

* Fix jump to declarations for variadic method.
* Fix parse try resources.
* Supported jump to third party library source.(from sources.jar or decompiled file)

# Version 0.6.4 (2017-02-24)

* Fix jump from method refernce.
* Supported jump to java standard lib source.

# Version 0.6.3 (2017-02-22)

* Fix jump to declarations for overload method.
* Fix wildcard type completion.
* Fix cache error log.
* Reduce jar size.

# Version 0.6.2 (2017-02-20)

* Fix jump to declarations on method name.
* Change cache hash filename.

# Version 0.6.1 (2017-02-17)

* Fix type analyze bugs.
* Fix write cache bugs.
* Fix optimize import bugs.
* Fix some bugs.
* Add bazel build

# Version 0.6.0 (2017-02-13)

## Highlights

* Support android plugin for gradle. (experimental. supported only 2.2.0+)
* Add clear cache mode. (-c option)
* Add class index auto reload.
* Add project auto reload.
* Change cache format.
* Fix nested class completion.
* Fix some bugs.

# Version 0.5.0 (2017-01-31)

## Highlights

* Add code format command.
* Improve incremental build.
* Fix method reference bugs.
* Use zstd compression.

# Version 0.4.0 (2017-01-25)

## Highlights

* Improve gradle integration. build with dependency modules (experimental)
* Improve incremental build.
* Fix some cache bugs.

# Version 0.3.1 (2017-01-21)

## Highlights

* Fix local variable completion.

# Version 0.3.0 (2017-01-20)

## Highlights

* Use compiler API and improve lambda and method reference support.
* Improve compile and analyze performance.
* Decrease memory usage.
* Fix some bugs.

# Version 0.2.4 (2016-10-18)

## Highlights

* To begin supporting annotaion completion.
* Add autoupdate server module.
