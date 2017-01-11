package meghanada.analyze;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.tools.javac.api.JavacTaskImpl;
import com.sun.tools.javac.parser.FuzzyParserFactory;
import com.sun.tools.javac.util.Context;
import meghanada.config.Config;
import meghanada.parser.source.JavaSource;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.lang.model.element.Element;
import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class JavaAnalyzer {

    private static final String COMPILE_CHECKSUM = "compile_checksum.dat";
    private static Logger log = LogManager.getLogger(JavaAnalyzer.class);
    public final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private Context context = new Context();
    private String compileSource = "1.8";
    private String compileTarget = "1.8";
    private Set<File> sourceRoots;

    public JavaAnalyzer() {
    }

    public JavaAnalyzer(final String compileSource, final String compileTarget, final Set<File> sourceRoots) {
        this();
        this.compileSource = compileSource;
        this.compileTarget = compileTarget;
        this.sourceRoots = sourceRoots;
        log.debug("Compiler settings compileSource:{} compileTarget:{}", this.compileSource, this.compileTarget);
    }

    public CompileResult analyzeAndCompile(final List<File> files, final String classpath, final String out, boolean force) throws IOException {

        if (files == null || files.isEmpty()) {
            final Map<File, Source> analyzedMap = new HashMap<>();
            log.warn("compile targets is empty");
            return new CompileResult(true, analyzedMap);
        }

        final File tempOut = new File(out);
        if (!tempOut.exists()) {
            if (!tempOut.mkdirs()) {
                log.warn("fail mkdirs path:{}", tempOut);
            }
        }

        final List<File> compileFiles = force ? files : getCompileTargets(files, this.sourceRoots, tempOut);

        if (compileFiles.isEmpty()) {
            final Map<File, Source> analyzedMap = new HashMap<>();
            log.warn("compileFiles isEmpty");
            return new CompileResult(true, analyzedMap);
        }

        log.trace("start compile classpath={} files={} output={}", classpath, compileFiles, out);

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
            return new CompileResult(success, analyzedMap, diagnostics);
        }
    }

    private void replaceParser(final JavaCompiler.CompilationTask compilerTask) {
        final JavacTaskImpl javacTaskImpl = (JavacTaskImpl) compilerTask;
        final Context context = javacTaskImpl.getContext();
        FuzzyParserFactory.instance(context);
    }

    private boolean hasClassFile(String path, Set<File> sourceRoots, File out) throws IOException {
        if (sourceRoots == null) {
            return false;
        }
        for (final File rootFile : sourceRoots) {
            final String root = rootFile.getCanonicalPath();
            if (path.startsWith(root)) {
                // find
                final String src = path.substring(root.length());
                final String classFile = ClassNameUtils.replace(src, ".java", ".class");
                final File file = new File(out, classFile);
                return file.exists();
            }
        }
        return false;
    }

    private List<File> getCompileTargets(final List<File> files, final Set<File> sourceRoots, final File output) throws IOException {

        final File checksumFile = FileUtils.getSettingFile(JavaAnalyzer.COMPILE_CHECKSUM);
        final Map<File, Map<String, String>> checksum = Config.load().getAllChecksumMap();

        if (!checksum.containsKey(checksumFile)) {
            Map<String, String> checksumMap = new ConcurrentHashMap<>(64);
            if (checksumFile.exists()) {
                checksumMap = new ConcurrentHashMap<>(FileUtils.readMapSetting(checksumFile));
            }
            checksum.put(checksumFile, checksumMap);
        }

        final Map<String, String> finalChecksumMap = checksum.get(checksumFile);
        final List<File> fileList = files
                .stream()
                .parallel()
                .filter(f -> {
                    if (!JavaSource.isJavaFile(f)) {
                        return false;
                    }
                    try {
                        final String path = f.getCanonicalPath();
                        final String md5sum = FileUtils.md5sum(f);
                        if (!this.hasClassFile(path, sourceRoots, output)) {
                            return true;
                        }

                        if (finalChecksumMap.containsKey(path)) {
                            // compare checksum
                            final String prevSum = finalChecksumMap.get(path);
                            if (md5sum.equals(prevSum)) {
                                // not modify
                                return false;
                            }
                            // update
                            finalChecksumMap.put(path, md5sum);
                        } else {
                            // save checksum
                            finalChecksumMap.put(path, md5sum);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return true;
                }).collect(Collectors.toList());

        FileUtils.writeMapSetting(finalChecksumMap, checksumFile);
        checksum.put(checksumFile, finalChecksumMap);
        return fileList;
    }

}
