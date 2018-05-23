package meghanada.config;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static meghanada.config.Config.CompletionType.CAMEL_CASE;
import static meghanada.config.Config.CompletionType.CONTAINS;
import static meghanada.config.Config.CompletionType.FUZZY;
import static meghanada.config.Config.CompletionType.PREFIX;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

public class Config {

  public static final String MEGHANADA_CONF_FILE = ".meghanada.conf";
  public static final String GRADLE_PREPARE_COMPILE_TASK = "gradle-prepare-compile-task";
  public static final String GRADLE_PREPARE_TEST_COMPILE_TASK = "gradle-prepare-test-compile-task";
  public static final String MEGHANADA_DIR = ".meghanada";
  public static final String DEFAULT_JAVAC_ARG = "-Xlint:all";
  public static final String PROJECT_ROOT_KEY = "project.root";

  private static final String COMPLETION_TYPE_PREFIX = "prefix";
  private static final String COMPLETION_TYPE_CONTAINS = "contains";
  private static final String COMPLETION_TYPE_FUZZY = "fuzzy";
  private static final String COMPLETION_TYPE_CAME_CASE = "camel-case";

  private static final Logger log = LogManager.getLogger(Config.class);
  private static Config config;

  private com.typesafe.config.Config c;
  private boolean debug;
  private List<String> includeList;
  private List<String> excludeList;
  private List<String> java8JavacArgs = new ArrayList<>(8);
  private List<String> java9JavacArgs = new ArrayList<>(8);
  private List<String> java10JavacArgs = new ArrayList<>(8);
  private boolean buildWithDependency = true;

  private Config() {
    this.c = ConfigFactory.load();
    final String logLevel = c.getString("log-level");
    Level level = Level.toLevel(logLevel);
    final String lowerLevel = logLevel.toLowerCase();

    if (lowerLevel.equals("debug") || lowerLevel.equals("trace")) {
      this.debug = true;
    }
    // force change
    Object ctx = LogManager.getContext(false);
    if (ctx instanceof LoggerContext) {
      LoggerContext context = (LoggerContext) ctx;
      Configuration configuration = context.getConfiguration();
      LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      loggerConfig.setLevel(level);
      context.updateLoggers();
    }
  }

  public static Config load() {
    if (isNull(config)) {
      config = new Config();
    }
    return config;
  }

  public static <T> T timeIt(final SimpleSupplier<T> supplier) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info("elapsed:{}", stopwatch.stop());
    }
  }

  public static void timeIt(final SimpleConsumer consumer) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      consumer.accept();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info("elapsed:{}", stopwatch.stop());
    }
  }

  public static void timeIt(final String prefix, final SimpleConsumer consumer) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      consumer.accept();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info("{} elapsed:{}", prefix, stopwatch.stop());
    }
  }

  public static <T> T timeItF(final String format, final SimpleSupplier<T> supplier) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info(format, stopwatch.stop());
    }
  }

  public static void timeItF(final String format, final SimpleConsumer consumer) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      consumer.accept();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info(format, stopwatch.stop());
    }
  }

  public static <T> T debugTimeItF(final String format, final SimpleSupplier<T> supplier) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.debug(format, stopwatch.stop());
    }
  }

  public static void debugTimeItF(final String format, final SimpleConsumer consumer) {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      consumer.accept();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.debug(format, stopwatch.stop());
    }
  }

  public static <T> T debugIt(final SimpleSupplier<T> supplier) {
    Config.load().setDebug();
    return runSupplier(supplier);
  }

  private static <T> T runSupplier(SimpleSupplier<T> supplier) {
    Stopwatch stopwatch = Stopwatch.createStarted();
    try {
      return supplier.get();
    } catch (Exception e) {
      throw new RuntimeException(e);
    } finally {
      log.info("elapsed:{}", stopwatch.stop());
      Config.load().setLogLevel("INFO");
    }
  }

  public static <T> T traceIt(SimpleSupplier<T> supplier) {
    Config.load().setTrace();
    return runSupplier(supplier);
  }

  public static File getInstalledPath() {
    try {
      return new File(
          Config.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
    } catch (URISyntaxException e) {
      log.catching(e);
      throw new RuntimeException(e);
    }
  }

  public static void showMemory() {
    final Runtime runtime = Runtime.getRuntime();
    final float maxMemory = runtime.maxMemory() / 1024 / 1024;
    final float totalMemory = runtime.totalMemory() / 1024 / 1024;
    final float usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    log.info(
        "memory usage (used/total/max): {}MB / {}MB / {}MB",
        String.format("%.2f", usedMemory),
        String.format("%.2f", totalMemory),
        String.format("%.2f", maxMemory));
  }

  public static String showMemoryString() {

    final Runtime runtime = Runtime.getRuntime();
    final float maxMemory = runtime.maxMemory() / 1024 / 1024;
    final float totalMemory = runtime.totalMemory() / 1024 / 1024;
    final float usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
    return String.format(
        "memory usage (used/total/max): %sMB / %sMB / %sMB",
        String.format("%.2f", usedMemory),
        String.format("%.2f", totalMemory),
        String.format("%.2f", maxMemory));
  }

  public static String getProjectRoot() {
    return System.getProperty(PROJECT_ROOT_KEY);
  }

  public static void setProjectRoot(String path) {
    System.setProperty(PROJECT_ROOT_KEY, path);
  }

  public List<String> gradlePrepareCompileTask() {
    final String tasks = c.getString(GRADLE_PREPARE_COMPILE_TASK);
    final String[] split = StringUtils.split(tasks, ",");
    if (isNull(split)) {
      return Collections.emptyList();
    }
    return Arrays.asList(split);
  }

  public List<String> gradlePrepareTestCompileTask() {
    final String tasks = c.getString(GRADLE_PREPARE_TEST_COMPILE_TASK);
    final String[] split = StringUtils.split(tasks, ",");
    if (isNull(split)) {
      return Collections.emptyList();
    }
    return Arrays.asList(split);
  }

  private void setDebug() {
    this.setLogLevel("Debug");
  }

  private void setTrace() {
    this.setLogLevel("Trace");
  }

  private void setLogLevel(final String logLevel) {
    Object ctx = LogManager.getContext(false);
    if (ctx instanceof LoggerContext) {
      LoggerContext context = (LoggerContext) ctx;
      Level level = Level.toLevel(logLevel);
      Configuration configuration = context.getConfiguration();
      LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
      loggerConfig.setLevel(level);
      context.updateLoggers();
      this.debug = !logLevel.toLowerCase().equals("info");
    }
  }

  public String getHomeDir() {
    return c.getString("home");
  }

  public String getUserHomeDir() {
    return c.getString("user-home");
  }

  public String getJavaHomeDir() {
    return c.getString("java-home");
  }

  public String getProjectSettingDir() {

    String useTemp = c.getString("temp-project-setting-dir");
    if (nonNull(useTemp) && !useTemp.isEmpty()) {
      return useTemp;
    }

    final String root = System.getProperty(PROJECT_ROOT_KEY);
    try {
      if (nonNull(root) && !root.isEmpty()) {
        return new File(root, MEGHANADA_DIR).getCanonicalPath();
      } else {
        return c.getString("project-setting-dir");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public String getJavaVersion() {
    return c.getString("java-version");
  }

  public String getProjectRootDir() {
    return c.getString("project-root-dir");
  }

  public List<String> getIncludeList() {
    if (isNull(this.includeList)) {
      return Collections.emptyList();
    }
    return includeList;
  }

  public void setIncludeList(List<String> includeList) {
    this.includeList = includeList;
  }

  public List<String> getExcludeList() {
    if (isNull(this.excludeList)) {
      return Collections.emptyList();
    }
    return excludeList;
  }

  public void setExcludeList(List<String> excludeList) {
    this.excludeList = excludeList;
  }

  public boolean isDebug() {
    return debug;
  }

  public String getGradleVersion() {
    return c.getString("gradle-version");
  }

  public String getMavenPath() {
    return c.getString("maven-path");
  }

  public boolean useFastBoot() {
    return c.getBoolean("fast-boot");
  }

  public boolean useSourceCache() {
    return c.getBoolean("source-cache");
  }

  public boolean useExternalBuilder() {
    return c.getBoolean("external-builder");
  }

  public boolean clearCacheOnStart() {
    return c.getBoolean("clear-cache-on-start");
  }

  public boolean isBuildWithDependency() {
    return buildWithDependency;
  }

  public void setBuildWithDependency(boolean buildWithDependency) {
    this.buildWithDependency = buildWithDependency;
  }

  public List<String> getAllowClass() {
    return c.getStringList("allow-class");
  }

  public String getJavacArg() {
    return c.getString("javac-arg");
  }

  public String getMavenLocalRepository() {
    return c.getString("maven-local-repository");
  }

  public boolean isSkipBuildSubProjects() {
    return c.getBoolean("skip-build-subprojects");
  }

  public int getSourceCacheSize() {
    return c.getInt("source-cache-size");
  }

  public int getDebuggerPort() {
    return c.getInt("debugger-port");
  }

  public void update(String key, Object newVal) {
    com.typesafe.config.Config newConfig = c.withValue(key, ConfigValueFactory.fromAnyRef(newVal));
    this.c = newConfig;
  }

  public boolean isJava8() {
    return getJavaVersion().equals("1.8");
  }

  public boolean isJava9() {
    return getJavaVersion().equals("9");
  }

  public boolean isJava10() {
    return getJavaVersion().equals("10");
  }

  public boolean useAOSPStyle() {
    return c.getBoolean("aosp-style");
  }

  public List<String> getJava8JavacArgs() {
    return this.java8JavacArgs;
  }

  public void setJava8JavacArgs(List<String> lst) {
    this.java8JavacArgs = lst;
  }

  public List<String> getJava9JavacArgs() {
    return this.java9JavacArgs;
  }

  public void setJava9JavacArgs(List<String> lst) {
    this.java9JavacArgs = lst;
  }

  public List<String> getJava10JavacArgs() {
    return this.java10JavacArgs;
  }

  public void setJava10JavacArgs(List<String> lst) {
    this.java10JavacArgs = lst;
  }

  public String getCacheRoot() {
    return c.getString("cache-root");
  }

  public boolean isCacheInProject() {
    return c.getBoolean("cache-in-project");
  }

  public boolean useFullTextSearch() {
    return c.getBoolean("full-text-search");
  }

  public List<String> searchStaticMethodClasses() {
    final String classes = c.getString("search-static-method-classes");
    final String[] split = StringUtils.split(classes, ",");
    if (isNull(split)) {
      return Collections.emptyList();
    }
    return Arrays.stream(split).map(String::trim).collect(Collectors.toList());
  }

  public CompletionType completionMatcher() {
    String m = c.getString("completion-matcher");
    if (Strings.isNullOrEmpty(m)) {
      return PREFIX;
    }
    return getCompletionType(m);
  }

  public CompletionType classCompletionMatcher() {
    String m = c.getString("class-completion-matcher");
    if (Strings.isNullOrEmpty(m)) {
      return PREFIX;
    }
    return getCompletionType(m);
  }

  private Config.CompletionType getCompletionType(String m) {
    switch (m) {
      case COMPLETION_TYPE_PREFIX:
        return PREFIX;
      case COMPLETION_TYPE_CONTAINS:
        return CONTAINS;
      case COMPLETION_TYPE_FUZZY:
        return FUZZY;
      case COMPLETION_TYPE_CAME_CASE:
        return CAMEL_CASE;
      default:
        log.warn("invalid matcher: '{}'. use default 'prefix' matcher", m);
        return PREFIX;
    }
  }

  public enum CompletionType {
    PREFIX(COMPLETION_TYPE_PREFIX),
    CONTAINS(COMPLETION_TYPE_CONTAINS),
    FUZZY(COMPLETION_TYPE_FUZZY),
    CAMEL_CASE(COMPLETION_TYPE_CAME_CASE);

    private final String typeName;

    CompletionType(final String typeName) {
      this.typeName = typeName;
    }

    @Override
    public String toString() {
      return this.typeName;
    }

    public String type() {
      return this.typeName;
    }
  }

  @FunctionalInterface
  public interface SimpleSupplier<R> {
    R get() throws Exception;
  }

  @FunctionalInterface
  public interface SimpleConsumer {
    void accept() throws IOException;
  }
}
