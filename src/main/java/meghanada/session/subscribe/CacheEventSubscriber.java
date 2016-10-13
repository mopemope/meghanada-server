package meghanada.session.subscribe;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import meghanada.config.Config;
import meghanada.parser.source.JavaSource;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import meghanada.utils.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static meghanada.config.Config.timeItF;
import static meghanada.utils.FunctionUtils.wrapIO;

public class CacheEventSubscriber extends AbstractSubscriber {

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

        reflector.addClasspath(session.getDependentJars());
        reflector.addClasspath(project.getOutputDirectory());
        reflector.addClasspath(project.getTestOutputDirectory());

        final Stopwatch stopwatch = Stopwatch.createStarted();
        reflector.createClassIndexes();
        log.info("done index size:{} elapsed:{}", reflector.getGlobalClassIndex().size(), stopwatch.stop());

        if (Config.load().analyzeAll()) {
            stopwatch.reset();
            stopwatch.start();
            this.requestParse();
            log.info("analyzed elapsed:{}", stopwatch.stop());
        }
    }

    private void requestParse() throws IOException {
        final AtomicInteger count = new AtomicInteger(0);

        final Session session = this.sessionEventBus.getSession();
        final Project project = session.getCurrentProject();
        final List<File> fileList = project.getSourceDirectories()
                .parallelStream()
                .filter(File::exists)
                .flatMap(wrapIO(root -> Files.walk(root.toPath())))
                .map(Path::toFile)
                .filter(JavaSource::isJavaFile)
                .filter(FileUtils::filterFile)
                .collect(Collectors.toList());
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
        final Session session = this.sessionEventBus.getSession();
        session.getSourceCache().get(file);
        return true;
    }
}
