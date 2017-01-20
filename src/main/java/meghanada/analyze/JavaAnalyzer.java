package meghanada.analyze;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.util.Context;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.lang.model.element.Element;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JavaAnalyzer {

    public static final String COMPILE_CHECKSUM = "compile_checksum.dat";
    public static final String CALLER = "source_caller.dat";

    private static final Logger log = LogManager.getLogger(JavaAnalyzer.class);
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final TreeAnalyzer treeAnalyzer = new TreeAnalyzer();
    private final Set<File> sourceRoots;
    private String compileSource = "1.8";
    private String compileTarget = "1.8";

    public JavaAnalyzer(final String compileSource, final String compileTarget, final Set<File> sourceRoots) {
        this.compileSource = compileSource;
        this.compileTarget = compileTarget;
        this.sourceRoots = sourceRoots;
        log.debug("Compiler settings compileSource:{} compileTarget:{}", this.compileSource, this.compileTarget);
    }

    public CompileResult analyzeAndCompile(final List<File> files, final String classpath, final String out) throws IOException {

        if (files == null || files.isEmpty()) {
            final Map<File, Source> analyzedMap = new HashMap<>();
            log.warn("compile targets is empty");
            return new CompileResult(true, analyzedMap);
        }

        final File tempOut = new File(out);
        if (!tempOut.exists() && !tempOut.mkdirs()) {
            log.warn("fail mkdirs path:{}", tempOut);
        }
        log.trace("start compile classpath={} files={} output={}", classpath, files, out);
        return this.runAnalyzeAndCompile(classpath, out, files);
    }

    private CompileResult runAnalyzeAndCompile(final String classpath, final String out, List<File> compileFiles) throws IOException {

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(compileFiles);
            final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            final JavaCompiler.CompilationTask compilerTask = compiler.getTask(null,
                    fileManager,
                    diagnosticCollector,
                    Arrays.asList(
                            "-cp", classpath,
                            "-g", "-deprecation",
                            "-d", out,
                            "-source", this.compileSource,
                            "-target", this.compileTarget,
                            "-encoding", "UTF-8"
                    ),
                    null,
                    compilationUnits);

            final JavacTask javacTask = (JavacTask) compilerTask;

            // this.replaceParser(compilerTask);

            final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
            final Iterable<? extends Element> analyzed = javacTask.analyze();

            final List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
            final Set<File> errorFiles = this.getErrorFiles(diagnostics);

            final Map<File, Source> analyzedMap = treeAnalyzer.analyze(parsedIter, errorFiles);

            final Iterable<? extends JavaFileObject> generate = javacTask.generate();

            boolean success = errorFiles.size() == 0;
            return new CompileResult(success, analyzedMap, diagnostics, errorFiles);
        }
    }

    private Set<File> getErrorFiles(final List<Diagnostic<? extends JavaFileObject>> diagnostics) {
        final Set<File> temp = Collections.newSetFromMap(new ConcurrentHashMap<File, Boolean>());
        diagnostics.forEach(diagnostic -> {
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

    private void replaceParser(final JavaCompiler.CompilationTask compilerTask) {
        final JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilerTask;
        final Context context = javacTaskImpl.getContext();
        FuzzyParserFactory.instance(context);
    }

}
