package meghanada.session.subscribe;

import com.google.common.eventbus.Subscribe;
import meghanada.session.SessionEventBus;
import meghanada.watcher.FileSystemWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileWatchEventSubscriber extends AbstractSubscriber {

    private static Logger log = LogManager.getLogger(FileWatchEventSubscriber.class);

    private FileSystemWatcher fileSystemWatcher;

    public FileWatchEventSubscriber(SessionEventBus sessionEventBus) {
        super(sessionEventBus);
        log.debug("subscribe file watch");
    }

    @Subscribe
    public void on(FileSystemWatcher.CreateEvent event) {
        log.debug("create event {}", event);
        File file = event.getFile();
        // parse
        this.sessionEventBus.requestParse(file);
    }

    @Subscribe
    public void on(FileSystemWatcher.ModifyEvent event) {
        log.debug("modify event {}", event);
        File file = event.getFile();
        // parse
        this.sessionEventBus.requestParse(file);
    }

    @Subscribe
    public void on(SessionEventBus.FileWatchRequest request) throws IOException, InterruptedException {
        if (this.fileSystemWatcher == null) {
            this.fileSystemWatcher = new FileSystemWatcher(super.sessionEventBus.getEventBus());
        }
        List<File> files = request.getFiles();
        this.fileSystemWatcher.start(files);
    }


}
