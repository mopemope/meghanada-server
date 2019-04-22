package meghanada.analyze;

import com.google.common.base.MoreObjects;
import java.io.Serializable;

public abstract class AccessSymbol implements Serializable, Symbol {

  static final int SCOPE_LIMIT = 32;
  private static final long serialVersionUID = -135641377128159037L;

  public String declaringClass;
  public String scope = "";
  public final String name;
  public final int pos;
  public final Range range;
  public String returnType;
  public int argumentIndex = -1;

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
    final long start = this.range.begin.column;
    final long end = this.range.end.column;
    return start <= col && col <= end;
  }

  public abstract boolean match(int line, int column);

  @Override
  public String getFQCN() {
    return this.returnType;
  }
}
