package meghanada.index;

import java.util.List;
import org.apache.lucene.document.Document;

public interface SearchIndexable {

  String GROUP_ID = "GROUP_ID";
  String LINE_NUMBER = "LINE_NUMBER";
  String CODE = "code";
  String CLASS_NAME = "class";
  String METHOD_NAME = "method";
  String SYMBOL_NAME = "symbol";

  String getIndexGroupId();

  List<Document> getDocumentIndices();
}
