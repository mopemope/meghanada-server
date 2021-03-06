package meghanada.session.subscribe;

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
import meghanada.telemetry.ErrorReporter;
import meghanada.telemetry.TelemetryUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CacheEventSubscriber extends AbstractSubscriber {

  private static final Logger log = LogManager.getLogger(CacheEventSubscriber.class);

  public CacheEventSubscriber(final SessionEventBus sessionEventBus) {
    super(sessionEventBus);
    log.debug("subscribe cache");
  }

  @Subscribe
  @SuppressWarnings("try")
  public void on(final SessionEventBus.ClassCacheRequest request) {
    try (TelemetryUtils.ParentSpan span =
            TelemetryUtils.startExplicitParentSpan("CacheEventSubscriber/on");
        TelemetryUtils.ScopedSpan scope = TelemetryUtils.withSpan(span.getSpan())) {
      this.analyze();
      span.setStatusOK();
    }
  }

  private void analyze() {
    final Stopwatch stopwatch = Stopwatch.createStarted();
    final Session session = super.sessionEventBus.getSession();
    final Project project = session.getCurrentProject();
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    reflector.addClasspath(project.getOutput());
    reflector.addClasspath(project.getTestOutput());
    project.getDependencies().stream()
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
    reflector.addClasspath(dependentJars);
    final Stopwatch s = Stopwatch.createStarted();
    CachedASMReflector.getInstance().createClassIndexes();
    log.info("create class index ... read " + size + " jars. elapsed:{}", s.stop());

    if (cleanUnusedSource(project)) {
      project.resetCallerMap();
    }

    log.info("start analyze sources ...");
    timeItF(
        "analyzed and compiled. elapsed:{}",
        () -> {
          try {
            final CompileResult compileResult = project.compileJava();
            compileResult.displayDiagnosticsSummary();
            if (compileResult.isSuccess()) {
              final CompileResult testCompileResult = project.compileTestJava();
              testCompileResult.displayDiagnosticsSummary();
            }
          } catch (Exception e) {
            log.catching(e);
            ErrorReporter.report(e);
          }
        });

    log.info(
        "class index size:{} total elapsed:{}",
        reflector.getGlobalClassIndex().size(),
        stopwatch.stop());
    // System.gc();
    Config.showMemory();
    log.info("Ready");
    reflector.scanAllStaticMembers();

    // String db = System.getProperty("new-project-database");
    // if (nonNull(db) && db.isEmpty()) {
    //   createStandardClassCache();
    // }
    // start idle monitor
    this.sessionEventBus.requestIdleMonitor();
  }

  private static boolean cleanUnusedSource(Project project) {
    return ProjectDatabaseHelper.deleteUnunsedSource(project);
  }

  @SuppressWarnings({"CheckReturnValue", "try"})
  private static void createStandardClassCache() {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final GlobalCache globalCache = GlobalCache.getInstance();
    reflector
        .getStandardClasses()
        .values()
        .forEach(
            fqcn -> {
              try (TelemetryUtils.ScopedSpan scope =
                  TelemetryUtils.startScopedSpan("CacheEventSubscriber.createStandardClassCache")) {
                TelemetryUtils.ScopedSpan.addAnnotation(
                    TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));
                globalCache.loadMemberDescriptors(fqcn);
              } catch (Exception e) {
                log.catching(e);
                ErrorReporter.report(e);
              }
            });
    createClassCache("java.util.*");
    createClassCache("java.io.*");
  }

  @SuppressWarnings({"CheckReturnValue", "try"})
  private static void createClassCache(String name) {
    final CachedASMReflector reflector = CachedASMReflector.getInstance();
    final GlobalCache globalCache = GlobalCache.getInstance();
    reflector
        .getPackageClasses(name)
        .values()
        .forEach(
            fqcn -> {
              try (TelemetryUtils.ScopedSpan scope =
                  TelemetryUtils.startScopedSpan("CacheEventSubscriber.createClassCache")) {
                TelemetryUtils.ScopedSpan.addAnnotation(
                    TelemetryUtils.annotationBuilder().put("fqcn", fqcn).build("args"));
                globalCache.loadMemberDescriptors(fqcn);
              } catch (Exception e) {
                log.catching(e);
                ErrorReporter.report(e);
              }
            });
  }
}
