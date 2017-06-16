package meghanada.location;

import com.google.common.base.MoreObjects;

public class Location {
  private final String path;
  private final long line;
  private final long column;

  public Location(final String path, final long line, final long column) {
    this.path = path;
    this.line = line;
    this.column = column;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("path", path)
        .add("line", line)
        .add("column", column)
        .toString();
  }

  public String getPath() {
    return path;
  }

  public long getLine() {
    return line;
  }

  public long getColumn() {
    return column;
  }
}
