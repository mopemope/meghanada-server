package meghanada.compiler;

import com.google.common.collect.Lists;
import meghanada.config.Config;
import meghanada.parser.source.JavaSource;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class SimpleJavaCompiler {

    public static final String COMPILE_CHECKSUM = "compile_checksum.dat";
    private static Logger log = LogManager.getLogger(SimpleJavaCompiler.class);

    private final Set<File> sourceRoots;
    private String compileSource = "1.8";
    private String compileTarget = "1.8";

    public SimpleJavaCompiler(final String compileSource, final String compileTarget, final Set<File> sourceRoots) {
        this.compileSource = compileSource;
        this.compileTarget = compileTarget;
        this.sourceRoots = sourceRoots;
        log.debug("Compiler settings compileSource:{} compileTarget:{}", this.compileSource, this.compileTarget);
    }

    public CompileResult compile(File file, String classpath, String output, boolean force) throws IOException {
        return compileFiles(Lists.newArrayList(file), classpath, output, force);
    }

    public CompileResult compileFiles(final List<File> files, final String classpath, final String output, boolean force) throws IOException {

        File tempOut = new File(output);
        if (!tempOut.exists()) {
            tempOut.mkdirs();
        }
        final List<File> compileFiles = force ? files : getCompileFiles(files, this.sourceRoots, tempOut);
        if (compileFiles.isEmpty()) {
            log.warn("compileFiles isEmpty");
            return new CompileResult(true);
        }
        // log.debug("start compile classpath {} output {}", classpath, output);
        log.debug("start compile output {}", output);
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, Charset.forName("UTF-8"))) {
            final Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(compileFiles);
            final DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<>();
            final JavaCompiler.CompilationTask task = compiler.getTask(null,
                    fileManager,
                    diagnosticCollector,
                    Arrays.asList(
                            "-cp", classpath,
                            "-g", "-deprecation",
                            "-d", output,
                            "-source", this.compileSource,
                            "-target", this.compileTarget,
                            "-encoding", "UTF-8",
                            "-Xlint:-options"
                    ),
                    null,
                    compilationUnits);
            final boolean success = task.call();

            if (!success && log.isDebugEnabled()) {
                log.warn("CompileError Diagnostics:{}", diagnosticCollector.getDiagnostics());
            }
            return new CompileResult(success, diagnosticCollector.getDiagnostics());
        }
    }

    private boolean hasClassFile(String path, Set<File> sourceRoots, File out) throws IOException {
        for (File rootFile : sourceRoots) {
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

    private List<File> getCompileFiles(final List<File> files, final Set<File> sourceRoots, final File output) throws IOException {

        final File checksumFile = FileUtils.getSettingFile(SimpleJavaCompiler.COMPILE_CHECKSUM);
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
