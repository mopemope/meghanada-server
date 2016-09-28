package meghanada.session.subscribe;

import com.google.common.eventbus.Subscribe;
import meghanada.parser.source.JavaSource;
import meghanada.project.Project;
import meghanada.session.SessionEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CompileEventSubscriber extends AbstractSubscriber {
    private static Logger log = LogManager.getLogger(CompileEventSubscriber.class);

    public CompileEventSubscriber(SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe compile");
    }

    @Subscribe
    public synchronized void on(SessionEventBus.CompileRequest request) throws IOException {
        Project project = super.sessionEventBus.getSession().getCurrentProject();
        File file = request.getFile();
        if (!JavaSource.isJavaFile(file)) {
            return;
        }
        project.compileFile(file, false);
    }

    @Subscribe
    public synchronized void on(SessionEventBus.CompileFilesRequest request) throws IOException {
        Project project = super.sessionEventBus.getSession().getCurrentProject();
        List<File> files = request.getFiles();
        project.compileFile(files, false);
    }

}
