package meghanada.cache;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
import meghanada.analyze.CompileResult;
import meghanada.analyze.LineRange;
import meghanada.analyze.Position;
import meghanada.analyze.Range;
import meghanada.analyze.Scope;
import meghanada.analyze.Source;
import meghanada.analyze.Variable;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.project.gradle.GradleProject;
import meghanada.project.maven.MavenProject;
import meghanada.project.meghanada.MeghanadaProject;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.MethodParameter;
import meghanada.reflect.names.MethodParameterNames;
import meghanada.reflect.names.ParameterName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.nustaq.serialization.FSTConfiguration;
import org.nustaq.serialization.FSTObjectInput;
import org.nustaq.serialization.FSTObjectOutput;

public class GlobalCache {

  public static final String SOURCE_CACHE_DIR = "source";
  public static final String CLASS_CACHE_DIR = "class";
  public static final String MEMBER_CACHE_DIR = "member";
  public static final String DATA_DIR = "data";
  public static final String SOURCE_CHECKSUM_DATA = "source_checksum";
  public static final String COMPILE_CHECKSUM_DATA = "compile_checksum";
  public static final String CALLER_DATA = "source_caller";
  public static final String PROJECT_DATA = "project";
  public static final String CACHE_EXT = ".dat";

  private static final int SOURCE_CACHE_MAX = 64;
  private static final int MEMBER_CACHE_MAX = SOURCE_CACHE_MAX;
  private static final int BURST_LIMIT = 16;

  private static final Logger log = LogManager.getLogger(GlobalCache.class);
  private static FSTConfiguration fst;

  private static GlobalCache globalCache;

  private final Map<File, LoadingCache<File, Source>> sourceCaches;
  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final BlockingQueue<CacheRequest> blockingQueue = new LinkedBlockingDeque<>();

  private LoadingCache<String, List<MemberDescriptor>> memberCache;
  private boolean isTerminated = false;
  private boolean ioBurstMode;

  private GlobalCache() {

    this.sourceCaches = new HashMap<>(1);
    this.executorService.execute(
        () -> {
          while (!this.isTerminated) {
            try {
              final CacheRequest cr = blockingQueue.take();
              if (cr != null && !cr.shutdown) {
                GlobalCache.getInstance().writeCacheToFile(cr.getFile(), cr.getTarget());
              }
            } catch (Exception e) {
              log.catching(e);
            }
          }
          log.info("shutdown cache worker");
        });

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

  private static FSTConfiguration getFST() {
    if (fst != null) {
      return fst;
    }
    FSTConfiguration conf = FSTConfiguration.createDefaultConfiguration();
    conf.registerClass(
        Project.class,
        ProjectDependency.class,
        GradleProject.class,
        MavenProject.class,
        MeghanadaProject.class,
        ParameterName.class,
        MethodParameterNames.class,
        Scope.class,
        LineRange.class,
        Position.class,
        Variable.class,
        Range.class,
        CompileResult.class,
        Source.class,
        MethodParameter.class,
        ClassIndex.class,
        MemberDescriptor.class);
    fst = conf;
    return fst;
  }

  private static void writeObject(OutputStream output, Object obj) throws IOException {
    FSTObjectOutput out = getFST().getObjectOutput(output);
    out.writeObject(obj);
    out.flush();
  }

  private static <T> T readObject(InputStream input, Class<T> clazz) throws Exception {
    FSTObjectInput in = getFST().getObjectInput(input);
    Object obj = in.readObject(clazz);
    input.close();
    if (obj == null) {
      return null;
    }
    return clazz.cast(obj);
  }

  public void writeCacheToFile(final File file, final Object obj) {

    final File parentFile = file.getParentFile();

    if (!parentFile.exists() && !parentFile.mkdirs()) {
      log.warn("{} mkdirs fail", parentFile);
    }

    try (FileOutputStream out = new FileOutputStream(file)) {
      writeObject(out, obj);
    } catch (Exception e) {
      log.catching(e);
      if (!file.delete()) {
        log.warn("{} delete fail", file);
      }
    }
  }

  @Nullable
  public <T> T readCacheFromFile(final File file, final Class<T> clazz) {
    if (!file.exists()) {
      log.warn("file not exists:{}", file);
      return null;
    }

    try {
      try (FileInputStream in = new FileInputStream(file)) {
        return readObject(in, clazz);
      }
    } catch (Exception e) {
      log.catching(e);
      if (file.exists() && !file.delete()) {
        log.warn("{} delete fail", file);
      }
      return null;
    }
  }

  public <T> T readCacheFromInputStream(final InputStream in, final Class<T> clazz)
      throws Exception {
    return readObject(in, clazz);
  }

  public void setMemberCache(final LoadingCache<String, List<MemberDescriptor>> memberCache) {
    this.memberCache = memberCache;
  }

  public void setupMemberCache(
      final Map<String, File> classFileMap, final Map<ClassIndex, File> reflectIndex) {
    if (this.memberCache != null) {
      return;
    }
    final MemberCacheLoader memberCacheLoader = new MemberCacheLoader(classFileMap, reflectIndex);
    this.memberCache =
        CacheBuilder.newBuilder()
            .maximumSize(MEMBER_CACHE_MAX)
            .expireAfterAccess(5, TimeUnit.MINUTES)
            .removalListener(memberCacheLoader)
            .build(memberCacheLoader);
  }

  public List<MemberDescriptor> getMemberDescriptors(final String fqcn) throws ExecutionException {
    return this.memberCache.get(fqcn);
  }

  public void replaceMemberDescriptors(
      final String fqcn, final List<MemberDescriptor> memberDescriptors) {
    this.memberCache.put(fqcn, memberDescriptors);
  }

  public void asyncWriteCache(final File f, final Object o) {
    final CacheRequest cacheRequest = new CacheRequest(f, o);
    try {
      this.blockingQueue.put(cacheRequest);
    } catch (InterruptedException e) {
      log.catching(e);
    }

    if (!this.ioBurstMode && this.blockingQueue.size() > BURST_LIMIT) {
      this.ioBurstMode = true;
      this.executorService.execute(
          () -> {
            boolean readyShutdown = false;
            while (!this.isTerminated && this.ioBurstMode) {
              try {
                final CacheRequest cr = blockingQueue.poll(5, TimeUnit.SECONDS);
                if (cr != null && !cr.shutdown) {
                  GlobalCache.getInstance().writeCacheToFile(cr.getFile(), cr.getTarget());
                  readyShutdown = false;
                }
                if (this.blockingQueue.isEmpty()) {
                  if (readyShutdown) {
                    this.ioBurstMode = false;
                  } else {
                    readyShutdown = true;
                  }
                }
              } catch (Exception e) {
                log.catching(e);
              }
            }
          });
    }
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
      final LoadingCache<File, Source> loadingCache =
          CacheBuilder.newBuilder()
              .maximumSize(SOURCE_CACHE_MAX)
              .expireAfterAccess(5, TimeUnit.MINUTES)
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

  public void cacheSource(Project p, Source s) throws IOException {
    JavaSourceLoader.writeSourceCache(p, s);
  }

  public void shutdown() throws InterruptedException {
    if (this.isTerminated) {
      return;
    }

    if (this.memberCache != null) {
      this.memberCache.asMap().forEach((k, v) -> this.memberCache.put(k, v));
    }

    this.sourceCaches.forEach(
        (root, sourceLoadingCache) -> {
          // force replace
          sourceLoadingCache.asMap().forEach(sourceLoadingCache::put);
        });
    this.isTerminated = true;

    final CacheRequest cacheRequest = new CacheRequest(new File(""), this);
    cacheRequest.shutdown = true;
    try {
      this.blockingQueue.put(cacheRequest);
    } catch (InterruptedException e) {
      log.catching(e);
    }
    this.executorService.shutdown();
    this.executorService.awaitTermination(5, TimeUnit.SECONDS);
  }
}
