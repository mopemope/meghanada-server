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
