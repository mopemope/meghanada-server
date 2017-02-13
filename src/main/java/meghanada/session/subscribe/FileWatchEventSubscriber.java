package meghanada.session.subscribe;

import com.google.common.eventbus.Subscribe;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.session.SessionEventBus;
import meghanada.watcher.FileSystemWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileWatchEventSubscriber extends AbstractSubscriber {

    private static Logger log = LogManager.getLogger(FileWatchEventSubscriber.class);

    private FileSystemWatcher fileSystemWatcher;

    public FileWatchEventSubscriber(final SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe file watch");
    }

    @Subscribe
    public void on(final FileSystemWatcher.CreateEvent event) {
        log.debug("create event {}", event);
        final File file = event.getFile();

        // parse
        this.sessionEventBus.requestParse(file);
    }

    @Subscribe
    public void on(final FileSystemWatcher.ModifyEvent event) {
        log.debug("modify event {}", event);
        final File file = event.getFile();

        final String name = file.getName();
        if (name.endsWith(Project.GRADLE_PROJECT_FILE) ||
                name.endsWith(Project.MVN_PROJECT_FILE) ||
                name.endsWith(Config.MEGHANADA_CONF_FILE)) {
            // project reload
            try {
                this.sessionEventBus.getSession().reloadProject();
            } catch (Exception e) {
                log.catching(e);
            }
        } else {
            // parse
            this.sessionEventBus.requestParse(file);
        }
    }

    @Subscribe
    public void on(final SessionEventBus.FilesWatchRequest request) throws IOException, InterruptedException {
        if (this.fileSystemWatcher == null) {
            this.fileSystemWatcher = new FileSystemWatcher(super.sessionEventBus.getEventBus());
        }
        final List<File> files = request.getFiles();
        this.fileSystemWatcher.start(files);
    }

    @Subscribe
    public void on(final SessionEventBus.FileWatchRequest request) throws IOException, InterruptedException {
        if (this.fileSystemWatcher == null) {
            this.fileSystemWatcher = new FileSystemWatcher(super.sessionEventBus.getEventBus());
            if (!this.fileSystemWatcher.started) {
                this.fileSystemWatcher.start(new ArrayList<>());
            }
        }
        final File file = request.getFile();
        this.fileSystemWatcher.watch(file);
    }


}
