package meghanada.analyze.subscribe;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import meghanada.analyze.ClassScope;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.MemberDescriptor;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SourceCacheSubscriber {

  private static final Logger log = LogManager.getLogger(SourceCacheSubscriber.class);

  private final Map<Project, Map<String, Set<String>>> callers;
  private final Map<Project, Map<String, String>> checksums;

  private final Supplier<Project> projectSupplier;
  private long lastCached;
  private boolean importMemberCache = false;

  public SourceCacheSubscriber(final Supplier<Project> projectSupplier) {
    this.projectSupplier = projectSupplier;
    this.callers = new HashMap<>(2);
    this.checksums = new HashMap<>(2);
    this.lastCached = System.currentTimeMillis();
  }

  private Map<String, String> getChecksumMap(Project project) {
    if (this.checksums.containsKey(project)) {
      return this.checksums.get(project);
    }
    Map<String, String> checksumMap =
        ProjectDatabaseHelper.getChecksumMap(project.getProjectRootPath());
    this.checksums.put(project, checksumMap);
    return checksumMap;
  }

  private void analyzed(final Source source, final boolean isDiagnostics) throws IOException {
    Project project = projectSupplier.get();
    Map<String, Set<String>> callerMap = project.getCallerMap();
    Map<String, String> checksumMap = this.getChecksumMap(project);

    final Config config = Config.load();
    final boolean useSourceCache = config.useSourceCache();
    if (!useSourceCache) {
      return;
    }

    if (isDiagnostics) {
      final long now = System.currentTimeMillis();
      if (now - this.lastCached > 10000) {
        if (this.importMemberCache) {
          SourceCacheSubscriber.createImportMemberCache(source);
          this.lastCached = now;
        }
      }
    }

    final GlobalCache globalCache = GlobalCache.getInstance();
    final List<ClassScope> classScopes = source.getClassScopes();
    for (final ClassScope cs : classScopes) {

      final String fqcn = cs.getFQCN();
      globalCache.replaceSourceMap(fqcn, source.getFile().getCanonicalPath());

      for (String clazz : source.usingClasses) {
        if (callerMap.containsKey(clazz)) {
          final Set<String> set = callerMap.get(clazz);
          set.add(fqcn);
          callerMap.put(clazz, set);
        } else {
          final Set<String> set = new HashSet<>(16);
          set.add(fqcn);
          callerMap.put(clazz, set);
        }
      }
      source.usingClasses.clear();
    }

    final File sourceFile = source.getFile();
    final String path = sourceFile.getCanonicalPath();
    if (!isDiagnostics) {
      source.invalidateCache();
    }

    globalCache.replaceSource(source);
    if (!source.hasCompileError) {
      final String md5sum = FileUtils.getChecksum(sourceFile);
      checksumMap.put(path, md5sum);
      ProjectDatabaseHelper.saveSource(source);
    } else {
      // error
      checksumMap.remove(path);
    }
  }

  public void complete() throws IOException {
    Project project = this.projectSupplier.get();
    Map<String, String> checksumMap = getChecksumMap(project);
    boolean b = ProjectDatabaseHelper.saveChecksumMap(project.getProjectRootPath(), checksumMap);
    GlobalCache.getInstance().saveSourceMap();
    project.writeCaller();
  }

  @SuppressWarnings("CheckReturnValue")
  private static void createImportMemberCache(final Source src) {
    if (!src.hasCompileError) {
      try {
        final GlobalCache globalCache = GlobalCache.getInstance();
        src.importClasses.forEach(
            impFqcn -> {
              try {
                List<MemberDescriptor> descriptors = globalCache.getMemberDescriptors(impFqcn);
                log.trace("cached:{} size:{}", impFqcn, descriptors.size());
              } catch (Exception e) {
                log.catching(e);
              }
            });
      } catch (Exception e) {
        log.catching(e);
      }
    }
  }

  @Subscribe
  public void on(final JavaAnalyzer.AnalyzedEvent event) {
    final Map<File, Source> analyzedMap = event.analyzedMap;
    analyzedMap
        .values()
        .forEach(
            source -> {
              try {
                this.analyzed(source, event.diagnostics);
              } catch (Exception ex) {
                log.catching(ex);
              }
            });
    try {
      this.complete();
    } catch (Exception ex) {
      log.catching(ex);
    }
  }
}
