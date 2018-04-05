package meghanada.index;

import java.util.List;
import org.apache.lucene.document.Document;

public interface SearchIndexable {

  String GROUP_ID = "GROUP_ID";
  String LINE_NUMBER = "LINE_NUMBER";
  String CATEGORY = "category";

  String getIndexGroupId();

  List<Document> getDocumentIndices();
}
