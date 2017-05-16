package meghanada.analyze;

import com.google.common.base.MoreObjects;

public class Position {

  public int line;
  public int column;

  public Position() {}

  public Position(final int line, final int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("l", line).add("c", column).toString();
  }
}
