package meghanada.session.subscribe;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import meghanada.cache.GlobalCache;
import meghanada.config.Config;
import meghanada.project.Project;
import meghanada.session.SessionEventBus;
import meghanada.store.ProjectDatabaseHelper;
import meghanada.utils.FileUtils;
import meghanada.watcher.FileSystemWatcher;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileWatchEventSubscriber extends AbstractSubscriber {

  private static final Logger log = LogManager.getLogger(FileWatchEventSubscriber.class);

  private FileSystemWatcher fileSystemWatcher;

  public FileWatchEventSubscriber(final SessionEventBus sessionEventBus) {
    super(sessionEventBus);
  }

  @Subscribe
  public void on(final FileSystemWatcher.CreateEvent event) {}

  @Subscribe
  public void on(final FileSystemWatcher.DeleteEvent event) {
    final File file = event.getFile();

    try {
      String filePath = file.getCanonicalPath();
      GlobalCache globalCache = GlobalCache.getInstance();
      Project project = sessionEventBus.getSession().getCurrentProject();
      globalCache.invalidateSource(project, file);
      boolean b = ProjectDatabaseHelper.deleteSource(filePath);
      FileUtils.getClassFile(filePath, project.getSources(), project.getOutput())
          .ifPresent(File::delete);
      FileUtils.getClassFile(filePath, project.getTestSources(), project.getTestOutput())
          .ifPresent(File::delete);

    } catch (Throwable e) {
      log.catching(e);
    }
  }

  @Subscribe
  public void on(final FileSystemWatcher.ModifyEvent event) {
    final File file = event.getFile();

    final String name = file.getName();
    if (name.endsWith(Project.GRADLE_PROJECT_FILE)
        || name.endsWith(Project.MVN_PROJECT_FILE)
        || name.endsWith(Project.ECLIPSE_PROJECT_FILE)
        || name.endsWith(Config.MEGHANADA_CONF_FILE)) {
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
  public void on(final SessionEventBus.FilesWatchRequest request) throws IOException {
    if (this.fileSystemWatcher == null) {
      this.fileSystemWatcher = new FileSystemWatcher(super.sessionEventBus.getEventBus());
    }
    final List<File> files = request.getFiles();
    this.fileSystemWatcher.start(files);
  }

  @Subscribe
  public void on(final SessionEventBus.FileWatchRequest request) throws IOException {
    if (this.fileSystemWatcher == null) {
      this.fileSystemWatcher = new FileSystemWatcher(super.sessionEventBus.getEventBus());
      if (!this.fileSystemWatcher.started) {
        this.fileSystemWatcher.start(new ArrayList<>(2));
      }
    }
    final File file = request.getFile();
    this.fileSystemWatcher.watch(file);
  }
}
