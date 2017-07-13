package meghanada.config;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

import com.google.common.base.Stopwatch;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

  private static final Logger log = LogManager.getLogger(Config.class);
  private static Config config;

  private com.typesafe.config.Config c;
  private boolean debug;
  private List<String> includeList;
  private List<String> excludeList;
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
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    final Configuration configuration = context.getConfiguration();
    final LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    loggerConfig.setLevel(level);
    context.updateLoggers();

    log.debug("home:{}", getHomeDir());
    log.debug("java-home:{}", getJavaHomeDir());
    log.debug("java-version:{}", getJavaVersion());
    log.debug("user-home:{}", getUserHomeDir());
    log.debug("project-root-dir:{}", getProjectRootDir());
    log.debug("fast-boot:{}", useFastBoot());
    log.debug("class-fuzzy-search:{}", useClassFuzzySearch());
    log.debug("javac-arg:{}", getJavacArg());
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
    final Level level = Level.toLevel(logLevel);
    // force change
    final LoggerContext context = (LoggerContext) LogManager.getContext(false);
    final Configuration configuration = context.getConfiguration();
    final LoggerConfig loggerConfig = configuration.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
    loggerConfig.setLevel(level);
    context.updateLoggers();

    this.debug = !logLevel.toLowerCase().equals("info");
  }

  private String getHomeDir() {
    return c.getString("home");
  }

  private String getUserHomeDir() {
    return c.getString("user-home");
  }

  public String getJavaHomeDir() {
    return c.getString("java-home");
  }

  public String getProjectSettingDir() {

    String useTemp = c.getString("temp-project-setting-dir");
    if (nonNull(useTemp) && !useTemp.isEmpty()) {
      log.debug("use temp project setting dir {}", useTemp);
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

  private String getJavaVersion() {
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

  public boolean useClassFuzzySearch() {
    return c.getBoolean("class-fuzzy-search");
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

  public boolean useAOSPStyle() {
    return c.getBoolean("aosp-style");
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
