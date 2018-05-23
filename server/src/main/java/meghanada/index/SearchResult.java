package meghanada.index;

import static java.util.Objects.isNull;

public class SearchResult {

  final String category;
  private final String filePath;
  private final String lineNumber;
  private final String contents;

  public SearchResult(
      final String filePath, final String lineNumber, final String contents, String category) {
    this.filePath = filePath;
    this.lineNumber = lineNumber;
    this.contents = contents;
    this.category = isNull(category) ? "" : category;
  }

  @Override
  public String toString() {
    return filePath + ':' + lineNumber + ":\n" + contents;
  }
}
