package meghanada.session.subscribe;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static meghanada.config.Config.timeItF;

public class CacheEventSubscriber extends AbstractSubscriber {

    private static final String SRC_FILTER = "src-filter";
    private static Logger log = LogManager.getLogger(CacheEventSubscriber.class);

    public CacheEventSubscriber(SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe cache");
    }

    @Subscribe
    public synchronized void on(SessionEventBus.ClassCacheRequest request) throws IOException {
        final Session session = super.sessionEventBus.getSession();
        final Project project = session.getCurrentProject();
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        boolean result = timeItF("project compiled elapsed:{}", () -> {
            try {
                project.compileJava(false);
                project.compileTestJava(false);
            } catch (Exception e) {
                log.catching(e);
            }
            return true;
        });

        reflector.addJars(session.getDependentJars());
        reflector.addDirectory(project.getOutputDirectory());
        reflector.addDirectory(project.getTestOutputDirectory());

        final Stopwatch stopwatch = Stopwatch.createStarted();
        reflector.createClassIndexes();
        log.info("done index size:{} elapsed:{}", reflector.getGlobalClassIndex().size(), stopwatch.stop());
        stopwatch.reset();
    }

    private void parseFiles(final List<File> fileList) throws IOException {
        final AtomicInteger count = new AtomicInteger(0);
        final int size = fileList.size();
        final Config config = Config.load();
        final Stream<File> fileStream = config.isDebug() ? fileList.stream() : fileList.parallelStream();
        fileStream.forEach(file -> {
            try {
                this.parseFile(file);
                count.incrementAndGet();
            } catch (Exception e) {
                log.catching(e);
            } finally {
                log.info("analyze {} / {}", count.get(), size);
            }
        });

    }

    private boolean parseFile(final File file) throws ExecutionException, IOException {
        final String srcFilter = System.getProperty(SRC_FILTER);
        if (srcFilter != null) {
            final String path = file.getCanonicalPath();
            if (!path.matches(srcFilter)) {
                // skip
                return false;
            }
        }
        final Session session = this.sessionEventBus.getSession();
        session.getSourceCache().get(file);
        return true;
    }
}
