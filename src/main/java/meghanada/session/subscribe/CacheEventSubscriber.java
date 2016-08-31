package meghanada.session.subscribe;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import meghanada.config.Config;
import meghanada.parser.JavaSource;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static meghanada.utils.FunctionUtils.wrapIO;

public class CacheEventSubscriber extends AbstractSubscriber {

    private static final String SRC_FILTER = "src-filter";
    private static Logger log = LogManager.getLogger(CacheEventSubscriber.class);
    private int parsedCount = 0;

    public CacheEventSubscriber(SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe cache");
    }

    @Subscribe
    public synchronized void on(SessionEventBus.ClassCacheRequest request) throws IOException {
        final Session session = super.sessionEventBus.getSession();
        final Project project = session.getCurrentProject();
        final CachedASMReflector reflector = CachedASMReflector.getInstance();

        final Stopwatch stopwatch = Stopwatch.createStarted();
        try {
            project.compileJava(false);
            project.compileTestJava(false);
        } catch (Exception e) {
            log.catching(e);
        }

        log.info("project compiled elapsed:{}", stopwatch.stop());

        reflector.addJars(Session.getSystemJars());
        reflector.addJars(session.getDependentJars());
        reflector.addDirectory(project.getOutputDirectory());
        reflector.addDirectory(project.getTestOutputDirectory());

        stopwatch.reset();
        stopwatch.start();
        reflector.createClassIndexes();
        log.debug("done index size:{} elapsed:{}", reflector.getGlobalClassIndex().size(), stopwatch.stop());
        stopwatch.reset();

        stopwatch.start();
        this.requestParse();
        log.info("project analyzed elapsed:{}", stopwatch.stop());
    }

    private void requestParse() throws IOException {

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

        final Config config = Config.load();

        final Stream<File> fileStream = config.isDebug() ? fileList.stream() : fileList.parallelStream();

        fileStream.forEach(file -> {
            try {
                this.parseFile(file);
            } catch (IOException | ExecutionException e) {
                log.catching(e);
            } finally {
                log.info("analyze ...  [ {} / {}]", this.parsedCount, fileList.size());
            }
        });

    }

    private void parseFile(final File file) throws ExecutionException, IOException {
        final String srcFilter = System.getProperty(SRC_FILTER);
        if (srcFilter != null) {
            final String path = file.getCanonicalPath();
            if (!path.matches(srcFilter)) {
                // skip
                // log.debug("Skip: filter:{} path:{}", srcFilter, path);
                this.parsedCount++;
                return;
            }
        }
        final Session session = this.sessionEventBus.getSession();
        session.getSourceCache().get(file);
        this.parsedCount++;
    }
}
