package meghanada.config;

import com.google.common.base.Stopwatch;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import java.io.File;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {

    public static final String MEGHANADA_CONF_FILE = ".meghanada.conf";
    private static Logger log = LogManager.getLogger(Config.class);
    private static Config config;

    private com.typesafe.config.Config c;
    private boolean debug;
    private List<String> includeList;
    private List<String> excludeList;

    private Map<File, Map<String, String>> checksumMap = new HashMap<>();

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
        log.debug("root-cache-dir:{}", getRootCacheDir());
        log.debug("project-setting-dir:{}", getProjectSettingDir());
        log.debug("project-cache-dir:{}", getProjectCacheDir());
        log.debug("fast-boot:{}", useFastBoot());
        log.debug("class-fuzzy-search:{}", useClassFuzzySearch());

        final File cache = new File(getProjectCacheDir());
        if (!cache.exists()) {
            cache.mkdirs();
        }
    }

    public static Config load() {
        if (config == null) {
            config = new Config();
        }
        return config;
    }

    public static <T> T timeIt(final SimpleSupplier<T> supplier) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            return supplier.get();
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

    public static <T> T debugIt(SimpleSupplier<T> supplier) {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Config.load().setDebug();
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
        Stopwatch stopwatch = Stopwatch.createStarted();
        Config.load().setTrace();
        try {
            return supplier.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            log.info("elapsed:{}", stopwatch.stop());
            Config.load().setLogLevel("INFO");
        }
    }

    public static File getInstalledPath() {
        try {
            return new File(Config.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
        } catch (URISyntaxException e) {
            log.catching(e);
        }
        return null;
    }

    public void setDebug() {
        this.setLogLevel("Debug");
    }

    public void setTrace() {
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

    public String getHomeDir() {
        return c.getString("home");
    }

    public String getUserHomeDir() {
        return c.getString("user-home");
    }

    public String getJavaHomeDir() {
        return c.getString("java-home");
    }

    public String getRootCacheDir() {
        return c.getString("root-cache-dir");
    }

    public String getProjectSettingDir() {
        return c.getString("project-setting-dir");
    }

    public String getProjectCacheDir() {
        return c.getString("project-cache-dir");
    }

    public String getJavaVersion() {
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

    public boolean analyzeAll() {
        return c.getBoolean("analyze-all");
    }

    public Map<File, Map<String, String>> getAllChecksumMap() {
        return checksumMap;
    }

    public Map<String, String> getChecksumMap(final File file) {
        return checksumMap.get(file);
    }

    public List<String> getAllowClass() {
        return c.getStringList("allow-class");
    }

    @FunctionalInterface
    public interface SimpleSupplier<R> {

        R get() throws Exception;
    }

}
