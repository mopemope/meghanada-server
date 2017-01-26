package meghanada.cache;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.ByteBufferInput;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import com.esotericsoftware.kryo.pool.KryoPool;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

public class GlobalCache {

    private static final Logger log = LogManager.getLogger(GlobalCache.class);
    private static GlobalCache globalCache;
    private final KryoPool kryoPool;
    private Map<File, LoadingCache<File, Source>> sourceCaches;
    private LoadingCache<String, List<MemberDescriptor>> memberCache;
    private boolean isTerminated = false;

    private GlobalCache() {
        this.sourceCaches = new HashMap<>();
        this.kryoPool = new KryoPool.Builder(() -> {
            final Kryo kryo = new Kryo();
            kryo.register(ClassIndex.class);
            kryo.register(MemberDescriptor.class);
            kryo.register(MethodParameterNames.class);
            return kryo;
        }).softReferences().build();

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

    private KryoPool getKryoPool() {
        return kryoPool;
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

    public void replaceMemberDescriptors(final String fqcn, final List<MemberDescriptor> memberDescriptors) throws ExecutionException {
        this.memberCache.put(fqcn, memberDescriptors);
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

    public void replaceSource(final Project project, final Source source) throws ExecutionException {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.put(source.getFile(), source);
    }

    public void invalidateSource(final Project project, final File file) throws ExecutionException {
        final LoadingCache<File, Source> sourceCache = this.getSourceCache(project);
        sourceCache.invalidate(file);
    }

    public <T> T readCacheFromFile(final File file, final Class<T> type) {
        return getKryoPool().run(kryo -> {
            try (final Input input = new Input(new InflaterInputStream(new ByteBufferInput(new FileInputStream(file), 8192)))) {
                return kryo.readObject(input, type);
            } catch (Exception e) {
                file.delete();
                throw new UncheckedExecutionException(e);
            }
        });
    }

    public <T> T readCacheFromInputStream(final InputStream in, final Class<T> type) {
        return getKryoPool().run(kryo -> {
            try (final Input input = new Input(in)) {
                return kryo.readObject(input, type);
            } catch (Exception e) {
                throw new UncheckedExecutionException(e);
            }
        });
    }

    public void writeCacheToFile(final File file, final Object obj) {
        this.getKryoPool().run(kryo -> {
            try (final Output output = new Output(new DeflaterOutputStream(new BufferedOutputStream(new FileOutputStream(file), 8192)))) {
                kryo.writeObject(output, obj);
                return obj;
            } catch (Exception e) {
                file.delete();
                throw new UncheckedExecutionException(e);
            }
        });
    }

    public void shutdown() {
        if (this.isTerminated) {
            return;
        }

        if (this.memberCache != null) {
            this.memberCache.asMap().forEach((k, v) -> {
                this.memberCache.put(k, v);
            });
        }

        this.sourceCaches.forEach((root, sourceLoadingCache) -> {
            sourceLoadingCache.asMap().forEach((k, v) -> {
                // force replace
                sourceLoadingCache.put(k, v);
            });
        });

        this.isTerminated = true;
    }
}
