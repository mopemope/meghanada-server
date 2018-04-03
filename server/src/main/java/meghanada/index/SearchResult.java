package meghanada.index;

public class SearchResult {

  private final String filePath;
  private final String lineNumber;
  private final String contents;
  final String category;

  public SearchResult(
      final String filePath, final String lineNumber, final String contents, String category) {
    this.filePath = filePath;
    this.lineNumber = lineNumber;
    this.contents = contents;
    this.category = category;
  }

  @Override
  public String toString() {
    return filePath + ':' + lineNumber + ":\n" + contents;
  }
}
