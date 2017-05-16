package meghanada.analyze;

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
import javax.annotation.Nullable;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import meghanada.config.Config;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JavaAnalyzer {

  private static final Logger log = LogManager.getLogger(JavaAnalyzer.class);

  private String compileSource = "1.8";
  private String compileTarget = "1.8";

  public JavaAnalyzer(final String compileSource, final String compileTarget) {
    this.compileSource = compileSource;
    this.compileTarget = compileTarget;
    log.debug(
        "compiler settings compileSource:{} compileTarget:{}",
        this.compileSource,
        this.compileTarget);
  }

  private static Set<File> getErrorFiles(
      final List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    final Set<File> temp =
        Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>(diagnostics.size()));
    diagnostics.forEach(
        diagnostic -> {
          final Diagnostic.Kind kind = diagnostic.getKind();
          final JavaFileObject fileObject = diagnostic.getSource();
          if (fileObject != null && kind.equals(Diagnostic.Kind.ERROR)) {
            final URI uri = fileObject.toUri();
            try {
              temp.add(new File(uri.normalize()).getCanonicalFile());
            } catch (IOException e) {
              throw new UncheckedIOException(e);
            }
          }
        });

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

    final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    final Config config = Config.load();
    final TreeAnalyzer treeAnalyzer = new TreeAnalyzer();
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

      // if (Main.isDevelop()) {
      // log.info("---------- development compile log. output={}
      // ----------", out);
      // for (final String cp : StringUtils.split(classpath,
      // File.pathSeparatorChar)) {
      // log.info("{}", cp);
      // }
      // }

      final JavaCompiler.CompilationTask compilerTask =
          compiler.getTask(
              null, fileManager, diagnosticCollector, compileOptions, null, compilationUnits);

      final JavacTask javacTask = (JavacTask) compilerTask;

      // this.replaceParser(compilerTask);

      final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
      javacTask.analyze();

      final List<Diagnostic<? extends JavaFileObject>> diagnostics =
          diagnosticCollector.getDiagnostics();
      final Set<File> errorFiles = JavaAnalyzer.getErrorFiles(diagnostics);

      final Map<File, Source> analyzedMap = treeAnalyzer.analyze(parsedIter, errorFiles, handler);

      if (generate && !Config.load().useExternalBuilder()) {
        javacTask.generate();
      }

      final boolean success = errorFiles.size() == 0;
      return new CompileResult(success, analyzedMap, diagnostics, errorFiles);
    }
  }

  public interface SourceAnalyzedHandler {
    void analyzed(final Source javaSource) throws IOException;

    void complete() throws IOException;
  }
}
