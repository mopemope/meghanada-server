package meghanada.analyze;

import static java.util.Objects.nonNull;
import static meghanada.analyze.TreeAnalyzer.analyze;

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
  private ExecutorService executorService = Executors.newSingleThreadExecutor();
  private String compileSource = "1.8";
  private String compileTarget = "1.8";

  public JavaAnalyzer(String compileSource, String compileTarget) {
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

  private static Set<File> getErrorFiles(List<Diagnostic<? extends JavaFileObject>> diagnostics) {

    Set<File> temp =
        Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(diagnostics.size()));

    for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics) {
      Diagnostic.Kind kind = diagnostic.getKind();
      JavaFileObject fileObject = diagnostic.getSource();
      if (nonNull(fileObject) && kind.equals(Diagnostic.Kind.ERROR)) {
        URI uri = fileObject.toUri();
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
  private static void replaceParser(JavaCompiler.CompilationTask compilerTask) {
    JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilerTask;
    Context context = javacTaskImpl.getContext();
    FuzzyParserFactory.instance(context);
  }

  public CompileResult analyzeAndCompile(List<File> files, String classpath, String out)
      throws IOException {
    return analyzeAndCompile(files, classpath, out, true);
  }

  public CompileResult analyzeAndCompile(
      List<File> files, String classpath, String out, boolean generate) throws IOException {
    return analyzeAndCompile(files, classpath, out, generate, null);
  }

  public CompileResult analyzeAndCompile(
      List<File> files,
      String classpath,
      String out,
      boolean generate,
      @Nullable SourceAnalyzedHandler handler)
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
    return this.runAnalyzeAndCompile(classpath, out, files, generate, handler);
  }

  private CompileResult runAnalyzeAndCompile(
      String classpath,
      String out,
      List<File> compileFiles,
      boolean generate,
      @Nullable SourceAnalyzedHandler handler)
      throws IOException {

    Config config = Config.load();

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      Iterable<? extends JavaFileObject> compilationUnits =
          fileManager.getJavaFileObjectsFromFiles(compileFiles);
      DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      List<String> compileOptions =
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

      JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      JavacTask javacTask = (JavacTask) compilerTask;

      Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
      Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      Map<File, Source> analyzedMap = analyze(parsedIter, errorFiles);

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

      boolean success = errorFiles.size() == 0;
      CompileResult result = new CompileResult(success, analyzedMap, diagnostics, errorFiles);
      // ProjectDatabaseHelper.saveCompileResult(result);
      return result;
    }
  }

  public CompileResult runAnalyzeAndCompile(
      String classpath,
      String out,
      String sourcePath,
      String sourceCode,
      boolean generate,
      @Nullable SourceAnalyzedHandler handler)
      throws IOException {

    Config config = Config.load();

    try (StandardJavaFileManager fileManager =
        compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {

      File sourceFile = new File(sourcePath);
      JavaFileObject fileObject =
          new JavaSourceFromString(sourceFile.getCanonicalPath(), sourceCode);
      List<? extends JavaFileObject> compilationUnits = Arrays.asList(fileObject);
      DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
      List<String> compileOptions =
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

      JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      JavacTask javacTask = (JavacTask) compilerTask;

      Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();

      Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      Future<?> future =
          this.getExecutorService()
              .submit(
                  () -> {
                    try {
                      Map<File, Source> analyzedMap = analyze(parsedIter, errorFiles);
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
      boolean success = errorFiles.size() == 0;
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
    void analyzed(Source javaSource) throws IOException;

    void complete() throws IOException;
  }

  private static class JavaSourceFromString extends SimpleJavaFileObject {
    String code;

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
