package meghanada.index;

import java.io.IOException;
import java.util.Optional;
import org.apache.lucene.document.Document;

@FunctionalInterface
public interface DocumentConverter<T> {

  Optional<T> convert(Document d) throws IOException;
}
