package meghanada.config;

import com.google.common.base.Stopwatch;
import com.typesafe.config.ConfigFactory;
import meghanada.project.Project;
import meghanada.utils.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Config {

    public static final String MEGHANADA_CONF_FILE = ".meghanada.conf";
    public static final String GRADLE_PREPARE_COMPILE_TASK = "gradle-prepare-compile-task";
    public static final String GRADLE_PREPARE_TEST_COMPILE_TASK = "gradle-prepare-test-compile-task";
    public static final String MEGHANADA_DIR = ".meghanada";
    private static final Logger log = LogManager.getLogger(Config.class);
    private static Config config;

    private final com.typesafe.config.Config c;
    private final Map<File, Map<String, String>> checksumMap = new ConcurrentHashMap<>(4);
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

    }

    public static Config load() {
        if (config == null) {
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
            return new File(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            log.catching(e);
            throw new RuntimeException(e);
        }
    }

    public List<String> gradlePrepareCompileTask() {
        final String tasks = c.getString(GRADLE_PREPARE_COMPILE_TASK);
        final String[] split = StringUtils.split(tasks, ",");
        if (split == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(split);
    }

    public List<String> gradlePrepareTestCompileTask() {
        final String tasks = c.getString(GRADLE_PREPARE_TEST_COMPILE_TASK);
        final String[] split = StringUtils.split(tasks, ",");
        if (split == null) {
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
        final String root = System.getProperty(Project.PROJECT_ROOT_KEY);
        try {
            if (root != null && !root.isEmpty()) {
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
        if (this.includeList == null) {
            return Collections.emptyList();
        }
        return includeList;
    }

    public void setIncludeList(List<String> includeList) {
        this.includeList = includeList;
    }

    public List<String> getExcludeList() {
        if (this.excludeList == null) {
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

    public boolean androidDevelopment() {
        return c.getBoolean("android-development");
    }

    public boolean clearCacheOnStart() {
        return c.getBoolean("clear-cache-on-start");
    }

    public Map<File, Map<String, String>> getAllChecksumMap() {
        return checksumMap;
    }

    public Map<String, String> getChecksumMap(final File file) throws IOException {
        if (!this.checksumMap.containsKey(file)) {
            Map<String, String> checksumMap = new ConcurrentHashMap<>(64);
            if (file.exists()) {
                checksumMap = new ConcurrentHashMap<>(FileUtils.readMapSetting(file));
            }
            this.checksumMap.put(file, checksumMap);
        }
        return checksumMap.get(file);
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

    @FunctionalInterface
    public interface SimpleSupplier<R> {

        R get() throws Exception;
    }

    @FunctionalInterface
    public interface SimpleConsumer {

        void accept() throws IOException;
    }


}
