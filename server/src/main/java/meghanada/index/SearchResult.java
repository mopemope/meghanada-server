package meghanada.index;

public class SearchResult {

  private final String filePath;
  private final String lineNumber;
  private final String contents;

  public SearchResult(final String filePath, final String lineNumber, final String contents) {
    this.filePath = filePath;
    this.lineNumber = lineNumber;
    this.contents = contents;
  }

  @Override
  public String toString() {
    return filePath + ':' + lineNumber + ":\n" + contents;
  }
}
