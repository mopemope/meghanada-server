package meghanada.cache;

import java.io.File;

class CacheRequest {

    private final File file;
    private final Object target;
    public boolean shutdown;

    public CacheRequest(final File file, final Object target) {
        this.file = file;
        this.target = target;
    }

    public File getFile() {
        return file;
    }

    public Object getTarget() {
        return target;
    }
}
