package meghanada.analyze;

import static java.util.Objects.nonNull;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.util.Context;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
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
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;
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
  private final TreeAnalyzer treeAnalyzer = new TreeAnalyzer();
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();
  private String compileSource = "1.8";
  private String compileTarget = "1.8";

  public JavaAnalyzer(final String compileSource, final String compileTarget) {
    this.compileSource = compileSource;
    this.compileTarget = compileTarget;
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

    final Set<File> temp =
        Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(diagnostics.size()));

    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
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

  @SuppressWarnings("CheckReturnValue")
  private static void replaceParser(final JavaCompiler.CompilationTask compilerTask) {
    final JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilerTask;
    final Context context = javacTaskImpl.getContext();
    FuzzyParserFactory.instance(context);
  }

  public CompileResult analyzeAndCompile(
      final List<File> files, final String classpath, final String out) throws IOException {
    return analyzeAndCompile(files, classpath, out, true);
  }

  public CompileResult analyzeAndCompile(
      final List<File> files, final String classpath, final String out, final boolean generate)
      throws IOException {
    return analyzeAndCompile(files, classpath, out, generate, null);
  }

  public CompileResult analyzeAndCompile(
      final List<File> files,
      final String classpath,
      final String out,
      final boolean generate,
      @Nullable final SourceAnalyzedHandler handler)
      throws IOException {

    if (files.isEmpty()) {
      final Map<File, Source> analyzedMap = new HashMap<>(0);
      log.debug("compile targets is empty");
      return new CompileResult(true, analyzedMap);
    }

    final File tempOut = new File(out);
    if (!tempOut.exists() && !tempOut.mkdirs()) {
      log.warn("fail mkdirs path:{}", tempOut);
    }
    log.trace("start compile classpath={} files={} output={}", classpath, files, out);
    return this.runAnalyzeAndCompile(classpath, out, files, generate, handler);
  }

  private CompileResult runAnalyzeAndCompile(
      final String classpath,
      final String out,
      final List<File> compileFiles,
      final boolean generate,
      @Nullable final SourceAnalyzedHandler handler)
      throws IOException {

    final Config config = Config.load();

    try (final StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      final Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(compileFiles);
      final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      final List<String> compileOptions =
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

      final JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      final JavacTask javacTask = (JavacTask) compilerTask;

      final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          diagnosticCollector.getDiagnostics();
      final Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      final Map<File, Source> analyzedMap = treeAnalyzer.analyze(parsedIter, errorFiles);

      if (generate && !Config.load().useExternalBuilder()) {
        javacTask.generate();
        CachedASMReflector.getInstance().updateClassIndexFromDirectory();
      }

      if (nonNull(handler)) {
        analyzedMap
            .values()
            .parallelStream()
            .forEach(
                source -> {
                  try {
                    handler.analyzed(source);
                  } catch (IOException e) {
                    throw new UncheckedIOException(e);
                  }
                });
        handler.complete();
      }

      final boolean success = errorFiles.size() == 0;
      CompileResult result = new CompileResult(success, analyzedMap, diagnostics, errorFiles);
      // ProjectDatabaseHelper.saveCompileResult(result);
      return result;
    }
  }

  public CompileResult runAnalyzeAndCompile(
      final String classpath,
      final String out,
      final String sourcePath,
      final String sourceCode,
      final boolean generate,
      @Nullable final SourceAnalyzedHandler handler)
      throws IOException {

    final Config config = Config.load();

    try (final StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      File sourceFile = new File(sourcePath);
      JavaFileObject fileObject =
          new JavaSourceFromString(sourceFile.getCanonicalPath(), sourceCode);
      List<? extends JavaFileObject> compilationUnits = Arrays.asList(fileObject);
      final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      final List<String> compileOptions =
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

      final JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      final JavacTask javacTask = (JavacTask) compilerTask;

      final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          diagnosticCollector.getDiagnostics();

      final Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      Future<?> future =
          this.getExecutorService()
              .submit(
                  () -> {
                    try {
                      final Map<File, Source> analyzedMap =
                          treeAnalyzer.analyze(parsedIter, errorFiles);
                      if (generate && !Config.load().useExternalBuilder()) {
                        javacTask.generate();
                        CachedASMReflector.getInstance().updateClassIndexFromDirectory();
                      }
                      if (nonNull(handler)) {
                        for (Source source : analyzedMap.values()) {
                          handler.analyzed(source);
                        }
                        handler.complete();
                      }
                    } catch (IOException e) {
                      log.catching(e);
                    }
                  });
      final boolean success = errorFiles.size() == 0;
      return new CompileResult(success, new HashMap<>(0), diagnostics, errorFiles);
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
    void analyzed(final Source javaSource) throws IOException;

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
}
