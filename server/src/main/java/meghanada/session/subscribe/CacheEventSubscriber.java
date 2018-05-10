package meghanada.session.subscribe;

import static java.util.Objects.nonNull;
import static meghanada.config.Config.timeItF;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.Collection;
import meghanada.analyze.CompileResult;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.project.ProjectDependency;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import meghanada.store.ProjectDatabaseHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CacheEventSubscriber extends AbstractSubscriber {

  private static final Logger log = LogManager.getLogger(CacheEventSubscriber.class);

  public CacheEventSubscriber(final SessionEventBus sessionEventBus) {
    super(sessionEventBus);
    log.debug("subscribe cache");
  }

  @Subscribe
  public void on(final SessionEventBus.ClassCacheRequest request) {
    this.analyze();
  }

  private void analyze() {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    final Session session = super.sessionEventBus.getSession();
    final Project project = session.getCurrentProject();
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector.addClasspath(project.getOutput());
    reflector.addClasspath(project.getTestOutput());
    project
        .getDependencies()
        .stream()
        .filter(pd -> pd.getType().equals(ProjectDependency.Type.PROJECT))
        .forEach(
            pd -> {
              final File df = new File(pd.getDependencyFilePath());
              if (df.exists() && df.isDirectory()) {
                reflector.addClasspath(df);
              }
            });

    final Collection<File> dependentJars = session.getDependentJars();
    final int size = dependentJars.size();
    timeItF(
        "create class index ... read " + size + " jars. elapsed:{}",
        () -> {
          reflector.addClasspath(dependentJars);
          reflector.createClassIndexes();
        });

    if (cleanUnusedSource(project)) {
      project.resetCallerMap();
    }

    log.info("start analyze sources ...");
    timeItF(
        "analyzed and compiled. elapsed:{}",
        () -> {
          try {
            final CompileResult compileResult = project.compileJava();
            if (compileResult.isSuccess()) {
              if (compileResult.hasDiagnostics()) {
                log.warn("compile message: {}", compileResult.getDiagnosticsSummary());
              }
              final CompileResult testCompileResult = project.compileTestJava();
              if (testCompileResult.isSuccess()) {
                if (testCompileResult.hasDiagnostics()) {
                  log.warn("compile(test) message: {}", testCompileResult.getDiagnosticsSummary());
                }
              } else {
                log.warn("compile(test) error: {}", testCompileResult.getDiagnosticsSummary());
              }
            } else {
              log.warn("compile message  {}", compileResult.getDiagnosticsSummary());
            }
          } catch (Exception e) {
            log.catching(e);
          }
        });

    log.info(
        "class index size:{} total elapsed:{}",
        reflector.getGlobalClassIndex().size(),
        stopwatch.stop());
    Config.showMemory();
    log.info("Ready");
    reflector.scanAllStaticMembers();

    String db = System.getProperty("new-project-database");
    if (nonNull(db) && db.isEmpty()) {
      createStandardClassCache();
    }
    // start idle monitor
    this.sessionEventBus.requestIdleMonitor();
  }

  private boolean cleanUnusedSource(Project project) {
    return ProjectDatabaseHelper.deleteUnunsedSource(project);
  }

  @SuppressWarnings("CheckReturnValue")
  private void createStandardClassCache() {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final GlobalCache globalCache = GlobalCache.getInstance();
    reflector
        .getStandardClasses()
        .values()
        .forEach(
            c -> {
              try {
                globalCache.getMemberDescriptors(c);
              } catch (Exception e) {
                log.catching(e);
              }
            });
    createClassCache("java.util.*");
    createClassCache("java.io.*");
  }

  @SuppressWarnings("CheckReturnValue")
  private void createClassCache(String name) {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final GlobalCache globalCache = GlobalCache.getInstance();
    reflector
        .getPackageClasses(name)
        .values()
        .forEach(
            c -> {
              try {
                globalCache.getMemberDescriptors(c);
              } catch (Exception e) {
                log.catching(e);
              }
            });
  }
}
