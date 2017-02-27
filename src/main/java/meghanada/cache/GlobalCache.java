package meghanada.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.github.luben.zstd.ZstdInputStream;
import com.github.luben.zstd.ZstdOutputStream;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import meghanada.analyze.Source;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.names.MethodParameterNames;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class GlobalCache {

    public static final String SOURCE_CACHE_DIR = "source";
    public static final String CLASS_CACHE_DIR = "class";
    public static final String MEMBER_CACHE_DIR = "member";
    public static final String DATA_DIR = "data";
    public static final String SOURCE_CHECKSUM_DATA = "source_checksum";
    public static final String COMPILE_CHECKSUM_DATA = "compile_checksum";
    public static final String CALLER_DATA = "source_caller";
    public static final String PROJECT_DATA = "project";
    public static final String CACHE_EXT = ".dat";
    private static final int COMPRESSION_LEVEL = 3;

    private static final Logger log = LogManager.getLogger(GlobalCache.class);
    private static GlobalCache globalCache;
    private final KryoPool kryoPool;
    private final Map<File, LoadingCache<File, Source>> sourceCaches;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final BlockingQueue<CacheRequest> blockingQueue = new LinkedBlockingQueue<>();

    private LoadingCache<String, List<MemberDescriptor>> memberCache;
    private boolean isTerminated = false;

    private GlobalCache() {
        this.sourceCaches = new HashMap<>(1);
        this.kryoPool = new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            kryo.register(ClassIndex.class);
            kryo.register(MemberDescriptor.class);
            kryo.register(MethodParameterNames.class);
            return kryo;
        }).softReferences().build();

        executorService.submit(() -> {
            while (!this.isTerminated) {
                try {
                    final CacheRequest cr = blockingQueue.take();
                    if (cr != null && !cr.shutdown) {
                        this.writeCacheToFile(cr.getFile(), cr.getTarget());
                    }
                } catch (Exception e) {
                    log.catching(e);
                }
            }
            log.info("shutdown cache worker");
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                shutdown();
            } catch (Throwable t) {
                log.catching(t);
            }
        }));
    }

    public static GlobalCache getInstance() {
        if (globalCache == null) {
            globalCache = new GlobalCache();
        }
        return globalCache;
    }

    public void setMemberCache(final LoadingCache<String, List<MemberDescriptor>> memberCache) {
        this.memberCache = memberCache;
    }

    public void setupMemberCache(final Map<String, File> classFileMap, final Map<ClassIndex, File> reflectIndex) {
        if (this.memberCache != null) {
            return;
        }
        final MemberCacheLoader memberCacheLoader = new MemberCacheLoader(classFileMap, reflectIndex);
        this.memberCache = CacheBuilder.newBuilder()
                .maximumSize(64)
                .expireAfterAccess(1, TimeUnit.MINUTES)
                .removalListener(memberCacheLoader)
                .build(memberCacheLoader);
    }

    public List<MemberDescriptor> getMemberDescriptors(final String fqcn) throws ExecutionException {
        return this.memberCache.get(fqcn);
    }

    public void replaceMemberDescriptors(final String fqcn, final List<MemberDescriptor> memberDescriptors) {
        this.memberCache.put(fqcn, memberDescriptors);
    }

    public void asyncWriteCache(final File f, final Object o) {
        final CacheRequest cacheRequest = new CacheRequest(f, o);
        try {
            this.blockingQueue.put(cacheRequest);
        } catch (InterruptedException e) {
            log.catching(e);
        }
    }

    public void invalidateMemberDescriptors(final String fqcn) {
        this.memberCache.invalidate(fqcn);
    }

    public LoadingCache<File, Source> getSourceCache(final Project project) {
        final File projectRoot = project.getProjectRoot();
        if (this.sourceCaches.containsKey(projectRoot)) {
            return this.sourceCaches.get(projectRoot);
        } else {
            final JavaSourceLoader javaSourceLoader = new JavaSourceLoader(project);
            final LoadingCache<File, Source> loadingCache = CacheBuilder.newBuilder()
                    .maximumSize(16)
                    .expireAfterAccess(1, TimeUnit.MINUTES)
                    .removalListener(javaSourceLoader)
                    .build(javaSourceLoader);
            this.sourceCaches.put(projectRoot, loadingCache);
            return loadingCache;
        }
    }

    public Source getSource(final Project project, final File file) throws ExecutionException {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        return sourceCache.get(file);
    }

    public void replaceSource(final Project project, final Source source) {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.put(source.getFile(), source);
    }

    public void invalidateSource(final Project project, final File file) {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.invalidate(file);
    }

    public <T> T readCacheFromFile(final File file, final Class<T> type) {
        return kryoPool.run(kryo -> {
            try (final Input input = new Input(new ZstdInputStream(new ByteBufferInput(new FileInputStream(file), 8192)))) {
                return kryo.readObject(input, type);
            } catch (Exception e) {
                file.delete();
                return null;
            }
        });
    }

    public <T> T readCacheFromInputStream(final InputStream in, final Class<T> type) {
        return kryoPool.run(kryo -> {
            try (final Input input = new Input(in)) {
                return kryo.readObject(input, type);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private void writeCacheToFile(final File file, final Object obj) {
        final File parentFile = file.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }
        kryoPool.run(kryo -> {
            try (final Output output = new Output(new ZstdOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192), COMPRESSION_LEVEL))) {
                kryo.writeObject(output, obj);
                return obj;
            } catch (Exception e) {
                file.delete();
                log.catching(e);
                return null;
            }
        });
    }

    private void shutdown() throws InterruptedException {
        if (this.isTerminated) {
            return;
        }

        if (this.memberCache != null) {
            this.memberCache.asMap().forEach((k, v) -> this.memberCache.put(k, v));
        }

        this.sourceCaches.forEach((root, sourceLoadingCache) -> {
            // force replace
            sourceLoadingCache.asMap().forEach(sourceLoadingCache::put);
        });
        this.isTerminated = true;

        final CacheRequest cacheRequest = new CacheRequest(new File(""), this);
        cacheRequest.shutdown = true;
        try {
            this.blockingQueue.put(cacheRequest);
        } catch (InterruptedException e) {
            log.catching(e);
        }
        this.executorService.shutdown();
        this.executorService.awaitTermination(5, TimeUnit.SECONDS);
    }
}
