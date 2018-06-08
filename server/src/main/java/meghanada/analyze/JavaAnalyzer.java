package meghanada.analyze;

import static java.util.Objects.nonNull;
import static meghanada.analyze.TreeAnalyzer.analyze;

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
import java.util.concurrent.Future;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import meghanada.config.Config;
import meghanada.event.SystemEventBus;
import meghanada.reflect.asm.CachedASMReflector;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaAnalyzer {

  private static final Logger log = LogManager.getLogger(JavaAnalyzer.class);

  private static final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

  private final String compileSource;
  private final String compileTarget;

  public JavaAnalyzer(String compileSource, String compileTarget) {
    this.compileSource = compileSource;
    this.compileTarget = compileTarget;
    log.debug(
        "compiler settings compileSource:{} compileTarget:{}",
        this.compileSource,
        this.compileTarget);
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
      setJavacArgs(config, compileOptions);
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

      SystemEventBus.getInstance()
          .getEventBus()
          .post(new AnalyzedEvent(analyzedMap, isDiagnostics));

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
      setJavacArgs(config, compileOptions);
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
      SystemEventBus systemEventBus = SystemEventBus.getInstance();
      Future<?> future =
          systemEventBus
              .getExecutorService()
              .submit(
                  () -> {
                    try {
                      if (generate && !Config.load().useExternalBuilder()) {
                        javacTask.generate();
                        CachedASMReflector.getInstance().updateClassIndexFromDirectory();
                      }
                      systemEventBus
                          .getEventBus()
                          .post(new AnalyzedEvent(analyzedMap, isDiagnostics));
                    } catch (IOException e) {
                      log.catching(e);
                    }
                  });
      final boolean success = errorFiles.size() == 0;
      return new CompileResult(success, analyzedMap, diagnostics, errorFiles);
    }
  }

  private void setJavacArgs(Config config, List<String> compileOptions) {
    if (this.compileTarget.equals("1.8")) {
      compileOptions.addAll(config.getJava8JavacArgs());
    } else if (this.compileTarget.equals("1.9") || this.compileTarget.equals("9")) {
      compileOptions.addAll(config.getJava9JavacArgs());
    } else if (this.compileTarget.equals("1.10") || this.compileTarget.equals("10")) {
      compileOptions.addAll(config.getJava10JavacArgs());
    }
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

  public static class AnalyzedEvent {
    public final Map<File, Source> analyzedMap;
    public boolean diagnostics;

    AnalyzedEvent(Map<File, Source> analyzedMap, boolean diagnostics) {
      this.analyzedMap = analyzedMap;
      this.diagnostics = diagnostics;
    }
  }
}
