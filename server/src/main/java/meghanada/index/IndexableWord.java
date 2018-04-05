package meghanada.index;

public class IndexableWord {

  public final Field field;
  public final long line;
  public final long column;
  public final String word;

  public IndexableWord(final Field f, final long line, final long column, final String word) {
    this.field = f;
    this.line = line;
    this.column = column;
    this.word = word;
  }

  public enum Field {
    PACKAGE_NAME("package", true, 1),
    CLASS_NAME("class", true, 2),
    METHOD_NAME("method", true, 3),
    SYMBOL_NAME("symbol", true, 4),
    USAGE("usage", true, 5),
    // declaringClass = dc
    DECLARING_CLASS("dc", false, 98),
    CODE("code", false, 99);

    private final String name;
    private final boolean categorize;
    private final int sortNo;

    Field(final String name, final boolean categorize, int sortNo) {
      this.name = name;
      this.categorize = categorize;
      this.sortNo = sortNo;
    }

    public boolean isCategorize() {
      return categorize;
    }

    public String getName() {
      return name;
    }

    public Integer getSortNo() {
      return sortNo;
    }

    @Override
    public String toString() {
      return name;
    }
  }
}
