package meghanada.analyze;

import static java.util.Objects.nonNull;
import static meghanada.analyze.TreeAnalyzer.analyze;

import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import meghanada.config.Config;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaAnalyzer {

  private static final Logger log = LogManager.getLogger(JavaAnalyzer.class);

  private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
  private final ExecutorService executorService;

  private final String compileSource;
  private final String compileTarget;
  private final EventBus eventBus;

  public JavaAnalyzer(String compileSource, String compileTarget) {
    this.compileSource = compileSource;
    this.compileTarget = compileTarget;
    this.executorService = Executors.newFixedThreadPool(2);
    this.eventBus =
        new AsyncEventBus(
            executorService,
            (throwable, subscriberExceptionContext) -> {
              if (!(throwable instanceof RejectedExecutionException)) {
                log.error(throwable.getMessage(), throwable);
              }
            });

    log.debug(
        "compiler settings compileSource:{} compileTarget:{}",
        this.compileSource,
        this.compileTarget);
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

  private static Set<File> getErrorFiles(
      final List<Diagnostic<? extends JavaFileObject>> diagnostics) {

    final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<>(diagnostics.size()));

    for (final Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      final Diagnostic.Kind kind = diagnostic.getKind();
      final JavaFileObject fileObject = diagnostic.getSource();
      if (nonNull(fileObject) && kind.equals(Diagnostic.Kind.ERROR)) {
        final URI uri = fileObject.toUri();
        try {
          temp.add(new File(uri.normalize()).getCanonicalFile());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    }

    return temp;
  }

  public EventBus getEventBus() {
    return this.eventBus;
  }

  public CompileResult analyzeAndCompile(final List<File> files, final String classpath, String out)
      throws IOException {
    return analyzeAndCompile(files, classpath, out, true);
  }

  public CompileResult analyzeAndCompile(
      final List<File> files, final String classpath, final String out, final boolean generate)
      throws IOException {
    return analyzeAndCompile(files, classpath, out, generate, false);
  }

  public CompileResult analyzeAndCompile(
      final List<File> files,
      final String classpath,
      final String out,
      final boolean generate,
      final boolean isDiagnostic)
      throws IOException {

    if (files.isEmpty()) {
      Map<File, Source> analyzedMap = new HashMap<>(0);
      log.debug("compile targets is empty");
      return new CompileResult(true, analyzedMap);
    }

    File tempOut = new File(out);
    if (!tempOut.exists() && !tempOut.mkdirs()) {
      log.warn("fail mkdirs path:{}", tempOut);
    }
    log.trace("start compile classpath={} files={} output={}", classpath, files, out);
    return this.runAnalyzeAndCompile(classpath, out, files, generate, isDiagnostic);
  }

  private CompileResult runAnalyzeAndCompile(
      final String classpath,
      final String out,
      final List<File> compileFiles,
      final boolean generate,
      final boolean isDiagnostics)
      throws IOException {

    final Config config = Config.load();

    try (final StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      final Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(compileFiles);
      final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      final List<String> opts =
          Arrays.asList(
              "-cp",
              classpath,
              "-g",
              config.getJavacArg(),
              "-d",
              out,
              "-source",
              this.compileSource,
              "-target",
              this.compileTarget,
              "-encoding",
              "UTF-8");

      List<String> compileOptions = new ArrayList<>(16);
      if (this.compileTarget.equals("1.8")) {
        compileOptions.addAll(config.getJava8JavacArgs());
      } else if (this.compileTarget.equals("1.9") || this.compileTarget.equals("9")) {
        compileOptions.addAll(config.getJava9JavacArgs());
      } else if (this.compileTarget.equals("1.10") || this.compileTarget.equals("10")) {
        compileOptions.addAll(config.getJava10JavacArgs());
      }
      compileOptions.addAll(opts);
      final JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      final JavacTask javacTask = (JavacTask) compilerTask;

      final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          diagnosticCollector.getDiagnostics();
      final Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      final Map<File, Source> analyzedMap = analyze(parsedIter, errorFiles);

      if (generate && !Config.load().useExternalBuilder()) {
        javacTask.generate();
        CachedASMReflector.getInstance().updateClassIndexFromDirectory();
      }

      this.eventBus.post(new AnalyzedEvent(analyzedMap, isDiagnostics));

      final boolean success = errorFiles.size() == 0;
      // ProjectDatabaseHelper.saveCompileResult(result);
      return new CompileResult(success, analyzedMap, diagnostics, errorFiles);
    }
  }

  public CompileResult runAnalyzeAndCompile(
      final String classpath,
      final String out,
      final String sourcePath,
      final String sourceCode,
      final boolean generate,
      final boolean isDiagnostics)
      throws IOException {

    final Config config = Config.load();

    try (final StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      final File sourceFile = new File(sourcePath);
      final JavaFileObject fileObject =
          new JavaSourceFromString(sourceFile.getCanonicalPath(), sourceCode);
      final List<? extends JavaFileObject> compilationUnits = Collections.singletonList(fileObject);
      final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      final List<String> opts =
          Arrays.asList(
              "-cp",
              classpath,
              "-g",
              config.getJavacArg(),
              "-d",
              out,
              "-source",
              this.compileSource,
              "-target",
              this.compileTarget,
              "-encoding",
              "UTF-8");
      List<String> compileOptions = new ArrayList<>(16);
      if (this.compileTarget.equals("1.8")) {
        compileOptions.addAll(config.getJava8JavacArgs());
      } else if (this.compileTarget.equals("1.9") || this.compileTarget.equals("9")) {
        compileOptions.addAll(config.getJava9JavacArgs());
      } else if (this.compileTarget.equals("1.10") || this.compileTarget.equals("10")) {
        compileOptions.addAll(config.getJava10JavacArgs());
      }
      compileOptions.addAll(opts);

      final JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      final JavacTask javacTask = (JavacTask) compilerTask;

      final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          diagnosticCollector.getDiagnostics();

      final Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      final Map<File, Source> analyzedMap = analyze(parsedIter, errorFiles);
      Future<?> future =
          this.getExecutorService()
              .submit(
                  () -> {
                    try {
                      if (generate && !Config.load().useExternalBuilder()) {
                        javacTask.generate();
                        CachedASMReflector.getInstance().updateClassIndexFromDirectory();
                      }
                      this.eventBus.post(new AnalyzedEvent(analyzedMap, isDiagnostics));
                    } catch (IOException e) {
                      log.catching(e);
                    }
                  });
      final boolean success = errorFiles.size() == 0;
      return new CompileResult(success, analyzedMap, diagnostics, errorFiles);
    }
  }

  public ExecutorService getExecutorService() {
    return this.executorService;
  }

  public void shutdown() {
    this.executorService.shutdown();
    try {
      this.executorService.awaitTermination(3, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      log.catching(e);
    }
  }

  public interface SourceAnalyzedHandler {
    void analyzed(Source javaSource) throws IOException;

    void complete() throws IOException;
  }

  private static class JavaSourceFromString extends SimpleJavaFileObject {
    final String code;

    JavaSourceFromString(String filePath, String code) {
      super(new File(filePath).toURI(), Kind.SOURCE);
      this.code = code;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return code;
    }
  }

  public static class AsyncRunEvent {
    public final Runnable runnable;

    public AsyncRunEvent(Runnable runnable) {
      this.runnable = runnable;
    }
  }

  public static class AnalyzedEvent {
    public final Map<File, Source> analyzedMap;
    public boolean diagnostics;

    public AnalyzedEvent(Map<File, Source> analyzedMap) {
      this.analyzedMap = analyzedMap;
    }

    public AnalyzedEvent(Map<File, Source> analyzedMap, boolean diagnostics) {
      this.analyzedMap = analyzedMap;
      this.diagnostics = diagnostics;
    }
  }
}
