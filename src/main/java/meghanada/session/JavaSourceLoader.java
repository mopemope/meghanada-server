
package meghanada.session;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.github.javaparser.ParseException;
import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import meghanada.analyze.CompileResult;
import meghanada.analyze.JavaAnalyzer;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;


public class JavaSourceLoader extends CacheLoader<File, Source> {

    private static final Logger log = LogManager.getLogger(JavaSourceLoader.class);

    private Project project;

    public JavaSourceLoader(final Project project) {
        this.project = project;
    }

    @Override
    public Source load(final File file) throws IOException, ParseException {
        final Config config = Config.load();

        if (!config.useSourceCache()) {
            final CompileResult compileResult = project.compileFile(file, true);
            return compileResult.getSources().get(file);
        }

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

        final String path = file.getCanonicalPath();
        final String md5sum = FileUtils.md5sum(file);
        if (finalChecksumMap.containsKey(path)) {
            // compare checksum
            final String prevSum = finalChecksumMap.get(path);
            if (md5sum.equals(prevSum)) {
                // not modify
                // load from cache
                final Optional<Source> source = this.loadFromCache(file);
                if (source.isPresent()) {
                    return source.get();
                }
            }
            // update
            finalChecksumMap.put(path, md5sum);
        } else {
            // save checksum
            finalChecksumMap.put(path, md5sum);
        }

        final CompileResult compileResult = project.compileFile(file.getCanonicalFile(), true);
        final Source source = compileResult.getSources().get(file.getCanonicalFile());
        FileUtils.writeMapSetting(finalChecksumMap, checksumFile);
        return this.writeCache(source);
    }

    private Optional<Source> loadFromCache(final File sourceFile) throws IOException, ParseException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final Config config = Config.load();
        final String dir = config.getProjectCacheDir();
        final File root = new File(dir);
        final String javaVersion = config.getJavaVersion();
        final String path = FileUtils.toHashedPath(sourceFile, ".dat");
        final String out = Joiner.on(File.separator).join(javaVersion, "source", path);
        final File file = new File(root, out);

        if (!file.exists()) {
            return Optional.empty();
        }

        log.debug("load file:{}", file);
        try {
            final Source source = reflector.getKryoPool().run(kryo -> {
                try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(file), 8192)))) {
                    return kryo.readObject(input, Source.class);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            return Optional.ofNullable(source);
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }

    }

    private Source writeCache(final Source source) throws IOException, ParseException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectCacheDir();
        final File root = new File(dir);
        final String javaVersion = config.getJavaVersion();
        final String path = FileUtils.toHashedPath(sourceFile, ".dat");
        final String out = Joiner.on(File.separator).join(javaVersion, "source", path);
        final File file = new File(root, out);

        file.getParentFile().mkdirs();

        log.debug("write file:{}", file);
        reflector.getKryoPool().run(new KryoCallback<Source>() {
            @Override
            public Source execute(Kryo kryo) {
                try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192)))) {
                    kryo.writeObject(output, source);
                    return source;
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });
        return source;
    }

}
