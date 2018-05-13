package meghanada.session;

import com.google.common.base.MoreObjects;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import meghanada.session.subscribe.CacheEventSubscriber;
import meghanada.session.subscribe.FileWatchEventSubscriber;
import meghanada.session.subscribe.IdleMonitorSubscriber;
import meghanada.session.subscribe.ParseEventSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SessionEventBus {

  private static final Logger log = LogManager.getLogger(Session.class);
  private final EventBus eventBus;
  private final Session session;
  private final ExecutorService executorService;
  private final IdleTimer idleTimer;

  SessionEventBus(final Session session) {
    this.session = session;
    this.executorService = Executors.newCachedThreadPool();
    this.eventBus =
        new AsyncEventBus(
            executorService,
            (throwable, subscriberExceptionContext) -> {
              if (!(throwable instanceof RejectedExecutionException)) {
                log.error(throwable.getMessage(), throwable);
              }
            });
    this.idleTimer = new IdleTimer();
    this.idleTimer.lastRun = Long.MAX_VALUE;
  }

  void subscribeFileWatch() {
    this.eventBus.register(new FileWatchEventSubscriber(this));
  }

  void subscribeParse() {
    this.eventBus.register(new ParseEventSubscriber(this));
  }

  void subscribeCache() {
    this.eventBus.register(new CacheEventSubscriber(this));
  }

  void subscribeIdle() {
    this.eventBus.register(new IdleMonitorSubscriber(this, idleTimer));
  }

  void shutdown(int timeout) {
    if (executorService.isShutdown()) {
      return;
    }
    try {
      executorService.shutdown();
      if (!executorService.awaitTermination(timeout, TimeUnit.SECONDS)) {
        executorService.shutdownNow();
        executorService.awaitTermination(timeout, TimeUnit.SECONDS);
      }
    } catch (InterruptedException e) {
      executorService.shutdownNow();
      try {
        Thread.currentThread().interrupt();
      } catch (Exception ex) {
        // ignore
      }
    }
  }

  public Session getSession() {
    return session;
  }

  public EventBus getEventBus() {
    return eventBus;
  }

  public void requestCreateCache() {
    this.eventBus.post(new ClassCacheRequest(this.session));
  }

  public void requestParse(File file) {
    this.eventBus.post(new ParseRequest(this.session, file));
  }

  public void requestWatchFiles(final List<File> files) {
    this.eventBus.post(new FilesWatchRequest(this.session, files));
  }

  public void requestWatchFile(final File file) {
    this.eventBus.post(new FileWatchRequest(this.session, file));
  }

  public void requestIdleMonitor() {
    IdleMonitorEvent event = new IdleMonitorEvent(this.session);
    this.eventBus.post(event);
  }

  abstract static class IORequest {

    final Session session;
    File file;

    IORequest(Session session) {
      this.session = session;
    }

    IORequest(Session session, File file) {
      this.session = session;
      this.file = file;
    }

    public File getFile() {
      return file;
    }

    public Session getSession() {
      return session;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("session", session).add("file", file).toString();
    }
  }

  abstract static class IOListRequest {

    final Session session;
    final List<File> files;

    IOListRequest(Session session, List<File> files) {
      this.session = session;
      this.files = files;
    }

    public List<File> getFiles() {
      return files;
    }
  }

  public static class ClassCacheRequest extends IORequest {

    public ClassCacheRequest(final Session session) {
      super(session);
    }
  }

  public static class ParseRequest extends IORequest {

    public ParseRequest(Session session, File file) {
      super(session, file);
    }
  }

  public static class FilesWatchRequest extends IOListRequest {

    public FilesWatchRequest(final Session session, final List<File> files) {
      super(session, files);
    }
  }

  public static class FileWatchRequest extends IORequest {

    public FileWatchRequest(final Session session, final File file) {
      super(session, file);
    }
  }

  public static class ParseFilesRequest extends IOListRequest {

    public ParseFilesRequest(Session session, List<File> files) {
      super(session, files);
    }
  }

  public static class IdleTimer {
    public long lastRun;
  }

  public static class IdleMonitorEvent {
    public final Session session;

    public IdleMonitorEvent(Session session) {
      this.session = session;
    }
  }

  public static class IdleEvent {
    final Session session;
    final IdleTimer idleTimer;

    public IdleEvent(Session session, IdleTimer idleTimer) {
      this.session = session;
      this.idleTimer = idleTimer;
    }

    public IdleTimer getIdleTimer() {
      return idleTimer;
    }

    public Session getSession() {
      return session;
    }
  }

  public IdleTimer getIdleTimer() {
    return idleTimer;
  }
}
