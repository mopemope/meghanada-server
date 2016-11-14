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
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JavaAnalyzer {

    private static Logger log = LogManager.getLogger(JavaAnalyzer.class);
    public final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private Context context = new Context();
    private String compileSource = "1.8";
    private String compileTarget = "1.8";

    public JavaAnalyzer() {
    }

    public JavaAnalyzer(final String compileSource, final String compileTarget) {
        this();
        this.compileSource = compileSource;
        this.compileTarget = compileTarget;
    }

    public Result analayzeAndCompile(final List<File> files, final String classpath, final String out) throws IOException {

        if (files == null || files.isEmpty()) {
            final Map<File, Source> analyzedMap = new HashMap<>();
            log.warn("compile targets is empty");
            return new Result(true, analyzedMap);
        }
        log.trace("start compile classpath={} files={} output={}", classpath, files, out);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(files);
            final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            final JavaCompiler.CompilationTask compilerTask = compiler.getTask(null,
                    fileManager,
                    diagnosticCollector,
                    Arrays.asList(
                            "-cp", classpath,
                            "-g", "-deprecation",
                            "-d", out,
                            "-source", "1.8",
                            "-target", "1.8",
                            "-encoding", "UTF-8"
                    ),
                    null,
                    compilationUnits);

            final JavacTask javacTask = (JavacTask) compilerTask;
            this.replaceParser(compilerTask);

            final Iterable<? extends CompilationUnitTree> parsedIter = javacTask.parse();
            final Iterable<? extends Element> analyzed = javacTask.analyze();

            final TreeAnalyzer treeAnalyzer = new TreeAnalyzer();
            final Map<File, Source> analyzedMap = treeAnalyzer.analyze(parsedIter);
            final Iterable<? extends JavaFileObject> generate = javacTask.generate();

            final List<Diagnostic<? extends JavaFileObject>> diagnostics = diagnosticCollector.getDiagnostics();
            // TODO check success
            boolean success = diagnostics == null || diagnostics.size() == 0;
            return new Result(success, analyzedMap, diagnostics);
        }
    }

    private void replaceParser(final JavaCompiler.CompilationTask compilerTask) {
        final JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilerTask;
        final Context context = javacTaskImpl.getContext();
        FuzzyParserFactory.instance(context);
    }

}
