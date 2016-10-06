package meghanada.reflect.asm;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoCallback;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.base.Stopwatch;
import com.google.common.cache.CacheLoader;
import meghanada.config.Config;
import meghanada.reflect.CandidateUnit;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.names.MethodParameterNames;
import meghanada.utils.ClassName;
import meghanada.utils.ClassNameUtils;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import static meghanada.utils.FunctionUtils.wrapIO;
import static meghanada.utils.FunctionUtils.wrapIOConsumer;

class MemberCacheLoader extends CacheLoader<String, List<MemberDescriptor>> {

    private static final Logger log = LogManager.getLogger(MemberCacheLoader.class);
    private static final String CLASS_CHECKSUM = "class_checksum.dat";
    private final Map<String, File> classFileMap;
    private final Map<ClassIndex, File> reflectIndex;
    private final String projectCache;
    private final String javaVersion;
    private final Map<String, String> cacheChecksum;
    private final File cacheChecksumFile;
    private final KryoPool kryoPool;

    MemberCacheLoader(Map<String, File> classFileMap, Map<ClassIndex, File> reflectIndex) {
        this.classFileMap = classFileMap;
        this.reflectIndex = reflectIndex;
        Config config = Config.load();
        this.projectCache = config.getProjectCacheDir();
        this.javaVersion = Config.load().getJavaVersion();

        this.kryoPool = new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            kryo.register(ClassIndex.class);
            kryo.register(MemberDescriptor.class);
            kryo.register(MethodParameterNames.class);
            return kryo;
        }).softReferences().build();


        this.cacheChecksumFile = getChecksumFile();
        if (this.cacheChecksumFile.exists()) {
            this.cacheChecksum = new ConcurrentHashMap<>(this.readCacheChecksum(this.cacheChecksumFile));
        } else {
            this.cacheChecksum = new ConcurrentHashMap<>(64);
        }

        this.startCacheFlusher();
    }

    private File getChecksumFile() {
        final String settingDir = Config.load().getProjectSettingDir();
        final File settingFile = new File(settingDir);
        if (!settingFile.exists()) {
            settingFile.mkdirs();
        }
        return new File(settingFile, CLASS_CHECKSUM);
    }

    @Override
    public List<MemberDescriptor> load(final String className) throws IOException {

        final ClassName cn = new ClassName(className);
        final String fqcn = cn.getName();
        String path = ClassNameUtils.replace(fqcn, ".", File.separator);
        File cacheFilePath = new File(this.projectCache, this.javaVersion + "/member/" + path + ".dat");

        File classFile = this.classFileMap.get(fqcn);
        if (classFile == null) {
            // try inner class
            classFile = this.classFileMap.get(ClassNameUtils.replaceInnerMark(fqcn));
            if (classFile == null) {
                log.debug("Missing FQCN:{}'s file is null", fqcn);
                return Collections.emptyList();
            }
        }

        @SuppressWarnings("unchecked") List<MemberDescriptor> cachedResult = getCachedMemberDescriptors(fqcn, cacheFilePath, classFile);
        if (cachedResult != null) {
            return cachedResult;
        }

        final String fileName = classFile.getName();

        final String initName = ClassNameUtils.getSimpleName(fqcn);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        ASMReflector asmReflector = ASMReflector.getInstance();
        final InheritanceInfo info = asmReflector.getReflectInfo(reflectIndex, fqcn);
        List<MemberDescriptor> list = asmReflector.reflectAll(info);

        final List<MemberDescriptor> memberDescriptors = list.stream().filter(md -> {
            if (md.matchType(CandidateUnit.MemberType.CONSTRUCTOR)) {
                final String name = ClassNameUtils.getSimpleName(md.getName());
                return name.equals(initName);
            }
            return true;
        }).collect(Collectors.toList());

        this.writeFileCache(fqcn, memberDescriptors);
        log.trace("load fqcn:{} elapsed:{}", fqcn, stopwatch.stop());
        return memberDescriptors;
    }

    private List<MemberDescriptor> getCachedMemberDescriptors(final String fqcn, final File cacheFilePath, final File file) throws IOException {
        if (file.exists()) {
            final String fileName = file.getName();
            if (file.isFile() && fileName.endsWith(".class")) {
                final String md5sum = FileUtils.md5sum(file);
                final String filePath = file.getCanonicalPath();
                if (this.cacheChecksum.containsKey(filePath)) {
                    if (this.cacheChecksum.get(filePath).equals(md5sum)) {
                        // not modified
                        @SuppressWarnings("unchecked")
                        List<MemberDescriptor> cachedResult = this.loadFromCache(fqcn, cacheFilePath);
                        if (cachedResult != null) {
                            return cachedResult;
                        }
                    } else {
                        this.cacheChecksum.put(filePath, md5sum);
                    }
                } else {
                    this.cacheChecksum.put(filePath, md5sum);
                }
            } else if (file.isFile() && fileName.endsWith(".jar") && !fileName.contains("SNAPSHOT")) {
                @SuppressWarnings("unchecked")
                final List<MemberDescriptor> cachedResult = this.loadFromCache(fqcn, cacheFilePath);
                if (cachedResult != null) {
                    return cachedResult;
                }
            } else if (file.isFile() && fileName.endsWith(".jar") && fileName.contains("SNAPSHOT")) {
                // skip
                return null;
            } else {
                // Dir
                final File classFile = new File(file, ClassNameUtils.replace(fqcn, ".", File.separator) + ".class");
                if (classFile.exists()) {
                    final String md5sum = FileUtils.md5sum(classFile);
                    final String classFilePath = classFile.getCanonicalPath();
                    if (this.cacheChecksum.containsKey(classFilePath)) {
                        if (this.cacheChecksum.get(classFilePath).equals(md5sum)) {
                            // not modified
                            @SuppressWarnings("unchecked")
                            final List<MemberDescriptor> cachedResult = this.loadFromCache(fqcn, cacheFilePath);
                            if (cachedResult != null) {
                                return cachedResult;
                            }
                        } else {
                            this.cacheChecksum.put(classFilePath, md5sum);
                        }
                    } else {
                        this.cacheChecksum.put(classFilePath, md5sum);
                    }
                } else {
                    log.warn("not exists:{}", classFile);
                }
            }
        }
        return null;
    }

    private synchronized void writeFileCache(String fqcn, final List<MemberDescriptor> list) {
        final CachedASMReflector reflector = CachedASMReflector.getInstance();
        reflector.containsClassIndex(fqcn)
                .map(wrapIO(classIndex -> {
                    reflector.writeCache(classIndex, list, new File(this.projectCache));
                    return true;
                }))
                .orElseGet(() -> {
                    final String fqcn2 = ClassNameUtils.replaceInnerMark(fqcn);
                    reflector.containsClassIndex(fqcn2)
                            .ifPresent(wrapIOConsumer(classIndex -> {
                                reflector.writeCache(classIndex, list, new File(this.projectCache));
                            }));
                    return true;
                });

    }

    private List<MemberDescriptor> loadFromCache(String fqcn, File in) throws FileNotFoundException {
        if (in.exists()) {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(in), 8192)))) {
                return this.kryoPool.run(new KryoCallback<List<MemberDescriptor>>() {
                    @SuppressWarnings("unchecked")
                    @Override
                    public List<MemberDescriptor> execute(Kryo kryo) {
                        return kryo.readObject(input, ArrayList.class);
                    }
                });
            } finally {
                log.trace("load from cache file:{} elapsed:{}", fqcn, stopwatch.stop());
            }
        }
        return null;
    }

    private Map<String, String> readCacheChecksum(final File inFile) {
        return this.kryoPool.run(kryo -> {
            try (Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(inFile), 8192)))) {
                @SuppressWarnings("unchecked")
                Map<String, String> map = kryo.readObject(input, HashMap.class);
                return map;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void writeCacheChecksum(final Map<String, String> map, final File outFile) {
        this.kryoPool.run(kryo -> {
            try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(outFile), 8192)))) {
                kryo.writeObject(output, map);
                return map;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void startCacheFlusher() {
        final ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleWithFixedDelay(() -> {
            final String settingDir = Config.load().getProjectSettingDir();
            final File settingFile = new File(settingDir);
            if (!settingFile.exists()) {
                settingFile.mkdirs();
            }
            if (settingFile.exists()) {
                MemberCacheLoader.this.writeCacheChecksum(MemberCacheLoader.this.cacheChecksum, MemberCacheLoader.this.cacheChecksumFile);
            }
        }, 1, 5, TimeUnit.SECONDS);
    }
}
