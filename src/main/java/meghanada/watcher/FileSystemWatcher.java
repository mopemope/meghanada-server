package meghanada.watcher;

import com.google.common.base.MoreObjects;
import com.google.common.eventbus.EventBus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class FileSystemWatcher {

    private static Logger log = LogManager.getLogger(FileSystemWatcher.class);
    private final EventBus eventBus;
    private boolean abort;

    public FileSystemWatcher(EventBus eventBus) {
        this.eventBus = eventBus;
        abort = false;
    }

    @SuppressWarnings("unchecked")
    private static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>) event;
    }

    public void stop() {
        this.abort = true;
    }

    public void start(List<File> files) throws IOException {
        this.abort = false;
        final FileSystem fileSystem = FileSystems.getDefault();

        try (final WatchService watchService = fileSystem.newWatchService()) {

            final WatchKeyHolder watchKeyHolder = new WatchKeyHolder(watchService);
            for (final File root : files) {
                if (root.exists()) {
                    final Path rootPath = root.toPath();
                    watchKeyHolder.walk(rootPath);
                }
            }

            while (!abort) {
                final WatchKey key = watchService.take();

                this.handleEvent(watchKeyHolder, key);

                if (!key.reset()) {
                    watchKeyHolder.remove(key);
                }

                watchKeyHolder.sweep();
                if (watchKeyHolder.isEmpty()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            // ignore
        }
    }

    private void handleEvent(final WatchKeyHolder watchKeys, final WatchKey key) throws IOException {
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
            } else if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                log.debug("{} {}", watchEvent.kind().name(), path);
                // Dispatch
                FileEvent fe = toEvent(watchEvent, path);
                if (fe != null) {
                    this.eventBus.post(fe);
                }
            }
        }
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    private FileEvent toEvent(WatchEvent<?> watchEvent, Path path) {
        if (watchEvent.kind().name().equals("ENTRY_CREATE")) {
            return new CreateEvent(path.toFile());
        } else if (watchEvent.kind().name().equals("ENTRY_MODIFY")) {
            return new ModifyEvent(path.toFile());
        } else if (watchEvent.kind().name().equals("ENTRY_DELETE")) {
            return new DeleteEvent(path.toFile());
        }
        return null;
    }

    static class FileEvent {
        File file;

        FileEvent(File file) {
            this.file = file;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("file", file)
                    .toString();
        }
    }

    public static class CreateEvent extends FileEvent {

        CreateEvent(File file) {
            super(file);
        }

    }

    public static class ModifyEvent extends FileEvent {

        ModifyEvent(File file) {
            super(file);
        }
    }

    private static class DeleteEvent extends FileEvent {

        DeleteEvent(File file) {
            super(file);
        }
    }

    private static class WatchKeyHolder {

        private final WatchService watchService;
        private final Map<WatchKey, Path> watchKeys = new HashMap<>();

        WatchKeyHolder(final WatchService watchService) {
            this.watchService = watchService;
        }

        void walk(Path rootPath) throws IOException {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    register(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        void register(final Path path) throws IOException {
            if (path == null) {
                return;
            }
            if (meghanada.utils.FileUtils.filterFile(path.toFile())) {
                final WatchKey key = path.register(this.watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
                this.watchKeys.put(key, path);
            }
        }

        void sweep() {
            for (final Iterator<Map.Entry<WatchKey, Path>> it = watchKeys.entrySet().iterator(); it.hasNext(); ) {
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

