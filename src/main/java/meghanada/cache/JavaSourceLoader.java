package meghanada.cache;

import com.google.common.base.Joiner;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.RemovalCause;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;
import meghanada.analyze.CompileResult;
import meghanada.analyze.Source;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;


class JavaSourceLoader extends CacheLoader<File, Source> implements RemovalListener<File, Source> {

    private static final Logger log = LogManager.getLogger(JavaSourceLoader.class);

    private final Project project;

    public JavaSourceLoader(final Project project) {
        this.project = project;
    }

    private static void writeSourceCache(final Source source) throws IOException {
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);

        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            log.warn("{} mkdirs fail", file.getParent());
        }

        log.debug("write file:{}", file);
        GlobalCache.getInstance().asyncWriteCache(file, source);
    }

    private static void removeSourceCache(final Source source) throws IOException {
        final File sourceFile = source.getFile();
        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);
        if (file.exists() && !file.delete()) {
            log.warn("{} delete fail", file);
        }
    }

    private static Optional<Source> loadFromCache(final File sourceFile) throws IOException {

        final Config config = Config.load();
        final String dir = config.getProjectSettingDir();
        final File root = new File(dir);
        final String path = FileUtils.toHashedPath(sourceFile, GlobalCache.CACHE_EXT);
        final String out = Joiner.on(File.separator).join(GlobalCache.SOURCE_CACHE_DIR, path);
        final File file = new File(root, out);

        if (!file.exists()) {
            return Optional.empty();
        }

        log.debug("load file:{}", file);
        final Source source = GlobalCache.getInstance().readCacheFromFile(file, Source.class);
        return Optional.ofNullable(source);
    }

    @Override
    @Nonnull
    public Source load(@Nonnull final File file) throws IOException {
        final Config config = Config.load();
        if (!file.exists()) {
            return new Source(file.getPath());
        }

        if (!config.useSourceCache()) {
            final CompileResult compileResult = project.parseFile(file);
            return compileResult.getSources().get(file);
        }

        final File checksumFile = FileUtils.getProjectDataFile(GlobalCache.SOURCE_CHECKSUM_DATA);
        final Map<String, String> finalChecksumMap = config.getChecksumMap(checksumFile);

        final String path = file.getCanonicalPath();
        final String md5sum = FileUtils.getChecksum(file);
        if (finalChecksumMap.containsKey(path)) {
            // compare checksum
            final String prevSum = finalChecksumMap.get(path);
            if (md5sum.equals(prevSum)) {
                // not modify
                // load from cache
                try {
                    final Optional<Source> source = JavaSourceLoader.loadFromCache(file);
                    if (source.isPresent()) {
                        return source.get();
                    }
                } catch (Exception e) {
                    log.catching(e);
                }
            }
        }

        final CompileResult compileResult = project.parseFile(file.getCanonicalFile());
        return compileResult.getSources().get(file.getCanonicalFile());
    }

    @Override
    public void onRemoval(@Nonnull final RemovalNotification<File, Source> notification) {
        final RemovalCause cause = notification.getCause();

        final Config config = Config.load();
        if (config.useSourceCache()) {
            if (cause.equals(RemovalCause.EXPIRED) ||
                    cause.equals(RemovalCause.SIZE) ||
                    cause.equals(RemovalCause.REPLACED)) {
                final Source source = notification.getValue();
                try {
                    writeSourceCache(source);
                } catch (Exception e) {
                    log.catching(e);
                }
            } else if (cause.equals(RemovalCause.EXPLICIT)) {
                final Source source = notification.getValue();
                try {
                    removeSourceCache(source);
                } catch (Exception e) {
                    log.catching(e);
                }
            }
        }
    }
}
