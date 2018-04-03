package meghanada.index;

import java.util.List;
import org.apache.lucene.document.Document;

public interface SearchIndexable {

  String GROUP_ID = "GROUP_ID";
  String LINE_NUMBER = "LINE_NUMBER";
  String CONTENTS = "CONTENTS";
  String CLASS_NAME = "CLASS_NAME";
  String METHOD_NAME = "METHOD_NAME";
  String SYMBOL_NAME = "SYMBOL_NAME";

  String getIndexGroupId();

  List<Document> getDocumentIndices();
}
