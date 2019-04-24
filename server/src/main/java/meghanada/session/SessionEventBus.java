package meghanada.session;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.Collection;
import java.util.List;
import meghanada.Executor;
import meghanada.session.subscribe.CacheEventSubscriber;
import meghanada.session.subscribe.FileWatchEventSubscriber;
import meghanada.session.subscribe.IdleMonitorSubscriber;
import meghanada.session.subscribe.ParseEventSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SessionEventBus {

  private static final Logger log = LogManager.getLogger(Session.class);
  private final Session session;
  private final IdleTimer idleTimer;

  SessionEventBus(final Session session) {
    this.session = session;
    this.idleTimer = new IdleTimer();
    this.idleTimer.lastRun = Long.MAX_VALUE;
  }

  void subscribeFileWatch() {
    Executor.getInstance().getEventBus().register(new FileWatchEventSubscriber(this));
  }

  void subscribeParse() {
    Executor.getInstance().getEventBus().register(new ParseEventSubscriber(this));
  }

  void subscribeCache() {
    Executor.getInstance().getEventBus().register(new CacheEventSubscriber(this));
  }

  void subscribeIdle() {
    Executor.getInstance().getEventBus().register(new IdleMonitorSubscriber(this, idleTimer));
  }

  void shutdown(int timeout) {}

  public Session getSession() {
    return session;
  }

  public void requestCreateCache() {
    Executor.getInstance().getEventBus().post(new ClassCacheRequest(this.session));
  }

  public void requestParse(File file) {
    Executor.getInstance().getEventBus().post(new ParseRequest(this.session, file));
  }

  public void requestWatchFiles(final List<File> files) {
    Executor.getInstance().getEventBus().post(new FilesWatchRequest(this.session, files));
  }

  public void requestWatchFile(final File file) {
    Executor.getInstance().getEventBus().post(new FileWatchRequest(this.session, file));
  }

  public void requestIdleMonitor() {
    IdleMonitorEvent event = new IdleMonitorEvent(this.session);
    Executor.getInstance().getEventBus().post(event);
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

  public static class IdleCacheEvent {
    private final Collection<String> names;

    public IdleCacheEvent(Collection<String> names) {
      this.names = names;
    }

    public Collection<String> getNames() {
      return names;
    }
  }

  public IdleTimer getIdleTimer() {
    return idleTimer;
  }
}
