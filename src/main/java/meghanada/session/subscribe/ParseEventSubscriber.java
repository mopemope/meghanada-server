package meghanada.session.subscribe;

import com.google.common.cache.LoadingCache;
import com.google.common.eventbus.Subscribe;
import meghanada.compiler.SimpleJavaCompiler;
import meghanada.config.Config;
import meghanada.parser.source.JavaSource;
import meghanada.parser.source.TypeScope;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public class ParseEventSubscriber extends AbstractSubscriber {

    private static Logger log = LogManager.getLogger(ParseEventSubscriber.class);

    public ParseEventSubscriber(final SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe source parser");
    }

    @Subscribe
    public synchronized void on(final SessionEventBus.ParseRequest request) throws ExecutionException {

        final Session session = super.sessionEventBus.getSession();
        final File file = request.getFile();
        if (!JavaSource.isJavaFile(file)) {
            return;
        }
        try {
            this.parseFile(session, file);
        } catch (Exception e) {
            log.warn("parse error {}", e.getMessage());
        }
    }

    @Subscribe
    public synchronized void on(SessionEventBus.ParseFilesRequest request) throws ExecutionException {
        final Session session = super.sessionEventBus.getSession();
        final List<File> files = request.getFiles();
        for (File file : files) {
            if (!JavaSource.isJavaFile(file)) {
                continue;
            }
            try {
                this.parseFile(session, file);
            } catch (Exception e) {
                log.warn("parse error {}", e.getMessage());
            }
        }

    }

    private void parseFile(final Session session, final File file) throws ExecutionException, IOException {

        final CachedASMReflector cachedReflector = CachedASMReflector.getInstance();

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

        final LoadingCache<File, JavaSource> sourceCache = session.getSourceCache();
        final JavaSource old = sourceCache.get(file);
        if (old != null) {
            cachedReflector.invalidate(old.getPkg());
        }
        finalChecksumMap.remove(file.getCanonicalPath());
        sourceCache.invalidate(file);

        // reload
        final JavaSource source = sourceCache.get(file);

        final Set<String> target = new HashSet<>();
        final String pkg = source.getPkg();

        for (final TypeScope typeScope : source.getTypeScopes()) {
            final String fqcn = typeScope.getFQCN();
            target.add(fqcn);
            cachedReflector.invalidate(fqcn);
            finalChecksumMap.remove(file.getCanonicalPath());
        }

        for (final Map.Entry<File, JavaSource> entry : sourceCache.asMap().entrySet()) {
            final File key = entry.getKey();
            final JavaSource javaSource = entry.getValue();
            if (pkg.equals(javaSource.getPkg())) {
                finalChecksumMap.remove(key.getCanonicalPath());
                continue;
            }
            for (String impFqcn : javaSource.importClass.values()) {
                if (target.contains(impFqcn)) {
                    // depend
                    finalChecksumMap.remove(key.getCanonicalPath());
                    break;
                }
            }
        }
        FileUtils.writeMapSetting(finalChecksumMap, checksumFile);
    }
}
