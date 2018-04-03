package meghanada.index;

import java.io.IOException;
import org.apache.lucene.document.Document;

@FunctionalInterface
public interface DocumentConverter<T> {

  public T convert(Document d) throws IOException;
}
