package meghanada.analyze;

import com.google.common.base.MoreObjects;

public abstract class AccessSymbol {

  static final int SCOPE_LIMIT = 32;
  public String declaringClass;
  public String scope = "";
  public String name;
  public int pos;
  public Range range;
  public String returnType;
  public int argumentIndex = -1;

  public AccessSymbol() {}

  public AccessSymbol(final String name, final int pos, final Range range) {
    this.name = name;
    this.pos = pos;
    this.range = range;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("declaringClass", declaringClass)
        .add("name", name)
        .add("returnType", returnType)
        .add("range", range)
        .toString();
  }

  public boolean containsLine(final int line) {
    return this.range.containsLine(line);
  }

  public boolean containsColumn(final int col) {
    final int start = this.range.begin.column;
    final int end = this.range.end.column;
    return start <= col && col <= end;
  }

  public abstract boolean match(int line, int column);
}
