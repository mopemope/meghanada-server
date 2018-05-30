package meghanada.analyze.subscribe;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private final Map<String, Set<String>> callerMap;
  private final Map<String, String> checksumMap;
  private final Project project;
  private long lastCached;
  private boolean importMemberCache = false;

  public SourceCacheSubscriber(final Project project) {
    this.project = project;
    this.callerMap = project.getCallerMap();
    this.checksumMap = ProjectDatabaseHelper.getChecksumMap(project.getProjectRootPath());
    this.lastCached = System.currentTimeMillis();
  }

  private void analyzed(final Source source, final boolean isDiagnostics) throws IOException {

    final Config config = Config.load();
    final boolean useSourceCache = config.useSourceCache();
    if (!useSourceCache) {
      return;
    }

    if (isDiagnostics) {
      final long now = System.currentTimeMillis();
      if (now - this.lastCached > 10000) {
        if (this.importMemberCache) {
          this.createImportMemberCache(source);
          this.lastCached = now;
        }
      }
    }

    final GlobalCache globalCache = GlobalCache.getInstance();
    final List<ClassScope> classScopes = source.getClassScopes();
    for (final ClassScope cs : classScopes) {
      final String fqcn = cs.getFQCN();
      for (String clazz : source.usingClasses) {
        if (this.callerMap.containsKey(clazz)) {
          final Set<String> set = this.callerMap.get(clazz);
          set.add(fqcn);
          this.callerMap.put(clazz, set);
        } else {
          final Set<String> set = new HashSet<>(16);
          set.add(fqcn);
          this.callerMap.put(clazz, set);
        }
      }
      source.usingClasses.clear();
    }

    final File sourceFile = source.getFile();
    final String path = sourceFile.getCanonicalPath();
    if (!isDiagnostics) {
      source.invalidateCache();
    }

    if (!source.hasCompileError) {
      final String md5sum = FileUtils.getChecksum(sourceFile);
      checksumMap.put(path, md5sum);
      globalCache.replaceSource(this.project, source);
      ProjectDatabaseHelper.saveSource(source);
    } else {
      // error
      checksumMap.remove(path);
      if (!isDiagnostics) {
        globalCache.replaceSource(this.project, source);
      }
    }
  }

  public void complete() throws IOException {
    boolean b =
        ProjectDatabaseHelper.saveChecksumMap(this.project.getProjectRootPath(), this.checksumMap);
    this.project.writeCaller();
  }

  @SuppressWarnings("CheckReturnValue")
  private void createImportMemberCache(final Source src) {
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
