# Next Release

# 1.3.2 (2021-01-01)

## Fixed

- Fix CVE-2021-44228

# 1.3.1 (2020-08-15)

## Added

- Add the function of sending an error report.

## changed

- Update gradle to 6.6.
- Fix to use the latest library.
- Improve compile error logs.
- Improve detection of dependent libraries in gradle projects.
- Fix to keep a class cache for each project.
- Shorten the File path in the log

## Fixed

# 1.3.0 (2020-05-07)

## Added

- global code formatter configuration file support.

## Changed

- fix the thread pool settings to improve performance.
- improve performance a bit by using the bloom filter to find references.
- fix to use the latest library.

## Fixed

- fix a reference search that was incorrect.

# 1.2.1 (2019-12-15)

- fix some bugs and improve stability.

# 1.2.0 (2019-05-26)

- collect telemetry data of meghanada-server (default disabled).
- fix constructor completion format.
- fix array completion.
- fix file paths not being processed correctly in windows.
- fix some bugs and improve stability.

# 1.1.2 (2019-05-09)

- fix NoClassDefFoundError when run junit.
- fix some bugs and improve stability.

# 1.1.1 (2019-05-07)

- fix detect gradle project root.
- fix some bugs and improve stability.

# 1.1.0 (2019-05-06)

- support open j9 (experimental).
- fix some bugs and improve stability.

# 1.0.14 (2019-04-26)

- works without the project tool (mvn, gradle, etc).
- support java 12 (experimental).
- add jvm option setting (meghanada-jvm-option).
- support company-box.
- fix some bugs and improve stability.

# 1.0.13 (2019-02-25)

- support build.gradle.kts.
- fix some bugs and improve stability.

# 1.0.12 (2019-01-22)

- Support Java 11.
- Fix some bugs and improve stability.

# 1.0.11 (2019-01-09)

- Improve completion order.
- Support annotaion value completion.
- Fix some bugs and improve stability.

# 1.0.10 (2018-11-16)

- Support junit5 and junit4. (thanks @roman)
- Add extra arguments to exec/debug main commands. (thanks @roman)
- Fix some bugs and improve stability.

# 1.0.9 (2018-09-10)

- Add jump-symbol. (thanks @iocanel)
- Fix search for junit test.
- Display maven error details.
- Fix some bugs and improve stability.

# 1.0.8 (2018-07-18)

- Update libs.

# 1.0.7 (2018-06-28)

- Fix chained completion.

# 1.0.6 (2018-06-20)

- Fix broken completion.
- Fix some bugs and improve stability.

# 1.0.5 (2018-06-13)

- Fix broken completion.
- Fix some bugs and improve stability.

# 1.0.4 (2018-06-05)

- Support eclipse project (experimental).
- Fix broken custom source formatter.
- Fix broken optimize import.
- Fix decompiled source parse error.
- Decrease cpu usage a little.
- Fix some bugs and improve stability.

# 1.0.3 (2018-05-24)

- Support import completion from symbol (added import-at-point function).
- Completion matcher is selectable (default is prefix matcher).
- Fix broken static import completion.
- Fix import statement sort (in case google-java-format).
- Fix some bugs and improve stability.

# 1.0.2 (2018-05-08)

- Show progress when meghanada downloading server module
- Fix some bugs and improve stability.

# 1.0.1 (2018-04-26)

- Disable full-text-search by default
- Supported import static method completion (experimental)
- Fix package completion
- Fix some bugs and improve stability.

# 1.0.0 (2018-04-07)

## Highlights

- Change to download server module from setup program
- Add full-featured text search as search-everywhere (meghanada-searcheverywhere)
- Supported to automatically assign port number
- Decrease jar file size
- Fix bugs and improve stability.

# 0.9.2 (2018-02-22)

- Fix bugs and improve stability.
- Update some libraries

# 0.9.1 (2018-02-14)

- Initial support for Windows
- Experimental support Android plugin for gradle. (supported only 3.0.0+)
- Update some libraries
- Fix bugs and improve stability.

# 0.9.0 (2017-12-31)

- Initial support for Java 9
- Changeable meghanada cache directory
- Update some libraries
- Fix bugs and improve stability.

# 0.8.4 (2017-11-29)

- Update some libraries
- Fix bugs and improve stability

# 0.8.3 (2017-07-25)

- Support external debugger
- Fix autocompletion when use multiline statement
- Fix bugs and improve stability.

# 0.8.2 (2017-06-29)

## Highlights

- Add type information command (meghanada-typeinfo)

# 0.8.1 (2017-06-26)

- Fix non-escaped code string used for reference.
- Fix implicit type conversion bugs

# 0.8.0 (2017-06-26)

## Highlights

- Support main class execution (meghanada-exec-main).
- Support on the fly syntax checking (and analyze, compile) .
- Support search reference.
- Fix bugs and improve stability.

# 0.7.13 (2017-06-12)

- Fix cache update bugs

# 0.7.11 (2017-06-09)

- Change to use fast-serialization.
- Change to use xodous for cache backend.
- Change not to build gradle subproject by default
- Fix auto change project.
- Fix jump to references to super class's field.
- Fix optimize import is broken.
- Fix bugs and improve stability.

# 0.7.10 (2017-05-26)

- Fix incorrect compile target file.
- Fix import completion.
- Fix compilation sort.
- Fix bugs and improve stability.

# 0.7.9 (2017-05-24)

- Change completion sort to a better sort.
- Fix Sexp parse error.
- Fix crash when displaying eldoc.
- Fix an empty line on optimize import.
- Fix many bugs and improve stability.

# 0.7.8 (2017-05-22)

- Fix many bugs and improve stability.

# 0.7.7 (2017-05-18)

- Fix many bugs and improve stability.

# 0.7.6 (2017-05-16)

- Change sort order of the candidates to more better
- Support maven parent pom
- Add server error logger
- Change default formatter to google-java-format
- Support make import from eclipse code format settings (meghanadaFormatter.xml)
- Fix many bugs and improve stability.

# 0.7.5 (2017-04-27)

- Exclude anonymous class from candidates
- Use maven ModelBuilder

# 0.7.4 (2017-04-13)

- Fix import all and optimize import bugs
- Fix jump to enum declaration bugs

# 0.7.3 (2017-04-03)

- Fix optimize import
- Fix el-doc API bugs

# 0.7.2 (2017-03-28)

- Fix NPE #11
- Improve code fomatter

# 0.7.1 (2017-03-23)

- Fix jump to declaration bugs

# 0.7.0 (2017-03-18)

## Highlights

- Support show declaration (eldoc support)
- Using kryo 4 and improve cache performance
- Improve gradle sub module build performance
- Fix zip and file stream memory leaks
- Fix broken android project completions
- Fix many bugs.

# 0.6.6 (2017-03-01)

- Fix jump to declarations bugs.

# 0.6.5 (2017-02-28)

- Fix jump to declarations for variadic method.
- Fix parse try resources.
- Supported jump to third party library source.(from sources.jar or decompiled file)

# 0.6.4 (2017-02-24)

- Fix jump from method refernce.
- Supported jump to java standard lib source.

# 0.6.3 (2017-02-22)

- Fix jump to declarations for overload method.
- Fix wildcard type completion.
- Fix cache error log.
- Reduce jar size.

# 0.6.2 (2017-02-20)

- Fix jump to declarations on method name.
- Change cache hash filename.

# 0.6.1 (2017-02-17)

- Fix type analyze bugs.
- Fix write cache bugs.
- Fix optimize import bugs.
- Fix some bugs.
- Add bazel build

# 0.6.0 (2017-02-13)

## Highlights

- Support android plugin for gradle. (experimental. supported only 2.2.0+)
- Add clear cache mode. (-c option)
- Add class index auto reload.
- Add project auto reload.
- Change cache format.
- Fix nested class completion.
- Fix some bugs.

# 0.5.0 (2017-01-31)

## Highlights

- Add code format command.
- Improve incremental build.
- Fix method reference bugs.
- Use zstd compression.

# 0.4.0 (2017-01-25)

## Highlights

- Improve gradle integration. build with dependency modules (experimental)
- Improve incremental build.
- Fix some cache bugs.

# 0.3.1 (2017-01-21)

## Highlights

- Fix local variable completion.

# 0.3.0 (2017-01-20)

## Highlights

- Use compiler API and improve lambda and method reference support.
- Improve compile and analyze performance.
- Decrease memory usage.
- Fix some bugs.

# 0.2.4 (2016-10-18)

## Highlights

- To begin supporting annotaion completion.
- Add autoupdate server module.
