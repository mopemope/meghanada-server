package meghanada.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.io.UnsafeInput;
import com.esotericsoftware.kryo.io.UnsafeOutput;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import meghanada.analyze.Source;
import meghanada.project.Project;
import meghanada.reflect.ClassIndex;
import meghanada.reflect.MemberDescriptor;
import meghanada.reflect.names.MethodParameterNames;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
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

    private static final int SOURCE_CACHE_MAX = 64;
    private static final int MEMBER_CACHE_MAX = SOURCE_CACHE_MAX;
    private static final int BURST_LIMIT = 32;
    private static final int BUFFER_SIZE = 1024 * 32;

    private static final Logger log = LogManager.getLogger(GlobalCache.class);

    private static GlobalCache globalCache;
    private final KryoPool kryoPool;
    private final Map<File, LoadingCache<File, Source>> sourceCaches;
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final BlockingQueue<CacheRequest> blockingQueue = new LinkedBlockingDeque<>();

    private LoadingCache<String, List<MemberDescriptor>> memberCache;
    private boolean isTerminated = false;
    private boolean ioBurstMode;

    private GlobalCache() {
        this.sourceCaches = new HashMap<>(1);
        this.kryoPool = new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            kryo.getFieldSerializerConfig().setUseAsm(false);
            kryo.register(ClassIndex.class);
            kryo.register(MemberDescriptor.class);
            kryo.register(MethodParameterNames.class);
            return kryo;
        }).build();

        this.executorService.execute(() -> {
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
                .maximumSize(MEMBER_CACHE_MAX)
                .expireAfterAccess(5, TimeUnit.MINUTES)
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

        if (!this.ioBurstMode && this.blockingQueue.size() > BURST_LIMIT) {
            this.ioBurstMode = true;
            this.executorService.execute(() -> {
                boolean readyShutdown = false;
                while (!this.isTerminated && this.ioBurstMode) {
                    try {
                        final CacheRequest cr = blockingQueue.poll(5, TimeUnit.SECONDS);
                        if (cr != null && !cr.shutdown) {
                            this.writeCacheToFile(cr.getFile(), cr.getTarget());
                            readyShutdown = false;
                        }
                        if (this.blockingQueue.isEmpty()) {
                            if (readyShutdown) {
                                this.ioBurstMode = false;
                            } else {
                                readyShutdown = true;
                            }
                        }
                    } catch (Exception e) {
                        log.catching(e);
                    }
                }
            });
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
                    .maximumSize(SOURCE_CACHE_MAX)
                    .expireAfterAccess(5, TimeUnit.MINUTES)
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

    public void replaceSource(final Project project,
                              final Source source) {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.put(source.getFile(), source);
    }

    public void invalidateSource(final Project project,
                                 final File file) {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.invalidate(file);
    }

    @Nullable
    public <T> T readCacheFromFile(final File file, final Class<T> type) {
        if (!file.exists()) {
            return null;
        }
        try {
            return kryoPool.run(kryo -> {
                try (final Input input = new UnsafeInput(new FileInputStream(file), BUFFER_SIZE)) {
                    return kryo.readObject(input, type);
                } catch (FileNotFoundException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (Exception e) {
            log.catching(e);
            if (file.exists() && !file.delete()) {
                log.warn("{} delete fail", file);
            }
            return null;
        }
    }

    public <T> T readCacheFromInputStream(final InputStream in, final Class<T> type) {
        return kryoPool.run(kryo -> {
            try (final Input input = new UnsafeInput(in)) {
                return kryo.readObject(input, type);
            }
        });
    }

    private void writeCacheToFile(final File file, final Object obj) {
        final File parentFile = file.getParentFile();
        if (!parentFile.exists() && !parentFile.mkdirs()) {
            log.warn("{} mkdirs fail", parentFile);
        }
        kryoPool.run(kryo -> {
            try (final Output output = new UnsafeOutput(new FileOutputStream(file), BUFFER_SIZE)) {
                kryo.writeObject(output, obj);
            } catch (Exception e) {
                log.catching(e);
                if (!file.delete()) {
                    log.warn("{} delete fail", file);
                }
            }
            return true;
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
