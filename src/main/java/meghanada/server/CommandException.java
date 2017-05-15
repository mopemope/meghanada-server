package meghanada.server;

public class CommandException extends RuntimeException {

    private static final long serialVersionUID = 7546398760925908568L;

    public CommandException(final Throwable cause) {
        super(cause);
    }

}
