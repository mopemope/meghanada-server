package meghanada.session;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.github.javaparser.ParseException;
import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import meghanada.compiler.SimpleJavaCompiler;
import meghanada.config.Config;
import meghanada.parser.JavaParser;
import meghanada.parser.source.JavaSource;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class JavaSourceLoader extends CacheLoader<File, JavaSource> {

    private static final Logger log = LogManager.getLogger(JavaSourceLoader.class);

    private JavaParser javaParser;

    public JavaSourceLoader() {
    }

    @Override
    public JavaSource load(final File file) throws IOException, ParseException {
        if (this.javaParser == null) {
            this.javaParser = new JavaParser();
        }
        final Config config = Config.load();

        if (!config.useSourceCache()) {
            return javaParser.parse(file);
        }

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

        final String path = file.getCanonicalPath();
        final String md5sum = FileUtils.md5sum(file);
        if (finalChecksumMap.containsKey(path)) {
            // compare checksum
            final String prevSum = finalChecksumMap.get(path);
            if (md5sum.equals(prevSum)) {
                // not modify
                // load from cache
                return this.loadFromCache(file);
            }
            // update
            // finalChecksumMap.put(path, md5sum);
        } else {
            // save checksum
            // finalChecksumMap.put(path, md5sum);
        }

        final JavaSource source = javaParser.parse(file);
        return this.writeCache(source);
    }

    private JavaSource loadFromCache(final File sourceFile) throws IOException, ParseException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir, "cache");
        final String javaVersion = config.getJavaVersion();
        final String path = FileUtils.toHashedPath(sourceFile, ".dat");
        final String out = Joiner.on(File.separator).join(javaVersion, "source", path);
        final File file = new File(root, out);

        if (!file.exists()) {
            final JavaSource source = this.javaParser.parse(sourceFile);
            return this.writeCache(source);
        }

        log.debug("load file:{}", file);
        try {
            return reflector.getKryoPool().run(kryo -> {
                try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(file), 8192)))) {
                    return kryo.readObject(input, JavaSource.class);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw new IOException(e);
        }

    }

    private JavaSource writeCache(final JavaSource source) throws IOException, ParseException {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir, "cache");
        final String javaVersion = config.getJavaVersion();
        final String path = FileUtils.toHashedPath(sourceFile, ".dat");
        final String out = Joiner.on(File.separator).join(javaVersion, "source", path);
        final File file = new File(root, out);

        file.getParentFile().mkdirs();

        log.debug("write file:{}", file);
        reflector.getKryoPool().run(new KryoCallback<JavaSource>() {
            @Override
            public JavaSource execute(Kryo kryo) {
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
