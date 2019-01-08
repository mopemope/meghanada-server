package meghanada.watcher;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import meghanada.event.SystemEventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FileSystemWatcher {

  private static final Logger log = LogManager.getLogger(FileSystemWatcher.class);
  public boolean started;
  private boolean abort;
  private WatchKeyHolder watchKeyHolder;

  public FileSystemWatcher() {
    abort = false;
  }

  @SuppressWarnings("unchecked")
  private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }

  private static FileEvent toEvent(final WatchEvent<?> watchEvent, final Path path) {
    if (watchEvent.kind().name().equals("ENTRY_CREATE")) {
      return new CreateEvent(path.toFile());
    } else if (watchEvent.kind().name().equals("ENTRY_MODIFY")) {
      return new ModifyEvent(path.toFile());
    } else if (watchEvent.kind().name().equals("ENTRY_DELETE")) {
      return new DeleteEvent(path.toFile());
    }
    return null;
  }

  public void stop() {
    this.abort = true;
    this.started = false;
  }

  public void watch(final File file) throws IOException {
    if (this.watchKeyHolder != null) {
      final Path path = file.toPath();
      this.watchKeyHolder.register(path);
    }
  }

  public void start(final List<File> files) throws IOException {
    this.abort = false;

    try (final FileSystem fileSystem = FileSystems.getDefault();
        final WatchService watchService = fileSystem.newWatchService()) {

      this.watchKeyHolder = new WatchKeyHolder(watchService);
      for (final File root : files) {
        if (root.exists()) {
          final Path rootPath = root.toPath();
          this.watchKeyHolder.walk(rootPath);
        }
      }
      this.started = true;
      while (!abort) {
        final WatchKey key = watchService.take();

        FileSystemWatcher.handleEvent(this.watchKeyHolder, key);

        if (!key.reset()) {
          this.watchKeyHolder.remove(key);
        }

        this.watchKeyHolder.sweep();
        if (this.watchKeyHolder.isEmpty()) {
          break;
        }
      }
    } catch (InterruptedException e) {
      // ignore
    }
  }

  private static void handleEvent(final WatchKeyHolder watchKeys, final WatchKey key)
      throws IOException {
    for (final WatchEvent<?> event : key.pollEvents()) {
      if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
        continue;
      }

      final WatchEvent<Path> watchEvent = cast(event);
      Path path = watchKeys.get(key);
      if (path == null) {
        continue;
      }

      path = path.resolve(watchEvent.context());
      if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE) {
          watchKeys.register(path);
        }
      } else {
        // Dispatch
        FileEvent fe = toEvent(watchEvent, path);
        if (fe != null) {
          SystemEventBus.getInstance().getEventBus().post(fe);
        }
      }
    }
  }

  static class FileEvent {
    final File file;

    FileEvent(final File file) {
      this.file = file;
    }

    public File getFile() {
      return file;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this).add("file", file).toString();
    }
  }

  public static class CreateEvent extends FileEvent {
    CreateEvent(final File file) {
      super(file);
    }
  }

  public static class ModifyEvent extends FileEvent {
    ModifyEvent(final File file) {
      super(file);
    }
  }

  public static class DeleteEvent extends FileEvent {
    DeleteEvent(final File file) {
      super(file);
    }
  }

  private static class WatchKeyHolder {

    private final WatchService watchService;
    private final Map<WatchKey, Path> watchKeys = new ConcurrentHashMap<>(16);

    WatchKeyHolder(final WatchService watchService) {
      this.watchService = watchService;
    }

    void walk(final Path rootPath) throws IOException {
      Files.walkFileTree(
          rootPath,
          new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                throws IOException {
              register(dir);
              return FileVisitResult.CONTINUE;
            }
          });
    }

    void register(final Path path) throws IOException {
      if (meghanada.utils.FileUtils.filterFile(path.toFile())) {
        final WatchKey key =
            path.register(
                this.watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        this.watchKeys.put(key, path);
      }
    }

    void sweep() {
      for (final Iterator<Map.Entry<WatchKey, Path>> it = watchKeys.entrySet().iterator();
          it.hasNext(); ) {
        final Map.Entry<WatchKey, Path> entry = it.next();
        if (Files.notExists(entry.getValue(), LinkOption.NOFOLLOW_LINKS)) {
          entry.getKey().cancel();
          it.remove();
        }
      }
    }

    boolean isEmpty() {
      return watchKeys.isEmpty();
    }

    void remove(final WatchKey key) {
      watchKeys.remove(key);
    }

    Path get(final WatchKey key) {
      return watchKeys.get(key);
    }
  }
}
