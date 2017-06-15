package meghanada.session.subscribe;

import static meghanada.config.Config.timeItF;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.util.Collection;
import meghanada.analyze.CompileResult;
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
        "create class index. read " + size + " jars. elapsed:{}",
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
                log.warn("Compile Warning : {}", compileResult.getDiagnosticsSummary());
              }
              final CompileResult testCompileResult = project.compileTestJava();
              if (testCompileResult.isSuccess()) {
                if (testCompileResult.hasDiagnostics()) {
                  log.warn("Test Compile Warning : {}", testCompileResult.getDiagnosticsSummary());
                }
              } else {
                log.warn("Test Compile Error : {}", testCompileResult.getDiagnosticsSummary());
              }
            } else {
              log.warn("Compile Error : {}", compileResult.getDiagnosticsSummary());
            }
          } catch (Exception e) {
            log.catching(e);
          }
        });

    final Runtime runtime = Runtime.getRuntime();
    final float maxMemory = runtime.maxMemory() / 1024 / 1024;
    final float totalMemory = runtime.totalMemory() / 1024 / 1024;
    final float usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;

    log.info(
        "class index size:{} total elapsed:{}",
        reflector.getGlobalClassIndex().size(),
        stopwatch.stop());
    log.info(
        "memory usage (used/total/max): {}MB / {}MB / {}MB",
        String.format("%.2f", usedMemory),
        String.format("%.2f", totalMemory),
        String.format("%.2f", maxMemory));
    log.info("Ready");
  }

  private boolean cleanUnusedSource(Project project) {
    return ProjectDatabaseHelper.deleteUnunsedSource(project);
  }
}
