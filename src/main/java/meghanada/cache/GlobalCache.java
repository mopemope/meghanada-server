package meghanada.cache;

import static java.util.Objects.nonNull;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.MemberDescriptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class GlobalCache {

  private static final int MEMBER_CACHE_MAX = 128;

  private static final Logger log = LogManager.getLogger(GlobalCache.class);

  private static GlobalCache globalCache;
  private final Map<File, LoadingCache<File, Source>> sourceCaches;
  private LoadingCache<String, List<MemberDescriptor>> memberCache;

  private GlobalCache() {

    this.sourceCaches = new HashMap<>(1);

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

  public void setupMemberCache() {
    if (this.memberCache != null) {
      return;
    }
    final MemberCacheLoader memberCacheLoader = new MemberCacheLoader();
    this.memberCache =
        CacheBuilder.newBuilder()
            .maximumSize(MEMBER_CACHE_MAX)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .removalListener(memberCacheLoader)
            .build(memberCacheLoader);
  }

  public List<MemberDescriptor> getMemberDescriptors(final String fqcn) throws ExecutionException {
    return this.memberCache.get(fqcn);
  }

  public void invalidateMemberDescriptors(final String fqcn) {
    this.memberCache.invalidate(fqcn);
  }

  public LoadingCache<File, Source> getSourceCache(final Project project) {
    final File projectRoot = project.getProjectRoot();
    if (this.sourceCaches.containsKey(projectRoot)) {
      return this.sourceCaches.get(projectRoot);
    } else {
      final JavaSourceLoader javaSourceLoader = new JavaSourceLoader(project);
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

  public Source getSource(final Project project, final File file) throws ExecutionException {
    final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
    return sourceCache.get(file);
  }

  public void replaceSource(final Project project, final Source source) {
    final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
    sourceCache.put(source.getFile(), source);
  }

  public void invalidateSource(final Project project, final File file) {
    final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
    sourceCache.invalidate(file);
  }

  public void shutdown() throws InterruptedException {

    if (nonNull(this.memberCache)) {
      this.memberCache.asMap().forEach((k, v) -> this.memberCache.put(k, v));
    }
  }
}
