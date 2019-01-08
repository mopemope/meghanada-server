package meghanada.index;

import java.io.IOException;
import org.apache.lucene.document.Document;

@FunctionalInterface
public interface DocumentCreator {

  Document create() throws IOException;
}
