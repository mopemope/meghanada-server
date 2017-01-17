package meghanada.session.subscribe;

import com.google.common.base.Stopwatch;
import com.google.common.eventbus.Subscribe;
import meghanada.project.Project;
import meghanada.reflect.asm.CachedASMReflector;
import meghanada.session.Session;
import meghanada.session.SessionEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

import static meghanada.config.Config.timeItF;

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

        boolean result = timeItF("project analyze and compile elapsed:{}", () -> {
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
    }
}
