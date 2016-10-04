package meghanada.session;

import com.google.common.base.MoreObjects;
import com.google.common.eventbus.AsyncEventBus;
import com.google.common.eventbus.EventBus;
import meghanada.session.subscribe.CacheEventSubscriber;
import meghanada.session.subscribe.CompileEventSubscriber;
import meghanada.session.subscribe.FileWatchEventSubscriber;
import meghanada.session.subscribe.ParseEventSubscriber;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class SessionEventBus {

    private static final Logger log = LogManager.getLogger(Session.class);
    private final EventBus eventBus;
    private final Session session;
    private final ExecutorService executorService;

    SessionEventBus(final Session session) {
        this.session = session;
        this.executorService = Executors.newCachedThreadPool();

        this.eventBus = new AsyncEventBus(executorService, (throwable, subscriberExceptionContext) -> {
            if (!(throwable instanceof RejectedExecutionException)) {
                log.error(throwable.getMessage(), throwable);
            }
        });
    }

    void subscribeFileWatch() {
        this.eventBus.register(new FileWatchEventSubscriber(this));
    }

    void subscribeCompile() {
        this.eventBus.register(new CompileEventSubscriber(this));
    }

    void subscribeParse() {
        this.eventBus.register(new ParseEventSubscriber(this));
    }

    void subscribeCache() {
        this.eventBus.register(new CacheEventSubscriber(this));
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

    public void requestClassCache() {
        this.eventBus.post(new ClassCacheRequest(this.session));
    }

    public void requestCompile(File file) {
        this.eventBus.post(new CompileRequest(this.session, file));
    }

    public void requestCompileFiles(List<File> files) {
        this.eventBus.post(new CompileFilesRequest(this.session, files));
    }

    public void requestTestCompile(File file) {
        this.eventBus.post(new TestCompileRequest(this.session, file));
    }

    public void requestParse(File file) {
        this.eventBus.post(new ParseRequest(this.session, file));
    }

    public void requestFileWatch(List<File> files) {
        this.eventBus.post(new FileWatchRequest(this.session, files));
    }

    public void requestParseFiles(List<File> files) {
        this.eventBus.post(new ParseFilesRequest(this.session, files));
    }

    static abstract class IORequest {

        protected Session session;
        protected File file;

        IORequest(Session session, File file) {
            this.session = session;
            this.file = file;
        }

        public Session getSession() {
            return session;
        }

        public File getFile() {
            return file;
        }

        @Override
        public String toString() {
            return MoreObjects.toStringHelper(this)
                    .add("session", session)
                    .add("file", file)
                    .toString();
        }
    }

    static abstract class IOListRequest {

        protected Session session;
        protected List<File> files;

        IOListRequest(Session session, List<File> files) {
            this.session = session;
            this.files = files;
        }

        public Session getSession() {
            return session;
        }

        public List<File> getFiles() {
            return files;
        }

    }

    public static class ClassCacheRequest extends IORequest {

        public ClassCacheRequest(final Session session) {
            super(session, null);
        }

    }

    public static class CompileRequest extends IORequest {

        public CompileRequest(Session session, File file) {
            super(session, file);
        }

    }

    public static class TestCompileRequest extends IORequest {

        public TestCompileRequest(Session session, File file) {
            super(session, file);
        }

    }

    public static class ParseRequest extends IORequest {

        public ParseRequest(Session session, File file) {
            super(session, file);
        }

    }

    public static class FileWatchRequest extends IOListRequest {

        public FileWatchRequest(Session session, List<File> files) {
            super(session, files);
        }

    }

    public static class ParseFilesRequest extends IOListRequest {

        public ParseFilesRequest(Session session, List<File> files) {
            super(session, files);
        }

    }

    public static class CompileFilesRequest extends IOListRequest {

        public CompileFilesRequest(Session session, List<File> files) {
            super(session, files);
        }

    }


}

