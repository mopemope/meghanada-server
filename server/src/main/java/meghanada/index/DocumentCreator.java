package meghanada.index;

import java.io.IOException;
import org.apache.lucene.document.Document;

@FunctionalInterface
public interface DocumentCreator {

  public Document create() throws IOException;
}
