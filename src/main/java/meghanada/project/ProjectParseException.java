package meghanada.project;

import java.io.IOException;

public class ProjectParseException extends IOException {

    private static final long serialVersionUID = 1L;

    public ProjectParseException(String message) {
        super(message);
    }

    public ProjectParseException(Throwable cause) {
        super(cause);
    }

    public ProjectParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
