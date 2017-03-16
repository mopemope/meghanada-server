package meghanada.server;

import javax.annotation.Nonnull;

public class CommandException extends RuntimeException {

    public CommandException(@Nonnull final Throwable cause) {
        super(cause);
    }

}
