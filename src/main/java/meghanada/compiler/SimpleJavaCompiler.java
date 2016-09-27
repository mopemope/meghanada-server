package meghanada.compiler;

import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.google.common.collect.Lists;
import meghanada.config.Config;
import meghanada.parser.JavaSource;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.tools.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class SimpleJavaCompiler {

    public static final String COMPILE_CHECKSUM = "compile_checksum.dat";
    private static Logger log = LogManager.getLogger(SimpleJavaCompiler.class);

    private final Set<File> sourceRoots;
    private String compileSource = "1.8";
    private String compileTarget = "1.8";
    private Map<File, Map<String, String>> checksum = new HashMap<>();

    public SimpleJavaCompiler(String compileSource, String compileTarget, Set<File> sourceRoots) {
        this.compileSource = compileSource;
        this.compileTarget = compileTarget;
        this.sourceRoots = sourceRoots;
        log.debug("Compiler settings compileSource:{} compileTarget:{}", this.compileSource, this.compileTarget);
    }

    public static File getChecksumFile() {
        final String settingDir = Config.load().getProjectSettingDir();
        final File setting = new File(settingDir);
        if (!setting.exists()) {
            setting.mkdirs();
        }
        return new File(setting, COMPILE_CHECKSUM);
    }

    public static void writeChecksum(final Map<String, String> map, final File outFile) throws FileNotFoundException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        reflector.getKryoPool().run(kryo -> {
            try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(outFile), 8192)))) {
                kryo.writeObject(output, map);
                return map;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    public static Map<String, String> readChecksum(final File inFile) throws FileNotFoundException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        return reflector.getKryoPool().run(kryo -> {
            try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(inFile), 8192)))) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = kryo.readObject(input, HashMap.class);
                return map;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

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
            boolean success = task.call();
            log.debug("finish compile result {} {}", success, diagnosticCollector.getDiagnostics());
            if (!success) {
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

    private List<File> getCompileFiles(final List<File> files, final Set<File> sourceRoots, final File output) throws FileNotFoundException {

        final File checksumFile = getChecksumFile();
        if (!this.checksum.containsKey(checksumFile)) {
            Map<String, String> checksumMap = new ConcurrentHashMap<>(64);
            if (checksumFile.exists()) {
                checksumMap = new ConcurrentHashMap<>(this.readChecksum(checksumFile));
            }
            this.checksum.put(checksumFile, checksumMap);
        }

        Map<String, String> finalChecksumMap = this.checksum.get(checksumFile);
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

        SimpleJavaCompiler.writeChecksum(finalChecksumMap, checksumFile);
        this.checksum.put(checksumFile, finalChecksumMap);
        return fileList;
    }
}
