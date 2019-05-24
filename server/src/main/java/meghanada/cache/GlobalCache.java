package meghanada.cache;

import static java.util.Objects.nonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.telemetry.TelemetryUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GlobalCache {

  private static final int MEMBER_CACHE_MAX = 512;

  private static final Logger log = LogManager.getLogger(GlobalCache.class);

  private static GlobalCache globalCache;
  private final Map<File, LoadingCache<File, Source>> sourceCaches;
  private final Map<File, JavaSourceLoader> sourceLoaders;
  private final Map<File, Map<String, String>> sourceMapCaches;

  private LoadingCache<String, List<MemberDescriptor>> memberCache;
  private MemberCacheLoader memberCacheLoader;

  private Supplier<Project> projectSupplier;

  private GlobalCache() {

    this.sourceCaches = new HashMap<>(1);
    this.sourceLoaders = new HashMap<>(1);
    this.sourceMapCaches = new HashMap<>(1);

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  try {
                    shutdown();
                  } catch (Throwable t) {
                    log.catching(t);
                  }
                }));
  }

  public static GlobalCache getInstance() {
    if (globalCache == null) {
      globalCache = new GlobalCache();
    }
    return globalCache;
  }

  public void setProjectSupplier(Supplier<Project> supplier) {
    this.projectSupplier = supplier;
  }

  public void setupMemberCache() {
    if (nonNull(this.memberCache)) {
      return;
    }
    this.memberCacheLoader = new MemberCacheLoader();
    this.memberCache =
        CacheBuilder.newBuilder()
            .maximumSize(MEMBER_CACHE_MAX)
            .recordStats()
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .removalListener(memberCacheLoader)
            .build(memberCacheLoader);
  }

  public List<MemberDescriptor> getMemberDescriptors(final String fqcn) throws ExecutionException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpanLow("GlobalCache.getMemberDescriptors")) {

      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));

      return this.memberCache.get(fqcn);
    }
  }

  public void loadMemberDescriptors(final String fqcn) throws IOException, ExecutionException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpanLow("GlobalCache.loadMemberDescriptors")) {

      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));

      List<MemberDescriptor> descriptors = this.memberCacheLoader.load(fqcn);
      this.memberCache.put(fqcn, descriptors);
    }
  }

  public double[] getMemberDescriptorsRateStats() throws ExecutionException {
    CacheStats cacheStats = this.memberCache.stats();
    return new double[] {
      cacheStats.hitRate(), cacheStats.loadExceptionRate(), cacheStats.missRate(),
    };
  }

  public long[] getMemberDescriptorsCountStats() throws ExecutionException {
    CacheStats cacheStats = this.memberCache.stats();
    return new long[] {
      cacheStats.hitCount(),
      cacheStats.loadCount(),
      cacheStats.loadExceptionCount(),
      cacheStats.loadSuccessCount(),
      cacheStats.missCount(),
      cacheStats.requestCount(),
    };
  }

  public void invalidateMemberDescriptors(final String fqcn) {
    this.memberCache.invalidate(fqcn);
  }

  private LoadingCache<File, Source> getSourceCache() {
    Project project = this.projectSupplier.get();
    final File projectRoot = project.getProjectRoot();
    if (this.sourceCaches.containsKey(projectRoot)) {
      return this.sourceCaches.get(projectRoot);
    } else {
      final JavaSourceLoader javaSourceLoader = new JavaSourceLoader(this.projectSupplier);

      int size = Config.load().getSourceCacheSize();
      final LoadingCache<File, Source> loadingCache =
          CacheBuilder.newBuilder()
              .maximumSize(size)
              .expireAfterAccess(10, TimeUnit.MINUTES)
              .removalListener(javaSourceLoader)
              .build(javaSourceLoader);
      this.sourceCaches.put(projectRoot, loadingCache);
      return loadingCache;
    }
  }

  public Source getSource(final File file) throws ExecutionException {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("GlobalCache.getSource")) {
      scope.addAnnotation(
          TelemetryUtils.annotationBuilder().put("file", file.getPath()).build("args"));
      final LoadingCache<File, Source> sourceCache = this.getSourceCache();
      return sourceCache.get(file);
    }
  }

  public void replaceSource(final Source source) {
    final LoadingCache<File, Source> sourceCache = this.getSourceCache();
    sourceCache.put(source.getFile(), source);
  }

  public void invalidateSource(final File file) {
    final LoadingCache<File, Source> sourceCache = this.getSourceCache();
    sourceCache.invalidate(file);
  }

  private Map<String, String> getSourceMapCache() {
    Project project = this.projectSupplier.get();
    final File projectRoot = project.getProjectRoot();
    if (this.sourceMapCaches.containsKey(projectRoot)) {
      return this.sourceMapCaches.get(projectRoot);
    } else {
      Map<String, String> sourceMap =
          ProjectDatabaseHelper.getSourceMap(project.getProjectRootPath());
      this.sourceMapCaches.put(projectRoot, sourceMap);
      return sourceMap;
    }
  }

  public Optional<String> getSourceMap(final String fqcn) throws ExecutionException {

    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("GlobalCache.getSourceMap")) {

      scope.addAnnotation(TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));

      Map<String, String> sourceMap = this.getSourceMapCache();
      return Optional.ofNullable(sourceMap.get(fqcn));
    }
  }

  public void replaceSourceMap(final String fqcn, final String path) {
    Map<String, String> sourceMap = this.getSourceMapCache();
    sourceMap.put(fqcn, path);
  }

  public void saveSourceMap() {
    try (TelemetryUtils.ScopedSpan scope =
        TelemetryUtils.startScopedSpan("GlobalCache.saveSourceMap")) {
      Map<String, String> sourceMap = this.getSourceMapCache();
      boolean b =
          ProjectDatabaseHelper.saveSourceMap(
              projectSupplier.get().getProjectRootPath(), sourceMap);
    }
  }

  public void shutdown() throws InterruptedException {

    if (nonNull(this.memberCache)) {
      this.memberCache.asMap().forEach((k, v) -> this.memberCache.put(k, v));
    }
  }
}
