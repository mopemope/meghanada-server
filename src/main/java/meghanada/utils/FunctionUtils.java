package meghanada.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Consumer;
import java.util.function.Function;

public class FunctionUtils {

    public static <T, R> Function<T, R> wrapIO(final IOFunction<T, R> function) {
        return (T t) -> {
            try {
                return function.apply(t);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }

    public static <T> Consumer<T> wrapIOConsumer(final IOConsumer<T> function) {
        return (T t) -> {
            try {
                function.accept(t);
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        };
    }

}
